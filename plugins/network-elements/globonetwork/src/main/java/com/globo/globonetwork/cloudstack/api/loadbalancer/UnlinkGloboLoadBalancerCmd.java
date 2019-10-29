package com.globo.globonetwork.cloudstack.api.loadbalancer;

import com.cloud.event.EventTypes;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboLoadBalancerService;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.FirewallRuleResponse;
import org.apache.cloudstack.api.response.LoadBalancerResponse;


import javax.inject.Inject;

@APICommand(name = "unlinkGloboLoadBalancer", description = "Unlink a load balancer from another one, all vms and networks keep in load balancer", responseObject = LoadBalancerResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UnlinkGloboLoadBalancerCmd extends BaseAsyncCmd {

    private static final String s_name = "unlinkgloboloadbalancerresponse";

    @Inject
    protected GloboLoadBalancerService globoLBService;


    @Parameter(name = ApiConstants.LBID,
            type = CommandType.UUID,
            entityType = FirewallRuleResponse.class,
            required = true,
            description = "the ID of the load balancer rule")
    private Long lbid;

    @Override
    public String getEventType() {
        return EventTypes.EVENT_LB_UNLINK;
    }

    @Override
    public String getEventDescription() {
        return EventTypes.EVENT_LB_UNLINK;
    }


    @Override
    public void execute() throws CloudRuntimeException {
        LoadBalancer lb = globoLBService.unlinkLoadBalancer(lbid);

        LoadBalancerResponse lbResponse = _responseGenerator.createLoadBalancerResponse(lb);

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
}
