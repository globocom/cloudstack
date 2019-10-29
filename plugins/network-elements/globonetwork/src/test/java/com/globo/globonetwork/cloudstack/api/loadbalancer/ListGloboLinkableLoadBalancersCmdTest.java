package com.globo.globonetwork.cloudstack.api.loadbalancer;

import com.cloud.network.dao.LoadBalancerVO;
import com.globo.globonetwork.cloudstack.api.response.LinkableLoadBalancerResponse;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;

import junit.framework.TestCase;
import org.apache.cloudstack.api.response.ListResponse;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ListGloboLinkableLoadBalancersCmdTest extends TestCase {

    public void testExecute() throws Exception {

        ListGloboLinkableLoadBalancersCmd cmd = new ListGloboLinkableLoadBalancersCmd();
        cmd.setLbid(123L);
        cmd.setProjectId(33L);
        cmd.globoNetworkSvc = mock(GloboNetworkManager.class);
        List<LoadBalancerVO> list = new ArrayList<>();
        LoadBalancerVO l = new LoadBalancerVO(null, "lb123", "descrip", 1L, 0, 0, "leastcon", 3L, 1L, 1L, "TCP");
        l.setUuid("id_123");
        list.add(l);

        when(cmd.globoNetworkSvc.listLinkableLoadBalancers(123L, 33L)).thenReturn(list);

        cmd.execute();

        Object result = cmd.getResponseObject();

        assertTrue(result instanceof ListResponse);
        ListResponse listResponse = (ListResponse)result;
        assertEquals("listlinkableloadbalancerresponse", listResponse.getResponseName());

        assertEquals(1, listResponse.getResponses().size());

        LinkableLoadBalancerResponse llb = (LinkableLoadBalancerResponse)listResponse.getResponses().get(0);
        assertEquals("lb123", llb.getName());
        assertEquals("id_123", llb.getUuid());

    }
}