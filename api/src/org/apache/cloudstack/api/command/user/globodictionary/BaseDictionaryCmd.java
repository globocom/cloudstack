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
package org.apache.cloudstack.api.command.user.globodictionary;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.globodictionary.GloboDictionaryService;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ComponentResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.context.CallContext;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseDictionaryCmd extends BaseCmd {

    @Inject
    private GloboDictionaryService globoDictionaryService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = BaseCmd.CommandType.STRING, description = "the ID of the object being listed")
    private String id;

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        ListResponse<ComponentResponse> response = new ListResponse<>();
        List<ComponentResponse> componentResponses = new ArrayList<>();

        if(id != null){
            GloboDictionaryEntity component = globoDictionaryService.get(this.getEntity(), id);
            if(component != null){
                componentResponses.add(createResponse(component));
            }
        }else {
            List<GloboDictionaryEntity> components = globoDictionaryService.list(this.getEntity());
            for (GloboDictionaryEntity component : components) {
                componentResponses.add(createResponse(component));
            }
        }

        response.setResponses(componentResponses);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return null;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    private ComponentResponse createResponse(GloboDictionaryEntity component) {
        ComponentResponse componentResponse = new ComponentResponse(component.getId(), component.getName());
        componentResponse.setObjectName(this.getResponseName());
        return componentResponse;
    }

    abstract GloboDictionaryService.GloboDictionaryEntityType getEntity();

    abstract String getResponseName();

}
