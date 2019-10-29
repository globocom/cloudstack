// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.globo.globonetwork.cloudstack.api;

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.PhysicalNetworkResponse;
import org.apache.cloudstack.context.CallContext;

import com.cloud.api.ApiDBUtils;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.GloboNetworkEnvironmentVO;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;

@APICommand(name = "addGloboNetworkEnvironment", responseObject = GloboNetworkEnvironmentResponse.class, description = "Adds a GloboNetwork environment to a zone")
public class AddGloboNetworkEnvironmentCmd extends BaseAsyncCmd {

    private static final String s_name = "addglobonetworkenvironmentresponse";
    @Inject
    GloboNetworkService _globoNetworkService;

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.PHYSICAL_NETWORK_ID, type = CommandType.UUID, entityType = PhysicalNetworkResponse.class, required = true, description = "the Physical Network ID")
    private Long physicalNetworkId;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "Name of the relationship between zone and GloboNetwork environment (like BACKEND, FRONTEND)")
    private String name;

    @Parameter(name = "napienvironmentid", type = CommandType.LONG, required = true, description = "the Id of environment in GloboNetwork")
    private Long globoNetworkEnvironmentId;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    public String getName() {
        return name;
    }

    public Long getGloboNetworkEnvironmentId() {
        return globoNetworkEnvironmentId;
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException {
        try {
            GloboNetworkEnvironmentVO napiEnvironmentVO = _globoNetworkService.addGloboNetworkEnvironment(physicalNetworkId, name, globoNetworkEnvironmentId);
            GloboNetworkEnvironmentResponse response = new GloboNetworkEnvironmentResponse();
            response.setId(napiEnvironmentVO.getId());
            response.setName(napiEnvironmentVO.getName());
            response.setPhysicalNetworkId(ApiDBUtils.findPhysicalNetworkById(napiEnvironmentVO.getPhysicalNetworkId()).getUuid());
            response.setNapiEnvironmentId(napiEnvironmentVO.getGloboNetworkEnvironmentId());
            response.setObjectName("globonetworkenvironment");
            response.setResponseName(getCommandName());
            this.setResponseObject(response);

        } catch (InvalidParameterValueException invalidParamExcp) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, invalidParamExcp.getMessage());
        } catch (CloudRuntimeException runtimeExcp) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, runtimeExcp.getMessage());
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    @Override
    public String getEventType() {
        //EventTypes.EVENT_NETWORK_CREATE
        return EventTypes.EVENT_NETWORK_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "Adding a GloboNetwork Environment to Zone";
    }

}
