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
package com.globo.globonetwork.cloudstack.response;

import com.cloud.agent.api.Answer;
import com.globo.globonetwork.cloudstack.commands.CheckDSREnabled;

public class CheckDSREnabledResponse extends Answer {

    private Boolean dsrEnabled;

    public CheckDSREnabledResponse(CheckDSREnabled command, boolean result, boolean dsrEnabled, String message) {
        super(command, result, message);
        this.dsrEnabled = dsrEnabled;
    }

    public Boolean isDsrEnabled() {
        return dsrEnabled;
    }

    public void setDsrEnabled(Boolean dsrEnabled) {
        this.dsrEnabled = dsrEnabled;
    }
}
