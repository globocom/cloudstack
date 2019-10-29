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

import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import com.cloud.dc.DataCenter;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;

@APICommand(name = "listGloboNetworkCapabilities", responseObject = GloboNetworkCapabilitiesResponse.class, description = "Lists GloboNetwork capabilities")
public class ListGloboNetworkCapabilitiesCmd extends BaseCmd {

    public static final Logger s_logger = Logger.getLogger(ListGloboNetworkCapabilitiesCmd.class);
    private static final String s_name = "listglobonetworkcapabilitiesresponse";

    @Inject
    GloboNetworkService _globoNetworkService;

    @Override
    public void execute() {
        s_logger.debug("listGloboNetworkCapabilities command");
        List<DataCenter> enabledInZones = _globoNetworkService.getAllZonesThatProviderAreEnabled();

        GloboNetworkCapabilitiesResponse response = new GloboNetworkCapabilitiesResponse();
        response.setDomainSuffix(_globoNetworkService.getDomainSuffix());
        response.setSupportCustomNetworkDomain(_globoNetworkService.isSupportedCustomNetworkDomain());
        response.setEnabled(enabledInZones != null && !enabledInZones.isEmpty());
        response.setAllowedLbSuffixes(_globoNetworkService.listAllowedLbSuffixes());
        response.setObjectName("globoNetworkCapability");
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
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
