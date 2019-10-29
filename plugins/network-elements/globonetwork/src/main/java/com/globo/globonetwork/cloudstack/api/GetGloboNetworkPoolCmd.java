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
import javax.inject.Inject;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.PoolResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

@APICommand(name = "getGloboNetworkPool", description = "Get pool from globonetwork.", responseObject = PoolResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class GetGloboNetworkPoolCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(GetGloboNetworkPoolCmd.class.getName());

    private static final String s_name = "getglobonetworkpoolresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name= "poolid", type = CommandType.LONG, entityType = PoolResponse.class, description = "the ID of the pool load balancer rule")
    private Long poolId;

    @Parameter(name= ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "the ID of the zone")
    private Long zoneId;


    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////


    public Long getPoolId() {
        return poolId;
    }

    public void setPoolId(Long poolId) {
        this.poolId = poolId;
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
        GloboNetworkPoolResponse.Pool pool = _globoNetworkService.getPoolById(getPoolId(), getZoneId());

        PoolResponse poolResp = new PoolResponse();
        poolResp.setId(pool.getId());
        poolResp.setName(pool.getIdentifier());
        poolResp.setLbMethod(pool.getLbMethod());
        poolResp.setPort(pool.getPort());
        poolResp.setHealthcheckType(pool.getHealthcheckType());
        poolResp.setHealthcheck(pool.getHealthcheck());
        poolResp.setExpectedHealthcheck(pool.getExpectedHealthcheck());
        poolResp.setMaxconn(pool.getMaxconn());

        poolResp.setObjectName("globonetworkpool");
        poolResp.setResponseName(getCommandName());
        this.setResponseObject(poolResp);
    }

    private Long getZoneId() {

        return zoneId;
    }

    public void setZoneId(long zoneId) {
        this.zoneId = zoneId;
    }
}
