package com.globo.globonetwork.cloudstack.api;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.utils.StringUtils;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import com.globo.globonetwork.cloudstack.response.GetGloboResourceConfigurationResponse;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.apache.log4j.Logger;

import javax.inject.Inject;

/**
 * Created by sinval.neto on 7/27/16.
 */
@APICommand(name = "getGloboResourceConfiguration", description = "Get a GloboResourceConfiguration", responseObject = GetGloboResourceConfigurationResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class GetGloboResourceConfigurationCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(GetGloboResourceConfigurationCmd.class.getName());

    private static final String s_name = "getgloboresourceconfigurationresponse";

    @Inject
    GloboNetworkService _globoNetworkService;

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.RESOURCE_ID, type = CommandType.STRING, entityType = GetGloboResourceConfigurationResponse.class, description = "the ID of the RESOURCE")
    private String resourceUuid;

    @Parameter(name = ApiConstants.RESOURCE_TYPE, type = CommandType.STRING, required = true, entityType = GetGloboResourceConfigurationResponse.class, description = "the type of the resource")
    private String resourceType;

    @Parameter(name = ApiConstants.RESOURCE_KEY, type = CommandType.STRING, required = true, entityType = GetGloboResourceConfigurationResponse.class, description = "the type of the resource")
    private String resourceKey;

    public String getUuid() {
        return resourceUuid;
    }

    public GloboResourceType getResourceType() {
        try {
            return GloboResourceType.valueOf(resourceType);
        } catch (Exception e) {
            throw new CloudRuntimeException("Globo resource type \'" + resourceType + "\' does not exist. Possible values: " + StringUtils.join(",", GloboResourceType.values()));
        }

    }

    public GloboResourceKey getResourceKey() {
        try {
            return GloboResourceKey.valueOf(resourceKey);
        } catch (Exception e) {
            throw new CloudRuntimeException("Globo resource type \'" + resourceKey + "\' does not exist. Possible values: " + StringUtils.join(",", GloboResourceKey.values()));
        }
    }


    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        GetGloboResourceConfigurationResponse response = new GetGloboResourceConfigurationResponse();
        response.setObjectName("globoresourceconfiguration");
        response.setResponseName(getCommandName());


        GloboResourceConfigurationVO globoResourceConfigurationVO = _globoNetworkService.getGloboResourceConfiguration(this.getUuid(), getResourceType(), getResourceKey());
        if (globoResourceConfigurationVO == null) {
            s_logger.warn("Could not find GloboResource Configuration. resouceid:" + resourceUuid + ", resourceType: " + resourceType + ", resoucekey: " + resourceKey);
        } else {
            response.setUuid(this.getUuid());
            response.setResourceType(globoResourceConfigurationVO.getResourceType().toString());
            response.setConfigurationValue(globoResourceConfigurationVO.getValue());
            response.setConfigurationKey(globoResourceConfigurationVO.getKey().toString());
        }


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
}
