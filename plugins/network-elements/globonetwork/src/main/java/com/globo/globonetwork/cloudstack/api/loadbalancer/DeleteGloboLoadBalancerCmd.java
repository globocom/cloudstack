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
package com.globo.globonetwork.cloudstack.api.loadbalancer;

import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import javax.inject.Inject;
import org.apache.log4j.Logger;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandJobType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.FirewallRuleResponse;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.context.CallContext;

import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.user.Account;

@APICommand(name = "deleteGloboLoadBalancer", description = "Deletes a load balancer rule.", responseObject = SuccessResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class DeleteGloboLoadBalancerCmd extends BaseAsyncCmd {
    public static final Logger s_logger = Logger.getLogger(DeleteGloboLoadBalancerCmd.class.getName());
    private static final String s_name = "deletegloboloadbalancerresponse";
    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Inject
    private GloboNetworkService globoNetworkSvc;

    @Parameter(name = ApiConstants.ID,
            type = CommandType.UUID,
            entityType = FirewallRuleResponse.class,
            required = true,
            description = "the ID of the load balancer rule")
    private Long id;

    @Parameter(name = ApiConstants.KEEP_IP,
            type = CommandType.BOOLEAN,
            description = "the equipment should keep Ip for future use?")
    private Boolean keepip;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    public Boolean getKeepip() {
        if(keepip != null)
            return keepip;
        else
            return false;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        LoadBalancer lb = _entityMgr.findById(LoadBalancer.class, getId());
        if (lb != null) {
            return lb.getAccountId();
        }

        return Account.ACCOUNT_ID_SYSTEM; // no account info given, parent this command to SYSTEM so ERROR events are tracked
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_LOAD_BALANCER_DELETE;
    }

    @Override
    public String getEventDescription() {
        return "deleting load balancer: " + getId();
    }

    @Override
    public void execute() {
        _lbService.throwExceptionIfIsParentLoadBalancer(id, getActualCommandName());

        LoadBalancer lb = _lbService.findById(id);
        if (lb == null) {
            throw new CloudRuntimeException("Could not find load balancer " + getId());
        }
        CallContext.current().setEventDetails("Load balancer Id: " + getId());

        boolean result = _firewallService.revokeRelatedFirewallRule(id, true);
        result = result && _lbService.deleteLoadBalancerRule(id, true, getKeepip());

        if (result) {
            if(!getKeepip()) {
                removeIpAddress(lb);
            }
            SuccessResponse response = new SuccessResponse(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to delete load balancer");
        }
    }

    private void removeIpAddress(final LoadBalancer lb) {
        try {
            s_logger.debug("[load_balancer " + lb.getName() + "] removing ipaddress " + lb.getSourceIpAddressId());
            globoNetworkSvc.disassociateIpAddrFromGloboNetwork(lb.getSourceIpAddressId());

        } catch (Exception e) {
            throw new CloudRuntimeException("Load balancer " + lb.getName() + " was deleted, but the IpAddress "+ lb.getSourceIpAddressId() +" could not be released, please contact your system administrator.", e);
        }
    }

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.networkSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        LoadBalancer lb = _lbService.findById(id);
        if (lb == null) {
            throw new InvalidParameterValueException("Unable to find load balancer rule: " + id);
        }
        return lb.getNetworkId();
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.FirewallRule;
    }
}
