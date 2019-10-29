package com.globo.globonetwork.cloudstack.manager;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class ProtocolTest {
    @Test
    public void testValidProtocols() throws Exception {


        Protocol.L4 l4 = Protocol.L4.TCP;
        Protocol.L7 l7 = Protocol.L7.HTTP;

        boolean valid = Protocol.validProtocols(l4, l7);
        assertTrue(valid);

        l4 = Protocol.L4.TCP;
        l7 = Protocol.L7.HTTPS;

        valid = Protocol.validProtocols(l4, l7);

        assertTrue(valid);

        l4 = Protocol.L4.TCP;
        l7 = Protocol.L7.OTHERS;

        valid = Protocol.validProtocols(l4, l7);

        assertTrue(valid);

        l4 = Protocol.L4.UDP;
        l7 = Protocol.L7.OTHERS;

        valid = Protocol.validProtocols(l4, l7);

        assertTrue(valid);

    }

    @Test
    public void testValidProtocolsFalse() {
        Protocol.L4 l4 = Protocol.L4.UDP;
        Protocol.L7 l7 = Protocol.L7.HTTP;

        boolean valid = Protocol.validProtocols(l4, l7);
        assertFalse(valid);
    }

}