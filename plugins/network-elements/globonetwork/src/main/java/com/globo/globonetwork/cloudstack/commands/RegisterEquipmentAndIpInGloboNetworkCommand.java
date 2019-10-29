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

public class RegisterEquipmentAndIpInGloboNetworkCommand extends Command {

    private String nicIp;
    private String nicDescription;
    private String vmName;
    private Long vlanId;
    private Long environmentId;
    private Long equipmentGroupId;
    private Long equipmentModelId;

    @Override
    public boolean executeInSequence() {
        return true;
    }

    public String getNicIp() {
        return nicIp;
    }

    public void setNicIp(String nicIp) {
        this.nicIp = nicIp;
    }

    public String getNicDescription() {
        return nicDescription;
    }

    public void setNicDescription(String nicDescription) {
        this.nicDescription = nicDescription;
    }

    public String getVmName() {
        return vmName;
    }

    public void setVmName(String vmName) {
        this.vmName = vmName;
    }

    public Long getVlanId() {
        return vlanId;
    }

    public void setVlanId(Long vlanId) {
        this.vlanId = vlanId;
    }

    public Long getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(Long environmentId) {
        this.environmentId = environmentId;
    }

    public Long getEquipmentGroupId() {
        return equipmentGroupId;
    }

    public void setEquipmentGroupId(Long equipmentGroupId) {
        this.equipmentGroupId = equipmentGroupId;
    }

    public Long getEquipmentModelId() {
        return equipmentModelId;
    }

    public void setEquipmentModelId(Long equipmentModelId) {
        this.equipmentModelId = equipmentModelId;
    }
}
