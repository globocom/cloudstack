package com.globo.globonetwork.cloudstack.api;


import org.junit.Test;

public class CreateGloboNetworkPoolCmdTest {

    @Test
    public void testValidatel4l7Params() {
        CreateGloboNetworkPoolCmd cmd = new CreateGloboNetworkPoolCmd();

        cmd.validateParams();
    }
}