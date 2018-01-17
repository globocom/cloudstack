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

import org.apache.cloudstack.api.response.ClientResponse;
import com.cloud.globodictionary.GloboDictionaryService;
import org.apache.cloudstack.api.APICommand;

@APICommand(name = "listClients", description = "Lists clients", responseObject = ClientResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListClientsCmd extends BaseDictionaryCmd {

    private static final String s_name = "listclientsresponse";
    private static final String response_name = "client";

    @Override
    GloboDictionaryService.GloboDictionaryEntityType getEntity() {
        return GloboDictionaryService.GloboDictionaryEntityType.CLIENT;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    String getResponseName() {
        return response_name;
    }
}
