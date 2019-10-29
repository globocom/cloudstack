package com.globo.globonetwork.cloudstack.api;

import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PoolResponse;

import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;


public class ListGloboNetworkPoolsCmdTest extends TestCase {

    public void testExecute() throws Exception {
        ListGloboNetworkPoolsCmd cmd = new ListGloboNetworkPoolsCmd();

        List<GloboNetworkPoolResponse.Pool> lbResponses = new ArrayList<GloboNetworkPoolResponse.Pool>();
        GloboNetworkPoolResponse.Pool pool1 = new GloboNetworkPoolResponse.Pool();
        pool1.setId(123l);
        pool1.setIdentifier("my_pool");
        pool1.setLbMethod("leastcon");
        pool1.setPort(80);
        lbResponses.add(pool1);


        GloboNetworkPoolResponse.Pool pool2 = new GloboNetworkPoolResponse.Pool();
        pool2.setId(124l);
        pool2.setIdentifier("my_pool_2");
        pool2.setLbMethod("round");
        pool2.setPort(8090);
        lbResponses.add(pool2);

        GloboNetworkManager mock = mock(GloboNetworkManager.class);
        when(mock.listAllPoolByVipId(123l, 10l)).thenReturn(lbResponses);
        cmd._globoNetworkService = mock;


        cmd.setLbId(123l);
        cmd.setZoneId(10l);
        cmd.execute();

        ListResponse list = (ListResponse) cmd.getResponseObject();
        assertEquals((Integer) 2, list.getCount());

        List<PoolResponse> pools = list.getResponses();

        PoolResponse pool11 = pools.get(0);
        assertEquals((Long)123l, pool11.getId());
        assertEquals("my_pool", pool11.getName());
        assertEquals("leastcon", pool11.getLbMethod());
        assertEquals((Integer) 80, pool11.getPort());

        PoolResponse pool21 = pools.get(1);
        assertEquals((Long)124l, pool21.getId());
        assertEquals("my_pool_2", pool21.getName());
        assertEquals("round", pool21.getLbMethod());
        assertEquals((Integer) 8090, pool21.getPort());

    }
}