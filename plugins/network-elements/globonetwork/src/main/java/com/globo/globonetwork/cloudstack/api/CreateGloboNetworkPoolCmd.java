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
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import com.globo.globonetwork.cloudstack.manager.Protocol;
import javax.inject.Inject;

import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.FirewallRuleResponse;
import org.apache.cloudstack.api.response.PoolResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

@APICommand(name = "createGloboNetworkPool", description = "Creates a new pool to a load balancer", responseObject = PoolResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateGloboNetworkPoolCmd extends BaseAsyncCmd {

    public static final Logger s_logger = Logger.getLogger(CreateGloboNetworkPoolCmd.class.getName());

    private static final String s_name = "createglobonetworkpoolresponse";

    @Parameter(name= ApiConstants.ZONE_ID, type = CommandType.UUID, required = true, entityType = ZoneResponse.class, description = "the ID of the zone")
    private Long zoneId;

    @Parameter(name= ApiConstants.LBID, type = CommandType.UUID, required = true, entityType = FirewallRuleResponse.class, description = "the ID of the load balancer rule")
    private Long lbId;

    @Parameter(name = ApiConstants.PUBLIC_PORT, type = CommandType.INTEGER, required = true, description = "the public port from where the network traffic will be load balanced from")
    private Integer publicPort;

    @Parameter(name = ApiConstants.PRIVATE_PORT, type = CommandType.INTEGER, required = true, description = "the private port of the private ip address/virtual machine where the network traffic will be load balanced to")
    private Integer privatePort;

    @Parameter(name = ApiConstants.L4_PROTOCOL, type = CommandType.STRING, description = "layer 4 protocol for connection between vip and pool")
    private String l4Protocol = Protocol.L4.TCP.getNetworkApiOptionValue();

    @Parameter(name = ApiConstants.L7_PROTOCOL, type = CommandType.STRING, description = "layer 7 protocol for connection between vip and pool")
    private String l7Protocol = Protocol.L7.OTHERS.getNetworkApiOptionValue();

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

        validateParams();
        GloboNetworkPoolResponse.Pool pool = _globoNetworkService.createPool(this);
        PoolResponse poolResp = new PoolResponse();
        if(pool != null) {
            poolResp.setId(pool.getId());
            poolResp.setName(pool.getIdentifier());
            poolResp.setLbMethod(pool.getLbMethod());
            poolResp.setPort(pool.getPort());
            poolResp.setHealthcheckType(pool.getHealthcheckType());
            poolResp.setHealthcheck(pool.getHealthcheck());
            poolResp.setExpectedHealthcheck(pool.getExpectedHealthcheck());
            poolResp.setMaxconn(pool.getMaxconn());
            poolResp.setObjectName("globonetworkpool");
        }
        poolResp.setResponseName(getCommandName());
        this.setResponseObject(poolResp);
    }

    protected void validateParams() {
        if (l4Protocol == null) {
            throw new CloudRuntimeException("l4protocol can not be null.");
        }

        if (l7Protocol == null) {
            throw new CloudRuntimeException("l7protocol can not be null.");
        }

        Protocol.L4 l4 = Protocol.L4.valueOfFromNetworkAPI(l4Protocol);
        Protocol.L7 l7 = Protocol.L7.valueOfFromNetworkAPI(l7Protocol);

        if (!Protocol.validProtocols(l4, l7)) {
            throw new CloudRuntimeException("l4protocol with value '" + l4.getNetworkApiOptionValue() + "' does not match with l7protocol '" + l7.getNetworkApiOptionValue() + "'. Possible l7 value(s): " + l4.getL7s() + ".");
        }
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

    public Integer getPublicPort() {
        return publicPort;
    }

    public void setPublicPort(Integer publicPort) {
        this.publicPort = publicPort;
    }

    public Integer getPrivatePort() {
        return privatePort;
    }

    public void setPrivatePort(Integer privatePort) {
        this.privatePort = privatePort;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(long zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_LB_CREATE_POOL;
    }

    @Override
    public String getEventDescription() {
        return "Creates a new pool";
    }

    public String getL4Protocol() {
        return l4Protocol;
    }

    public void setL4Protocol(String l4Protocol) {
        this.l4Protocol = l4Protocol;
    }

    public String getL7Protocol() {
        return l7Protocol;
    }

    public void setL7Protocol(String l7Protocol) {
        this.l7Protocol = l7Protocol;
    }


    public Protocol.L4 getL4() {
        return Protocol.L4.valueOfFromNetworkAPI(l4Protocol);
    }
    public Protocol.L7 getL7() {
        return Protocol.L7.valueOfFromNetworkAPI(l7Protocol);
    }
}
