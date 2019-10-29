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
package com.globo.globonetwork.cloudstack.api;

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandJobType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.BaseAsyncCreateCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.IPAddressResponse;
import org.apache.cloudstack.api.response.NetworkResponse;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.IpAddress;
import com.cloud.network.Network;
import com.cloud.network.addr.PublicIp;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;

@APICommand(name = "acquireNewLBIp", description = "Acquires and associates a Load Balancer IP to an network.", responseObject = IPAddressResponse.class)
public class AcquireNewIpForLbInGloboNetworkCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = Logger.getLogger(AcquireNewIpForLbInGloboNetworkCmd.class.getName());
    private static final String s_name = "associateipaddressresponse";

    @Inject
    private GloboNetworkService globoNetworkSvc;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.NETWORK_ID, type = CommandType.UUID, entityType = NetworkResponse.class, description = "The network this ip address should be associated to.", required = true)
    private Long networkId;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "Project the IP address will be associated with")
    private Long projectId;

    @Parameter(name = "lbenvironmentid", type = CommandType.LONG, entityType = GloboNetworkLBEnvironmentResponse.class, description = "Globo Network LB Environment Id", required = true)
    private Long globoNetworkLBEnvironmentId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getNetworkId() {
        return networkId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getGloboNetworkLBEnvironmentId() {
        return globoNetworkLBEnvironmentId;
    }

    public Network getNetwork() {
        Network network = _networkService.getNetwork(networkId);
        if (network == null) {
            throw new InvalidParameterValueException("Unable to find network by network id specified");
        }
        return network;
    }

    @Override
    public long getEntityOwnerId() {
        Network network = getNetwork();
        return network.getAccountId();
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_PORTABLE_IP_ASSIGN;
    }

    @Override
    public String getEventDescription() {
        return "acquiring lb ip to network id: " + getNetworkId();
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public void create() throws ResourceAllocationException {
        try {
            PublicIp ip = globoNetworkSvc.acquireLbIp(getNetworkId(), getProjectId(), getGloboNetworkLBEnvironmentId());

            if (ip != null) {
                setEntityId(ip.getId());
                setEntityUuid(ip.getUuid());
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to allocate ip address");
            }
        } catch (ConcurrentOperationException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        } catch (InsufficientCapacityException ex) {
            throw new ServerApiException(ApiErrorCode.INSUFFICIENT_CAPACITY_ERROR, ex.getMessage());
        } catch (ResourceUnavailableException ex) {
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, ex.getMessage());
        }
    }

    @Override
    public void execute() throws ResourceUnavailableException, ResourceAllocationException, ConcurrentOperationException, InsufficientCapacityException {
        CallContext.current().setEventDetails("Ip Id: " + getEntityId());

        IpAddress result = _networkService.associateIPToNetwork(getEntityId(), getNetworkId());

        if (result != null) {
            IPAddressResponse ipResponse = _responseGenerator.createIPAddressResponse(ResponseView.Full, result);
            ipResponse.setResponseName(getCommandName());
            setResponseObject(ipResponse);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to acquire lb ip address");
        }
    }

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.networkSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        return getNetworkId();
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.IpAddress;
    }

}
