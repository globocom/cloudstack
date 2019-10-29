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

import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import com.globo.globonetwork.cloudstack.response.GloboNetworkExpectHealthcheckResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.response.ExpectedHealthcheckResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PoolResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

@APICommand(name = "listGloboNetworkExpectedHealthchecks", description = "Lists expected healthchecks from networkapi .", responseObject = PoolResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListGloboNetworkExpectedHealthchecksCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(ListGloboNetworkExpectedHealthchecksCmd.class.getName());

    private static final String s_name = "listgloboNetworkexpectedhealthchecksresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////


    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    @Inject
    GloboNetworkManager _globoNetworkService;

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    @Override
    public void execute() {
        ListResponse<ExpectedHealthcheckResponse> response = new ListResponse<ExpectedHealthcheckResponse>();

        List<GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck> expectedHealthchecks = _globoNetworkService.listAllExpectedHealthchecks();;

        List<ExpectedHealthcheckResponse> lbResponses = new ArrayList<>();

        for ( GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck expectedHealthcheck : expectedHealthchecks) {

            ExpectedHealthcheckResponse poolResp = new ExpectedHealthcheckResponse();
            poolResp.setId(expectedHealthcheck.getId());
            poolResp.setExpected(expectedHealthcheck.getExpected());

            poolResp.setObjectName("globonetworkexpectedhealthcheck");
            lbResponses.add(poolResp);
        }

        Collections.sort(lbResponses);

        response.setResponses(lbResponses);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }
}
