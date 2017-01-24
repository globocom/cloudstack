/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.globo.globonetwork.cloudstack.resource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.cloud.exception.InvalidParameterValueException;
import com.globo.globonetwork.client.api.ExpectHealthcheckAPI;
import com.globo.globonetwork.client.api.GloboNetworkAPI;
import com.globo.globonetwork.client.api.NetworkAPI;
import com.globo.globonetwork.client.api.NetworkJsonAPI;
import com.globo.globonetwork.client.api.PoolAPI;
import com.globo.globonetwork.client.model.IPv4Network;
import com.globo.globonetwork.client.model.Network;
import com.globo.globonetwork.client.model.Pool;
import com.globo.globonetwork.client.model.PoolOption;
import com.globo.globonetwork.client.model.VipJson;
import com.globo.globonetwork.client.model.VipPoolMap;
import com.globo.globonetwork.client.model.VipXml;
import com.globo.globonetwork.client.model.Vlan;

import com.globo.globonetwork.client.model.healthcheck.ExpectHealthcheck;
import com.globo.globonetwork.client.model.pool.PoolV3;
import com.globo.globonetwork.cloudstack.commands.ApplyVipInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.CreatePoolCommand;
import com.globo.globonetwork.cloudstack.commands.ListExpectedHealthchecksCommand;
import com.globo.globonetwork.cloudstack.commands.ListPoolLBCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveVipFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.UpdatePoolCommand;
import com.globo.globonetwork.cloudstack.manager.HealthCheckHelper;
import com.globo.globonetwork.cloudstack.response.GloboNetworkExpectHealthcheckResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.naming.ConfigurationException;

import com.globo.globonetwork.cloudstack.commands.ListPoolOptionsCommand;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolOptionResponse;
import org.junit.Before;
import org.junit.Test;

import com.cloud.agent.api.Answer;
import com.globo.globonetwork.client.api.EquipmentAPI;
import com.globo.globonetwork.client.api.IpAPI;
import com.globo.globonetwork.client.api.VipAPI;
import com.globo.globonetwork.client.api.VipEnvironmentAPI;
import com.globo.globonetwork.client.api.VlanAPI;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.model.Equipment;
import com.globo.globonetwork.client.model.Ip;
import com.globo.globonetwork.client.model.Ipv4;
import com.globo.globonetwork.client.model.Real.RealIP;
import com.globo.globonetwork.client.model.VipEnvironment;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;

public class GloboNetworkResourceTest {

    GloboNetworkResource _resource;
    GloboNetworkAPI gnAPI;
    Ipv4 vipIp;

    @Before
    public void setUp() throws ConfigurationException {
        _resource = spy(new GloboNetworkResource());
        gnAPI = mock(GloboNetworkAPI.class);
        doReturn(gnAPI).when(_resource).getNewGloboNetworkAPI();
        when(gnAPI.getEquipmentAPI()).thenReturn(mock(EquipmentAPI.class));
        when(gnAPI.getIpAPI()).thenReturn(mock(IpAPI.class));
        when(gnAPI.getVipAPI()).thenReturn(mock(VipAPI.class));
        when(gnAPI.getVipEnvironmentAPI()).thenReturn(mock(VipEnvironmentAPI.class));
        when(gnAPI.getVlanAPI()).thenReturn(mock(VlanAPI.class));
        when(gnAPI.getNetworkAPI()).thenReturn(mock(NetworkAPI.class));
        when(gnAPI.getNetworkJsonAPI()).thenReturn(mock(NetworkJsonAPI.class));
        when(gnAPI.getPoolAPI()).thenReturn(mock(PoolAPI.class));
        when(gnAPI.getExpectHealthcheckAPI()).thenReturn(mock(ExpectHealthcheckAPI.class));
        when(gnAPI.getVipAPI()).thenReturn(mock(VipAPI.class));
    }

    static long s_ipSequence = 100;

    @Test
    public void testRemoveNullVIP(){
        RemoveVipFromGloboNetworkCommand cmd = new RemoveVipFromGloboNetworkCommand();
        Answer answer = _resource.execute(cmd);
        assertTrue(answer.getResult());
        assertEquals("Vip request was previously removed from GloboNetwork", answer.getDetails());
    }

    @Test
    public void testRemoveAlreadyRemovedVIP() throws GloboNetworkException {
        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.hasVip()).thenReturn(false);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);

        RemoveVipFromGloboNetworkCommand cmd = new RemoveVipFromGloboNetworkCommand();
        cmd.setVipId(1L);
        Answer answer = _resource.execute(cmd);
        assertTrue(answer.getResult());
        assertEquals("Vip request 1 was previously removed from GloboNetwork", answer.getDetails());
    }

    @Test
    public void testRemoveVIP() throws GloboNetworkException {
        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.hasVip()).thenReturn(true);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);

        RemoveVipFromGloboNetworkCommand cmd = new RemoveVipFromGloboNetworkCommand();
        cmd.setVipId(1L);
        cmd.setKeepIp(true);

        Answer answer = _resource.execute(cmd);

        assertTrue(answer.getResult());
        verify(facadeMock).undeploy();
        verify(facadeMock).delete(true);
    }

    @Test
    public void testRemoveVIPAndDeleteIP() throws GloboNetworkException {
        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.hasVip()).thenReturn(true);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);

        RemoveVipFromGloboNetworkCommand cmd = new RemoveVipFromGloboNetworkCommand();
        cmd.setVipId(1L);
        cmd.setKeepIp(false);

        Answer answer = _resource.execute(cmd);

        assertTrue(answer.getResult());
        verify(facadeMock).undeploy();
        verify(facadeMock).delete(false);
    }

    @Test
    public void testRemoveVipWithNetworkApiError() throws GloboNetworkException {
        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.hasVip()).thenReturn(true);
        doThrow(new GloboNetworkException("API error")).when(facadeMock).undeploy();
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);

        RemoveVipFromGloboNetworkCommand cmd = new RemoveVipFromGloboNetworkCommand();
        cmd.setVipId(1L);

        Answer answer = _resource.execute(cmd);
        assertFalse(answer.getResult());
    }

    @Test
    public void testGetBalancingAlgorithmGivenRoundRobin(){
        assertEquals(GloboNetworkResource.LbAlgorithm.RoundRobin, _resource.getBalancingAlgorithm("roundrobin"));
    }

    @Test
    public void testGetBalancingAlgorithmGivenLeastConn(){
        assertEquals(GloboNetworkResource.LbAlgorithm.LeastConn, _resource.getBalancingAlgorithm("leastconn"));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testGetBalancingAlgorithmGivenInvalid(){
        _resource.getBalancingAlgorithm("random");
    }

    @Test
    public void testFindPoolByPort(){
        List<PoolV3> pools = new ArrayList<>();
        PoolV3 pool = new PoolV3();
        pool.setDefaultPort(80);
        pools.add(pool);

        assertFalse(_resource.findPoolsByPort(80, pools).isEmpty());
        assertTrue(_resource.findPoolsByPort(81, pools).isEmpty());
    }

    @Test
    public void testFindPoolByPorGivenNullVip(){
        assertTrue(_resource.findPoolsByPort(80, null).isEmpty());
    }

    private PoolV3 mockPoolSave(Long poolId, Long idReturned, Boolean hasPoolMember, Integer vipPort, Integer port, String ip, String healthCheckType, String healthCheck, String expectedHealthCheck, int maxConn,  String serviceDAction) throws GloboNetworkException {
        PoolV3 expectedPool = new PoolV3();
        expectedPool.setId(poolId);
        expectedPool.setIdentifier(_resource.buildPoolName("region", "vip.domain.com",vipPort,  port));
        expectedPool.setLbMethod("round-robin");
        expectedPool.setMaxconn(maxConn);
        expectedPool.setDefaultPort(port);
        expectedPool.setEnvironment(120L);

        PoolV3.Healthcheck healthcheck = expectedPool.getHealthcheck();
        healthcheck.setHealthcheck(healthCheckType, healthCheck, expectedHealthCheck);
        healthcheck.setDestination("*:*");

        PoolV3.ServiceDownAction serviceDownAction = new PoolV3.ServiceDownAction();
        serviceDownAction.setName(serviceDAction);
        expectedPool.setServiceDownAction(serviceDownAction);

        if (hasPoolMember) {
            PoolV3.PoolMember poolMember = new PoolV3.PoolMember();
            poolMember.setPortReal(port);
            poolMember.setWeight(0);
            poolMember.setPriority(0);
            poolMember.setMemberStatus(7);
            poolMember.setEquipmentId(1L);
            poolMember.setEquipmentName("vm-01");

            PoolV3.Ip ipp = new PoolV3.Ip();
            ipp.setIpFormated(ip);
            ipp.setId(1L);
            poolMember.setIp(ipp);
            expectedPool.getPoolMembers().add(poolMember);
        }

        PoolV3 newPool = new PoolV3();
        newPool.setId(idReturned);
        when(gnAPI.getPoolAPI().save(expectedPool)).thenReturn(newPool);

        return expectedPool;
    }

    @Test
    public void testGetVipInfosGivenInvalidVlan() throws GloboNetworkException {
        Ipv4 ip = new Ipv4();
        ip.setNetworkId(1L);
        when(gnAPI.getVipEnvironmentAPI().search(123L, null, null, null)).thenReturn(new VipEnvironment());
        when(gnAPI.getIpAPI().checkVipIp("10.0.0.1", 123L, false)).thenReturn(ip);
        when(gnAPI.getNetworkAPI().getNetwork(1L, false)).thenReturn(new IPv4Network());
        when(gnAPI.getVlanAPI().getById(999L)).thenReturn(new Vlan());
        try{
            _resource.getVipInfos(gnAPI, 123L, "10.0.0.1");
        }catch(InvalidParameterValueException e){
            assertEquals("Vlan " + null + " was not found in GloboNetwork", e.getMessage());
        }
    }

    @Test
    public void testGetVipInfosGivenInvalidNetwork() throws GloboNetworkException {
        Ipv4 ip = new Ipv4();
        ip.setNetworkId(1L);
        when(gnAPI.getVipEnvironmentAPI().search(123L, null, null, null)).thenReturn(new VipEnvironment());
        when(gnAPI.getIpAPI().checkVipIp("10.0.0.1", 123L, false)).thenReturn(ip);
        when(gnAPI.getNetworkAPI().getNetwork(1L, false)).thenReturn(new IPv4Network());
        when(gnAPI.getVlanAPI().getById(anyLong())).thenReturn(new Vlan());
        try{
            _resource.getVipInfos(gnAPI, 123L, "10.0.0.1");
        }catch(InvalidParameterValueException e){
            assertEquals("Network " + null + " was not found in GloboNetwork", e.getMessage());
        }
    }

    @Test
    public void testCreateNewVIPWithZeroReals() throws Exception {
        List<String> ports = Collections.singletonList("80:8080");
        VipJson vipToBeCreated = buildFakeVip(120L, 546L, 345L, ports);
        ApplyVipInGloboNetworkCommand cmd = createTestApplyVipCommand(vipToBeCreated);

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.getPoolIds()).thenReturn(new ArrayList<Long>());
        when(facadeMock.hasVip()).thenReturn(false);
        when(facadeMock.createVipResponse(cmd)).thenReturn(new GloboNetworkVipResponse());
        doReturn(facadeMock).when(_resource).createVipAPIFacade(cmd.getVipId(), gnAPI);

        mockGetVipMetadata(cmd);
        when(gnAPI.getPoolAPI().save(any(PoolV3.class))).thenReturn(new PoolV3());

        Answer answer = _resource.execute(cmd);

        assertTrue(answer.getResult());
        assertTrue(answer instanceof GloboNetworkVipResponse);
        verify(gnAPI.getPoolAPI(), times(1)).save(any(PoolV3.class));
        verify(facadeMock).save(any(ApplyVipInGloboNetworkCommand.class), any(String.class), any(VipEnvironment.class), any(Ip.class), any(List.class));
    }

    private ApplyVipInGloboNetworkCommand createTestApplyVipCommand(VipJson vipToBeCreated) {
        ApplyVipInGloboNetworkCommand cmd = new ApplyVipInGloboNetworkCommand();
        cmd.setVipId(null);
        cmd.setHost(vipToBeCreated.getHost());
        cmd.setIpv4(vipToBeCreated.getIps().get(0));
        cmd.setVipEnvironmentId(120L);
        cmd.setPorts(Collections.singletonList("80:8080"));
        cmd.setRealList(new ArrayList<GloboNetworkVipResponse.Real>());
        cmd.setMethodBal("roundrobin");

        HealthCheckHelper build = HealthCheckHelper.build("vip.domain.com", "TCP", "", null);
        cmd.setHealthcheckType(build.getHealthCheckType());
        cmd.setExpectedHealthcheck(build.getExpectedHealthCheck());
        cmd.setHealthcheck(build.getHealthCheck());
        return cmd;
    }

    @Test
    public void testCreateNewVIPWithMultiplePortsAndNoReal() throws Exception {
        List<String> ports = Arrays.asList("80:8080", "443:8443");
        VipJson vipToBeCreated = buildFakeVip(120L, 546L, 345L, ports);
        ApplyVipInGloboNetworkCommand cmd = createTestApplyVipCommand(vipToBeCreated);
        cmd.setPorts(ports);

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.getPoolIds()).thenReturn(new ArrayList<Long>());
        when(facadeMock.hasVip()).thenReturn(false);
        when(facadeMock.createVipResponse(cmd)).thenReturn(new GloboNetworkVipResponse());
        doReturn(facadeMock).when(_resource).createVipAPIFacade(cmd.getVipId(), gnAPI);

        mockGetVipMetadata(cmd);
        when(gnAPI.getPoolAPI().save(any(PoolV3.class))).thenReturn(new PoolV3());

        Answer answer = _resource.execute(cmd);

        assertTrue(answer.getResult());
        assertTrue(answer instanceof GloboNetworkVipResponse);
        verify(gnAPI.getPoolAPI(), times(2)).save(any(PoolV3.class));
        verify(facadeMock).save(any(ApplyVipInGloboNetworkCommand.class), any(String.class), any(VipEnvironment.class), any(Ip.class), any(List.class));
    }

    @Test
    public void testUpdateVip() throws Exception {
        List<String> ports = Collections.singletonList("80:8080");
        VipJson vipToBeCreated = buildFakeVip(120L, 546L, 345L, ports);
        ApplyVipInGloboNetworkCommand cmd = createTestApplyVipCommand(vipToBeCreated);
        vipToBeCreated.setId(1L);
        cmd.setVipId(1L);

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.getPoolIds()).thenReturn(new ArrayList<Long>());
        when(facadeMock.hasVip()).thenReturn(true);
        when(facadeMock.createVipResponse(cmd)).thenReturn(new GloboNetworkVipResponse());
        doReturn(facadeMock).when(_resource).createVipAPIFacade(cmd.getVipId(), gnAPI);

        mockGetVipMetadata(cmd);
        when(gnAPI.getPoolAPI().save(any(PoolV3.class))).thenReturn(new PoolV3());

        Answer answer = _resource.execute(cmd);

        assertTrue(answer.getResult());
        assertTrue(answer instanceof GloboNetworkVipResponse);
        verify(gnAPI.getPoolAPI(), times(1)).save(any(PoolV3.class));
        verify(facadeMock).update(any(ApplyVipInGloboNetworkCommand.class), any(Ip.class), any(List.class));
    }

    @Test
    public void testCreateVipGivenFailedOperation() throws Exception {
        List<String> ports = Collections.singletonList("80:8080");
        VipJson vipToBeCreated = buildFakeVip(120L, 546L, 345L, ports);
        ApplyVipInGloboNetworkCommand cmd = createTestApplyVipCommand(vipToBeCreated);

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.getPoolIds()).thenReturn(new ArrayList<Long>());
        when(facadeMock.hasVip()).thenReturn(false);
        when(facadeMock.save(eq(cmd), any(String.class), any(VipEnvironment.class), any(Ip.class), any(List.class))).thenThrow(new GloboNetworkException("API Error"));
        doReturn(facadeMock).when(_resource).createVipAPIFacade(cmd.getVipId(), gnAPI);

        mockGetVipMetadata(cmd);
        when(gnAPI.getPoolAPI().save(any(PoolV3.class))).thenReturn(new PoolV3());
        Answer answer = _resource.execute(cmd);

        assertFalse(answer.getResult());
        assertFalse(answer instanceof GloboNetworkVipResponse);
        verify(facadeMock, times(1)).save(any(ApplyVipInGloboNetworkCommand.class), any(String.class), any(VipEnvironment.class), any(Ip.class), any(List.class));
        verify(gnAPI.getPoolAPI(), times(1)).delete(any(List.class));
    }

    @Test
    public void testUpdateVipGivenFailedOperation() throws Exception {
        List<String> ports = Collections.singletonList("80:8080");
        VipJson vipToBeCreated = buildFakeVip(120L, 546L, 345L, ports);
        ApplyVipInGloboNetworkCommand cmd = createTestApplyVipCommand(vipToBeCreated);
        cmd.setVipId(1L);

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.getPoolIds()).thenReturn(new ArrayList<Long>());
        when(facadeMock.hasVip()).thenReturn(true);
        when(facadeMock.update(eq(cmd), any(Ip.class), any(List.class))).thenThrow(new GloboNetworkException("API Error"));
        doReturn(facadeMock).when(_resource).createVipAPIFacade(cmd.getVipId(), gnAPI);

        mockGetVipMetadata(cmd);
        when(gnAPI.getPoolAPI().save(any(PoolV3.class))).thenReturn(new PoolV3());
        Answer answer = _resource.execute(cmd);

        assertFalse(answer.getResult());
        assertFalse(answer instanceof GloboNetworkVipResponse);
        verify(facadeMock, times(1)).update(any(ApplyVipInGloboNetworkCommand.class), any(Ip.class), any(List.class));
        verify(gnAPI.getPoolAPI(), times(0)).delete(any(List.class));
    }

    private void mockGetVipMetadata(ApplyVipInGloboNetworkCommand cmd) throws GloboNetworkException {
        when(gnAPI.getVipEnvironmentAPI().search(anyLong(), isNull(String.class), isNull(String.class), isNull(String.class))).thenReturn(new VipEnvironment());
        when(gnAPI.getIpAPI().checkVipIp(cmd.getIpv4(), 120L, false)).thenReturn(new Ipv4());
        when(gnAPI.getNetworkAPI().getNetwork(anyLong(), eq(false))).thenReturn(new IPv4Network());
        when(gnAPI.getVlanAPI().getById(anyLong())).thenReturn(new Vlan());
    }

    @Test
    public void testCreateVipResponseGivenVipWithOnePortMapping() throws GloboNetworkException {
        VipXml vip = createVipXML(Collections.singletonList("8080:80"), new RealIP(1L, 80, "192.268.0.10", 8080));

        VipEnvironment vipEnvironment = new VipEnvironment();
        vipEnvironment.setId(1L);
        when(gnAPI.getVipEnvironmentAPI().search(null, vip.getFinality(), vip.getClient(), vip.getEnvironment())).thenReturn(vipEnvironment);
        when(gnAPI.getIpAPI().checkVipIp(vip.getIps().get(0), vipEnvironment.getId(), false)).thenReturn(new Ipv4());

        GloboNetworkVipResponse answer = (GloboNetworkVipResponse) _resource.createVipResponse(vip, new ApplyVipInGloboNetworkCommand(), gnAPI);

        assertTrue(answer.getResult());
        assertEquals(1, answer.getReals().size());
        assertEquals(1, answer.getReals().get(0).getPorts().size());
        assertEquals(answer.getId(), vip.getId());
        assertEquals(answer.getName(), vip.getHost());
        assertEquals(answer.getIp(), vip.getIps().get(0));
        assertEquals(answer.getLbEnvironmentId(), new Long(vip.getEnvironment()));
        assertEquals(answer.getCache(), vip.getCache());
        assertEquals(answer.getMethod(), vip.getMethod());
        assertEquals(answer.getPersistence(), vip.getPersistence());
        assertEquals(answer.getHealthcheckType(), vip.getHealthcheckType());
        assertEquals(answer.getHealthcheck(), vip.getHealthcheck());
        assertEquals(answer.getMaxConn(), vip.getMaxConn());
    }

    @Test
    public void testCreateVipResponseGivenVipWithMoreThanOnePortMapping() throws GloboNetworkException {
        VipXml vip = createVipXML(Arrays.asList("8080:80", "443: 8443"), new RealIP(1L, 80, "192.268.0.10", 8080));

        VipEnvironment vipEnvironment = new VipEnvironment();
        vipEnvironment.setId(1L);
        when(gnAPI.getVipEnvironmentAPI().search(null, vip.getFinality(), vip.getClient(), vip.getEnvironment())).thenReturn(vipEnvironment);
        when(gnAPI.getIpAPI().checkVipIp(vip.getIps().get(0), vipEnvironment.getId(), false)).thenReturn(new Ipv4());

        GloboNetworkVipResponse answer = (GloboNetworkVipResponse) _resource.createVipResponse(vip, new ApplyVipInGloboNetworkCommand(), gnAPI);

        assertTrue(answer.getResult());
        assertEquals(1, answer.getReals().size());
        assertEquals(2, answer.getReals().get(0).getPorts().size());
    }

    @Test
    public void testCreateVipResponseGivenVipWithMoreThanOneReal() throws GloboNetworkException {
        VipXml vip = createVipXML(Arrays.asList("8080:80", "443: 8443"), new RealIP(1L, 80, "192.268.0.10", 8080), new RealIP(2L, 80, "192.268.0.11", 8080));

        VipEnvironment vipEnvironment = new VipEnvironment();
        vipEnvironment.setId(1L);
        when(gnAPI.getVipEnvironmentAPI().search(null, vip.getFinality(), vip.getClient(), vip.getEnvironment())).thenReturn(vipEnvironment);
        when(gnAPI.getIpAPI().checkVipIp(vip.getIps().get(0), vipEnvironment.getId(), false)).thenReturn(new Ipv4());

        GloboNetworkVipResponse answer = (GloboNetworkVipResponse) _resource.createVipResponse(vip, new ApplyVipInGloboNetworkCommand(), gnAPI);

        assertTrue(answer.getResult());
        assertEquals(2, answer.getReals().size());
    }

    @Test
    public void testListPoolOptions() throws IOException, GloboNetworkException {
        List<PoolOption> options = Collections.singletonList(new PoolOption(1L, "reset"));
        IPv4Network network = new IPv4Network();
        network.setVlanId(1L);
        Vlan vlan = new Vlan();
        vlan.setEnvironment(1L);
        when(gnAPI.getNetworkJsonAPI().listVipNetworks(45L, false)).thenReturn(Arrays.<Network>asList(network));
        when(gnAPI.getVlanAPI().getById(1L)).thenReturn(vlan);
        when(gnAPI.getPoolAPI().listPoolOptions(1L, "ServiceDownAction")).thenReturn(options);
        GloboNetworkPoolOptionResponse answer = (GloboNetworkPoolOptionResponse) _resource.executeRequest(new ListPoolOptionsCommand(45L, "ServiceDownAction"));
        assertFalse(answer.getPoolOptions().isEmpty());
        assertEquals(new Long(1L), answer.getPoolOptions().get(0).getId());
    }

    @Test
    public void testListPoolOptionsGivenNoPoolsReturned() throws IOException, GloboNetworkException {
        IPv4Network network = new IPv4Network();
        network.setVlanId(1L);
        Vlan vlan = new Vlan();
        vlan.setEnvironment(1L);
        when(gnAPI.getNetworkJsonAPI().listVipNetworks(45L, false)).thenReturn(Arrays.<Network>asList(network));
        when(gnAPI.getVlanAPI().getById(1L)).thenReturn(vlan);
        when(gnAPI.getPoolAPI().listPoolOptions(1L, "ServiceDownAction")).thenReturn(new ArrayList<PoolOption>());
        GloboNetworkPoolOptionResponse answer = (GloboNetworkPoolOptionResponse) _resource.executeRequest(new ListPoolOptionsCommand(45L, "ServiceDownAction"));
        assertTrue(answer.getPoolOptions().isEmpty());
    }

    @Test
    public void testListPoolOptionsGivenGloboNetworkException() throws IOException, GloboNetworkException {
        IPv4Network network = new IPv4Network();
        network.setVlanId(1L);
        Vlan vlan = new Vlan();
        vlan.setEnvironment(1L);
        when(gnAPI.getNetworkJsonAPI().listVipNetworks(45L, false)).thenReturn(Arrays.<Network>asList(network));
        when(gnAPI.getVlanAPI().getById(1L)).thenReturn(vlan);
        when(gnAPI.getPoolAPI().listPoolOptions(1L, "ServiceDownAction")).thenThrow(new GloboNetworkException("Netapi failed"));
        Answer answer = _resource.executeRequest(new ListPoolOptionsCommand(45L, "ServiceDownAction"));
        assertFalse(answer.getResult());
        assertEquals("Netapi failed", answer.getDetails());
    }

    @Test
    public void testListPoolOptionsGivenIOException() throws IOException, GloboNetworkException {
        IPv4Network network = new IPv4Network();
        network.setVlanId(1L);
        Vlan vlan = new Vlan();
        vlan.setEnvironment(1L);
        when(gnAPI.getNetworkJsonAPI().listVipNetworks(45L, false)).thenReturn(Arrays.<Network>asList(network));
        when(gnAPI.getVlanAPI().getById(1L)).thenReturn(vlan);
        when(gnAPI.getPoolAPI().listPoolOptions(1L, "ServiceDownAction")).thenThrow(new IOException());
        Answer answer = _resource.executeRequest(new ListPoolOptionsCommand(45L, "ServiceDownAction"));
        assertFalse(answer.getResult());
    }

    @Test
    public void testListPoolExecute() throws GloboNetworkException {
        List<Pool> poolsNetworkApi = new ArrayList<>();
        Pool pool1 = new Pool();
        pool1.setId(33L);
        pool1.setIdentifier("my_pool");
        pool1.setLbMethod("leastcon");
        pool1.setDefaultPort(80);
        poolsNetworkApi.add(pool1);

        Pool pool2 = new Pool();
        pool2.setId(22L);
        pool2.setIdentifier("my_pool_2");
        pool2.setLbMethod("round");
        pool2.setDefaultPort(8091);
        poolsNetworkApi.add(pool1);

        when(gnAPI.getPoolAPI().listAllByReqVip(123L)).thenReturn(poolsNetworkApi);

        ListPoolLBCommand cmd = new ListPoolLBCommand(123L);
        Answer answer = _resource.executeRequest(cmd);

        List<GloboNetworkPoolResponse.Pool> pools = ((GloboNetworkPoolResponse)answer).getPools();

        assertEquals(2, pools.size());

        GloboNetworkPoolResponse.Pool pool = pools.get(0);
        assertEquals((Long) 33L, pool.getId());
        assertEquals("my_pool", pool.getIdentifier());
        assertEquals("leastcon", pool.getLbMethod());
        assertEquals((Integer) 80, pool.getPort());
    }

    @Test
    public void testCreatePool() throws GloboNetworkException {
        CreatePoolCommand command = createMockCreatePoolCommand();
        PoolV3 pool = new PoolV3();
        pool.setDefaultPort(80);
        mockGetVipInfos();

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.hasVip()).thenReturn(true);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);
        when(gnAPI.getPoolAPI().save(any(PoolV3.class))).thenReturn(pool);

        GloboNetworkPoolResponse answer = (GloboNetworkPoolResponse) _resource.executeRequest(command);

        assertTrue(answer.getResult());
        assertNotNull(answer.getPool());
        verify(gnAPI.getPoolAPI()).save(any(PoolV3.class));
        verify(facadeMock).addPool(any(VipEnvironment.class), eq(80), eq("TCP"), any(PoolV3.class));
    }

    @Test
    public void testCreatePoolGivenPoolCreationError() throws GloboNetworkException {
        CreatePoolCommand command = createMockCreatePoolCommand();
        mockGetVipInfos();

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.hasVip()).thenReturn(true);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);
        when(gnAPI.getPoolAPI().save(any(PoolV3.class))).thenThrow(new GloboNetworkException(""));

        Answer answer = _resource.executeRequest(command);

        assertFalse(answer.getResult());
        verify(gnAPI.getPoolAPI()).save(any(PoolV3.class));
        verify(gnAPI.getPoolAPI(), times(0)).delete(any(Long.class));
        verify(facadeMock, times(0)).addPool(any(VipEnvironment.class), eq(80), eq("TCP"), any(PoolV3.class));
    }

    @Test
    public void testCreatePoolGivenVipDeployError() throws GloboNetworkException {
        CreatePoolCommand command = createMockCreatePoolCommand();
        PoolV3 pool = new PoolV3();
        pool.setId(1L);
        pool.setDefaultPort(80);
        mockGetVipInfos();

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.hasVip()).thenReturn(true);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);
        doThrow(new GloboNetworkException("")).when(facadeMock).addPool(any(VipEnvironment.class), eq(80), eq("TCP"), any(PoolV3.class));
        when(gnAPI.getPoolAPI().save(any(PoolV3.class))).thenReturn(pool);

        Answer answer = _resource.executeRequest(command);

        assertFalse(answer.getResult());
        verify(gnAPI.getPoolAPI()).save(any(PoolV3.class));
        verify(facadeMock).addPool(any(VipEnvironment.class), eq(80), eq("TCP"), any(PoolV3.class));
        verify(gnAPI.getPoolAPI()).deleteV3(any(Long.class));
    }

    @Test
    public void testCreatePoolGivenVipNotCreated() throws GloboNetworkException {
        CreatePoolCommand command = createMockCreatePoolCommand();

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.hasVip()).thenReturn(false);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);

        GloboNetworkPoolResponse answer = (GloboNetworkPoolResponse) _resource.executeRequest(command);

        assertTrue(answer.getResult());
        verify(facadeMock, times(0)).addPool(any(VipEnvironment.class), eq(80), eq("TCP"), any(PoolV3.class));
    }

    private void mockGetVipInfos() throws GloboNetworkException {
        when(gnAPI.getVipEnvironmentAPI().search(999L, null, null, null)).thenReturn(new VipEnvironment());
        Ipv4 ip = new Ipv4();
        ip.setNetworkId(1L);
        when(gnAPI.getIpAPI().checkVipIp("192.168.10.4", 999L, false)).thenReturn(ip);
        IPv4Network network = new IPv4Network();
        network.setVlanId(999L);
        when(gnAPI.getNetworkAPI().getNetwork(1L, false)).thenReturn(network);
        when(gnAPI.getVlanAPI().getById(999L)).thenReturn(new Vlan());
    }

    private CreatePoolCommand createMockCreatePoolCommand() {
        CreatePoolCommand command = new CreatePoolCommand();
        command.setVipId(1L);
        command.setVipName("dummy.lb.com");
        command.setVipIp("192.168.10.4");
        command.setVipEnvironment(999L);
        command.setPublicPort(80);
        command.setPrivatePort(80);
        command.setBalacingAlgorithm("leastconn");
        command.setReals(new ArrayList<GloboNetworkVipResponse.Real>());
        command.setRegion("region");
        return command;
    }

    @Test
    public void testExecuteUpdatePool() throws GloboNetworkException {
        UpdatePoolCommand cmd = new UpdatePoolCommand(Arrays.asList(12L, 13L), "HTTP", "GET /heal HTTP/1.0\\r\\nHost: vip.domain.com\\r\\n\\r\\n", "OK", 5, "vip.domain.com");

        PoolV3 pool1 = mockPool(12L, "ACS_POOL_vip.domain.com_8080", 8080, "least", "http", "/heal.html", "OK", "*:*", 10);
        PoolV3 pool2 = mockPool(13L, "ACS_POOL_vip.domain.com_8443", 8443, "least", "http", "/heal.html", "OK", "*:*", 10);
        List<PoolV3> poolsResponse = new ArrayList<>();
        poolsResponse.add(pool1);
        poolsResponse.add(pool2);

        when(gnAPI.getPoolAPI().getByIdsV3(Arrays.asList(12L, 13L))).thenReturn(poolsResponse);

        mockPoolSave(12L, 12L, true, 80, 8080, "10.0.0.1", "HTTP", "GET /heal HTTP/1.0\\r\\nHost: vip.domain.com\\r\\n\\r\\n", "OK", 5, "none" );
        mockPoolSave(13L, 13L, true, 443, 8443, "10.0.0.1", "HTTP", "GET /heal HTTP/1.0\\r\\nHost: vip.domain.com\\r\\n\\r\\n", "OK", 5, "none" );

        Answer answer = _resource.executeRequest(cmd);

        List<GloboNetworkPoolResponse.Pool> pools = ((GloboNetworkPoolResponse)answer).getPools();

        assertEquals(2, pools.size());

        GloboNetworkPoolResponse.Pool pool = pools.get(0);
        assertEquals((Long) 12L, pool.getId());
        assertEquals((Integer)5, pool.getMaxconn());
        assertEquals("HTTP", pool.getHealthcheckType());
        assertEquals("GET /heal HTTP/1.0\\r\\nHost: vip.domain.com\\r\\n\\r\\n", pool.getHealthcheck());
        assertEquals("OK", pool.getExpectedHealthcheck());

        pool = pools.get(1);
        assertEquals((Long) 13L, pool.getId());
        assertEquals((Integer)5, pool.getMaxconn());
        assertEquals("HTTP", pool.getHealthcheckType());
        assertEquals("GET /heal HTTP/1.0\\r\\nHost: vip.domain.com\\r\\n\\r\\n", pool.getHealthcheck());
        assertEquals("OK", pool.getExpectedHealthcheck());
    }

    private PoolV3 mockPool(Long poolId, String identifier, int port, String lbmethod, String healthheckType, String healthcheck, String expectedHealthcheck, String destination, Integer maxconn) {
        PoolV3.ServiceDownAction action = new PoolV3.ServiceDownAction();
        action.setId(3L);
        action.setName("none");

        PoolV3 pool = new PoolV3();
        pool.setId(poolId);
        pool.setIdentifier(identifier);
        pool.setDefaultPort(port);
        pool.setLbMethod(lbmethod);
        pool.setMaxconn(maxconn);
        pool.setEnvironment(120L);
        pool.setServiceDownAction(action);

        PoolV3.Healthcheck healthchecker = pool.getHealthcheck();
        healthchecker.setHealthcheck(healthheckType, healthcheck, expectedHealthcheck);
        healthchecker.setDestination(destination);

        return pool;
    }

    @Test
    public void testBuildPoolMembers() throws GloboNetworkException {
        List<GloboNetworkVipResponse.Real> realList = new ArrayList<>();
        GloboNetworkVipResponse.Real real = new GloboNetworkVipResponse.Real();
        real.setIp("10.0.0.1");
        real.setEnvironmentId(1212L);
        real.setVmName("vmname-1");
        realList.add(real);

        GloboNetworkVipResponse.Real real2 = new GloboNetworkVipResponse.Real();
        real2.setEnvironmentId(1212L);
        real2.setIp("10.0.0.2");
        real2.setVmName("vmname-2");
        realList.add(real2);

        GloboNetworkVipResponse.Real realRevoked = new GloboNetworkVipResponse.Real();
        realRevoked.setRevoked(true);
        realList.add(realRevoked);

        //real 1
        Ipv4 ipv4 = new Ipv4();
        ipv4.setId(1111L);
        when(gnAPI.getIpAPI().findByIpAndEnvironment("10.0.0.1", 1212L, false)).thenReturn(ipv4);

        Equipment equipment = new Equipment();
        equipment.setId(111L);
        equipment.setName("equip-1");
        when(gnAPI.getEquipmentAPI().listByName("vmname-1")).thenReturn(equipment);

        //real 2
        ipv4 = new Ipv4();
        ipv4.setId(2222L);
        when(gnAPI.getIpAPI().findByIpAndEnvironment("10.0.0.2", 1212L, false)).thenReturn(ipv4);

        equipment = new Equipment();
        equipment.setId(222L);
        equipment.setName("equip-2");
        when(gnAPI.getEquipmentAPI().listByName("vmname-2")).thenReturn(equipment);

        //execute
        List<PoolV3.PoolMember> poolMembers = _resource.buildPoolMembers(gnAPI, realList);

        //assert
        assertEquals(2, poolMembers.size());

        PoolV3.PoolMember poolMember = poolMembers.get(0);
        assertEquals("10.0.0.1", poolMember.getIp().getIpFormated());
        assertEquals((Long) 1111L, poolMember.getIp().getId());
        assertEquals((Long) 111L, poolMember.getEquipmentId());
        assertEquals("equip-1", poolMember.getEquipmentName());

        PoolV3.PoolMember poolMember2 = poolMembers.get(1);
        assertEquals("10.0.0.2", poolMember2.getIp().getIpFormated());
        assertEquals((Long) 2222L, poolMember2.getIp().getId());
        assertEquals((Long) 222L, poolMember2.getEquipmentId());
        assertEquals("equip-2", poolMember2.getEquipmentName());
    }

    @Test
    public void testSavePoolsEmptyPool() throws GloboNetworkException {
        //mock input
        List<String> ports = Arrays.asList("80:8080", "443:8443");
        ApplyVipInGloboNetworkCommand cmd = new ApplyVipInGloboNetworkCommand();
        cmd.setHost("vip.domain.com");
        cmd.setPorts(ports);
        cmd.setHealthcheckType("HTTP");
        cmd.setHealthcheck("/index.html");
        cmd.setExpectedHealthcheck("WORKING");
        cmd.setHealthCheckDestination(null);
        cmd.setMethodBal("roundrobin");
        cmd.setRegion("region");

        GloboNetworkResource.VipInfoHelper vipInfos = new GloboNetworkResource.VipInfoHelper(125L, null, null, null);

        //mock save
        List<PoolV3.PoolMember> poolMembers = new ArrayList<>();

        PoolV3 pool = new PoolV3();
        pool.setIdentifier("ACS_POOL_region_vip.domain.com_80_8080");
        pool.setLbMethod("round-robin");
        pool.setMaxconn(0);
        pool.setDefaultPort(8080);
        pool.setEnvironment(125L);

        PoolV3.Healthcheck healthcheck = pool.getHealthcheck();
        healthcheck.setHealthcheck("HTTP", "/index.html", "WORKING");
        healthcheck.setDestination(null);

        when(gnAPI.getPoolAPI().save(pool)).thenReturn(new PoolV3(123L));

        PoolV3 pool2 = new PoolV3();
        pool2.setIdentifier("ACS_POOL_region_vip.domain.com_443_8443");
        pool2.setLbMethod("round-robin");
        pool2.setMaxconn(0);
        pool2.setDefaultPort(8443);
        pool2.setEnvironment(125L);

        PoolV3.Healthcheck healthcheck2 = pool2.getHealthcheck();
        healthcheck2.setHealthcheck("TCP", "", "");
        healthcheck2.setDestination(null);

        when(gnAPI.getPoolAPI().save(pool2)).thenReturn(new PoolV3(321L));

        //execute
        List<VipPoolMap> vipPoolMaps = _resource.createPools(gnAPI, poolMembers, vipInfos.getEnvironment(), cmd);

        assertEquals(2, vipPoolMaps.size());

        VipPoolMap vipPoolMap = vipPoolMaps.get(0);
        assertEquals((Long) 123L, vipPoolMap.getPoolId());
        assertEquals((Integer)80, vipPoolMap.getPort());

        vipPoolMap = vipPoolMaps.get(1);
        assertEquals((Long) 321L, vipPoolMap.getPoolId());
        assertEquals((Integer)443, vipPoolMap.getPort());
    }

    @Test
    public void testSavePoolWithOneReal() throws GloboNetworkException {
        ApplyVipInGloboNetworkCommand cmd = new ApplyVipInGloboNetworkCommand();
        cmd.setRealList(Collections.singletonList(new GloboNetworkVipResponse.Real()));
        cmd.setMethodBal("roundrobin");
        cmd.setHost("vip.domain.com");
        cmd.setPorts(Collections.singletonList("80:8080"));

        HealthCheckHelper build = HealthCheckHelper.build("vip.domain.com", "TCP", "", null);
        cmd.setHealthcheckType(build.getHealthCheckType());
        cmd.setExpectedHealthcheck(build.getExpectedHealthCheck());
        cmd.setHealthcheck(build.getHealthCheck());
        cmd.setRegion("region");

        List<Long> poolIds = new ArrayList<>(); // new VIP no pools created yet
        Ipv4 ip = new Ipv4();
        ip.setNetworkId(1L);

        List<PoolV3.PoolMember> poolMembers = new ArrayList<>();
        PoolV3.PoolMember poolMember = new PoolV3.PoolMember();
        poolMember.setPortReal(8080);
        poolMember.setPriority(0);
        poolMember.setMemberStatus(7);
        poolMember.setEquipmentName("vm-01");
        poolMember.setEquipmentId(1L);
        poolMember.setWeight(0);

        PoolV3.Ip ipPm = new PoolV3.Ip();
        ipPm.setId(1L);
        ipPm.setIpFormated("10.0.0.1");

        poolMember.setIp(ipPm);
        poolMembers.add(poolMember);

        GloboNetworkResource.VipInfoHelper vipInfo = new GloboNetworkResource.VipInfoHelper(120L, null, null, null);

        PoolV3 expectedPool = mockPoolSave(null, 123L, true, 80, 8080, "10.0.0.1",
                build.getHealthCheckType(), build.getExpectedHealthCheck(), build.getHealthCheck(), 0,
                cmd.getServiceDownAction());


        List<VipPoolMap> vipPoolMaps = _resource.createPools(gnAPI, poolMembers, vipInfo.getEnvironment(), cmd);

        VipPoolMap vipPoolMap = vipPoolMaps.get(0);
        assertNotNull(vipPoolMap);
        assertEquals(new Integer(80), vipPoolMap.getPort());
        verify(gnAPI.getPoolAPI(), times(1)).save(expectedPool);
    }

    @Test
    public void testSavePoolAddRealToExistingPool() throws GloboNetworkException {
        //input 1 - vip
        Pool pool = new Pool();
        pool.setDefaultPort(8080);
        pool.setId(12L);
        pool.setMaxconn(0);

        List<Long> poolIds = Collections.singletonList(pool.getId());
        Ipv4 ip = new Ipv4();
        ip.setNetworkId(1L);

        //input 2 - vip info
        GloboNetworkResource.VipInfoHelper vipInfo = new GloboNetworkResource.VipInfoHelper(121L, null, null, null);

        //input 3 - poolMembers
        List<PoolV3.PoolMember> poolMembers = new ArrayList<>();
        PoolV3.PoolMember real1 = mockPoolMember(null, 8080, 1L, "10.0.0.1", 1L, "vm-01");
        poolMembers.add(real1);
        PoolV3.PoolMember real2 = mockPoolMember(null, 8080, 2L, "10.0.0.2", 2L, "vm-02");
        poolMembers.add(real2);

        //input 4 - cmd
        ApplyVipInGloboNetworkCommand cmd = new ApplyVipInGloboNetworkCommand();
        cmd.setMethodBal("roundrobin");
        cmd.setHost("vip.domain.com");
        cmd.setPorts(Collections.singletonList("80:8080"));
        HealthCheckHelper build = HealthCheckHelper.build("vip.domain.com", "TCP", "", null);
        cmd.setHealthcheckType(build.getHealthCheckType());
        cmd.setExpectedHealthcheck(build.getExpectedHealthCheck());
        cmd.setHealthcheck(build.getHealthCheck());

        //mock 1 - Pool find by id - v3
        PoolV3 poolv3GetById = mockPool(12L, "ACS_POOL_", 8080, "round-robin", build.getHealthCheckType(), build.getHealthCheck(), build.getExpectedHealthCheck(), "*", 5);
        PoolV3.PoolMember poolM = mockPoolMember(200L, 8080, 1L, "10.0.0.1", 1L, "vm-01");
        poolv3GetById.getPoolMembers().add(poolM);

        when(gnAPI.getPoolAPI().getById(12L)).thenReturn(poolv3GetById);
        when(gnAPI.getPoolAPI().getByIdsV3(Collections.singletonList(12L))).thenReturn(Collections.singletonList(poolv3GetById));

        //mock 2 - Pool save pool
        PoolV3 poolToSave = mockPool(12L, "ACS_POOL_", 8080, "round-robin", build.getHealthCheckType(), build.getHealthCheck(), build.getExpectedHealthCheck(), "*", 5);
        PoolV3.PoolMember poolMSaved = mockPoolMember(200L, 8080, 1L, "10.0.0.1", 1L, "vm-01");
        poolToSave.getPoolMembers().add(poolMSaved);
        PoolV3.PoolMember poolM2Saved = mockPoolMember(null, 8080, 2L, "10.0.0.2", 2L, "vm-02");
        poolToSave.getPoolMembers().add(poolM2Saved);

        when(gnAPI.getPoolAPI().save(poolToSave)).thenReturn(new PoolV3(12L));


        _resource.updatePools(gnAPI, poolIds, poolMembers, cmd.getPortPairs());
        verify(gnAPI.getPoolAPI(), times(1)).save(poolToSave);
    }

    @Test
    public void testForceSupportPoolOldVersion() {
        assertFalse(_resource.forceSupportOldPoolVersion("HTTP", 8080));
        assertFalse(_resource.forceSupportOldPoolVersion("HTTP", 8081));
        assertFalse(_resource.forceSupportOldPoolVersion("HTTP", 80));
        assertFalse(_resource.forceSupportOldPoolVersion("HTTP", 1234));
        assertTrue(_resource.forceSupportOldPoolVersion("HTTP", 8443));
        assertTrue(_resource.forceSupportOldPoolVersion("HTTP", 443));
        assertTrue(_resource.forceSupportOldPoolVersion("UDP", 8443));
        assertTrue(_resource.forceSupportOldPoolVersion("UDP", 443));
        assertTrue(_resource.forceSupportOldPoolVersion("TCP", 443));
        assertTrue(_resource.forceSupportOldPoolVersion("TCP", 8443));
        assertFalse(_resource.forceSupportOldPoolVersion("HTTPS", 443));
        assertFalse(_resource.forceSupportOldPoolVersion("HTTPS", 8443));
    }

    private PoolV3.PoolMember mockPoolMember(Long id,Integer port, Long ipId, String ip, Long equipId, String equipName) {
        PoolV3.PoolMember poolMSaved = new PoolV3.PoolMember();
        if ( id != null ){
            poolMSaved.setId(id);
        }
        poolMSaved.setPortReal(port);
        poolMSaved.setWeight(0);
        poolMSaved.setPriority(0);
        poolMSaved.setEquipmentId(equipId);
        poolMSaved.setEquipmentName(equipName);

        PoolV3.Ip ipppoolMSaved = new PoolV3.Ip();
        ipppoolMSaved.setIpFormated(ip);
        ipppoolMSaved.setId(ipId);
        poolMSaved.setIp(ipppoolMSaved);
        return poolMSaved;
    }

    @Test
    public void testExecuteListAllExpectedHealthcheck() throws GloboNetworkException {
        ListExpectedHealthchecksCommand command = new ListExpectedHealthchecksCommand();

        List<ExpectHealthcheck> result = new ArrayList<>();
        result.add(new ExpectHealthcheck(1L, "OK"));
        result.add(new ExpectHealthcheck(2L, "WORKING"));

        when(gnAPI.getExpectHealthcheckAPI().listHealthcheck()).thenReturn(result);

        Answer answer = _resource.executeRequest(command);

        assertNotNull(answer);

        GloboNetworkExpectHealthcheckResponse response = (GloboNetworkExpectHealthcheckResponse)answer;

        List<GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck> expectedHealthchecks = response.getExpectedHealthchecks();

        assertEquals(2, expectedHealthchecks.size());

        GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck expectedHealthcheck = expectedHealthchecks.get(0);
        assertEquals((Long) 1L, expectedHealthcheck.getId());
        assertEquals("OK", expectedHealthcheck.getExpected());

        expectedHealthcheck = expectedHealthchecks.get(1);
        assertEquals((Long) 2L, expectedHealthcheck.getId());
        assertEquals("WORKING", expectedHealthcheck.getExpected());
    }

    private long getNewIpID() {
        return ++s_ipSequence;
    }

    private VipJson buildFakeVip(Long vipEnvironment, Long realEnvironment, Long vipIpId, List<String> servicePorts) throws GloboNetworkException {
        vipIp  = new Ipv4();
        vipIp.setId(vipIpId);
        vipIp.setOct1(192);
        vipIp.setOct2(168);
        vipIp.setOct3(1);
        vipIp.setOct4(15);
        vipIp.setNetworkId(1L);
        when(gnAPI.getIpAPI().checkVipIp(vipIp.getIpString(), vipEnvironment, false)).thenReturn(vipIp);

        VipEnvironment environmentVip = new VipEnvironment();
        environmentVip.setId(vipEnvironment);
        environmentVip.setFinality("BACKEND");
        environmentVip.setClient("CLIENT");
        environmentVip.setEnvironmentName("TESTAPI");

        VipJson vip = new VipJson();
        vip.setId(987L);
        vip.setIps(Collections.singletonList(vipIp.getIpString()));
        vip.setServicePorts(servicePorts);
        vip.setHost("vip.domain.com");
        vip.setServiceName("vipservice");
        vip.setCreated(false);
        vip.setPools(new ArrayList<Pool>());

        List<RealIP> realIpList = new ArrayList<>();
        Ip ip = new Ipv4();
        ip.setId(getNewIpID());
        when(gnAPI.getIpAPI().findByIpAndEnvironment(ip.toString(), realEnvironment, false)).thenReturn(ip);

        RealIP realIp = new RealIP();
        realIp.setIpId(ip.getId());
        realIp.setName("vm-" + ip.getId());
        realIp.setVipPort(Integer.valueOf(servicePorts.get(0).split(":")[0]));
        realIp.setRealPort(Integer.valueOf(servicePorts.get(0).split(":")[1]));
        realIpList.add(realIp);

        for (String servicePort : servicePorts) {
            Pool pool = new Pool();
            pool.setId(Long.valueOf(servicePort.split(":")[0]));
            pool.setLbMethod("least-conn");
            pool.setPoolCreated(true);
            pool.setDefaultPort(new Integer(servicePort.split(":")[1]));
            pool.setIdentifier("POOL_" + servicePort.split(":")[1]);
            pool.setDefaultLimit(0);
            Pool.Environment env = new Pool.Environment();
            env.setId(120L);
            pool.setEnvironment(env);

            vip.getPools().add(pool);
        }
        vip.setRealsIp(realIpList);

        return vip;
    }

    protected VipXml createVipXML(List<String> ports, RealIP...realIPs) {
        VipXml vip = new VipXml();
        vip.setId(1L);
        vip.setHost("vip.domain.com");
        vip.setIps(Collections.singletonList("192.168.0.4"));
        vip.setEnvironment("1");
        vip.setCache("cache");
        vip.setMethod("round-robin");
        vip.setPersistence("cookie");
        vip.setHealthcheckType("healthcheckType");
        vip.setHealthcheck("healthcheck");
        vip.setMaxConn(0);
        vip.setServicePorts(ports);
        vip.setRealsIp(Arrays.asList(realIPs));
        return vip;
    }
}
