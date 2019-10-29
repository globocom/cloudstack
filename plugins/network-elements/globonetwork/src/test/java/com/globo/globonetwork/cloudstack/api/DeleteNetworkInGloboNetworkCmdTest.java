package com.globo.globonetwork.cloudstack.api;


import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import junit.framework.TestCase;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.log4j.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;


public class DeleteNetworkInGloboNetworkCmdTest extends TestCase {

    private static final Logger s_logger = Logger.getLogger(DeleteNetworkInGloboNetworkCmdTest.class);

    private DeleteNetworkInGloboNetworkCmd cmd;

    @Before
    public void setUp() {
        cmd = new DeleteNetworkInGloboNetworkCmd() {
            public Long getId() {
                return 1l;
            }
            public boolean isForced() {
                return false;
            }
        };
    }
    @After
    public void searDown() {
        cmd = null;
    }

    @Test
    public void testExecute() {
        GloboNetworkService glbMock = Mockito.mock(GloboNetworkService.class);
        Mockito.when(glbMock.destroyGloboNetwork(1l, false)).thenReturn(true);
        cmd._glbNetService = glbMock;

        cmd.execute();

        SuccessResponse response = (SuccessResponse)cmd.getResponseObject();
        assertEquals("deletenetworkresponse", response.getResponseName());

        Mockito.verify(glbMock, Mockito.times(1)).destroyGloboNetwork(1l, false);
    }

    @Test
    public void testExecute_fail() {
        GloboNetworkService glbMock = Mockito.mock(GloboNetworkService.class);
        Mockito.when(glbMock.destroyGloboNetwork(1l, false)).thenReturn(false);
        cmd._glbNetService = glbMock;

        try {
            cmd.execute();
        } catch (ServerApiException e) {
            assertEquals("Failed to delete network", e.getMessage());
            Mockito.verify(glbMock, Mockito.times(1)).destroyGloboNetwork(1l, false);
        } catch (Exception e) {
            s_logger.error("should be ServerApiException", e);
            fail("should be ServerApiException: " + e.getClass() + " " + e.getMessage());
        }

    }

    @Test
    public void testExecute_throws() {
        GloboNetworkService glbMock = Mockito.mock(GloboNetworkService.class);
        Mockito.when(glbMock.destroyGloboNetwork(1l, false)).thenThrow(new CloudRuntimeException("test delete error"));
        cmd._glbNetService = glbMock;

        try {
            cmd.execute();
        } catch (ServerApiException e) {
            assertEquals("test delete error", e.getMessage());
            Mockito.verify(glbMock, Mockito.times(1)).destroyGloboNetwork(1l, false);
        } catch (Exception e) {
            s_logger.error("should be ServerApiException", e);
            fail("should be ServerApiException: " + e.getClass() + " " + e.getMessage());
        }

    }

}
