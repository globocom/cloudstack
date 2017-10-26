/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.globo.globonetwork.cloudstack.resource;

import com.cloud.agent.api.Answer;
import com.cloud.network.lb.LoadBalancingRule;
import com.globo.globonetwork.client.api.GloboNetworkAPI;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.model.VipEnvironment;
import com.globo.globonetwork.client.model.VipPoolMap;
import com.globo.globonetwork.client.model.VipV3;
import com.globo.globonetwork.client.model.OptionVipV3;
import com.globo.globonetwork.client.model.Ip;
import com.globo.globonetwork.client.model.pool.PoolV3;
import com.globo.globonetwork.cloudstack.commands.ApplyVipInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.manager.HealthCheckHelper;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;
import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

public class VipAPIFacade {

    private VipV3 vip;
    private Ip ip;
    private GloboNetworkAPI globoNetworkAPI;

    private static final String DEFAULT_CACHE = "(nenhum)";
    private static final Integer DEFAULT_TIMEOUT = 5;
    private static final String DEFAULT_TRAFFIC_RETURN = "Normal";
    private static final String DSR_TRAFFIC_RETURN = "DSRL3";
    private static final Logger s_logger = Logger.getLogger(VipAPIFacade.class);

    public VipAPIFacade(Long id, GloboNetworkAPI globoNetworkAPI) throws GloboNetworkException {
        this.globoNetworkAPI = globoNetworkAPI;
        if(id != null){
            vip = globoNetworkAPI.getVipV3API().getById(id);
        }
    }

    Boolean hasVip(){
        return vip != null;
    }

    public VipAPIFacade save(ApplyVipInGloboNetworkCommand cmd, String host, VipEnvironment vipEnvironment, Ip ip, List<VipPoolMap> vipPoolMapping) throws GloboNetworkException {
        this.ip = ip;
        VipV3 vip = new VipV3();
        vip.setName(host);
        vip.setService(cmd.getServiceName());
        vip.setBusiness(cmd.getBusinessArea());
        vip.setEnvironmentVipId(vipEnvironment.getId());
        vip.setIpv4Id(ip.getId());
        vip.setOptions(buildVipOptions(cmd));

        OptionVipV3 l7Rule = getProtocolOption(vipEnvironment.getId(), "l7_rule", "default_vip");
        List<VipV3.Port> ports = new ArrayList<>();

        String l4ProtocolString;
        String l7ProtocolString;

        for(VipPoolMap vipPoolMap : vipPoolMapping){

            String healthcheckType = cmd.getHealthcheckType();
            Integer vipPort = vipPoolMap.getPort();


            l4ProtocolString = HealthCheckHelper.getL4Protocol(healthcheckType, vipPort);
            l7ProtocolString = HealthCheckHelper.getL7Protocol(healthcheckType, vipPort);


            VipV3.Port port = createPort(vipEnvironment, vipPoolMap.getPoolId(), vipPort, l4ProtocolString, l7ProtocolString, l7Rule);
            ports.add(port);
        }
        vip.setPorts(ports);

        globoNetworkAPI.getVipV3API().save(vip);

        this.vip = globoNetworkAPI.getVipV3API().getById(vip.getId());
        return this;
    }

    public VipV3.Port createPort(VipEnvironment vipEnvironment, Long poolId, Integer vipPort, String l4ProtocolValue, String l7ProtocolValue, OptionVipV3 defaultL7Rule) throws GloboNetworkException {

        OptionVipV3 l4Protocol = getProtocolOption(vipEnvironment.getId(), "l4_protocol", l4ProtocolValue);
        OptionVipV3 l7Protocol = getProtocolOption(vipEnvironment.getId(), "l7_protocol", l7ProtocolValue);

        VipV3.Port port = new VipV3.Port();
        port.setPort(vipPort);
        port.setOptions(new VipV3.PortOptions(l4Protocol.getId(), l7Protocol.getId()));

        VipV3.Pool pool = new VipV3.Pool(poolId, defaultL7Rule.getId(), null);
        port.setPools(Collections.singletonList(pool));
        return port;
    }

    public VipAPIFacade update(ApplyVipInGloboNetworkCommand cmd, Ip ip, List<VipPoolMap> vipPoolMapping) throws GloboNetworkException {
        this.ip = ip;
        VipV3 result;
        String lbPersistence = getPersistenceMethod(cmd.getPersistencePolicy());
        OptionVipV3 persistence = getProtocolOption(cmd.getVipEnvironmentId(), "Persistencia", lbPersistence);

        if (vip.getCreated()) {
            if(!persistence.getId().equals(vip.getOptions().getPersistenceId())) {
                globoNetworkAPI.getVipV3API().updatePersistence(vip.getId(), persistence.getId());
            }
            result = vip;
        }else{
            vip.setOptions(buildVipOptions(cmd));
            result = globoNetworkAPI.getVipV3API().save(vip);
        }
        this.vip = result;
        return this;
    }

    public OptionVipV3 getProtocolOption(Long vipEnvid, String protocol, String protocolString) throws GloboNetworkException {

        List<OptionVipV3> optionsByTypeAndName = globoNetworkAPI.getOptionVipV3API().findOptionsByTypeAndName(vipEnvid, protocol, protocolString);

        if (optionsByTypeAndName.size() == 0) {
            throw new GloboNetworkException("Integration problem, could not find option '"+ protocolString + "' for protocol '" + protocol + "' in environment vip '" + vipEnvid + "' in NetworkAPI. Please, contact you system administrator to register that option in NetworkAPI." );
        }

        return optionsByTypeAndName.get(0);
    }

    void deploy() throws GloboNetworkException {
        if (!vip.getCreated()) {
            s_logger.info("Requesting GloboNetwork to create vip " + vip.getId());
            globoNetworkAPI.getVipV3API().deploy(vip.getId());
        }
    }

    Answer createVipResponse(ApplyVipInGloboNetworkCommand cmd) {
        if (vip == null || vip.getId() == null) {
            return new Answer(cmd, false, "Vip request was not created in GloboNetwork");
        }

        return new GloboNetworkVipResponse(
            cmd, vip.getId(), vip.getName(), ip.getIpString(), vip.getIpv4Id(), vip.getEnvironmentVipId(), null, cmd.getCache(),
            cmd.getMethodBal(), getPersistenceMethod(cmd.getPersistencePolicy()), cmd.getHealthcheckType(), cmd.getHealthcheck(),
            0, new ArrayList<String>(), new ArrayList<GloboNetworkVipResponse.Real>(), vip.getCreated()
        );
    }

    void undeploy() throws GloboNetworkException {
        if (vip.getCreated()) {
            s_logger.info("Requesting GloboNetwork to undeploy vip from loadbalancer equipment vip_id=" + vip.getId());
            globoNetworkAPI.getVipV3API().undeploy(vip.getId());
        }
    }

    void delete(Boolean keepIp) throws GloboNetworkException {
        globoNetworkAPI.getVipV3API().delete(vip.getId(), keepIp);
    }

    void addPool(VipEnvironment vipEnvironment, Integer vipPort, String l4protocol,  String l7protocol, PoolV3 poolV3) throws GloboNetworkException {
        OptionVipV3 l7Rule = getProtocolOption(vipEnvironment.getId(), "l7_rule", "default_vip");

        VipV3.Port port = createPort(vipEnvironment, poolV3.getId(), vipPort, l4protocol, l7protocol, l7Rule);
        vip.getPorts().add(port);
        globoNetworkAPI.getVipV3API().deployUpdate(vip);
    }

    void removePool(Long poolId) throws GloboNetworkException {
        Iterator<VipV3.Port> portIterator = vip.getPorts().iterator();
        while(portIterator.hasNext()){
            VipV3.Port port = portIterator.next();
            Iterator<VipV3.Pool> poolIterator = port.getPools().iterator();

            while(poolIterator.hasNext()){
                VipV3.Pool pool = poolIterator.next();
                if(pool.getPoolId().equals(poolId)) {
                    if(port.getPools().size() == 1){
                        portIterator.remove();
                    }else{
                        poolIterator.remove();
                    }
                    globoNetworkAPI.getVipV3API().deployUpdate(vip);
                    return;
                }
            }
        }
    }

    private VipV3.VipOptions buildVipOptions(ApplyVipInGloboNetworkCommand cmd) throws GloboNetworkException {
        String cache = cmd.getCache() == null ? DEFAULT_CACHE : cmd.getCache();
        String lbPersistence = getPersistenceMethod(cmd.getPersistencePolicy());
        Long environment = cmd.getVipEnvironmentId();

        OptionVipV3 cacheGroup = getProtocolOption(environment, "cache", cache);
        OptionVipV3 trafficReturn = getProtocolOption(environment, "Retorno de trafego", cmd.isDsr() ? DSR_TRAFFIC_RETURN : DEFAULT_TRAFFIC_RETURN);
        OptionVipV3 timeout = getProtocolOption(environment, "timeout", String.valueOf(DEFAULT_TIMEOUT));
        OptionVipV3 persistence = getProtocolOption(environment, "Persistencia", lbPersistence);

        return new VipV3.VipOptions(cacheGroup.getId(), trafficReturn.getId(), timeout.getId(), persistence.getId());
    }

    List<Long> getPoolIds() throws GloboNetworkException {
        if(vip != null) {
            Long defaultVipL7Rule = getProtocolOption(vip.getEnvironmentVipId(), "l7_rule", "default_vip").getId();
            Set<Long> poolIdSet = new HashSet<>();
            for (VipV3.Port port : vip.getPorts()) {
                for (VipV3.Pool pool : port.getPools()) {
                    if (pool.getL7RuleId().equals(defaultVipL7Rule)) {
                        poolIdSet.add(pool.getPoolId());
                    }
                }
            }
            return new ArrayList<>(poolIdSet);
        }else{
            return new ArrayList<>();
        }
    }

    boolean isDSRVipEnabled(Long environmentId) throws GloboNetworkException {
        List<OptionVipV3> trafficReturns = globoNetworkAPI.getOptionVipV3API().findOptionsByTypeAndName(environmentId, "Retorno de trafego", DSR_TRAFFIC_RETURN);
        return trafficReturns.size() > 0;
    }

    protected static String getPersistenceMethod(LoadBalancingRule.LbStickinessPolicy persistencePolicy) {
        return GloboNetworkResource.PersistenceMethod.fromPersistencePolicy(persistencePolicy);
    }

    @Override
    public String toString() {
        return "VipAPIFacade{" + vip + "}";
    }

    public VipV3 getVip() {
        return vip;
    }

    protected void setVip(VipV3 vip) {
        this.vip = vip;
    }
}