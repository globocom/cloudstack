package com.globo.globonetwork.cloudstack.api.loadbalancer;

import com.cloud.event.EventTypes;

import com.cloud.network.rules.LoadBalancer;
import com.cloud.user.Account;
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

@APICommand(name = "linkGloboLoadBalancer", description = "Link a load balancer with another lb, it will add pool's load balancer parent in load balancer child ", responseObject = LoadBalancerResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class LinkGloboLoadBalancerCmd extends BaseAsyncCmd {

    private static final String s_name = "linkgloboloadbalancerresponse";

    @Inject
    protected GloboNetworkService globoNetworkSvc;

    @Inject
    protected GloboLoadBalancerService globoLBService;

    @Parameter(name = ApiConstants.LB_CHILD_LBID,
            type = CommandType.UUID,
            entityType = FirewallRuleResponse.class,
            required = true,
            description = "Load Balancer Child Id")
    private Long childlbid;


    @Parameter(name = ApiConstants.LB_PARENT_LBID,
            type = CommandType.UUID,
            entityType = FirewallRuleResponse.class,
            required = true,
            description = "Load Balancer Parent  Id")
    private Long parentlbid;

    @Override
    public String getEventType() {
        return EventTypes.EVENT_LB_LINK;
    }

    @Override
    public String getEventDescription() {
        return "link load balancer child in parent";
    }

    @Override
    public void execute() throws CloudRuntimeException {
        LoadBalancer lb = globoLBService.linkLoadBalancer(childlbid, parentlbid);

        LoadBalancerResponse lbResponse = _responseGenerator.createLoadBalancerResponse(lb);

        GloboResourceConfiguration linkedConfig = globoNetworkSvc.getGloboResourceConfiguration(lb.getUuid(), GloboResourceType.LOAD_BALANCER, GloboResourceKey.linkedLoadBalancer);

        if (linkedConfig != null) {
            LoadBalancer targetLb = _lbService.findByUuid(linkedConfig.getValue());

            lbResponse.setLinkedParentLoadBalancer(targetLb, linkedConfig);
        }

        setResponseObject(lbResponse);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        LoadBalancer lb = _entityMgr.findById(LoadBalancer.class, childlbid);
        if (lb == null) {
            return Account.ACCOUNT_ID_SYSTEM; // bad id given, parent this command to SYSTEM so ERROR events are tracked
        }
        return lb.getAccountId();
    }


    public Long getChildlbid() {
        return childlbid;
    }

    public void setChildlbid(Long childlbid) {
        this.childlbid = childlbid;
    }

    public Long getParentlbid() {
        return parentlbid;
    }

    public void setParentlbid(Long parentlbid) {
        this.parentlbid = parentlbid;
    }
}
