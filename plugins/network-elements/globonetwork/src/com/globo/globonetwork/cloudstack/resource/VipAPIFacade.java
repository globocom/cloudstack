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
import com.globo.globonetwork.cloudstack.commands.ApplyVipInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.manager.HealthCheckHelper.HealthCheckType;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

class VipAPIFacade {

    private VipV3 vip;
    private Ip ip;
    private GloboNetworkAPI globoNetworkAPI;

    private static final String DEFAULT_CACHE = "(nenhum)";
    private static final Integer DEFAULT_TIMEOUT = 5;
    private static final String DEFAULT_TRAFFIC_RETURN = "Normal";
    private static final String DSR_TRAFFIC_RETURN = "DSRL3";
    private static final Logger s_logger = Logger.getLogger(VipAPIFacade.class);

    VipAPIFacade(Long id, GloboNetworkAPI globoNetworkAPI) throws GloboNetworkException {
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

        OptionVipV3 l7Rule = globoNetworkAPI.getOptionVipV3API().findOptionsByTypeAndName(vipEnvironment.getId(), "l7_rule", "default_vip").get(0);
        List<VipV3.Port> ports = new ArrayList<>();
        for(VipPoolMap vipPoolMap : vipPoolMapping){
            String l4ProtocolString;
            String l7ProtocolString;

            if(HealthCheckType.isLayer7(cmd.getHealthcheckType())){
                l4ProtocolString = HealthCheckType.TCP.name();
                if (vipPoolMap.getPort() == 443 || vipPoolMap.getPort() == 8443) {
                    l7ProtocolString = HealthCheckType.HTTPS.name();
                } else if (vipPoolMap.getPort() == 80 || vipPoolMap.getPort() == 8080) {
                    l7ProtocolString = HealthCheckType.HTTP.name();
                } else{
                    l7ProtocolString = cmd.getHealthcheckType().toUpperCase();
                }
            } else{
                l4ProtocolString = cmd.getHealthcheckType().toUpperCase();
                l7ProtocolString = "Outros";
            }

            OptionVipV3 l4Protocol = globoNetworkAPI.getOptionVipV3API().findOptionsByTypeAndName(vipEnvironment.getId(), "l4_protocol", l4ProtocolString).get(0);
            OptionVipV3 l7Protocol = globoNetworkAPI.getOptionVipV3API().findOptionsByTypeAndName(vipEnvironment.getId(), "l7_protocol", l7ProtocolString).get(0);

            VipV3.Port port = new VipV3.Port();
            port.setPort(vipPoolMap.getPort());
            port.setOptions(new VipV3.PortOptions(l4Protocol.getId(), l7Protocol.getId()));

            VipV3.Pool pool = new VipV3.Pool(vipPoolMap.getPoolId(), l7Rule.getId(), null);
            port.setPools(Collections.singletonList(pool));
            ports.add(port);
        }
        vip.setPorts(ports);

        globoNetworkAPI.getVipV3API().save(vip);

        this.vip = globoNetworkAPI.getVipV3API().getById(vip.getId());
        return this;
    }

    public VipAPIFacade update(ApplyVipInGloboNetworkCommand cmd, Ip ip, List<VipPoolMap> vipPoolMapping) throws GloboNetworkException {
        this.ip = ip;
        VipV3 result;
        String lbPersistence = getPersistenceMethod(cmd.getPersistencePolicy());
        OptionVipV3 persistence = globoNetworkAPI.getOptionVipV3API().findOptionsByTypeAndName(cmd.getVipEnvironmentId(), "Persistencia", lbPersistence).get(0);

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

    private VipV3.VipOptions buildVipOptions(ApplyVipInGloboNetworkCommand cmd) throws GloboNetworkException {
        String cache = cmd.getCache() == null ? DEFAULT_CACHE : cmd.getCache();
        String lbPersistence = getPersistenceMethod(cmd.getPersistencePolicy());
        Long environment = cmd.getVipEnvironmentId();

        OptionVipV3 cacheGroup = globoNetworkAPI.getOptionVipV3API().findOptionsByTypeAndName(environment, "cache", cache).get(0);
        OptionVipV3 trafficReturn = globoNetworkAPI.getOptionVipV3API().findOptionsByTypeAndName(environment, "Retorno de trafego", cmd.isDsr() ? DSR_TRAFFIC_RETURN : DEFAULT_TRAFFIC_RETURN).get(0);
        OptionVipV3 timeout = globoNetworkAPI.getOptionVipV3API().findOptionsByTypeAndName(environment, "timeout", String.valueOf(DEFAULT_TIMEOUT)).get(0);
        OptionVipV3 persistence = globoNetworkAPI.getOptionVipV3API().findOptionsByTypeAndName(environment, "Persistencia", lbPersistence).get(0);

        return new VipV3.VipOptions(cacheGroup.getId(), trafficReturn.getId(), timeout.getId(), persistence.getId());
    }

    List<Long> getPoolIds() throws GloboNetworkException {
        if(vip != null) {
            Long defaultVipL7Rule = globoNetworkAPI.getOptionVipV3API().findOptionsByTypeAndName(vip.getEnvironmentVipId(), "l7_rule", "default_vip").get(0).getId();
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

    protected static String getPersistenceMethod(LoadBalancingRule.LbStickinessPolicy persistencePolicy) {
        return GloboNetworkResource.PersistenceMethod.fromPersistencePolicy(persistencePolicy);
    }

    @Override
    public String toString() {
        return "VipAPIFacade{" + vip + "}";
    }

    protected VipV3 getVip() {
        return vip;
    }

    protected void setVip(VipV3 vip) {
        this.vip = vip;
    }
}