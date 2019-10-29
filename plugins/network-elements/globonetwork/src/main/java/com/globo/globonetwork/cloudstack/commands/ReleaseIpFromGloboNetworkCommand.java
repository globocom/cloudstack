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

public class ReleaseIpFromGloboNetworkCommand extends Command {

    private String ip;

    private boolean isv6;

    private long vipEnvironmentId;

    public ReleaseIpFromGloboNetworkCommand(String ip, long vipEnvironmentId, boolean isv6) {
        this.ip = ip;
        this.vipEnvironmentId = vipEnvironmentId;
        this.isv6 = isv6;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public String getIp() {
        return ip;
    }

    public long getVipEnvironmentId() {
        return vipEnvironmentId;
    }

    public boolean isv6() {
        return isv6;
    }

    public void setIsv6(boolean isv6) {
        this.isv6 = isv6;
    }
}
