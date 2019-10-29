package com.globo.globonetwork.cloudstack.api;

import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import com.globo.globonetwork.cloudstack.response.GloboNetworkExpectHealthcheckResponse;
import java.util.ArrayList;
import java.util.List;
import org.apache.cloudstack.api.response.ExpectedHealthcheckResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Created by lucas.castro on 3/7/16.
 */
public class ListGloboNetworkExpectedHealthchecksCmdTest {

    @Test
    public void testExecute() throws Exception {
        ListGloboNetworkExpectedHealthchecksCmd cmd = new ListGloboNetworkExpectedHealthchecksCmd();

        List<GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck> list = new ArrayList<>();
        list.add(new GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck(3l, "OK"));
        list.add(new GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck(1l, "AC"));
        list.add(new GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck(5l, "WORKING"));
        list.add(new GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck(4l, "ok1"));
        list.add(new GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck(2l, "ac"));

        GloboNetworkManager mock = mock(GloboNetworkManager.class);
        when(mock.listAllExpectedHealthchecks()).thenReturn(list);

        cmd._globoNetworkService = mock;

        cmd.execute();

        ListResponse<ExpectedHealthcheckResponse> responseObject = (ListResponse< ExpectedHealthcheckResponse >)cmd.getResponseObject();
        assertNotNull(responseObject);

        assertEquals((Integer)5, responseObject.getCount());

        List<ExpectedHealthcheckResponse> result = responseObject.getResponses();

        ExpectedHealthcheckResponse expectedHealthcheck = result.get(0);
        assertEquals((Long)1l, expectedHealthcheck.getId());
        assertEquals("AC", expectedHealthcheck.getExpected());

        expectedHealthcheck = result.get(1);
        assertEquals((Long)2l, expectedHealthcheck.getId());
        assertEquals("ac", expectedHealthcheck.getExpected());


        expectedHealthcheck = result.get(result.size() -1);
        assertEquals((Long)5l, expectedHealthcheck.getId());
        assertEquals("WORKING", expectedHealthcheck.getExpected());
    }


}