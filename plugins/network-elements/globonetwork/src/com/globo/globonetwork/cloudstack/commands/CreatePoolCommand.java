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
import com.globo.globonetwork.cloudstack.manager.Protocol;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;

import java.util.List;

public class CreatePoolCommand extends Command {

    private Long vipId;
    private String vipName;
    private String vipIp;
    private Integer publicPort;
    private Integer privatePort;
    private String balacingAlgorithm;
    private String serviceDownAction;
    private String region;
    private Long vipEnvironment;
    private Protocol.L4 l4protocol;
    private Protocol.L7 l7protocol;
    private List<GloboNetworkVipResponse.Real> reals;

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public void setVipId(Long vipId) {
        this.vipId = vipId;
    }

    public Long getVipId() {
        return vipId;
    }

    public void setVipName(String vipName) {
        this.vipName = vipName;
    }

    public String getVipName() {
        return vipName;
    }

    public void setPublicPort(Integer publicPort) {
        this.publicPort = publicPort;
    }

    public Integer getPublicPort() {
        return publicPort;
    }

    public void setPrivatePort(Integer privatePort) {
        this.privatePort = privatePort;
    }

    public Integer getPrivatePort() {
        return privatePort;
    }

    public void setBalacingAlgorithm(String balacingAlgorithm) {
        this.balacingAlgorithm = balacingAlgorithm;
    }

    public String getBalacingAlgorithm() {
        return balacingAlgorithm;
    }

    public void setServiceDownAction(String serviceDownAction) {
        this.serviceDownAction = serviceDownAction;
    }

    public String getServiceDownAction() {
        return serviceDownAction;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getRegion() {
        return region;
    }

    public void setVipEnvironment(Long vipEnvironment) {
        this.vipEnvironment = vipEnvironment;
    }

    public Long getVipEnvironment() {
        return vipEnvironment;
    }

    public void setReals(List<GloboNetworkVipResponse.Real> reals) {
        this.reals = reals;
    }

    public List<GloboNetworkVipResponse.Real> getReals() {
        return reals;
    }

    public void setVipIp(String vipIp) {
        this.vipIp = vipIp;
    }

    public String getVipIp() {
        return vipIp;
    }

    public Protocol.L4 getL4protocol() {
        return l4protocol;
    }

    public void setL4protocol(Protocol.L4 l4protocol) {
        this.l4protocol = l4protocol;
    }

    public Protocol.L7 getL7protocol() {
        return l7protocol;
    }

    public void setL7protocol(Protocol.L7 l7protocol) {
        this.l7protocol = l7protocol;
    }
}
