package com.globo.globonetwork.cloudstack.api.loadbalancer;

import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.lb.LoadBalancingRulesService;
import com.globo.globonetwork.cloudstack.manager.GloboLoadBalancerService;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import org.apache.cloudstack.api.ResponseGenerator;

import org.apache.cloudstack.api.response.LoadBalancerResponse;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LinkGloboLoadBalancerCmdTest {

    @Test
    public void execute() throws Exception {
        //mocks
        LinkGloboLoadBalancerCmd cmd =  new LinkGloboLoadBalancerCmd();
        cmd.setChildlbid(123L);
        cmd.setParentlbid(456L);

        cmd.globoLBService = mock(GloboLoadBalancerService.class);
        LoadBalancerVO lb = new LoadBalancerVO();
        lb.setName("test.lb.com");
        lb.setUuid("123123");
        when(cmd.globoLBService.linkLoadBalancer(123L, 456L)).thenReturn(lb);


        cmd._responseGenerator = mock(ResponseGenerator.class);
        LoadBalancerResponse lbResponse = new LoadBalancerResponse();
        lbResponse.setName("test.lb.com");
        when(cmd._responseGenerator.createLoadBalancerResponse(lb)).thenReturn(lbResponse);

        cmd.globoNetworkSvc = mock(GloboNetworkManager.class);
        GloboResourceConfigurationVO globoResourceConfi = new GloboResourceConfigurationVO(GloboResourceType.LOAD_BALANCER, "123123", GloboResourceKey.linkedLoadBalancer, "456456");
        when(cmd.globoNetworkSvc.getGloboResourceConfiguration("123123", GloboResourceType.LOAD_BALANCER, GloboResourceKey.linkedLoadBalancer)).thenReturn(globoResourceConfi);

        cmd._lbService = mock(LoadBalancingRulesService.class);
        LoadBalancerVO targetLb = new LoadBalancerVO();
        targetLb.setUuid("456456");
        targetLb.setName("target.lb.com");

        when(cmd._lbService.findByUuid("456456")).thenReturn(targetLb);

        //execute
        cmd.execute();


        //check
        Object obj = cmd.getResponseObject();
        assertTrue(obj instanceof LoadBalancerResponse);

        LoadBalancerResponse lbResponseReturned = (LoadBalancerResponse) obj;
        assertEquals("test.lb.com", lbResponseReturned.getName());

        LoadBalancerResponse.LinkedLoadBalancer linkedLoadBalancer = lbResponseReturned.getLinkedparent();
        assertEquals("456456", linkedLoadBalancer.getUuid());
        assertEquals("target.lb.com", linkedLoadBalancer.getName());

    }
}