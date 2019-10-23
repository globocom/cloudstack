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
package com.globo.globodns.cloudstack.commands;

import com.cloud.agent.api.Command;

public class CreateOrUpdateRecordAndReverseCommand extends Command {

    private String recordName;

    private String recordIp;

    private String networkDomain;

    private Long reverseTemplateId;

    private boolean override;

    private boolean isIpv6;


    public CreateOrUpdateRecordAndReverseCommand(String recordName, String recordIp, String networkDomain, Long reverseTemplateId,
                                                 boolean override,
                                                 boolean isIpv6) {
        this.recordName = recordName;
        this.recordIp = recordIp;
        this.networkDomain = networkDomain;
        this.reverseTemplateId = reverseTemplateId;
        this.override = override;
        this.isIpv6 = isIpv6;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public String getRecordName() {
        return this.recordName;
    }

    public String getRecordIp() {
        return this.recordIp;
    }

    public String getNetworkDomain() {
        return this.networkDomain;
    }

    public Long getReverseTemplateId() {
        return reverseTemplateId;
    }

    public boolean isOverride() {
        return override;
    }

    public boolean isIpv6() {
        return isIpv6;
    }

    public void setIpv6(boolean isIpv6) {
        this.isIpv6 = isIpv6;
    }

}
