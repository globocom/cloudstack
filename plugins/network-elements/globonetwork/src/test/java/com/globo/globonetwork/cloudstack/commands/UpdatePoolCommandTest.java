package com.globo.globonetwork.cloudstack.commands;


import com.cloud.agent.api.Answer;
import com.globo.globonetwork.client.api.GloboNetworkAPI;
import com.globo.globonetwork.client.api.PoolAPI;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.model.pool.PoolV3;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static com.globo.globonetwork.cloudstack.resource.GloboNetworkResourceTest.mockPool;
import static com.globo.globonetwork.cloudstack.resource.GloboNetworkResourceTest.mockPoolSave;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UpdatePoolCommandTest {

    private UpdatePoolCommand cmd;
    private GloboNetworkAPI gnAPI;

    @Before
    public void setUp(){
        gnAPI = mock(GloboNetworkAPI.class);
        when(gnAPI.getPoolAPI()).thenReturn(mock(PoolAPI.class));
    }

    @Test
    public void testExecuteUpdatePool() throws GloboNetworkException {
        cmd = new UpdatePoolCommand(Arrays.asList(12L, 13L), "HTTP", "GET /heal HTTP/1.0\\r\\nHost: vip.domain.com\\r\\n\\r\\n", "OK", 5, "vip.domain.com");

        PoolV3 pool1 = mockPool(12L, "ACS_POOL_vip.domain.com_8080", 8080, "least", "http", "/heal.html", "OK", "*:*", 10);


        List<PoolV3.PoolMember> poolMembers = pool1.getPoolMembers();
        PoolV3.PoolMember poolMember = new PoolV3.PoolMember();
        poolMember.setLimit(10);
        poolMembers.add(poolMember);


        PoolV3 pool2 = mockPool(13L, "ACS_POOL_vip.domain.com_8443", 8443, "least", "http", "/heal.html", "OK", "*:*", 10);
        List<PoolV3> poolsResponse = new ArrayList<>();
        poolsResponse.add(pool1);
        poolsResponse.add(pool2);

        when(gnAPI.getPoolAPI().getByIdsV3(Arrays.asList(12L, 13L))).thenReturn(poolsResponse);

        mockPoolSave(12L, 12L, true, 80, 8080, "10.0.0.1", "HTTP", "GET /heal HTTP/1.0\\r\\nHost: vip.domain.com\\r\\n\\r\\n", "OK", 5, "none" , gnAPI);
        mockPoolSave(13L, 13L, true, 443, 8443, "10.0.0.1", "HTTP", "GET /heal HTTP/1.0\\r\\nHost: vip.domain.com\\r\\n\\r\\n", "OK", 5, "none", gnAPI );

        Answer answer = cmd.execute(gnAPI);

        List<GloboNetworkPoolResponse.Pool> pools = ((GloboNetworkPoolResponse)answer).getPools();

        assertEquals(2, pools.size());

        GloboNetworkPoolResponse.Pool pool = pools.get(0);
        assertEquals((Long) 12L, pool.getId());
        assertEquals((Integer)5, pool.getMaxconn());
        assertEquals("HTTP", pool.getHealthcheckType());
        assertEquals("GET /heal HTTP/1.0\\r\\nHost: vip.domain.com\\r\\n\\r\\n", pool.getHealthcheck());
        assertEquals("OK", pool.getExpectedHealthcheck());

        assertEquals((Integer)5, poolMember.getLimit());


        pool = pools.get(1);
        assertEquals((Long) 13L, pool.getId());
        assertEquals((Integer)5, pool.getMaxconn());
        assertEquals("HTTP", pool.getHealthcheckType());
        assertEquals("GET /heal HTTP/1.0\\r\\nHost: vip.domain.com\\r\\n\\r\\n", pool.getHealthcheck());
        assertEquals("OK", pool.getExpectedHealthcheck());
    }
}