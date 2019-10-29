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
import com.cloud.agent.api.Command;

public class GloboNetworkVlanResponse extends Answer {

    private Long vlanId;

    private String vlanName;

    private String vlanDescription;

    private Long vlanNum;

    private String mask;

    private String networkAddress;

    private Long networkId;

    private boolean isActive;

    private Integer block;

    private boolean isv6;

    public GloboNetworkVlanResponse(Command command, Long vlanId, String vlanName, String vlanDescription, Long vlanNum, String networkAddress, String mask,
            Long networkId, Boolean isActive, Integer block, boolean isv6) {
        super(command, true, null);
        this.vlanId = vlanId;
        this.vlanName = vlanName;
        this.vlanDescription = vlanDescription;
        this.vlanNum = vlanNum;
        this.mask = mask;
        this.networkAddress = networkAddress;
        this.setNetworkId(networkId);
        this.setIsActive(isActive == null ? false : isActive);
        this.setBlock(block);
        this.isv6 = isv6;
    }

    public Long getVlanNum() {
        return vlanNum;
    }

    public void setVlanNum(Long vlanNum) {
        this.vlanNum = vlanNum;
    }

    public Long getVlanId() {
        return vlanId;
    }

    public void setVlanId(Long vlanId) {
        this.vlanId = vlanId;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
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

    public String getNetworkAddress() {
        return networkAddress;
    }

    public void setNetworkAddress(String networkAddress) {
        this.networkAddress = networkAddress;
    }

    public Boolean isActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public boolean isv6() {
        return isv6;
    }

    public void setIsv6(boolean isv6) {
        this.isv6 = isv6;
    }

    public Integer getBlock() {
        return block;
    }

    public void setBlock(Integer block) {
        this.block = block;
    }

}
