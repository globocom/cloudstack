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

package com.globo.globonetwork.cloudstack.commands;

import com.cloud.agent.api.Answer;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.client.api.GloboNetworkAPI;
import com.globo.globonetwork.client.api.PoolAPI;
import com.globo.globonetwork.client.api.VipV3API;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.model.OptionVipV3;
import com.globo.globonetwork.client.model.VipV3;
import com.globo.globonetwork.client.model.pool.PoolV3;
import com.globo.globonetwork.cloudstack.manager.Protocol;
import com.globo.globonetwork.cloudstack.resource.GloboNetworkResource;
import com.globo.globonetwork.cloudstack.resource.VipAPIFacade;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

public class UpdatePoolCommand extends GloboNetworkResourceCommand {

    private static final Logger s_logger = Logger.getLogger(UpdatePoolCommand.class);

    private String expectedHealthcheck;
    private String healthcheck;
    private String healthcheckType;
    private List<Long> poolIds;
    private Integer maxConn;

    private String lbHostname;
    private Protocol.L4 l4Protocol;
    private Protocol.L7 l7Protocol;
    private boolean redeploy;
    private Long vipId;

    public UpdatePoolCommand(List<Long> poolIds) {
        this.poolIds = poolIds;
    }

    public UpdatePoolCommand(List<Long> poolIds, String healthcheckType, String healthcheck, String expectedHealthcheck, Integer maxConn, String lbHostname) {
        this.poolIds = poolIds;
        this.healthcheckType = healthcheckType;
        this.healthcheck = healthcheck;
        this.expectedHealthcheck = expectedHealthcheck;
        this.maxConn = maxConn;
        this.lbHostname = lbHostname;
    }

    public String getLbHostname() {
        return lbHostname;
    }

    public void setLbHostname(String lbHostname) {
        this.lbHostname = lbHostname;
    }

    @Override
    public Answer execute(GloboNetworkAPI api) {
        try {
            PoolAPI poolAPI = api.getPoolAPI();
            List<PoolV3> poolsV3 = poolAPI.getByIdsV3(this.getPoolIds());

            changePortsProtocol(api, poolsV3);

            List<GloboNetworkPoolResponse.Pool> pools = new ArrayList<GloboNetworkPoolResponse.Pool>();

            for (PoolV3 poolv3 : poolsV3) {

                PoolV3.Healthcheck healthCheck = poolv3.getHealthcheck();
                healthCheck.setHealthcheck(this.getHealthcheckType(), this.getHealthcheck(), this.getExpectedHealthcheck() );

                poolv3.setMaxconn(this.getMaxConn());

                for (PoolV3.PoolMember poolMember : poolv3.getPoolMembers()) {
                    poolMember.setLimit(this.getMaxConn());
                }
            }

            if ( poolsV3.size() > 0 ) {
                if (poolsV3.get(0).isPoolCreated()) {
                    poolAPI.updateDeployAll(poolsV3);
                } else {
                    poolAPI.updateAll(poolsV3);
                }
            }

            for (PoolV3 poolv3 : poolsV3) {
                pools.add(GloboNetworkResource.poolV3FromNetworkApi(poolv3));
            }

            GloboNetworkPoolResponse answer = new GloboNetworkPoolResponse(this, pools, true, "");

            return answer;
        } catch (GloboNetworkException e) {
            return GloboNetworkResourceCommand.handleGloboNetworkException(this, e);
        } catch (Exception e) {
            s_logger.error("Generic error accessing GloboNetwork while update pool", e);
            return new Answer(this, false, e.getMessage());
        }
    }

    private void changePortsProtocol(GloboNetworkAPI api, List<PoolV3> poolsV3) throws GloboNetworkException {
        if (getL4Protocol() == null && getL7Protocol() == null) {
            s_logger.debug("No changes to do in l4 and l7 protocols");
            return;
        }

        VipV3API vipV3API = api.getVipV3API();


        VipAPIFacade vipAPIFacade = new VipAPIFacade(this.vipId, api);
        VipV3 vip = vipAPIFacade.getVip();

        OptionVipV3 l4Protocol = vipAPIFacade.getProtocolOption(vip.getEnvironmentVipId(), "l4_protocol", getL4Protocol().getNetworkApiOptionValue());
        OptionVipV3 l7Protocol = vipAPIFacade.getProtocolOption(vip.getEnvironmentVipId(), "l7_protocol", getL7Protocol().getNetworkApiOptionValue());


        boolean hasPortToChange = false;
        for (VipV3.Port port : vip.getPorts()) {

            PoolV3 pool = getPoolToChangePort(port.getPools(), poolsV3);

            if (pool != null){
                VipV3.PortOptions options = port.getOptions();


                if (!options.getL4ProtocolId().equals(l4Protocol.getId())) {
                    options.setL4ProtocolId(l4Protocol.getId());
                    hasPortToChange = true;
                }

                if (!options.getL7ProtocolId().equals(l7Protocol.getId())) {
                    options.setL7ProtocolId(l7Protocol.getId());
                    hasPortToChange = true;
                }

            }
        }


        if (hasPortToChange && !redeploy) {
            throw new CloudRuntimeException("Change ports require undeploy and deploy Vip. Can not change ports protocol if parameter 'redeploy' is false.");
        }

        if (hasPortToChange) {
            if (vip.getCreated()) {
                vipV3API.undeploy(vip.getId());
            }
            vipV3API.save(vip);
            vipV3API.deploy(vip.getId());
        }

    }

    private PoolV3 getPoolToChangePort(List<VipV3.Pool> pools, List<PoolV3> poolV3s) {
        for (PoolV3 poolV3 : poolV3s){
            for(VipV3.Pool pool : pools) {
                if (poolV3.getId().equals(pool.getPoolId())){
                    return poolV3;
                }
            }
        }

        return null;
    }

    public String getExpectedHealthcheck() {
        return expectedHealthcheck;
    }

    public void setExpectedHealthcheck(String expectedHealthcheck) {
        this.expectedHealthcheck = expectedHealthcheck;
    }

    public String getHealthcheck() {
        return healthcheck;
    }

    public void setHealthcheck(String healthcheck) {
        this.healthcheck = healthcheck;
    }

    public String getHealthcheckType() {
        return healthcheckType;
    }

    public void setHealthcheckType(String healthcheckType) {
        this.healthcheckType = healthcheckType;
    }

    public List<Long> getPoolIds() {
        return poolIds;
    }

    public void setPoolIds(List<Long> poolIds) {
        this.poolIds = poolIds;
    }

    public List<Long> getPoolId() {
        return poolIds;
    }

    public void setPoolId(List<Long> poolIds) {
        this.poolIds = poolIds;
    }

    public Integer getMaxConn() { return maxConn; }

    public void setMaxConn(Integer maxConn) { this.maxConn = maxConn; }

    public void setL4Protocol(Protocol.L4 l4Protocol) {
        this.l4Protocol = l4Protocol;
    }

    public void setL7Protocol(Protocol.L7 l7Protocol) {
        this.l7Protocol = l7Protocol;
    }

    public void setRedeploy(boolean redeploy) {
        this.redeploy = redeploy;
    }

    public Protocol.L4 getL4Protocol() {
        return l4Protocol;
    }

    public Protocol.L7 getL7Protocol() {
        return l7Protocol;
    }

    public boolean isRedeploy() {
        return redeploy;
    }

    public Long getVipId() {
        return vipId;
    }

    public void setVipId(Long vipId) {
        this.vipId = vipId;
    }
}
