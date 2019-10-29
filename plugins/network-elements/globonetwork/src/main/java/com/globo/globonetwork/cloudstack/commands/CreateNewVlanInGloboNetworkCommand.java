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
package com.globo.globonetwork.cloudstack.commands;

import com.cloud.agent.api.Command;

public class CreateNewVlanInGloboNetworkCommand extends Command {

    private String vlanName;

    private String vlanDescription;

    private Long globoNetworkEnvironmentId;

    private Boolean isIpv6;

    private Long subnet;

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public String getVlanName() {
        return vlanName;
    }

    public void setVlanName(String vlanName) {
        this.vlanName = vlanName;
    }

    public String getVlanDescription() {
        return vlanDescription;
    }

    public void setVlanDescription(String vlanDescription) {
        this.vlanDescription = vlanDescription;
    }

    public Long getGloboNetworkEnvironmentId() {
        return globoNetworkEnvironmentId;
    }

    public void setGloboNetworkEnvironmentId(Long globoNetworkEnvironmentId) {
        this.globoNetworkEnvironmentId = globoNetworkEnvironmentId;
    }

    public Boolean getIsIpv6() {
        return isIpv6;
    }

    public Long getSubnet() {
        return subnet;
    }

    public void setSubnet(Long subnet) {
        this.subnet = subnet;
    }

    public Boolean isIpv6() {
        return isIpv6;
    }

    public void setIsIpv6(Boolean isIpv6) {
        this.isIpv6 = isIpv6;
    }
}
