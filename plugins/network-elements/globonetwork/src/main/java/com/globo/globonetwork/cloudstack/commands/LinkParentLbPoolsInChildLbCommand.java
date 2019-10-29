package com.globo.globonetwork.cloudstack.commands;

import com.cloud.agent.api.Answer;
import com.globo.globonetwork.client.api.GloboNetworkAPI;
import com.globo.globonetwork.client.api.PoolAPI;
import com.globo.globonetwork.client.api.VipV3API;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.model.VipV3;
import com.globo.globonetwork.client.model.pool.PoolV3;
import com.globo.globonetwork.cloudstack.resource.VipAPIFacade;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

public class LinkParentLbPoolsInChildLbCommand extends GloboNetworkResourceCommand {

    private static final Logger s_logger = Logger.getLogger(LinkParentLbPoolsInChildLbCommand.class);


    private String childLbId;
    private String childLbName;
    private Long childVipId;

    private String parentLbId;
    private Long parentVipId;
    private String parentLbName;

    @Override
    public Answer execute(GloboNetworkAPI api) {
        try {
            s_logger.debug("[LinkLBPools " + parentVipId + ":" + childVipId + "] adding pools from parent lb '" + parentLbName + "'(" + parentVipId + ") in bhild lb '" + childLbName + "'(" + childVipId + ").");

            VipAPIFacade parentFacade = new VipAPIFacade(parentVipId, api);
            VipAPIFacade childFacade = new VipAPIFacade(childVipId, api);

            VipV3 targetVip = parentFacade.getVip();

            VipV3 vip = childFacade.getVip();
            List<Long> poolsToDelete = getPoolsToDelete(vip);

            updateSourceVipWithNewPorts(api, targetVip.getPorts(), vip);

            s_logger.debug("[LinkLBPools " + parentVipId + ":" + childVipId + "] removing pools: " + poolsToDelete);
            deleteSourcePools(api, poolsToDelete);

            return new Answer(this, true, "");
        } catch (GloboNetworkException e) {
            return GloboNetworkResourceCommand.handleGloboNetworkException(this, e);
        }


    }

    private void updateSourceVipWithNewPorts(GloboNetworkAPI api, List<VipV3.Port> ports, VipV3 vip) throws GloboNetworkException {
        clearPortsIds(ports);

        VipV3API vipV3API = api.getVipV3API();
        vip.setPorts(ports);
        if (vip.getCreated()) {
            vipV3API.undeploy(vip.getId());
        }
        vipV3API.save(vip);
        vipV3API.deploy(vip.getId());
    }

    private void clearPortsIds(List<VipV3.Port> ports) {
        for (VipV3.Port port : ports) {
            port.setId(null);
            for(VipV3.Pool pool : port.getPools()) {
                pool.setId(null);
            }
        }
    }

    private void deleteSourcePools(GloboNetworkAPI api, List<Long> poolsToDelete) throws GloboNetworkException {
        PoolAPI poolAPI = api.getPoolAPI();
        List<PoolV3> byIdsV3 = poolAPI.getByIdsV3(poolsToDelete);
        for (PoolV3 pool : byIdsV3) {
            if (pool.isPoolCreated()){
                poolAPI.undeployV3(pool.getId());
            }
        }
        poolAPI.deleteV3(poolsToDelete);
    }

    private List<Long> getPoolsToDelete(VipV3 vip) {
        List<Long> poolsIds = new ArrayList<>();
        for (VipV3.Port port : vip.getPorts()) {

            for (VipV3.Pool pool : port.getPools()) {
                poolsIds.add(pool.getPoolId());
            }
            port.setPools(new ArrayList<VipV3.Pool>());

        }
        return poolsIds;
    }

    public void setParentLb(String uuid, String name, Long vipId) {
        this.parentLbId = uuid;
        this.parentLbName = name;
        this.parentVipId = vipId;
    }

    public void setChildLb(String uuid, String name, Long vipId) {
        this.childLbId = uuid;
        this.childLbName = name;
        this.childVipId = vipId;
    }
}
