package com.globo.globonetwork.cloudstack.manager;

import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.rules.LoadBalancer;
import java.util.List;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;

public interface GloboLoadBalancerService {

    LoadBalancer linkLoadBalancer(Long sourceLbid, Long targetLbid);

    LoadBalancer unlinkLoadBalancer(Long lbid);

    LoadBalancer copyVmsAndNetworks(Long fromLbId, Long toLbid);

    List<GloboResourceConfigurationVO> findLoadBalancerChilds(LoadBalancerVO lb);
}
