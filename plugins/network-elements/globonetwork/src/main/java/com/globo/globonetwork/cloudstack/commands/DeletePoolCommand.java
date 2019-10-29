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

public class DeletePoolCommand extends Command{

    private final Integer vipPort;
    private Long poolId;
    private Long vipId;

    public DeletePoolCommand(Long vipId, Long poolId, Integer vipPort) {
        this.vipId = vipId;
        this.poolId = poolId;
        this.vipPort = vipPort;
    }

    public Long getPoolId() {
        return poolId;
    }

    public Long getVipId() {
        return vipId;
    }

    public Integer getVipPort() {
        return vipPort;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }
}
