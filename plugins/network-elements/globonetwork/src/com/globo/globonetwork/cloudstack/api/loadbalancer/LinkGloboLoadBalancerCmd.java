package com.globo.globonetwork.cloudstack.api.loadbalancer;

import com.cloud.event.EventTypes;

import com.cloud.network.rules.LoadBalancer;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboLoadBalancerService;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;

import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.FirewallRuleResponse;
import org.apache.cloudstack.api.response.LoadBalancerResponse;
import org.apache.cloudstack.globoconfig.GloboResourceConfiguration;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;

import javax.inject.Inject;

@APICommand(name = "linkGloboLoadBalancer", description = "Link a load balancer with another lb, allow first source lb send connection to second load balancer vms", responseObject = LoadBalancerResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class LinkGloboLoadBalancerCmd extends BaseAsyncCmd {

    private static final String s_name = "linkgloboloadbalancerresponse";

    @Inject
    protected GloboNetworkService globoNetworkSvc;

    @Inject
    protected GloboLoadBalancerService globoLBService;

    @Parameter(name = ApiConstants.LB_SOURCE_LBID,
            type = CommandType.UUID,
            entityType = FirewallRuleResponse.class,
            required = true,
            description = "the ID of the load balancer rule")
    private Long sourcelbid;


    @Parameter(name = ApiConstants.LB_TARGET_LBID,
            type = CommandType.UUID,
            entityType = FirewallRuleResponse.class,
            required = true,
            description = "the ID of the load balancer rule")
    private Long targetlbid;


    @Override
    public String getEventType() {
        return EventTypes.EVENT_LB_LINK;
    }

    @Override
    public String getEventDescription() {
        return EventTypes.EVENT_LB_LINK;
    }

    @Override
    public void execute() throws CloudRuntimeException {
        LoadBalancer lb = globoLBService.linkLoadBalancer(sourcelbid, targetlbid);

        LoadBalancerResponse lbResponse = _responseGenerator.createLoadBalancerResponse(lb);

        GloboResourceConfiguration linkedConfig = globoNetworkSvc.getGloboResourceConfiguration(lb.getUuid(), GloboResourceType.LOAD_BALANCER, GloboResourceKey.linkedLoadBalancer);

        if (linkedConfig != null) {
            LoadBalancer targetLb = _lbService.findByUuid(linkedConfig.getValue());

            lbResponse.setLinkedLoadBalancer(targetLb, linkedConfig);
        }

        setResponseObject(lbResponse);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return 0;
    }


    public Long getSourcelbid() {
        return sourcelbid;
    }

    public void setSourcelbid(Long sourcelbid) {
        this.sourcelbid = sourcelbid;
    }

    public Long getTargetlbid() {
        return targetlbid;
    }

    public void setTargetlbid(Long targetlbid) {
        this.targetlbid = targetlbid;
    }
}
