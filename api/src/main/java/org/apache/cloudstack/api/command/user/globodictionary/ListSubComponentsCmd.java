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

import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.globodictionary.GloboDictionaryService;
import com.cloud.utils.StringUtils;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.GloboDictionaryResponse;
import org.apache.cloudstack.api.response.ListResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@APICommand(name = "listSubComponents", description = "Lists sub-components", responseObject = GloboDictionaryResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListSubComponentsCmd extends BaseDictionaryCmd {

    private static final String s_name = "listsubcomponentsresponse";
    private static final String response_name = "subcomponent";

    @Parameter(name = ApiConstants.COMPONENT_REQUIRED, type = CommandType.BOOLEAN, description = "only brings the Sub Components if the Component Id is passed")
    protected boolean componentRequired;

    @Parameter(name = ApiConstants.COMPONENT_ID, type = CommandType.STRING, description = "the ID of the Component which the Sub Components are children ")
    protected String componentId;

    @Override
    GloboDictionaryService.GloboDictionaryEntityType getEntity() {
        return GloboDictionaryService.GloboDictionaryEntityType.SUB_COMPONENT;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    public boolean isComponentRequired() { return componentRequired; }

    public boolean getComponentRequired() { return componentRequired; }

    public String getComponentId() { return componentId; }

    @Override
    String getResponseName() {
        return response_name;
    }

    @Override
    public void execute() throws ResourceUnavailableException, NetworkRuleConflictException, InsufficientCapacityException, ResourceAllocationException {
        if(componentRequired) {
            ListResponse<GloboDictionaryResponse> response = new ListResponse<>();
            List<GloboDictionaryResponse> globoDictionaryResponses = new ArrayList<>();

            if(StringUtils.isNotBlank(componentId)) {
                HashMap<String, String> example = new HashMap<>();
                example.put("componente_id", componentId);

                List<GloboDictionaryEntity> components = globoDictionaryService.listByExample(this.getEntity(), example);
                for (GloboDictionaryEntity component : components) {
                    globoDictionaryResponses.add(createResponse(component));
                }
            }

            response.setResponses(globoDictionaryResponses);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            super.execute();
        }
    }

}
