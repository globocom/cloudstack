package com.globo.globonetwork.cloudstack.resource;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.lb.LoadBalancingRule;
import com.globo.globonetwork.client.api.GloboNetworkAPI;
import com.globo.globonetwork.client.api.OptionVipV3API;
import com.globo.globonetwork.client.api.VipV3API;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.model.VipPoolMap;
import com.globo.globonetwork.client.model.VipV3;
import com.globo.globonetwork.client.model.OptionVipV3;
import com.globo.globonetwork.client.model.Ipv4;
import com.globo.globonetwork.client.model.VipEnvironment;
import com.globo.globonetwork.cloudstack.commands.ApplyVipInGloboNetworkCommand;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.any;

public class VipAPIFacadeTest {

    private GloboNetworkAPI globoNetworkAPI;
    private VipV3API vipAPI;
    private OptionVipV3API optionVipV3API;

    @Before
    public void setUp() throws Exception {
        globoNetworkAPI = mock(GloboNetworkAPI.class);
        vipAPI = mock(VipV3API.class);
        optionVipV3API = mock(OptionVipV3API.class);
        when(globoNetworkAPI.getVipV3API()).thenReturn(vipAPI);
        when(globoNetworkAPI.getOptionVipV3API()).thenReturn(optionVipV3API);
    }

    @Test
    public void testCreateVipAPIFacade() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(1L, false);
        assertTrue(facade.hasVip());
    }

    @Test
    public void testCreateVipAPIFacadeGivenNullId() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(null, false);
        assertFalse(facade.hasVip());
    }

    @Test
    public void testCreateVip() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(null, false);
        ApplyVipInGloboNetworkCommand cmd = new ApplyVipInGloboNetworkCommand();
        cmd.setVipEnvironmentId(1L);
        cmd.setHealthcheckType("TCP");
        VipEnvironment environment = new VipEnvironment();
        environment.setId(1L);
        Ipv4 ip = new Ipv4();
        List<VipPoolMap> vipPoolMaps = new ArrayList<>();
        VipPoolMap vipPoolMap = new VipPoolMap(1L, 1L, 1L, 80);
        vipPoolMaps.add(vipPoolMap);

        mockVipOptions(environment);

        facade.save(cmd, cmd.getHost(), environment, ip, vipPoolMaps);
        assertTrue(facade.hasVip());
        verify(vipAPI).save(any(VipV3.class));
    }

    @Test
    public void testUpdateNotDeployedVip() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(1L, false);
        ApplyVipInGloboNetworkCommand cmd = new ApplyVipInGloboNetworkCommand();
        cmd.setVipEnvironmentId(1L);
        cmd.setHealthcheckType("TCP");
        VipEnvironment environment = new VipEnvironment();
        environment.setId(1L);
        Ipv4 ip = new Ipv4();
        List<VipPoolMap> vipPoolMaps = new ArrayList<>();
        VipPoolMap vipPoolMap = new VipPoolMap(1L, 1L, 1L, 80);
        vipPoolMaps.add(vipPoolMap);

        mockVipOptions(environment);
        when(vipAPI.save(any(VipV3.class))).thenReturn(new VipV3());

        facade.update(cmd, ip, vipPoolMaps);
        assertTrue(facade.hasVip());
        verify(vipAPI).save(any(VipV3.class));
    }

    @Test
    public void testUpdateVipCreated() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(21L, true);
        facade.setVip(buildFakeVip(21L, true));

        ApplyVipInGloboNetworkCommand cmd = new ApplyVipInGloboNetworkCommand();
        LoadBalancingRule.LbStickinessPolicy stickinessPolicy = new LoadBalancingRule.LbStickinessPolicy("Source-ip", null, false);
        cmd.setPersistencePolicy(stickinessPolicy);
        cmd.setVipEnvironmentId(1L);

        when(optionVipV3API.findOptionsByTypeAndName(1L, "Persistencia", "source-ip")).thenReturn(Collections.singletonList(new OptionVipV3(7L, "Persistencia","Source-ip")));

        //execute
        facade.update(cmd, null, null);

        //test
        verify(vipAPI).updatePersistence(21L, 7L);
    }

    @Test
    public void testUpdateVipNotCreated() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(21L, false);
        ApplyVipInGloboNetworkCommand cmd = new ApplyVipInGloboNetworkCommand();

        cmd.setVipEnvironmentId(1L);
        cmd.setHealthcheckType("TCP");
        VipEnvironment environment = new VipEnvironment();
        environment.setId(1L);

        mockVipOptions(environment);

        when(vipAPI.save(facade.getVip())).thenReturn(facade.getVip());

        //execute
        facade.update(cmd, null, null);

        //test
        verify(vipAPI).save(facade.getVip());
    }

    @Test
    public void testDeployVip() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(1L, false);
        facade.deploy();
        verify(vipAPI, times(1)).deploy(1L);
    }

    @Test
    public void testUndeployVip() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(1L, true);
        facade.undeploy();
        verify(vipAPI, times(1)).undeploy(1L);
    }

    @Test
    public void testDeleteVipKeepingIP() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(1L, true);
        facade.delete(true);
        verify(vipAPI, times(1)).delete(1L, true);
    }

    @Test
    public void testDeleteVipNotKeepingIP() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(1L, true);
        facade.delete(false);
        verify(vipAPI, times(1)).delete(1L, false);
    }

    @Test
    public void testGetPersistenceMethodNone(){
        assertEquals("(nenhum)", VipAPIFacade.getPersistenceMethod(new LoadBalancingRule.LbStickinessPolicy("None", null)));
        assertEquals("(nenhum)", VipAPIFacade.getPersistenceMethod(null));
    }

    @Test
    public void testGetPersistenceMethodGivenCookie(){
        assertEquals("cookie", VipAPIFacade.getPersistenceMethod(new LoadBalancingRule.LbStickinessPolicy("Cookie", null)));
    }

    @Test
    public void testGetPersistenceMethodGivenSourceIp(){
        assertEquals("source-ip", VipAPIFacade.getPersistenceMethod(new LoadBalancingRule.LbStickinessPolicy("Source-ip", null)));
    }

    @Test
    public void testGetPersistenceMethodGivenSourceIpWithPersistence(){
        assertEquals("source-ip com persist. entre portas", VipAPIFacade.getPersistenceMethod(new LoadBalancingRule.LbStickinessPolicy("Source-ip with persistence between ports", null)));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testGetPersistenceMethodGivenInvalidPersistence(){
        VipAPIFacade.getPersistenceMethod(new LoadBalancingRule.LbStickinessPolicy("jsession", null));
    }

    @Test
    public void testGetPoolIds() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(1L, true);
        VipV3 vip = facade.getVip();
        VipV3.PortOptions options = new VipV3.PortOptions(1L, 2L);
        VipV3.Pool pool1 = new VipV3.Pool(1L, 5L, null);
        VipV3.Pool pool2 = new VipV3.Pool(2L, 2L, "/test");
        VipV3.Port port = new VipV3.Port(1L, 80, options, Arrays.asList(pool1, pool2));
        vip.setPorts(Collections.singletonList(port));

        OptionVipV3 l7Rule = new OptionVipV3(5L, "l7_rule", "default_vip");
        when(optionVipV3API.findOptionsByTypeAndName(vip.getEnvironmentVipId(), "l7_rule", "default_vip")).thenReturn(Collections.singletonList(l7Rule));

        List<Long> poolIds = facade.getPoolIds();

        assertEquals(1, poolIds.size());
        assertEquals(new Long(1), poolIds.get(0));
    }

    @Test
    public void testGetPoolIdsGivenNoPoolsFound() throws GloboNetworkException {
        VipAPIFacade facade = createTestVipAPIFacade(1L, true);
        VipV3 vip = facade.getVip();
        VipV3.PortOptions options = new VipV3.PortOptions(1L, 2L);
        VipV3.Pool pool1 = new VipV3.Pool(1L, 100L, null);
        VipV3.Port port = new VipV3.Port(1L, 80, options, Arrays.asList(pool1));
        vip.setPorts(Collections.singletonList(port));

        OptionVipV3 l7Rule = new OptionVipV3(5L, "l7_rule", "default_vip");
        when(optionVipV3API.findOptionsByTypeAndName(vip.getEnvironmentVipId(), "l7_rule", "default_vip")).thenReturn(Collections.singletonList(l7Rule));

        List<Long> poolIds = facade.getPoolIds();

        assertEquals(0, poolIds.size());
    }

    @Test
    public void testRemovePoolGivenVipWithOnePoolPerPort() throws GloboNetworkException {
        VipV3 vip = new VipV3();
        vip.setId(1L);

        List<VipV3.Pool> pools = new ArrayList<>();
        VipV3.Pool pool = new VipV3.Pool();
        pool.setPoolId(1L);
        pools.add(pool);

        List<VipV3.Port> ports = new ArrayList<>();
        VipV3.Port port = new VipV3.Port();
        port.setPools(pools);
        port.setPort(80);

        ports.add(port);
        vip.setPorts(ports);

        VipAPIFacade facade = createTestVipAPIFacade(vip);

        facade.removePool(1L);

        assertEquals(0, vip.getPorts().size());
        verify(globoNetworkAPI.getVipV3API()).deployUpdate(vip);
    }

    @Test
    public void testRemovePoolGivenVipPortWithTwoPools() throws GloboNetworkException {
        VipV3 vip = new VipV3();
        vip.setId(1L);

        List<VipV3.Pool> pools = new ArrayList<>();
        VipV3.Pool pool1 = new VipV3.Pool();
        pool1.setPoolId(1L);
        VipV3.Pool pool2 = new VipV3.Pool();
        pool2.setPoolId(2L);
        pools.add(pool1);
        pools.add(pool2);

        List<VipV3.Port> ports = new ArrayList<>();
        VipV3.Port port = new VipV3.Port();
        port.setPools(pools);
        port.setPort(80);

        ports.add(port);
        vip.setPorts(ports);

        VipAPIFacade facade = createTestVipAPIFacade(vip);

        facade.removePool(1L);

        assertEquals(1, vip.getPorts().size());
        assertEquals(1, vip.getPorts().get(0).getPools().size());
        assertEquals((Long) 2L, vip.getPorts().get(0).getPools().get(0).getPoolId());
        verify(globoNetworkAPI.getVipV3API()).deployUpdate(vip);
    }

    private VipV3 buildFakeVip(Long vipId, Boolean created) throws GloboNetworkException {
        VipV3 vip = new VipV3();
        vip.setId(vipId);
        vip.setCreated(created);
        vip.setOptions(new VipV3.VipOptions());
        return vip;
    }

    private VipAPIFacade createTestVipAPIFacade(Long vipId, Boolean created) throws GloboNetworkException {
        when(vipAPI.getById(vipId)).thenReturn(buildFakeVip(vipId, created));
        return new VipAPIFacade(vipId, globoNetworkAPI);
    }

    private VipAPIFacade createTestVipAPIFacade(VipV3 vip) throws GloboNetworkException {
        when(vipAPI.getById(vip.getId())).thenReturn(vip);
        return new VipAPIFacade(vip.getId(), globoNetworkAPI);
    }

    private void mockVipOptions(VipEnvironment environment) throws GloboNetworkException {
        OptionVipV3 cache = new OptionVipV3(1L, "cache", "(nenhum)");
        OptionVipV3 trafficReturn = new OptionVipV3(2L, "Retorno de trafego", "Normal");
        OptionVipV3 timeout = new OptionVipV3(3L, "timeout", "5");
        OptionVipV3 persistence = new OptionVipV3(4L, "Persistencia", "(nenhum)");
        OptionVipV3 l7Rule = new OptionVipV3(5L, "l7_rule", "default_vip");
        OptionVipV3 l4Protocol = new OptionVipV3(5L, "l4_protocol", "TCP");
        OptionVipV3 l7Protocol = new OptionVipV3(5L, "l7_protocol", "Outros");

        when(optionVipV3API.findOptionsByTypeAndName(environment.getId(), "cache", "(nenhum)")).thenReturn(Collections.singletonList(cache));
        when(optionVipV3API.findOptionsByTypeAndName(environment.getId(), "Retorno de trafego", "Normal")).thenReturn(Collections.singletonList(trafficReturn));
        when(optionVipV3API.findOptionsByTypeAndName(environment.getId(), "timeout", "5")).thenReturn(Collections.singletonList(timeout));
        when(optionVipV3API.findOptionsByTypeAndName(environment.getId(), "Persistencia", "(nenhum)")).thenReturn(Collections.singletonList(persistence));
        when(optionVipV3API.findOptionsByTypeAndName(environment.getId(), "l7_rule", "default_vip")).thenReturn(Collections.singletonList(l7Rule));
        when(optionVipV3API.findOptionsByTypeAndName(environment.getId(), "l4_protocol", "TCP")).thenReturn(Collections.singletonList(l4Protocol));
        when(optionVipV3API.findOptionsByTypeAndName(environment.getId(), "l7_protocol", "Outros")).thenReturn(Collections.singletonList(l7Protocol));
    }
}