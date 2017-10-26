package com.globo.globonetwork.cloudstack.manager;

import com.cloud.network.rules.LoadBalancer;

public interface GloboLoadBalancerService {

    LoadBalancer linkLoadBalancer(Long sourceLbid, Long targetLbid);

    LoadBalancer unlinkLoadBalancer(Long lbid);

    LoadBalancer copyVmsAndNetworks(Long fromLbId, Long toLbid);
}
