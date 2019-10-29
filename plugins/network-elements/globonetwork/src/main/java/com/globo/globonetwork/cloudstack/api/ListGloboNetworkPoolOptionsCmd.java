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

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolOptionResponse.PoolOption;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.NetworkResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@APICommand(name = "listGloboNetworkPoolOptions", responseObject = GloboNetworkPoolOptionExternalResponse.class, description = "List GloboNetwork pool options")
public class ListGloboNetworkPoolOptionsCmd extends BaseCmd {

    public static final Logger s_logger = Logger.getLogger(ListGloboNetworkPoolOptionsCmd.class);
    private static final String s_name = "listglobonetworkpooloptionsresponse";

    @Inject
    GloboNetworkService _globoNetworkService;

    @Parameter(name = "lbenvironmentid", type = CommandType.LONG, entityType = GloboNetworkLBEnvironmentResponse.class, description = "Globo Network LB Environment Id", required = true)
    private Long globoNetworkLBEnvironmentId;

    @Parameter(name = ApiConstants.NETWORK_ID, type = CommandType.UUID, entityType = NetworkResponse.class, required = true, description = "The network ID")
    private Long networkId;

    @Parameter(name = ApiConstants.TYPE, required = true, type = CommandType.STRING, entityType = GloboNetworkVipResponse.class, description = "The type of the pool option")
    private String type;

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        try {
            List<PoolOption> poolOptions = _globoNetworkService.listPoolOptions(this.globoNetworkLBEnvironmentId, this.networkId, this.type);

            List<GloboNetworkPoolOptionExternalResponse> responseList = new ArrayList<GloboNetworkPoolOptionExternalResponse>();

            for (PoolOption option : poolOptions) {
                GloboNetworkPoolOptionExternalResponse lbNetworkResponse = new GloboNetworkPoolOptionExternalResponse(option.getId(), option.getName());

                lbNetworkResponse.setObjectName("globonetworkpooloptions");
                responseList.add(lbNetworkResponse);
            }

            ListResponse<GloboNetworkPoolOptionExternalResponse> response = new ListResponse<GloboNetworkPoolOptionExternalResponse>();
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
