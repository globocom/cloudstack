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
package org.apache.cloudstack.api.command.user.project;

import org.apache.log4j.Logger;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.context.CallContext;

import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.projects.Project;

@APICommand(name = "updateProject", description = "Updates a project", responseObject = ProjectResponse.class, since = "3.0.0",
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UpdateProjectCmd extends BaseAsyncCmd {
    public static final Logger s_logger = Logger.getLogger(UpdateProjectCmd.class.getName());

    private static final String s_name = "updateprojectresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = ProjectResponse.class, required = true, description = "id of the project to be modified")
    private Long id;

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "new Admin account for the project")
    private String accountName;

    @Parameter(name = ApiConstants.DISPLAY_TEXT, type = CommandType.STRING, description = "display text of the project")
    private String displayText;

    @Parameter(name = ApiConstants.BUSINESS_SERVICE_ID, type = CommandType.STRING, required = false, description = "business service id")
    private String businessServiceId;

    @Parameter(name = ApiConstants.CLIENT_ID, type = CommandType.STRING, required = false, description = "client id")
    private String clientId;

    @Parameter(name = ApiConstants.COMPONENT_ID, type = CommandType.STRING, required = false, description = "component id")
    private String componentId;

    @Parameter(name = ApiConstants.SUB_COMPONENT_ID, type = CommandType.STRING, required = false, description = "sub-component id")
    private String subComponentId;

    @Parameter(name = ApiConstants.PRODUCT_ID, type = CommandType.STRING, required = false, description = "product id")
    private String productId;

    @Parameter(name = ApiConstants.DETAILED_USAGE, type = CommandType.BOOLEAN, required = false, description = "true if project has detailed usage by third party app")
    private Boolean detailedUsage;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public String getAccountName() {
        return accountName;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getBusinessServiceId() {
        return businessServiceId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getSubComponentId() {
        return subComponentId;
    }

    public String getProductId() {
        return productId;
    }

    public Boolean isDetailedUsage() {
        return detailedUsage;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        Project project = _projectService.getProject(id);
        //verify input parameters
        if (project == null) {
            throw new InvalidParameterValueException("Unable to find project by id " + id);
        }

        return _projectService.getProjectOwner(id).getId();
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() throws ResourceAllocationException {
        CallContext.current().setEventDetails("Project id: " + getId());
        Project project = _projectService.updateProject(getId(), getDisplayText(), getAccountName(), getBusinessServiceId(),
                getClientId(), getComponentId(), getSubComponentId(), getProductId(), isDetailedUsage());
        if (project != null) {
            ProjectResponse response = _responseGenerator.createProjectResponse(project);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update a project");
        }
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_PROJECT_UPDATE;
    }

    @Override
    public String getEventDescription() {
        return "Updating project: " + id;
    }
}
