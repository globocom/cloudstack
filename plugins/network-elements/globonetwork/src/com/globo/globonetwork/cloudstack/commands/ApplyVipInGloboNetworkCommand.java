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

import java.util.ArrayList;
import java.util.List;

import com.cloud.agent.api.Command;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.rules.FirewallRule;
import com.cloud.utils.Pair;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;

public class ApplyVipInGloboNetworkCommand extends Command {

    private FirewallRule.State ruleState;

    private Long vipId;

    private String ipv4;

    private String methodBal;

    private LoadBalancingRule.LbStickinessPolicy persistencePolicy;

    private Long vipEnvironmentId;

    private String businessArea;

    private String host;

    private String cache;

    private boolean dsr;

    private String serviceDownAction;

    private String healthCheckDestination;

    private String expectedHealthcheck;

    private String healthcheckType;

    private String healthcheck;

    private String serviceName;

    private List<String> ports;

    private List<GloboNetworkVipResponse.Real> realList;
    private String region;

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public Long getVipId() {
        return vipId;
    }

    public void setVipId(Long vipId) {
        this.vipId = vipId;
    }

    public String getIpv4() {
        return ipv4;
    }

    public void setIpv4(String ipv4) {
        this.ipv4 = ipv4;
    }

    public String getMethodBal() {
        return methodBal;
    }

    public void setMethodBal(String methodBal) {
        this.methodBal = methodBal;
    }

    public Long getVipEnvironmentId() {
        return vipEnvironmentId;
    }

    public void setVipEnvironmentId(Long vipEnvironmentId) {
        this.vipEnvironmentId = vipEnvironmentId;
    }

    public String getBusinessArea() {
        return businessArea;
    }

    public void setBusinessArea(String businessArea) {
        this.businessArea = businessArea;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getCache() { return cache; }

    public void setCache(String cache) { this.cache = cache; }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public List<String> getPorts() {
        return ports;
    }

    public List<Pair<Integer, Integer>> getPortPairs(){
        List<Pair<Integer, Integer>> portPairs = new ArrayList<>();
        for(String portPair : ports){
            String[] splitPorts = portPair.split(":");
            portPairs.add(new Pair<>(new Integer(splitPorts[0]), new Integer(splitPorts[1])));
        }
        return portPairs;
    }

    public void setPorts(List<String> ports) {
        this.ports = ports;
    }

    public List<GloboNetworkVipResponse.Real> getRealList() {
        return realList;
    }

    public void setRealList(List<GloboNetworkVipResponse.Real> realList) {
        this.realList = realList;
    }

    public FirewallRule.State getRuleState() {
        return ruleState;
    }

    public void setRuleState(FirewallRule.State ruleState) {
        this.ruleState = ruleState;
    }

    public LoadBalancingRule.LbStickinessPolicy getPersistencePolicy() {
        return persistencePolicy;
    }

    public void setPersistencePolicy(LoadBalancingRule.LbStickinessPolicy persistencePolicy) {
        this.persistencePolicy = persistencePolicy;
    }

    public String getHealthcheck() {
        return healthcheck;
    }

    public void setHealthcheck(String healthcheck) {
        this.healthcheck = healthcheck;
    }

    public void setServiceDownAction(String serviceDownAction) {
        this.serviceDownAction = serviceDownAction;
    }

    public String getServiceDownAction() {
        return serviceDownAction;
    }

    public void setHealthCheckDestination(String healthCheckDestination) {
        this.healthCheckDestination = healthCheckDestination;
    }

    public String getHealthCheckDestination() {
        return healthCheckDestination;
    }

    public String getExpectedHealthcheck() {
        return expectedHealthcheck;
    }

    public void setExpectedHealthcheck(String expectedHealthcheck) {
        this.expectedHealthcheck = expectedHealthcheck;
    }

    public String getHealthcheckType() {
        return healthcheckType;
    }

    public void setHealthcheckType(String healthcheckType) {
        this.healthcheckType = healthcheckType;
    }

    public String getRegion() {
        return region;
    }
    public void setRegion(String region) {
        this.region = region;
    }

    public void setDsr(boolean dsr) {
        this.dsr = dsr;
    }

    public boolean isDsr() {
        return dsr;
    }
}
