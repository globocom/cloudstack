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

package com.globo.globonetwork.cloudstack.api;

import com.cloud.event.EventTypes;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.FirewallRuleResponse;
import org.apache.cloudstack.api.response.PoolResponse;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import javax.inject.Inject;


@APICommand(name = "deleteGloboNetworkPool", description = "Deletes a pool from a load balancer", responseObject = PoolResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class DeleteGloboNetworkPoolCmd extends BaseAsyncCmd {

    public static final Logger s_logger = Logger.getLogger(DeleteGloboNetworkPoolCmd.class.getName());

    private static final String s_name = "deleteglobonetworkpoolresponse";

    @Parameter(name = ApiConstants.ID, type = CommandType.LONG, required = true, description = "the ID of the pool to be removed")
    private Long poolId;

    @Parameter(name= ApiConstants.LBID, type = CommandType.UUID, required = true, entityType = FirewallRuleResponse.class, description = "the ID of the load balancer rule")
    private Long lbId;

    @Parameter(name= ApiConstants.ZONE_ID, type = CommandType.UUID, required = true, entityType = ZoneResponse.class, description = "the ID of the zone")
    private Long zoneId;

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
        _lbService.throwExceptionIfIsChildLoadBalancer(lbId, getActualCommandName());

        _globoNetworkService.deletePool(this);
        setResponseObject(new SuccessResponse(getCommandName()));
    }

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public Long getLbId() {
        return lbId;
    }

    public void setLbId(Long lbId) {
        this.lbId = lbId;
    }

    public Long getPoolId() {
        return poolId;
    }

    public void setPoolId(Long poolId) {
        this.poolId = poolId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(long zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_LB_REMOVE_POOL;
    }

    @Override
    public String getEventDescription() {
        return "Deletes a pool";
    }
}
