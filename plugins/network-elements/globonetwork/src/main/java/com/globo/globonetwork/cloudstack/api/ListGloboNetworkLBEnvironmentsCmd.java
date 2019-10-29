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
import org.apache.cloudstack.api.response.NetworkResponse;
import org.apache.cloudstack.api.response.PhysicalNetworkResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.GloboNetworkLoadBalancerEnvironment;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;

@APICommand(name = "listGloboNetworkLBEnvironments", responseObject = GloboNetworkLBEnvironmentResponse.class, description = "Lists GloboNetwork LB environments")
public class ListGloboNetworkLBEnvironmentsCmd extends BaseCmd {

    public static final Logger s_logger = Logger.getLogger(ListGloboNetworkLBEnvironmentsCmd.class);
    private static final String s_name = "listglobonetworklbenvironmentsresponse";

    @Inject
    GloboNetworkService _globoNetworkService;

    @Parameter(name = ApiConstants.PHYSICAL_NETWORK_ID, type = CommandType.UUID, entityType = PhysicalNetworkResponse.class, required = true, description = "the Physical Network ID")
    private Long physicalNetworkId;

    @Parameter(name = ApiConstants.NETWORK_ID, type = CommandType.UUID, entityType = NetworkResponse.class, required = false, description = "the Network ID")
    private Long networkId;

    @Parameter(name = "environmentid", type = CommandType.LONG, required = false, description = "the Id of environment in GloboNetwork")
    private Long globoNetworkEnvironmentId;

    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public Long getGloboNetworkEnvironmentId() {
        return globoNetworkEnvironmentId;
    }

    /* Implementation */
    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException {
        try {
            s_logger.debug("listGloboNetworkLBEnvironmentsCmd command with physicalnetwork=" + physicalNetworkId + " and environmentId=" + globoNetworkEnvironmentId
                    + " and networkId=" + this.networkId);
            List<GloboNetworkLoadBalancerEnvironment> globoNetworkLBEnvironments = _globoNetworkService.listGloboNetworkLBEnvironmentsFromDB(this.physicalNetworkId,
                    this.networkId, this.globoNetworkEnvironmentId);

            List<GloboNetworkLBEnvironmentResponse> responseList = new ArrayList<GloboNetworkLBEnvironmentResponse>();

            for (GloboNetworkLoadBalancerEnvironment globoNetworkLBEnvironmentVO : globoNetworkLBEnvironments) {
                GloboNetworkLBEnvironmentResponse lbNetworkResponse = new GloboNetworkLBEnvironmentResponse();
                lbNetworkResponse.setId(globoNetworkLBEnvironmentVO.getId());
                lbNetworkResponse.setName(globoNetworkLBEnvironmentVO.getName());
                lbNetworkResponse.setGloboNetworkEnvironmentId(globoNetworkLBEnvironmentVO.getGloboNetworkEnvironmentRefId());
                lbNetworkResponse.setGloboNetworkLBEnvironmentId(globoNetworkLBEnvironmentVO.getGloboNetworkLoadBalancerEnvironmentId());
                lbNetworkResponse.setObjectName("globonetworklbenvironments");
                responseList.add(lbNetworkResponse);
            }

            ListResponse<GloboNetworkLBEnvironmentResponse> response = new ListResponse<GloboNetworkLBEnvironmentResponse>();
            response.setResponses(responseList);
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
}
