// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.api.response;

import java.util.ArrayList;
import java.util.List;

import com.cloud.network.rules.LoadBalancer;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.EntityReference;
import org.apache.cloudstack.globoconfig.GloboResourceConfiguration;

@SuppressWarnings("unused")
public class LoadBalancerResponse extends BaseResponse implements ControlledEntityResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the load balancer rule ID")
    private String id;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "the name of the load balancer")
    private String name;

    @SerializedName(ApiConstants.DESCRIPTION)
    @Param(description = "the description of the load balancer")
    private String description;

    @SerializedName(ApiConstants.PUBLIC_IP_ID)
    @Param(description = "the public ip address id")
    private String publicIpId;

    @SerializedName(ApiConstants.PUBLIC_IP)
    @Param(description = "the public ip address")
    private String publicIp;

    @SerializedName(ApiConstants.PUBLIC_PORT)
    @Param(description = "the public port")
    private String publicPort;

    @SerializedName(ApiConstants.PRIVATE_PORT)
    @Param(description = "the private port")
    private String privatePort;

    @SerializedName(ApiConstants.ALGORITHM)
    @Param(description = "the load balancer algorithm (source, roundrobin, leastconn)")
    private String algorithm;

    @SerializedName(ApiConstants.NETWORK_ID)
    @Param(description = "the id of the guest network the lb rule belongs to")
    private String networkId;

    @SerializedName(ApiConstants.CIDR_LIST)
    @Param(description = "the cidr list to forward traffic from")
    private String cidrList;

    @SerializedName(ApiConstants.ACCOUNT)
    @Param(description = "the account of the load balancer rule")
    private String accountName;

    @SerializedName(ApiConstants.PROJECT_ID)
    @Param(description = "the project id of the load balancer")
    private String projectId;

    @SerializedName(ApiConstants.PROJECT)
    @Param(description = "the project name of the load balancer")
    private String projectName;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the domain ID of the load balancer rule")
    private String domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the domain of the load balancer rule")
    private String domainName;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "the state of the rule")
    private String state;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "the id of the zone the rule belongs to")
    private String zoneId;

    @SerializedName(ApiConstants.PROTOCOL)
    @Param(description = "the protocol of the loadbalanacer rule")
    private String lbProtocol;

    @SerializedName("additionalnetworkids")
    @Param(description = "the additional networks that are associated with this load balancer")
    private List<String> additionalNetworks;

    @SerializedName(ApiConstants.ADDITIONAL_PORT_MAP)
    @Param(description = "additional port map associated with load balancing rule")
    private List<String> additionalPortMap;

    @SerializedName(ApiConstants.CACHE)
    @Param(description = "cache group associated with load balancing rule")
    private String cache;

    @SerializedName(ApiConstants.HEALTHCHECK_DESTINATION)
    @Param(description = "Port to be used as health check alternative to the service port (optional)")
    private String healthCheckDestination;

    @SerializedName(ApiConstants.SERVICE_DOWN_ACTION)
    @Param(description = "ID of the action to be executed when service is down")
    private String serviceDownAction;

    @SerializedName(ApiConstants.TAGS)
    @Param(description = "the list of resource tags associated with load balancer", responseObject = ResourceTagResponse.class)
    private List<ResourceTagResponse> tags;

    @SerializedName(ApiConstants.FOR_DISPLAY)
    @Param(description = "is rule for display to the regular user", since = "4.4", authorized = {RoleType.Admin})
    private Boolean forDisplay;

    @SerializedName(ApiConstants.GLOBO_RESOURCE_CONFIG)
    @Param(description = "list with globo resource configuration")
    private List<GloboResourceConfigurationResponse> globoResourceConfigs;


    @SerializedName(ApiConstants.LB_LINKED_PARENT_LOAD_BALANCER)
    @Param(description = "load balancer that this is linked")
    private LinkedLoadBalancer linkedparent;


    @SerializedName(ApiConstants.LB_LINKED_CHILDREN_LOAD_BALANCER)
    @Param(description = "load balancer linked children")
    private List<LinkedLoadBalancer> linkedchildren;

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPublicIp(String publicIp) {
        this.publicIp = publicIp;
    }

    public void setPublicPort(String publicPort) {
        this.publicPort = publicPort;
    }

    public void setPrivatePort(String privatePort) {
        this.privatePort = privatePort;
    }

    public void setCidrList(String cidrs) {
        this.cidrList = cidrs;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    @Override
    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    @Override
    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setPublicIpId(String publicIpId) {
        this.publicIpId = publicIpId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @Override
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setTags(List<ResourceTagResponse> tags) {
        this.tags = tags;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public void setLbProtocol(String lbProtocol) {
        this.lbProtocol = lbProtocol;
    }

    public void setAdditionalNetworks(List<String> additionalNetworks) {
        this.additionalNetworks = additionalNetworks;
    }

    public void setAdditionalPortMap(List<String> additionalPortMap) { this.additionalPortMap = additionalPortMap; }

    public void setCache(String cache) { this.cache = cache; }

    public void setHealthCheckDestination(String healthCheckDestination) {
        this.healthCheckDestination = healthCheckDestination;
    }

    public void setGloboResourceConfigs(List<GloboResourceConfigurationResponse> configs) {
        this.globoResourceConfigs = configs;
    }

    public void setServiceDownAction(String serviceDownAction) {
        this.serviceDownAction = serviceDownAction;
    }

    public void setForDisplay(Boolean forDisplay) {
        this.forDisplay = forDisplay;
    }

    public String getName() {
        return name;
    }

    public LinkedLoadBalancer getLinkedparent() {
        return linkedparent;
    }

    public void setLinkedParentLoadBalancer(LoadBalancer parentLb, GloboResourceConfiguration linkedConfig) {
        this.linkedparent = new LinkedLoadBalancer();
        linkedparent.setName(parentLb.getName());
        linkedparent.setUuid(parentLb.getUuid());
        linkedparent.setConfigId(linkedConfig.getId());

    }

    public void addLinkChild(LoadBalancer childlb, GloboResourceConfiguration linkedConfig) {
        if (linkedchildren == null) {
            linkedchildren = new ArrayList<>();
        }

        LinkedLoadBalancer child = new LinkedLoadBalancer();
        child.setName(childlb.getName());
        child.setUuid(childlb.getUuid());
        child.setConfigId(linkedConfig.getId());
        linkedchildren.add(child);

    }

    @EntityReference(value = LoadBalancer.class)
    public static class LinkedLoadBalancer {
        private String name;
        private String uuid;
        private Long configid;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public Long getConfigId() {
            return configid;
        }

        public void setConfigId(Long configId) {
            this.configid = configId;
        }
    }

    @EntityReference(value = GloboResourceConfiguration.class)
    public static class GloboResourceConfigurationResponse {
        @SerializedName("resourcetype")
        @Param(description = "the resourcetype of the resource")
        private String resourceType;

        @SerializedName("configurationkey")
        @Param(description = "the configuration key")
        private String configurationKey;

        @SerializedName("configurationvalue")
        @Param(description = "the configuration value")
        private String configurationValue;


        public GloboResourceConfigurationResponse() {
        }

        public String getResourceType() {
            return this.resourceType;
        }

        public void setResourceType(String resourceType) {
            this.resourceType = resourceType;
        }

        public void setConfigurationKey(String configurationKey){
            this.configurationKey = configurationKey;
        }

        public void setConfigurationValue(String configurationValue){
            this.configurationValue = configurationValue;
        }

        public String getConfigurationKey() {
            return configurationKey;
        }

        public String getConfigurationValue() {
            return configurationValue;
        }
    }
}
