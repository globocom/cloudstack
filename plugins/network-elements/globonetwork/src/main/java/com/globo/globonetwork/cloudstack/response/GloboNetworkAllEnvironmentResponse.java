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

import java.util.List;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;

public class GloboNetworkAllEnvironmentResponse extends Answer {

    private List<Environment> environmentList;

    public GloboNetworkAllEnvironmentResponse(Command command, List<Environment> environmentList) {
        super(command, true, null);
        this.environmentList = environmentList;
    }

    public List<Environment> getEnvironmentList() {
        return environmentList;
    }

    public static class Environment {
        private Long id;
        private String l3GroupName;
        private String logicalEnvironmentName;
        private String dcDivisionName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getL3GroupName() {
            return l3GroupName;
        }

        public void setL3GroupName(String l3GroupName) {
            this.l3GroupName = l3GroupName;
        }

        public String getLogicalEnvironmentName() {
            return logicalEnvironmentName;
        }

        public void setLogicalEnvironmentName(String logicalEnvironmentName) {
            this.logicalEnvironmentName = logicalEnvironmentName;
        }

        public String getDcDivisionName() {
            return dcDivisionName;
        }

        public void setDcDivisionName(String dcDivisionName) {
            this.dcDivisionName = dcDivisionName;
        }
    }

}
