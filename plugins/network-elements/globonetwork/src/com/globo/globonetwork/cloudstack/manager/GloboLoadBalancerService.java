package com.globo.globonetwork.cloudstack.manager;

import com.cloud.network.rules.LoadBalancer;

public interface GloboLoadBalancerService {

    public LoadBalancer linkLoadBalancer(Long sourceLbid, Long targetLbid);
}
