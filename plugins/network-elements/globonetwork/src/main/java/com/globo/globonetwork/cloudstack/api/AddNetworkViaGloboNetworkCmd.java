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

import com.cloud.event.ActionEventUtils;
import com.cloud.event.EventTypes;
import org.apache.cloudstack.acl.ControlledEntity.ACLType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.NetworkACLResponse;
import org.apache.cloudstack.api.response.NetworkOfferingResponse;
import org.apache.cloudstack.api.response.NetworkResponse;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;

@APICommand(name = "addNetworkViaGloboNetwork", responseObject = NetworkResponse.class, description = "Adds a vlan/network in Cloudstack and GloboNetwork")
public class AddNetworkViaGloboNetworkCmd extends BaseCmd {

    public static final Logger s_logger = Logger.getLogger(AddNetworkViaGloboNetworkCmd.class.getName());
    private static final String s_name = "addnetworkviaglobonetworkresponse";

    @Inject
    GloboNetworkService _globoNetworkService;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "the name of the network")
    private String name;

    @Parameter(name = ApiConstants.DISPLAY_TEXT, type = CommandType.STRING, required = true, description = "the display text of the network")
    private String displayText;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, required = true, description = "the Zone ID for the network")
    private Long zoneId;

    @Parameter(name = ApiConstants.NETWORK_OFFERING_ID, type = CommandType.UUID, entityType = NetworkOfferingResponse.class, required = true, description = "the network offering id")
    private Long networkOfferingId;

    @Parameter(name = ApiConstants.NETWORK_DOMAIN, type = CommandType.STRING, description = "network domain")
    private String networkDomain;

    @Parameter(name = ApiConstants.ACL_TYPE, type = CommandType.STRING, description = "Access control type; supported values"
            + " are account and domain. In 3.0 all shared networks should have aclType=Domain, and all Isolated networks"
            + " - Account. Account means that only the account owner can use the network, domain - all accouns in the domain can use the network")
    private String aclType;

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "account who will own the network")
    private String accountName;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "an optional project for the ssh key")
    private Long projectId;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class, description = "domain ID of the account owning a network")
    private Long domainId;

    @Parameter(name = ApiConstants.SUBDOMAIN_ACCESS, type = CommandType.BOOLEAN, description = "Defines whether to allow"
            + " subdomains to use networks dedicated to their parent domain(s). Should be used with aclType=Domain, defaulted to allow.subdomain.network.access global config if not specified")
    private Boolean subdomainAccess;

    @Parameter(name = ApiConstants.DISPLAY_NETWORK, type = CommandType.BOOLEAN, description = "an optional field, whether to the display the network to the end user or not.")
    private Boolean displayNetwork;

    @Parameter(name = ApiConstants.ACL_ID, type = CommandType.UUID, entityType = NetworkACLResponse.class, description = "Network ACL Id associated for the network")
    private Long aclId;

    @Parameter(name = "napienvironmentid", type = CommandType.LONG, required = true, description = "GloboNetwork environment ID.")
    private Long globoNetworkEnvironmentId;

    @Parameter(name = "isipv6", type = CommandType.BOOLEAN, description = "If true defines de network IP type as IPV6")
    private boolean isIpv6 = false;

    @Parameter(name = "subnet", type = CommandType.LONG, description = "Subnet mask in bits (24 for /24, 29 for /29).")
    private Long subnet;

    public Long getZoneId() {
        return zoneId;
    }

    public Long getNetworkOfferingId() {
        return networkOfferingId;
    }

    public ACLType getACLType() {
        if (aclType == null) {
            return null;
        }
        for (ACLType aclTypeEnum : ACLType.values()) {
            if (aclType.equalsIgnoreCase(aclTypeEnum.name())) {
                return aclTypeEnum;
            }
        }
        s_logger.warn("Invalid value for ACLType: " + aclType);
        return null;
    }

    /* Implementation */
    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException {
        try {
            s_logger.debug("addNetworkViaGloboNetworkCmd command with name=" + name + " displayText=" + displayText + " zoneId=" + zoneId + " networkOfferingId="
                    + networkOfferingId + " networkDomain=" + networkDomain + " aclType=" + aclType + " accountName=" + accountName + " projectId=" + projectId + " domainId"
                    + domainId + " subdomainAccess=" + subdomainAccess + " displayNetwork=" + displayNetwork + " aclId=" + aclId + " napienvironmentid="
                    + globoNetworkEnvironmentId + " isIpv6="+ isIpv6);
            Network network = _globoNetworkService.createNetwork(name, displayText, zoneId, networkOfferingId, globoNetworkEnvironmentId, networkDomain, getACLType(), accountName,
                    projectId, domainId, subdomainAccess, displayNetwork, aclId, isIpv6, subnet);
            if (network != null) {
                CallContext.current().putContextParameter(Network.class.getName(), network.getUuid());
                ActionEventUtils.onActionEvent(getUserId(), getEntityOwnerId(), domainId, EventTypes.EVENT_NETWORK_CREATE, getEventDescription(network.getId()));
                NetworkResponse response = _responseGenerator.createNetworkResponse(ResponseView.Full, network);
                response.setResponseName(getCommandName());
                this.setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create network from GloboNetwork.");
            }
        } catch (InvalidParameterValueException invalidParamExcp) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, invalidParamExcp.getMessage());
        } catch (CloudRuntimeException runtimeExcp) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, runtimeExcp.getMessage());
        }
    }

    private Long getUserId(){
        return CallContext.current().getCallingUserId();
    }

    public String getEventDescription(Long id) {
        return "Creating network. Network Id: " + id;
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
