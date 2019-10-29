package com.globo.globonetwork.cloudstack.api;

import com.cloud.event.EventTypes;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import com.globo.globonetwork.cloudstack.manager.Protocol;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.FirewallRuleResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PoolResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

@APICommand(name = "updateGloboNetworkPool", description = "Update pools.", responseObject = PoolResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UpdateGloboNetworkPoolCmd extends BaseAsyncCmd {
    public static final Logger s_logger = Logger.getLogger(UpdateGloboNetworkPoolCmd.class.getName());

    private static final String s_name = "updateglobonetworkpoolresponse";


    @Parameter(name= ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "the ID of the zone")
    private Long zoneId;

    @Parameter(name= "poolids", type = CommandType.LIST, collectionType = CommandType.LONG, entityType = PoolResponse.class, description = "comma separated list of pool ids")
    private List<Long> poolIds;

    @Parameter(name= ApiConstants.LBID, type = CommandType.UUID, entityType = FirewallRuleResponse.class, description = "the ID of the load balancer rule")
    private Long lbId;

    @Parameter(name = "healthchecktype", type = CommandType.STRING, required = true, description = "Healthcheck Type")
    private String healthcheckType;

    @Parameter(name = "healthcheck", type = CommandType.STRING, required = true, description = "HealthcheckURI")
    private String healthcheck;

    @Parameter(name = "expectedhealthcheck", type = CommandType.STRING, required = true, description = "Expected healthcheck.")
    private String expectedHealthcheck;

    @Parameter(name = "maxconn", type = CommandType.INTEGER, required = true, description = "Max number of connections")
    private Integer maxConn;

    @Parameter(name = "l4protocol", type = CommandType.STRING, required = false, description = "l4 protocol (TCP or UDP)")
    private String l4protocol;

    @Parameter(name = "l7protocol", type = CommandType.STRING, required = false, description = "l7 protocol (HTTP, HTTPS or Outros)")
    private String l7protocol;

    @Parameter(name = "redeploy", type = CommandType.BOOLEAN, required = false, description = "force redeploy vip when need to change l4 and l7 protocol")
    private Boolean redeploy = false;

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
        _lbService.throwExceptionIfIsChildLoadBalancer(lbId, getActualCommandName());
        validateParams();
        ListResponse<PoolResponse> response = new ListResponse<PoolResponse>();


        Protocol.L4 l4 = null;
        if (l4protocol != null){
            l4 = Protocol.L4.valueOfFromNetworkAPI(l4protocol);
        }
        Protocol.L7 l7 = null;
        if (l7protocol != null) {
            l7 = Protocol.L7.valueOfFromNetworkAPI(l7protocol);
        }

        List<GloboNetworkPoolResponse.Pool> pools = _globoNetworkService.updatePools(getPoolIds(), getLbId(), getZoneId(),
                getHealthcheckType(), getHealthcheck(), getExpectedHealthcheck(), getMaxConn(), l4, l7, redeploy);

        List<PoolResponse> lbResponses = new ArrayList<>();

        for ( GloboNetworkPoolResponse.Pool pool : pools) {

            PoolResponse poolResp = new PoolResponse();
            poolResp.setId(pool.getId());
            poolResp.setName(pool.getIdentifier());
            poolResp.setLbMethod(pool.getLbMethod());
            poolResp.setPort(pool.getPort());
            poolResp.setHealthcheckType(pool.getHealthcheckType());
            poolResp.setHealthcheck(pool.getHealthcheck());
            poolResp.setExpectedHealthcheck(pool.getExpectedHealthcheck());
            poolResp.setMaxconn(pool.getMaxconn());


            poolResp.setObjectName("globonetworkpool");
            lbResponses.add(poolResp);

        }

        response.setResponses(lbResponses);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    protected void validateParams() {
        if (l4protocol != null && !Protocol.validValueL4(l4protocol)) {
            throw new CloudRuntimeException("l4protocol invalid value, possible values: " + Protocol.L4.getNetworkAPIValues() + ".");
        }


        if (l7protocol != null && !Protocol.validValueL7(l7protocol)) {
            throw new CloudRuntimeException("l7protocol invalid value, possible values: " + Protocol.L7.getNetworkAPIValues() + ".");
        }

        if (l4protocol != null && l7protocol != null && !redeploy) {
            throw new CloudRuntimeException("Can not change l4protocol and l7protocol values when redeploy is false!");
        }

        if (l4protocol != null && l7protocol != null) {
            Protocol.L4 l4 = Protocol.L4.valueOfFromNetworkAPI(l4protocol);
            Protocol.L7 l7 = Protocol.L7.valueOfFromNetworkAPI(l7protocol);

            if (!Protocol.validProtocols(l4, l7)) {
                throw new CloudRuntimeException("l4protocol with value '" + l4.getNetworkApiOptionValue() + "' does not match with l7protocol '" + l7.getNetworkApiOptionValue() + "'. Possible l7 value(s): " + l4.getL7s() + ".");
            }
        }
    }


    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////


    public Long getLbId() {
        return lbId;
    }

    public String getHealthcheckType() {
        return healthcheckType;
    }

    public void setHealthcheckType(String healthcheckType) {
        this.healthcheckType = healthcheckType;
    }

    public String getHealthcheck() {
        if ( healthcheck != null && healthcheck.equals("null")) {
            healthcheck = null; //fix for sanity tests, cloudmonkey doesnt allow send parameter ""
        }
        return healthcheck;
    }

    public void setHealthcheck(String healthcheck) {
        this.healthcheck = healthcheck;
    }

    public String getExpectedHealthcheck() {
        if ( expectedHealthcheck != null && expectedHealthcheck.equals("null")) {
            expectedHealthcheck = null; //fix for sanity tests, cloudmonkey doesnt allow send parameter ""
        }
        return expectedHealthcheck;
    }

    public void setExpectedHealthcheck(String expectedHealthcheck) {
        this.expectedHealthcheck = expectedHealthcheck;
    }

    public Integer getMaxConn() { return maxConn != null ? maxConn : 0; }

    public void setMaxConn(Integer maxConn) { this.maxConn = maxConn; }

    private Long getZoneId() {

        return zoneId;
    }
    public void setLbId(Long lbId) {
        this.lbId = lbId;
    }

    public void setZoneId(long zoneId) {
        this.zoneId = zoneId;
    }

    public List<Long> getPoolIds() {
        return poolIds;
    }

    public void setPoolIds(List<Long> poolIds) {
        this.poolIds = poolIds;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_LB_HEALTHCHECKPOLICY_UPDATE;
    }

    @Override
    public String getEventDescription() {
        return "Updating Pool Healthcheck in GloboNetwork";
    }


    public String getL4protocol() {
        return l4protocol;
    }

    public void setL4protocol(String l4protocol) {
        this.l4protocol = l4protocol;
    }

    public String getL7protocol() {
        return l7protocol;
    }

    public void setL7protocol(String l7protocol) {
        this.l7protocol = l7protocol;
    }

    public Boolean getRedeploy() {
        return redeploy;
    }

    public void setRedeploy(Boolean redeploy) {
        this.redeploy = redeploy;
    }
}
