package com.globo.globonetwork.cloudstack.api;

import com.cloud.event.EventTypes;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import javax.inject.Inject;

import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.FirewallRuleResponse;
import org.apache.cloudstack.api.response.PoolResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

@APICommand(name = "createGloboNetworkPool", description = "Creates a new pool to a load balancer", responseObject = PoolResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateGloboNetworkPoolCmd extends BaseAsyncCmd {

    public static final Logger s_logger = Logger.getLogger(CreateGloboNetworkPoolCmd.class.getName());

    private static final String s_name = "createglobonetworkpoolresponse";

    @Parameter(name= ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "the ID of the zone")
    private Long zoneId;

    @Parameter(name= ApiConstants.LBID, type = CommandType.UUID, entityType = FirewallRuleResponse.class, description = "the ID of the load balancer rule")
    private Long lbId;

    @Parameter(name = ApiConstants.PUBLIC_PORT, type = CommandType.INTEGER, required = true, description = "the public port from where the network traffic will be load balanced from")
    private Integer publicPort;


    @Parameter(name = ApiConstants.PRIVATE_PORT, type = CommandType.INTEGER, required = true, description = "the private port of the private ip address/virtual machine where the network traffic will be load balanced to")
    private Integer privatePort;

    @Inject
    GloboNetworkManager _globoNetworkService;

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    @Override
    public void execute() {
        GloboNetworkPoolResponse.Pool pool = _globoNetworkService.createPool(this);
        PoolResponse poolResp = new PoolResponse();
        if(pool != null) {
            poolResp.setId(pool.getId());
            poolResp.setName(pool.getIdentifier());
            poolResp.setLbMethod(pool.getLbMethod());
            poolResp.setPort(pool.getPort());
            poolResp.setHealthcheckType(pool.getHealthcheckType());
            poolResp.setHealthcheck(pool.getHealthcheck());
            poolResp.setExpectedHealthcheck(pool.getExpectedHealthcheck());
            poolResp.setMaxconn(pool.getMaxconn());
            poolResp.setObjectName("globonetworkpool");
        }
        poolResp.setResponseName(getCommandName());
        this.setResponseObject(poolResp);
    }

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public Long getLbId() {
        return lbId;
    }

    public void setLbId(Long lbId) {
        this.lbId = lbId;
    }

    public Integer getPublicPort() {
        return publicPort;
    }

    public void setPublicPort(Integer publicPort) {
        this.publicPort = publicPort;
    }

    public Integer getPrivatePort() {
        return privatePort;
    }

    public void setPrivatePort(Integer privatePort) {
        this.privatePort = privatePort;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(long zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_LB_HEALTHCHECKPOLICY_UPDATE;
    }

    @Override
    public String getEventDescription() {
        return "Creates a new pool";
    }

}
