// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.globo.globonetwork.cloudstack.api;

import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.FirewallRuleResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PoolResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

@APICommand(name = "listGloboNetworkPools", description = "Lists pools .", responseObject = PoolResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListGloboNetworkPoolsCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(ListGloboNetworkPoolsCmd.class.getName());

    private static final String s_name = "listglobonetworkpoolresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name= ApiConstants.LBID, type = CommandType.UUID, entityType = FirewallRuleResponse.class, required=true, description = "the ID of the load balancer rule")
    private Long lbId;

    @Parameter(name= ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, required=true,  description = "the ID of the zone")
    private Long zoneId;


    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////


    public Long getLbId() {
        return lbId;
    }


    public Long getZoneId() {
        return zoneId;
    }


    @Inject
    GloboNetworkManager _globoNetworkService;

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    @Override
    public void execute() {
        ListResponse<PoolResponse> response = new ListResponse<PoolResponse>();

        List<GloboNetworkPoolResponse.Pool> pools = _globoNetworkService.listAllPoolByVipId(getLbId(), getZoneId());

        List<PoolResponse> lbResponses = new ArrayList<>();

        for ( GloboNetworkPoolResponse.Pool pool : pools) {

            PoolResponse poolResp = new PoolResponse();
            poolResp.setId(pool.getId());
            poolResp.setName(pool.getIdentifier());
            poolResp.setLbMethod(pool.getLbMethod());
            poolResp.setPort(pool.getPort());
            poolResp.setHealthcheckType(pool.getHealthcheckType());
            poolResp.setHealthcheck(pool.getHealthcheck());
            poolResp.setExpectedHealthcheck(pool.getExpectedHealthcheck());
            poolResp.setMaxconn(pool.getMaxconn());
            poolResp.setVipPort(pool.getVipPort());
            poolResp.setL4Protocol(pool.getL4protocol());
            poolResp.setL7Protocol(pool.getL7protocol());


            poolResp.setObjectName("globonetworkpool");
            lbResponses.add(poolResp);

        }

        response.setResponses(lbResponses);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }


    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public void setLbId(Long lbId) {
        this.lbId = lbId;
    }
}
