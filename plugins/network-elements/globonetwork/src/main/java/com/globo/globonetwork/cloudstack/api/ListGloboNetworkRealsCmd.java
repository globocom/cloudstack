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
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipExternalResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;
import com.google.common.base.Joiner;

@APICommand(name = "listGloboNetworkReals", responseObject = GloboNetworkVipExternalResponse.class, description = "List GloboNetwork Reals")
public class ListGloboNetworkRealsCmd extends BaseCmd {

    public static final Logger s_logger = Logger.getLogger(ListGloboNetworkRealsCmd.class);
    private static final String s_name = "listglobonetworkrealsresponse";

    @Inject
    GloboNetworkService _globoNetworkService;

    @Parameter(name = ApiConstants.VIP_ID, required = true, type = CommandType.LONG, entityType = GloboNetworkVipResponse.class, description = "the vip id")
    private Long vipId;

    /* Implementation */
    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException {
        try {
            s_logger.debug("listGloboNetworkRealsCmd command with vipId=" + vipId);
            List<GloboNetworkVipResponse.Real> globoNetworkReals = _globoNetworkService.listGloboNetworkReals(this.vipId);

            List<GloboNetworkVipExternalResponse.Real> responseList = new ArrayList<GloboNetworkVipExternalResponse.Real>();

            for (GloboNetworkVipResponse.Real globoNetworkReal : globoNetworkReals) {
                GloboNetworkVipExternalResponse.Real realResponse = new GloboNetworkVipExternalResponse.Real();
                realResponse.setVmname(globoNetworkReal.getVmName());
                realResponse.setIp(globoNetworkReal.getIp());
                realResponse.setNetwork(globoNetworkReal.getNetwork());
                realResponse.setPorts(Joiner.on(", ").join(globoNetworkReal.getPorts()));
                realResponse.setState(globoNetworkReal.getState());
                realResponse.setNic(globoNetworkReal.getNic());

                realResponse.setObjectName("globonetworkreal");
                responseList.add(realResponse);
            }

            ListResponse<GloboNetworkVipExternalResponse.Real> response = new ListResponse<GloboNetworkVipExternalResponse.Real>();
            response.setResponses(responseList);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } catch (InvalidParameterValueException invalidParamExcp) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, invalidParamExcp.getMessage());
        } catch (CloudRuntimeException runtimeExcp) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, runtimeExcp.getMessage(), runtimeExcp);
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
