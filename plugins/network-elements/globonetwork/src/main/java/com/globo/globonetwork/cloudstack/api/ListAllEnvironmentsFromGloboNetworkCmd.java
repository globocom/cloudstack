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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PhysicalNetworkResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAllEnvironmentResponse.Environment;
import com.globo.globonetwork.cloudstack.response.GloboNetworkEnvironmentExternalResponse;

@APICommand(name = "listAllEnvironmentsFromGloboNetwork", responseObject = GloboNetworkEnvironmentExternalResponse.class, description = "Lists all environments from GloboNetwork")
public class ListAllEnvironmentsFromGloboNetworkCmd extends BaseCmd {

    public static final Logger s_logger = Logger.getLogger(ListAllEnvironmentsFromGloboNetworkCmd.class.getName());
    private static final String s_name = "listallenvironmentsfromglobonetworkresponse";

    @Inject
    GloboNetworkService _globoNetworkService;

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.PHYSICAL_NETWORK_ID, type = CommandType.UUID, entityType = PhysicalNetworkResponse.class, required = true, description = "the Physical Network ID")
    private Long physicalNetworkId;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    /* Implementation */
    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException {
        s_logger.debug("listAllEnvironmentsFromGloboNetworkCmd command");
        List<Environment> environmentList = _globoNetworkService.listAllEnvironmentsFromGloboNetwork(physicalNetworkId);
        if (environmentList != null) {
            List<GloboNetworkEnvironmentExternalResponse> responseList = new ArrayList<GloboNetworkEnvironmentExternalResponse>();
            for (Environment environment : environmentList) {
                GloboNetworkEnvironmentExternalResponse envResponse = new GloboNetworkEnvironmentExternalResponse();
                envResponse.setEnvironmentId(environment.getId());
                envResponse.setEnvironmentFullName(environment.getDcDivisionName() + " - " + environment.getLogicalEnvironmentName() + " - " + environment.getL3GroupName());
                envResponse.setObjectName("globonetworkenvironment");
                responseList.add(envResponse);
            }

            ListResponse<GloboNetworkEnvironmentExternalResponse> response = new ListResponse<GloboNetworkEnvironmentExternalResponse>();
            response.setResponses(responseList);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to retrieve all environments from GloboNetwork.");
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
}
