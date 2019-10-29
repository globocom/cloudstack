package com.globo.globonetwork.cloudstack.api;

import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.utils.StringUtils;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.RegisterDnsForLoadBalancerResponse;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.apache.log4j.Logger;

import javax.inject.Inject;


/**
 * Created by sinval.neto on 7/20/16.
 */
@APICommand(name = "registerDnsForResource", description = "Register a DNS for a Load Balancer", responseObject = RegisterDnsForLoadBalancerResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class RegisterDnsForResourceCmd extends BaseAsyncCmd {
    public static final Logger s_logger = Logger.getLogger(RegisterDnsForResourceCmd.class.getName());

    private static final String s_name = "registerdnsforresourceresponse";

    @Inject
    GloboNetworkService _globoNetworkService;

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.UUID, type = CommandType.STRING, entityType = RegisterDnsForLoadBalancerResponse.class, description = "the ID of the Load Balancer")
    private String id;

    @Parameter(name = ApiConstants.RESOURCE_TYPE, type = CommandType.STRING, entityType = RegisterDnsForLoadBalancerResponse.class, description = "the type of the resource to register the DNS")
    private String resourceType;

    public String getId() {
        return id;
    }

    public GloboResourceType getResourceType() {
        try {
            return GloboResourceType.valueOf(resourceType);
        } catch (Exception e) {
            throw new CloudRuntimeException("Globo resource type \'" + resourceType + "\' does not exist. Possible values: " + StringUtils.join(",", GloboResourceType.values()));
        }
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        RegisterDnsForLoadBalancerResponse response = new RegisterDnsForLoadBalancerResponse();

        _globoNetworkService.registerDnsForResource(id, getResourceType());
        response.setResourceType(resourceType);
        response.setId(id);
        response.setObjectName("registerdns");
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return 0;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_LB_REGISTER_DNS;
    }

    @Override
    public String getEventDescription() {
        return "RegisterDnsForResourceDescription";
    }
}




