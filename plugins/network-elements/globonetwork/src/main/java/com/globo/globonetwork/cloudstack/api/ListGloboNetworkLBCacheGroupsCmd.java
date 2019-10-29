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

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.NetworkResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@APICommand(name = "listGloboNetworkLBCacheGroups", responseObject = GloboNetworkLBCacheGroupResponse.class, description = "Lists GloboNetwork LB cache groups")
public class ListGloboNetworkLBCacheGroupsCmd extends BaseCmd {

    public static final Logger s_logger = Logger.getLogger(ListGloboNetworkLBCacheGroupsCmd.class);
    private static final String s_name = "listglobonetworklbcachegroupsresponse";

    @Inject
    GloboNetworkService _globoNetworkService;

    @Parameter(name = "lbenvironment", type = CommandType.LONG, required = true, description = "the Id of LB environment in GloboNetwork")
    private Long globoNetworkLBEnvironmentId;

    @Parameter(name = ApiConstants.NETWORK_ID, type = CommandType.UUID, entityType = NetworkResponse.class, required = true, description = "the network ID")
    private Long networkId;

    public Long getGloboNetworkLBEnvironmentId() {
        return globoNetworkLBEnvironmentId;
    }

    public Long getNetworkId() { return networkId; }

    /* Implementation */
    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException {
        try {
            s_logger.debug("listGloboNetworkLBCacheGroupsCmd command with LBEnvironmentId=" + globoNetworkLBEnvironmentId);
            List<String> globoNetworkLBCacheGroups = _globoNetworkService.listGloboNetworkLBCacheGroups(getGloboNetworkLBEnvironmentId(), getNetworkId());

            List<GloboNetworkLBCacheGroupResponse> responseList = new ArrayList<GloboNetworkLBCacheGroupResponse>();

            for (String globoNetworkLBCacheGroup : globoNetworkLBCacheGroups) {
                GloboNetworkLBCacheGroupResponse lbCacheGroupResponse = new GloboNetworkLBCacheGroupResponse();
                lbCacheGroupResponse.setName(globoNetworkLBCacheGroup);
                lbCacheGroupResponse.setObjectName("globonetworkcachegroups");
                responseList.add(lbCacheGroupResponse);
            }

            ListResponse<GloboNetworkLBCacheGroupResponse> response = new ListResponse<GloboNetworkLBCacheGroupResponse>();
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
