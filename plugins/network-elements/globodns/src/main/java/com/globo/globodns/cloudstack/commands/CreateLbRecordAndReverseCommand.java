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
import com.globo.globodns.cloudstack.element.GloboDnsTO;

public class CreateLbRecordAndReverseCommand extends Command {

    private String lbRecordName;

    private String lbRecordIp;

    private String lbDomain;

    private Long reverseTemplateId;

    private boolean override;

    public CreateLbRecordAndReverseCommand(String lbRecordName, String lbRecordIp, String lbDomain, Long reverseTemplateId, boolean override) {
        this.lbRecordName = lbRecordName;
        this.lbRecordIp = lbRecordIp;
        this.lbDomain = lbDomain;
        this.reverseTemplateId = reverseTemplateId;
        this.override = override;
    }
    public CreateLbRecordAndReverseCommand(GloboDnsTO globoDns, Long reverseTemplateId, boolean override) {
        this.lbRecordName = globoDns.getRecord();
        this.lbRecordIp = globoDns.getIpAddress();
        this.lbDomain = globoDns.getDomain();
        this.reverseTemplateId = reverseTemplateId;
        this.override = override;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public String getLbRecordName() {
        return this.lbRecordName;
    }

    public String getLbRecordIp() {
        return this.lbRecordIp;
    }

    public String getLbDomain() {
        return this.lbDomain;
    }

    public Long getReverseTemplateId() {
        return reverseTemplateId;
    }

    public boolean isOverride() {
        return override;
    }

}
