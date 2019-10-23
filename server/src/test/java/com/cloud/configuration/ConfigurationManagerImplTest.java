package com.cloud.configuration;

import junit.framework.TestCase;

public class ConfigurationManagerImplTest extends TestCase {


    public void testShouldHaveRemovedInQuery() {
        ConfigurationManagerImpl manager = new ConfigurationManagerImpl();

        assertTrue(manager.shouldHaveRemovedQuery("host_pod_ref"));
        assertTrue(manager.shouldHaveRemovedQuery("host"));
        assertTrue(manager.shouldHaveRemovedQuery("volumes"));
        assertTrue(manager.shouldHaveRemovedQuery("physical_network"));
        assertTrue(manager.shouldHaveRemovedQuery("vm_instance"));


        assertFalse(manager.shouldHaveRemovedQuery("hos"));
        assertFalse(manager.shouldHaveRemovedQuery("volu"));
        assertFalse(manager.shouldHaveRemovedQuery("instance"));

    }
}