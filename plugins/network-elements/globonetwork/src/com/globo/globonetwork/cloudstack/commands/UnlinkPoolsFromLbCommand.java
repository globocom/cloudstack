package com.globo.globonetwork.cloudstack.commands;

import com.cloud.agent.api.Answer;
import com.globo.globonetwork.client.api.GloboNetworkAPI;
import com.globo.globonetwork.client.api.PoolAPI;
import com.globo.globonetwork.client.api.VipV3API;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.model.VipV3;
import com.globo.globonetwork.client.model.pool.PoolV3;
import com.globo.globonetwork.cloudstack.resource.GloboNetworkResource;
import com.globo.globonetwork.cloudstack.resource.VipAPIFacade;
import java.util.ArrayList;
import org.apache.log4j.Logger;


public class UnlinkPoolsFromLbCommand extends GloboNetworkResourceCommand{

    private static final Logger s_logger = Logger.getLogger(UnlinkPoolsFromLbCommand.class);

    private Long lbId;
    private Long vipId;
    private String lbName;
    private String region;

    public UnlinkPoolsFromLbCommand(Long lbId, Long vipId, String lbName, String region) {
        this.lbId = lbId;
        this.vipId = vipId;
        this.lbName = lbName;
        this.region = region;
    }

    @Override
    public Answer execute(GloboNetworkAPI api) {
        try {
            s_logger.debug("[Unlink " + lbName + "] lbid:+ " + lbId + "unlink lb "+ lbName);
            VipAPIFacade sourceFacade = new VipAPIFacade(vipId, api);
            PoolAPI poolAPI = api.getPoolAPI();
            VipV3 vip = sourceFacade.getVip();

            for (VipV3.Port port : vip.getPorts()) {
                for (VipV3.Pool poolPort : port.getPools()) {
                    PoolV3 pool = poolAPI.getById(poolPort.getPoolId());
                    pool.setId(null);

                    String identifier = GloboNetworkResource.buildPoolName(region, lbName, port.getPort(), pool.getDefaultPort());
                    pool.setIdentifier(identifier);

                    pool.setPoolCreated(false);
                    poolAPI.save(pool);
                    poolPort.setPoolId(pool.getId());
                    pool.setVips(new ArrayList<VipV3>());
                }
            }

            VipV3API vipV3API = api.getVipV3API();

            if (vip.getCreated()) {
                vipV3API.deployUpdate(vip);
            } else {
                vipV3API.save(vip);
            }

            return new Answer(this, true, "");
        } catch (GloboNetworkException e) {
            return GloboNetworkResourceCommand.handleGloboNetworkException(this, e);
        }

    }
}
