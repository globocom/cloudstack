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

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandJobType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.IPAddressResponse;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.IpAddress;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;

@APICommand(name = "disassociateIpAddressFromGloboNetwork", description = "Disassociates an ip address from GloboNetwork.", responseObject = IPAddressResponse.class)
public class DisassociateIpAddrFromGloboNetworkCmd extends BaseAsyncCmd {
    public static final Logger s_logger = Logger.getLogger(DisassociateIpAddrFromGloboNetworkCmd.class.getName());
    private static final String s_name = "disassociateipaddressfromglobonetworkresponse";

    @Inject
    private GloboNetworkService globoNetworkSvc;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = IPAddressResponse.class, required = true, description = "the id of the ip address"
            + " to disassociate")
    private long id;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public long getIpAddressId() {
        return id;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public void execute() throws ResourceUnavailableException, ResourceAllocationException, ConcurrentOperationException, InsufficientCapacityException {
        CallContext.current().setEventDetails("Ip Id: " + getIpAddressId());

        boolean result = globoNetworkSvc.disassociateIpAddrFromGloboNetwork(getIpAddressId());

        if (result) {
            SuccessResponse response = new SuccessResponse(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to disassociate IP address from Globo Network");
        }
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_PORTABLE_IP_RELEASE;
    }

    @Override
    public String getEventDescription() {
        return ("Disassociating ip address from GloboNetwork with id=" + id);
    }

    @Override
    public long getEntityOwnerId() {
        IpAddress ip = getIpAddress(id);
        if (ip == null) {
            throw new InvalidParameterValueException("Unable to find ip address by id=" + id);
        }
        return ip.getAccountId();
    }

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.networkSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        IpAddress ip = getIpAddress(id);
        return ip.getAssociatedWithNetworkId();
    }

    private IpAddress getIpAddress(long id) {
        IpAddress ip = _entityMgr.findById(IpAddress.class, id);

        if (ip == null) {
            throw new InvalidParameterValueException("Unable to find ip address by id=" + id);
        } else {
            return ip;
        }
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.IpAddress;
    }

    @Override
    public Long getInstanceId() {
        return getIpAddressId();
    }
}
