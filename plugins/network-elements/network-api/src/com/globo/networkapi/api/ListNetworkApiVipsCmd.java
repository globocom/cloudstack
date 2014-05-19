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

package com.globo.networkapi.api;

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
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.log4j.Logger;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.user.UserContext;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.networkapi.manager.NetworkAPIService;
import com.globo.networkapi.response.NetworkAPIVipsResponse.Vip;
import com.globo.networkapi.response.NetworkAPIVipExternalResponse;

@APICommand(name = "listNetworkApiVips", responseObject=NetworkAPIVipExternalResponse.class, description="Lists NetworkAPI Vips")
public class ListNetworkApiVipsCmd extends BaseCmd {

    public static final Logger s_logger = Logger.getLogger(ListNetworkApiVipsCmd.class);
    private static final String s_name = "listnetworkapivipsresponse";
    
    @Inject
    NetworkAPIService _ntwkAPIService;
    
    @Parameter(name=ApiConstants.PROJECT_ID, type=CommandType.UUID, entityType = ProjectResponse.class, description="the project id")
    private Long projectId;
    
    /* Implementation */
    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException {
        try {
        	s_logger.debug("listNetworkApiVipsCmd command with projectId=" + projectId);
        	List<Vip> napiVips = _ntwkAPIService.listNetworkAPIVips(this.projectId);
        	
        	List<NetworkAPIVipExternalResponse> responseList = new ArrayList<NetworkAPIVipExternalResponse>();
    		
    		for (Vip networkAPIVip : napiVips) {
    			NetworkAPIVipExternalResponse vipResponse = new NetworkAPIVipExternalResponse();
    			vipResponse.setId(networkAPIVip.getId());
    			vipResponse.setName(networkAPIVip.getName());
    			vipResponse.setIp(networkAPIVip.getIp());
    			vipResponse.setNetwork("NET01"); // FIXME
    			// FIXME Other attributes
    			vipResponse.setObjectName("networkapivip");
				responseList.add(vipResponse);
			}
    		 
    		ListResponse<NetworkAPIVipExternalResponse> response = new ListResponse<NetworkAPIVipExternalResponse>();
    		response.setResponses(responseList);
    		response.setResponseName(getCommandName());
    		this.setResponseObject(response);
        }  catch (InvalidParameterValueException invalidParamExcp) {
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
        return UserContext.current().getCaller().getId();
    }
}
