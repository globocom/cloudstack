package com.globo.globonetwork.cloudstack.api;

import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import junit.framework.TestCase;
import org.apache.cloudstack.api.response.PoolResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

public class GetGloboNetworkPoolCmdTest extends TestCase {

    public void testExecute() throws Exception {
        GetGloboNetworkPoolCmd cmd = new GetGloboNetworkPoolCmd();

        GloboNetworkPoolResponse.Pool pool1 = new GloboNetworkPoolResponse.Pool();
        pool1.setId(123l);
        pool1.setIdentifier("my_pool");
        pool1.setLbMethod("leastcon");
        pool1.setPort(80);
        pool1.setMaxconn(9);
        pool1.setHealthcheck("/heal.html");
        pool1.setHealthcheckType("HTTP");
        pool1.setExpectedHealthcheck("OK");


        GloboNetworkManager mock = mock(GloboNetworkManager.class);
        when(mock.getPoolById(123l, 10l)).thenReturn(pool1);
        cmd._globoNetworkService = mock;

        cmd.setPoolId(123l);
        cmd.setZoneId(10l);
        cmd.execute();

        PoolResponse poolR = (PoolResponse) cmd.getResponseObject();

        assertEquals((Long) 123l, poolR.getId());
        assertEquals("my_pool", poolR.getName());
        assertEquals("leastcon", poolR.getLbMethod());
        assertEquals((Integer) 80, poolR.getPort());

        assertEquals((Integer)9, poolR.getMaxconn());
        assertEquals("/heal.html", poolR.getHealthcheck());
        assertEquals("HTTP", poolR.getHealthcheckType());
        assertEquals("OK", poolR.getExpectedHealthcheck());


        verify(mock, times(1)).getPoolById(123l, 10l);
    }
}