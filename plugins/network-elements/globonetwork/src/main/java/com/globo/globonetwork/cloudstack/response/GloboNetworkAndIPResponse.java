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

public class GloboNetworkAndIPResponse extends Answer {

    // Vlan information
    private Long vlanId;

    private String vlanName;

    private String vlanDescription;

    private Integer vlanNum;

    // Network information
    private Long networkId;

    private String networkAddress;

    private String networkMask;

    private String networkCidr;

    private boolean isActive;

    private boolean isv6;

    // Vip information (optional)
    private Long vipEnvironmentId;

    // Ip information (optional)
    private Long ipId;

    private String ip;

    public GloboNetworkAndIPResponse(Command command) {
        super(command, true, null);
    }

    public GloboNetworkAndIPResponse(Command command, Long vlanId, String vlanName, String vlanDescription, Integer vlanNum, boolean isActive, Long networkId,
            String networkAddress, String networkMask, String networkCidr, Long ipId, String ip, Long vipEnvironmentId, boolean isv6) {
        this(command);
        this.vlanId = vlanId;
        this.vlanName = vlanName;
        this.vlanDescription = vlanDescription;
        this.vlanNum = vlanNum;
        this.networkAddress = networkAddress;
        this.networkMask = networkMask;
        this.networkCidr = networkCidr;
        this.networkId = networkId;
        this.isActive = isActive;
        this.ipId = ipId;
        this.ip = ip;
        this.vipEnvironmentId = vipEnvironmentId;
        this.setIsv6(isv6);
    }

    public Long getVlanId() {
        return vlanId;
    }

    public void setVlanId(Long vlanId) {
        this.vlanId = vlanId;
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

    public Integer getVlanNum() {
        return vlanNum;
    }

    public void setVlanNum(Integer vlanNum) {
        this.vlanNum = vlanNum;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public String getNetworkAddress() {
        return networkAddress;
    }

    public void setNetworkAddress(String networkAddress) {
        this.networkAddress = networkAddress;
    }

    public Long getIpId() {
        return ipId;
    }

    public void setIpId(Long ipId) {
        this.ipId = ipId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getNetworkMask() {
        return networkMask;
    }

    public void setNetworkMask(String networkMask) {
        this.networkMask = networkMask;
    }

    public String getNetworkCidr() {
        return networkCidr;
    }

    public void setNetworkCidr(String networkCidr) {
        this.networkCidr = networkCidr;
    }

    public Long getVipEnvironmentId() {
        return vipEnvironmentId;
    }

    public void setVipEnvironmentId(Long vipEnvironmentId) {
        this.vipEnvironmentId = vipEnvironmentId;
    }

    public boolean isv6() {
        return isv6;
    }

    public void setIsv6(boolean isv6) {
        this.isv6 = isv6;
    }

}
