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
package com.globo.globonetwork.cloudstack.api.loadbalancer;

import com.cloud.event.EventTypes;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;

import com.cloud.network.IpAddress;
import com.cloud.network.Network;
import com.cloud.network.addr.PublicIp;

import com.cloud.network.rules.HealthCheckPolicy;
import com.cloud.network.rules.StickinessPolicy;
import com.cloud.utils.StringUtils;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.exception.UserCloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import com.globo.globonetwork.cloudstack.manager.HealthCheckHelper;
import com.globo.globonetwork.cloudstack.resource.GloboNetworkResource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.inject.Inject;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandJobType;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.command.user.loadbalancer.CreateLBHealthCheckPolicyCmd;
import org.apache.cloudstack.api.command.user.loadbalancer.CreateLBStickinessPolicyCmd;
import org.apache.cloudstack.api.command.user.loadbalancer.CreateLoadBalancerRuleCmd;
import org.apache.cloudstack.api.response.LoadBalancerResponse;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.globoconfig.GloboResourceConfiguration;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.apache.log4j.Logger;

@APICommand(name = "createGloboLoadBalancer", description = "Creates a globo load balancer", responseObject = LoadBalancerResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateGloboLoadBalancerCmd extends CreateLoadBalancerRuleCmd /*implements LoadBalancer */{
    public static final Logger s_logger = Logger.getLogger(CreateGloboLoadBalancerCmd.class.getName());

    private static final String s_name = "creategloboloadbalancerresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Inject
    private GloboNetworkService globoNetworkSvc;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "Project the IP address will be associated with")
    private Long projectId;

    @Parameter(name = "lbenvironmentid", type = CommandType.LONG, description = "Globo Network LB Environment Id", required = true)
    private Long globoNetworkLBEnvironmentId;

    @Parameter(name = "healthcheckrequest", type = CommandType.STRING, required = false, description = "HTTP Request Path (ex: /healthcheck.html)")
    private String healthcheckRequest;


    @Parameter(name = "stickinessmethodname", type = CommandType.STRING,
               description = "name of the LB Stickiness policy method.")
    private String stickinessMethodName;
    private static final String DEFAULT_ERROR_MESSAGE = "Error trying to create load balancer, please contact your system administrator.";

    @Inject
    GloboNetworkService _globoNetworkService;

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////


    private boolean ipCreated = false;
    private CreateLBHealthCheckPolicyCmd healthCmd;
    private CreateLBStickinessPolicyCmd stickinessCmd;
    private Long ipAddressId = null;

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public void create() throws CloudRuntimeException{
        validation();
        CallContext current = CallContext.current();
        final String name = getName();
        try {
            PublicIp ip = globoNetworkSvc.acquireLbIp(getNetworkId(), getProjectId(), getGloboNetworkLBEnvironmentId());
            IpAddress ipAddress = _networkService.associateIPToNetwork(ip.getId(), getNetworkId());
            ipAddressId = ipAddress.getId();
            ipCreated = true;

            setPublicIpId(ip.getId());
            setSourceIpAddressId(ipAddress.getId());

            s_logger.debug("[load_balancer " + name + "] portable ip created with id " + ip.getId() + ", ipaddress " + ipAddress.getId());

            super.create();
        } catch (UserCloudRuntimeException e) {
            handleErrorAfterIpCreated(name, ipCreated, ipAddressId);
            throw e;
        } catch (Exception e) {
            s_logger.error("[load_balancer " + name + "] error creating load balancer ", e);
            handleErrorAfterIpCreated(name, ipCreated, ipAddressId);


            String msg = DEFAULT_ERROR_MESSAGE;
            if ( current != null) {
                msg += " Context: " + current.getNdcContext() + ". Error: " + e.getLocalizedMessage();
            }
            throw new CloudRuntimeException(msg, e);
        }
    }

    @Override
    public void execute() throws ResourceAllocationException, ResourceUnavailableException {
        final String name = getName();
        try {
            s_logger.debug("[load_balancer " + name + "] processing execute createGloboLoadBalance");

            super.execute();
            createHealthcheck();
            createStickiness();
            loadGloboResourceConfig();

        } catch (Exception e) {
            s_logger.warn("[load_balancer " + name + "] removing loadbalancer after error", e);
            _lbService.deleteLoadBalancerRule(getEntityId(), false, false);
            handleErrorAfterIpCreated(name, ipCreated, ipAddressId);
            throw e;
        }
    }

    private void validation() {
        HealthCheckHelper healthcheck = HealthCheckHelper.build(getName(), getHealthCheckType(), healthcheckRequest, getExpectedHealthCheck());

        if ( HealthCheckHelper.HealthCheckType.isLayer7(healthcheck.getHealthCheckType())
                && healthcheck.getHealthCheck().isEmpty()) {
            throw new CloudRuntimeException("Health check validation: when health check type is HTTP/HTTPS, health check request can not be empty. type: " + healthcheck.getHealthCheckType() + ", request: " + healthcheck.getHealthCheck());
        }
        if (stickinessMethodName != null && !stickinessMethodName.isEmpty())
            GloboNetworkResource.PersistenceMethod.validateValue(stickinessMethodName);

    }

    protected void createStickiness() throws ResourceUnavailableException {
        final String name = getName();

        if (!isToCreateStickiness()) {
            s_logger.debug("[load_balancer " + name + "] it is not necessary to create stickiness. stickinessmethodname: " + stickinessMethodName);
            return;
        }

        try {
            s_logger.debug("[load_balancer " + name + "] creating stickiness policy ");
            stickinessCmd = new CreateLBStickinessPolicyCmd();
            stickinessCmd.setStickinessMethodName(stickinessMethodName);
            stickinessCmd.setLbStickinessPolicyName(stickinessMethodName);
            stickinessCmd.setLbRuleId(getEntityId());

            StickinessPolicy result = _lbService.createLBStickinessPolicy(stickinessCmd);
            stickinessCmd.setEntityId(result.getId());
            stickinessCmd.setEntityUuid(result.getUuid());

            boolean success = _lbService.applyLBStickinessPolicy(stickinessCmd); // with error it already rollback the stickiness
            if (!success) {
                throw new CloudRuntimeException(DEFAULT_ERROR_MESSAGE);
            }
        } catch (NetworkRuleConflictException e) {
            throw new CloudRuntimeException(DEFAULT_ERROR_MESSAGE, e);
        }

    }


    protected void createHealthcheck() {
        final String name = getName();

        if (!isToCreateHealthcheck()) {
            s_logger.debug("[load_balancer " + name + "] it is not necessary to create healthcheck. healthcheckRequest: " + healthcheckRequest);
            return;
        }

        s_logger.debug("[load_balancer " + name + "] creating healthcheck ");
        healthCmd = new CreateLBHealthCheckPolicyCmd();
        healthCmd.setFullUrlParams(new HashMap<String, String>());
        healthCmd.setPingPath(healthcheckRequest);
        healthCmd.setLbRuleId(this.getEntityId());


        HealthCheckPolicy healthcheck = _lbService.createLBHealthCheckPolicy(healthCmd);

        healthCmd.setEntityId(healthcheck.getId());
        healthCmd.setEntityUuid(healthcheck.getUuid());

        s_logger.debug("[load_balancer " + name + "] apply healthcheck: " + healthcheck.getUuid());
        try {
            _lbService.applyLBHealthCheckPolicy(healthCmd); // with error it already rollback the healthcheck
        } catch (ResourceUnavailableException e) {
            throw new CloudRuntimeException(DEFAULT_ERROR_MESSAGE, e);
        }

    }


    private void loadGloboResourceConfig() {
        LoadBalancerResponse lbResponse = (LoadBalancerResponse)getResponseObject();
        List<GloboResourceConfigurationVO> globoResourceConfigs = _globoNetworkService.getGloboResourceConfigs(this.getEntityUuid(), GloboResourceType.LOAD_BALANCER);
        List<LoadBalancerResponse.GloboResourceConfigurationResponse> configs = new ArrayList<LoadBalancerResponse.GloboResourceConfigurationResponse>();
        for (GloboResourceConfiguration config : globoResourceConfigs) {
            LoadBalancerResponse.GloboResourceConfigurationResponse conf = new LoadBalancerResponse.GloboResourceConfigurationResponse();
            conf.setConfigurationKey(config.getKey().name());
            conf.setConfigurationValue(config.getValue());
            configs.add(conf);
        }

        lbResponse.setGloboResourceConfigs(configs);
    }


    @Override
    public long getEntityOwnerId() {
        return getAccountId();
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_LOAD_BALANCER_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "creating load balancer: " + getName() + " account: " + getAccountName();
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.FirewallRule;
    }

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.networkSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        return getNetworkId();
    }


    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getGloboNetworkLBEnvironmentId() {
        return globoNetworkLBEnvironmentId;
    }

    public void setGloboNetworkLBEnvironmentId(Long globoNetworkLBEnvironmentId) {
        this.globoNetworkLBEnvironmentId = globoNetworkLBEnvironmentId;
    }

    public long getAccountId() {
        long networkId = getNetworkId();

        Network network = _networkService.getNetwork(networkId);
        return network.getAccountId();
    }


    private void handleErrorAfterIpCreated(String lbName, boolean ipCreated, Long ipAddressId) {
        s_logger.warn("[load_balancer " + lbName + "] trying to disassociateIp after an error " + ipAddressId);
        if (ipCreated) {
            boolean result = globoNetworkSvc.disassociateIpAddrFromGloboNetwork(ipAddressId);
            if (result) {
                s_logger.warn("[load_balancer " + lbName + "] error after try to disassociateIp " + ipAddressId);
            }
        }
    }

    public boolean isToCreateStickiness() {
        return stickinessMethodName != null && !stickinessMethodName.isEmpty();
    }

    public boolean isToCreateHealthcheck() {
        return healthcheckRequest != null && !healthcheckRequest.isEmpty();
    }


    public void setPingPath(String pingPath) {
        this.healthcheckRequest = pingPath;
    }

    public void setStickinessMethodName(String stickinessMethodName) {
        this.stickinessMethodName = stickinessMethodName;
    }

    public String getStickinessMethodName() {
        return stickinessMethodName;
    }

    @Override
    public String getHealthCheckType() {
        try {
            HealthCheckHelper.HealthCheckType.valueOf(healthCheckType);
        }catch (IllegalArgumentException e) {
            throw new UserCloudRuntimeException("Health check type \'"+ healthCheckType + "\' not allow, possible values: " + StringUtils.join(", ", HealthCheckHelper.HealthCheckType.values()) + ".");
        }

        return healthCheckType;
    }
}
