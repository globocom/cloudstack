package com.globo.globonetwork.cloudstack.manager;

import com.cloud.network.dao.LoadBalancerVMMapDao;
import com.cloud.network.dao.LoadBalancerVMMapVO;
import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.lb.LoadBalancingRulesService;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.globoconfig.GloboResourceConfiguration;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationDao;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GloboLoadBalancerManagerTest {

    GloboLoadBalancerManager service;
    @Before
    public void setup() {
        service = new GloboLoadBalancerManager();
    }


    @Test(expected = CloudRuntimeException.class)
    public void testCheckIfSourceLBHasVmsItHasOne() {
        LoadBalancerVO lb = new LoadBalancerVO();
        lb.setUuid("123123");
        lb.setName("test.lb.com");
        lb.setId(123L);

        service._lb2VmMapDao = mock(LoadBalancerVMMapDao.class);
        List<LoadBalancerVMMapVO> listVms = new ArrayList<>();
        LoadBalancerVMMapVO vm = new LoadBalancerVMMapVO();
        listVms.add(vm);
        when(service._lb2VmMapDao.listByLoadBalancerId(123L)).thenReturn(listVms);


        service.checkIfSourceLBHasVms(lb);
    }

    @Test
    public void testCheckIfSourceLBHasVms() {
        LoadBalancerVO lb = new LoadBalancerVO();
        lb.setUuid("123123");
        lb.setName("test.lb.com");
        lb.setId(123L);

        service._lb2VmMapDao = mock(LoadBalancerVMMapDao.class);
        List<LoadBalancerVMMapVO> listVms = new ArrayList<>();
        when(service._lb2VmMapDao.listByLoadBalancerId(123L)).thenReturn(listVms);

        service.checkIfSourceLBHasVms(lb);

        verify(service._lb2VmMapDao, times(1)).listByLoadBalancerId(123L);
    }

    @Test()
    public void testCheckIfLBAlreadyIsLinkedItIsNotLinked() throws Exception {

        service.lbService = mock(LoadBalancingRulesService.class);
        LoadBalancerVO lb = new LoadBalancerVO();
        lb.setUuid("123123");
        lb.setName("test.lb.com");
        when(service.lbService.findById(123L)).thenReturn(lb);

        service.globoNetworkSvc = mock(GloboNetworkManager.class);
        when(service.globoNetworkSvc.getGloboResourceConfiguration("123123",
                GloboResourceType.LOAD_BALANCER, GloboResourceKey.linkedLoadBalancer)).thenReturn(null);

        LoadBalancer lbReturned = service.checkIfLBAlreadyIsLinked(123L);

        assertEquals("test.lb.com", lbReturned.getName());
        assertEquals("123123", lbReturned.getUuid());
    }

    @Test(expected = CloudRuntimeException.class)
    public void testCheckIfLBAlreadyIsLinkedItIsLinked() throws Exception {

        service.lbService = mock(LoadBalancingRulesService.class);
        LoadBalancerVO lb = new LoadBalancerVO();
        lb.setUuid("123123");
        lb.setName("test.lb.com");
        when(service.lbService.findById(123L)).thenReturn(lb);

        service.globoNetworkSvc = mock(GloboNetworkManager.class);
        GloboResourceConfigurationVO config = new GloboResourceConfigurationVO(GloboResourceType.LOAD_BALANCER, "123123",
                GloboResourceKey.linkedLoadBalancer, "789789");

        when(service.globoNetworkSvc.getGloboResourceConfiguration("123123",
                GloboResourceType.LOAD_BALANCER, GloboResourceKey.linkedLoadBalancer)).thenReturn(config);

        LoadBalancerVO targetExisted = new LoadBalancerVO();
        targetExisted.setUuid("789789");
        targetExisted.setName("targetExisted.lb.com");
        when(service.lbService.findByUuid("789789")).thenReturn(targetExisted);


        service.checkIfLBAlreadyIsLinked(123L);
    }

    @Test
    public void testRegister() {
        LoadBalancerVO sourceLb = new LoadBalancerVO();
        sourceLb.setUuid("123123");
        LoadBalancerVO targetLb = new LoadBalancerVO();
        targetLb.setUuid("456456");

        service.resourceConfigDao = mock(GloboResourceConfigurationDao.class);
        GloboResourceConfigurationVO config = new GloboResourceConfigurationVO(GloboResourceType.LOAD_BALANCER, sourceLb.getUuid(), GloboResourceKey.linkedLoadBalancer, targetLb.getUuid());

        GloboResourceConfigurationVO configResult = new GloboResourceConfigurationVO(GloboResourceType.LOAD_BALANCER, sourceLb.getUuid(), GloboResourceKey.linkedLoadBalancer, targetLb.getUuid());
        configResult.setId(1L);
        when(service.resourceConfigDao.persist(config)).thenReturn(configResult);

        GloboResourceConfiguration configPersisted = service.registerLink(sourceLb, targetLb);
        assertEquals("456456", configPersisted.getValue());
        assertEquals("123123", configPersisted.getResourceUuid());
        assertEquals(1L, configPersisted.getId().longValue());

        verify(service.resourceConfigDao, times(1)).persist(any(GloboResourceConfigurationVO.class));

    }

    @Test
    public void testGetNetworksToAddIntoSourceAllLbsWithOneNetwork() {
        LoadBalancerVO sourceLb = new LoadBalancerVO();
        sourceLb.setNetworkId(12L);
        LoadBalancerVO targetLb = new LoadBalancerVO();
        targetLb.setNetworkId(12L);

        LoadBalancingRule lbRule = new LoadBalancingRule(sourceLb, null, null, null, null);
        lbRule.setAdditionalNetworks(new ArrayList<Long>());

        LoadBalancingRule targetRule = new LoadBalancingRule(targetLb, null, null, null ,null);
        targetRule.setAdditionalNetworks(new ArrayList<Long>());

        List<Long> diffNetworks = this.service.getNetworksToAddIntoSource(lbRule, targetRule);

        assertTrue(diffNetworks.isEmpty());
    }

    @Test
    public void testGetNetworksToAddIntoSourceLbWith2NetworksAndTargetWithOne() {
        LoadBalancerVO sourceLb = new LoadBalancerVO();
        sourceLb.setNetworkId(12L);

        LoadBalancerVO targetLb = new LoadBalancerVO();
        targetLb.setNetworkId(12L);

        LoadBalancingRule lbRule = new LoadBalancingRule(sourceLb, null, null, null, null);
        List<Long> additionalNetworks = new ArrayList<>(Arrays.asList(5L));
        lbRule.setAdditionalNetworks(additionalNetworks);

        LoadBalancingRule targetRule = new LoadBalancingRule(targetLb, null, null, null ,null);
        targetRule.setAdditionalNetworks(new ArrayList<Long>());
        List<Long> diffNetworks = this.service.getNetworksToAddIntoSource(lbRule, targetRule);

        //it is not necessery to add networks into sourceLb
        assertTrue(diffNetworks.isEmpty());
    }

    @Test
    public void testGetNetworksToAddIntoSourceLbWith2NetworksAndTargetWith2() {
        LoadBalancerVO sourceLb = new LoadBalancerVO();
        sourceLb.setNetworkId(12L);

        LoadBalancerVO targetLb = new LoadBalancerVO();
        targetLb.setNetworkId(12L);

        LoadBalancingRule lbRule = new LoadBalancingRule(sourceLb, null, null, null, null);
        List<Long> additionalNetworks = new ArrayList<>(Arrays.asList(5L,6L));
        lbRule.setAdditionalNetworks(additionalNetworks);

        LoadBalancingRule targetRule = new LoadBalancingRule(targetLb, null, null, null ,null);
        targetRule.setAdditionalNetworks(new ArrayList<Long>(Arrays.asList(8L, 6L)));
        List<Long> diffNetworks = this.service.getNetworksToAddIntoSource(lbRule, targetRule);

        //only need to add network 8L
        assertFalse(diffNetworks.isEmpty());
        assertEquals(1, diffNetworks.size());
        assertTrue(diffNetworks.contains(8L));
    }

    @Test
    public void testGetNetworksToAddIntoSourceLbFirstNetworkIsDifferent() {
        LoadBalancerVO sourceLb = new LoadBalancerVO();
        sourceLb.setNetworkId(15L);

        LoadBalancerVO targetLb = new LoadBalancerVO();
        targetLb.setNetworkId(12L);

        LoadBalancingRule lbRule = new LoadBalancingRule(sourceLb, null, null, null, null);
        List<Long> additionalNetworks = new ArrayList<>(Arrays.asList(5L,6L));
        lbRule.setAdditionalNetworks(additionalNetworks);

        LoadBalancingRule targetRule = new LoadBalancingRule(targetLb, null, null, null ,null);
        targetRule.setAdditionalNetworks(new ArrayList<Long>(Arrays.asList(8L, 6L)));
        List<Long> diffNetworks = this.service.getNetworksToAddIntoSource(lbRule, targetRule);

        //only need to add network 8L
        assertFalse(diffNetworks.isEmpty());
        assertEquals(2, diffNetworks.size());
        assertTrue(diffNetworks.contains(8L));
        assertTrue(diffNetworks.contains(12L));
    }
}