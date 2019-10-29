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

/**
 * Removes network from a specific VLAN in GloboNetwork
 * @author Daniel Vega
 *
 */
public class RemoveNetworkInGloboNetworkCommand extends Command {

    private Long networkId;
    private boolean isIpv6;

    @Override
    public boolean executeInSequence() {
        return true;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public void setIsIpv6(boolean isIpv6) {
        this.isIpv6 = isIpv6;
    }

    public boolean isIpv6() {
        return isIpv6;
    }

    public void setIpv6(boolean isIpv6) {
        this.isIpv6 = isIpv6;
    }
}
