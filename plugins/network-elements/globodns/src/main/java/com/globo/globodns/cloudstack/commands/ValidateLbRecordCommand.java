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

public class ValidateLbRecordCommand extends Command {

    private String lbRecordName;

    private String lbRecordContent;

    private String lbDomain;

    private boolean override;

    private boolean skipDnsError;

    public ValidateLbRecordCommand(String lbRecordName, String lbRecordContent, String lbDomain, boolean override, boolean skipDnsError) {
        this.lbRecordName = lbRecordName;
        this.lbRecordContent = lbRecordContent;
        this.lbDomain = lbDomain;
        this.override = override;
        this.skipDnsError   = skipDnsError;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public String getLbRecordName() {
        return this.lbRecordName;
    }

    public String getLbRecordContent() {
        return this.lbRecordContent;
    }

    public String getLbDomain() {
        return this.lbDomain;
    }

    public boolean isOverride() {
        return override;
    }

    public boolean isSkipDnsError() {
        return skipDnsError;
    }

}
