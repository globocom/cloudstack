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
import com.globo.globonetwork.client.api.OptionVipV3API;
import com.globo.globonetwork.client.api.PoolAPI;
import com.globo.globonetwork.client.model.IPv4Network;
import com.globo.globonetwork.client.model.Network;
import com.globo.globonetwork.client.model.OptionVipV3;
import com.globo.globonetwork.client.model.Pool;
import com.globo.globonetwork.client.model.PoolOption;
import com.globo.globonetwork.client.model.VipPoolMap;
import com.globo.globonetwork.client.model.VipV3;
import com.globo.globonetwork.client.model.Vlan;

import com.globo.globonetwork.client.model.healthcheck.ExpectHealthcheck;
import com.globo.globonetwork.client.model.pool.PoolV3;
import com.globo.globonetwork.cloudstack.commands.ApplyVipInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.CreatePoolCommand;
import com.globo.globonetwork.cloudstack.commands.DeletePoolCommand;
import com.globo.globonetwork.cloudstack.commands.GetVipInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.ListExpectedHealthchecksCommand;
import com.globo.globonetwork.cloudstack.commands.ListPoolLBCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveVipFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.manager.HealthCheckHelper;
import com.globo.globonetwork.cloudstack.manager.Protocol;
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
import com.globo.globonetwork.client.api.VipEnvironmentAPI;
import com.globo.globonetwork.client.api.VlanAPI;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.model.Equipment;
import com.globo.globonetwork.client.model.Ip;
import com.globo.globonetwork.client.model.Ipv4;
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
        when(gnAPI.getVipEnvironmentAPI()).thenReturn(mock(VipEnvironmentAPI.class));
        when(gnAPI.getVlanAPI()).thenReturn(mock(VlanAPI.class));
        when(gnAPI.getNetworkAPI()).thenReturn(mock(NetworkAPI.class));
        when(gnAPI.getNetworkJsonAPI()).thenReturn(mock(NetworkJsonAPI.class));
        when(gnAPI.getPoolAPI()).thenReturn(mock(PoolAPI.class));
        when(gnAPI.getExpectHealthcheckAPI()).thenReturn(mock(ExpectHealthcheckAPI.class));
        when(gnAPI.getOptionVipV3API()).thenReturn(mock(OptionVipV3API.class));
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

    public static PoolV3 mockPoolSave(Long poolId, Long idReturned, Boolean hasPoolMember, Integer vipPort, Integer port, String ip, String healthCheckType, String healthCheck, String expectedHealthCheck, int maxConn,  String serviceDAction, GloboNetworkAPI gnAPI) throws GloboNetworkException {
        PoolV3 expectedPool = new PoolV3();
        expectedPool.setId(poolId);
        expectedPool.setIdentifier(GloboNetworkResource.buildPoolName("region", "vip.domain.com",vipPort,  port));
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
        ApplyVipInGloboNetworkCommand cmd = createTestApplyVipCommand("vip.test.com", "10.1.1.1");

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

    @Test
    public void testCreateNewVIPWithMultiplePortsAndNoReal() throws Exception {
        List<String> ports = Arrays.asList("80:8080", "443:8443");
        ApplyVipInGloboNetworkCommand cmd = createTestApplyVipCommand("vip.test.com", "10.1.1.1");
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
        ApplyVipInGloboNetworkCommand cmd = createTestApplyVipCommand("vip.test.com", "10.1.1.1");
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
        ApplyVipInGloboNetworkCommand cmd = createTestApplyVipCommand("vip.test.com", "10.1.1.1");

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
        verify(gnAPI.getPoolAPI(), times(1)).deleteV3(any(List.class));
    }

    @Test
    public void testUpdateVipGivenFailedOperation() throws Exception {
        ApplyVipInGloboNetworkCommand cmd = createTestApplyVipCommand("vip.test.com", "10.1.1.1");
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
        verify(gnAPI.getPoolAPI(), times(0)).deleteV3(any(List.class));
    }

    private ApplyVipInGloboNetworkCommand createTestApplyVipCommand(String name, String ip) {
        ApplyVipInGloboNetworkCommand cmd = new ApplyVipInGloboNetworkCommand();
        cmd.setVipId(null);
        cmd.setHost(name);
        cmd.setIpv4(ip);
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


    private void mockGetVipMetadata(ApplyVipInGloboNetworkCommand cmd) throws GloboNetworkException {
        when(gnAPI.getVipEnvironmentAPI().search(anyLong(), isNull(String.class), isNull(String.class), isNull(String.class))).thenReturn(new VipEnvironment());
        when(gnAPI.getIpAPI().checkVipIp(cmd.getIpv4(), 120L, false)).thenReturn(new Ipv4());
        when(gnAPI.getNetworkAPI().getNetwork(anyLong(), eq(false))).thenReturn(new IPv4Network());
        when(gnAPI.getVlanAPI().getById(anyLong())).thenReturn(new Vlan());
    }

    @Test
    public void testCreateVipResponseGivenVipWithOnePortMapping() throws GloboNetworkException {
        VipV3 vip = createVipV3(1L, "vip.teste.com", Collections.singletonList("80:8080"));

        when(gnAPI.getOptionVipV3API().findOptionsById(vip.getEnvironmentVipId(), vip.getOptions().getCacheGroupId())).thenReturn(new OptionVipV3(1L, "cache", "(nenhum)"));
        when(gnAPI.getOptionVipV3API().findOptionsById(vip.getEnvironmentVipId(), vip.getOptions().getPersistenceId())).thenReturn(new OptionVipV3(1L, "Persistence", "cookie"));
        when(gnAPI.getIpAPI().getIp(vip.getIpv4Id(), false)).thenReturn(createIpv4(10, 1, 1, 1));
        when(gnAPI.getPoolAPI().getById(0L)).thenReturn(mockPool(0L, "ACS_POOL_vip.teste.com_8080", 8080, "least", "HTTP", "/health.html", "OK", "*:*", 10));

        GloboNetworkVipResponse answer = (GloboNetworkVipResponse) _resource.createVipResponse(vip, new GetVipInfoFromGloboNetworkCommand(1L, false), gnAPI);

        assertTrue(answer.getResult());
        assertEquals(0, answer.getReals().size());
        assertEquals(vip.getId(), answer.getId());
        assertEquals(vip.getName(), answer.getName());
        assertEquals("10.1.1.1", answer.getIp());
        assertEquals(vip.getEnvironmentVipId(), answer.getLbEnvironmentId());
        assertEquals("(nenhum)", answer.getCache());
        assertEquals("cookie", answer.getPersistence());
        assertEquals("least", answer.getMethod());
        assertEquals("HTTP", answer.getHealthcheckType());
        assertEquals("/health.html", answer.getHealthcheck());
        assertEquals(new Integer(10), answer.getMaxConn());
    }

    @Test
    public void testCreateVipResponseGivenVipWithMoreThanOnePortMapping() throws GloboNetworkException {
        VipV3 vip = createVipV3(1L, "vip.teste.com", Arrays.asList("80:8080", "443:8443"));

        when(gnAPI.getOptionVipV3API().findOptionsById(vip.getEnvironmentVipId(), vip.getOptions().getCacheGroupId())).thenReturn(new OptionVipV3(1L, "cache", "(nenhum)"));
        when(gnAPI.getOptionVipV3API().findOptionsById(vip.getEnvironmentVipId(), vip.getOptions().getPersistenceId())).thenReturn(new OptionVipV3(1L, "Persistence", "cookie"));
        when(gnAPI.getIpAPI().getIp(vip.getIpv4Id(), false)).thenReturn(createIpv4(10, 1, 1, 1));
        when(gnAPI.getPoolAPI().getById(0L)).thenReturn(mockPool(0L, "ACS_POOL_vip.teste.com_8080", 8080, "least", "HTTP", "/health.html", "OK", "*:*", 10));
        when(gnAPI.getPoolAPI().getById(1L)).thenReturn(mockPool(1L, "ACS_POOL_vip.teste.com_8443", 8443, "least", "HTTP", "/health.html", "OK", "*:*", 10));

        GloboNetworkVipResponse answer = (GloboNetworkVipResponse) _resource.createVipResponse(vip, new GetVipInfoFromGloboNetworkCommand(1L, false), gnAPI);

        assertTrue(answer.getResult());
        assertEquals(2, answer.getPorts().size());
        assertEquals("80:8080", answer.getPorts().get(0));
        assertEquals("443:8443", answer.getPorts().get(1));
    }

    @Test
    public void testCreateVipResponseGivenVipWithMoreThanOneReal() throws GloboNetworkException {
        VipV3 vip = createVipV3(1L, "vip.teste.com", Arrays.asList("80:8080", "443:8443"));
        PoolV3.PoolMember member1 = new PoolV3.PoolMember();
        member1.setId(1L);
        member1.setEquipmentName("member1");
        PoolV3.Ip ip1 = new PoolV3.Ip();
        ip1.setId(1L);
        member1.setIp(ip1);

        PoolV3.PoolMember member2 = new PoolV3.PoolMember();
        member2.setId(2L);
        member2.setEquipmentName("member1");
        PoolV3.Ip ip2 = new PoolV3.Ip();
        ip2.setId(2L);
        member2.setIp(ip2);

        PoolV3 pool1 = mockPool(0L, "ACS_POOL_vip.teste.com_8080", 8080, "least", "HTTP", "/health.html", "OK", "*:*", 10);
        pool1.setPoolMembers(Arrays.asList(member1, member2));
        PoolV3 pool2 = mockPool(1L, "ACS_POOL_vip.teste.com_8443", 8443, "least", "HTTP", "/health.html", "OK", "*:*", 10);
        pool2.setPoolMembers(Arrays.asList(member1, member2));

        when(gnAPI.getOptionVipV3API().findOptionsById(vip.getEnvironmentVipId(), vip.getOptions().getCacheGroupId())).thenReturn(new OptionVipV3(1L, "cache", "(nenhum)"));
        when(gnAPI.getOptionVipV3API().findOptionsById(vip.getEnvironmentVipId(), vip.getOptions().getPersistenceId())).thenReturn(new OptionVipV3(1L, "Persistence", "cookie"));
        when(gnAPI.getIpAPI().getIp(vip.getIpv4Id(), false)).thenReturn(createIpv4(10, 1, 1, 1));
        when(gnAPI.getPoolAPI().getById(0L)).thenReturn(pool1);
        when(gnAPI.getPoolAPI().getById(1L)).thenReturn(pool2);

        GloboNetworkVipResponse answer = (GloboNetworkVipResponse) _resource.createVipResponse(vip, new GetVipInfoFromGloboNetworkCommand(1L, false), gnAPI);

        assertTrue(answer.getResult());
        assertEquals(2, answer.getPorts().size());
        assertEquals("80:8080", answer.getPorts().get(0));
        assertEquals("443:8443", answer.getPorts().get(1));
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
        when(gnAPI.getPoolAPI().listPoolOptionsV3(1L, "ServiceDownAction")).thenReturn(options);
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
        when(gnAPI.getPoolAPI().listPoolOptionsV3(1L, "ServiceDownAction")).thenReturn(new ArrayList<PoolOption>());
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
        when(gnAPI.getPoolAPI().listPoolOptionsV3(1L, "ServiceDownAction")).thenThrow(new GloboNetworkException("Netapi failed"));
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
        when(gnAPI.getPoolAPI().listPoolOptionsV3(1L, "ServiceDownAction")).thenThrow(new IOException());
        Answer answer = _resource.executeRequest(new ListPoolOptionsCommand(45L, "ServiceDownAction"));
        assertFalse(answer.getResult());
    }

    @Test
    public void testListPoolExecute() throws GloboNetworkException {
        List<PoolV3> poolsNetworkApi = new ArrayList<>();
        PoolV3 pool1 = new PoolV3();
        pool1.setId(33L);
        pool1.setIdentifier("my_pool");
        pool1.setLbMethod("leastcon");
        pool1.setDefaultPort(80);
        poolsNetworkApi.add(pool1);


        PoolV3 pool2 = new PoolV3();
        pool2.setId(22L);
        pool2.setIdentifier("my_pool_2");
        pool2.setLbMethod("round");
        pool2.setDefaultPort(8091);
        poolsNetworkApi.add(pool1);

        when(gnAPI.getPoolAPI().getById(33L)).thenReturn(pool1);
        when(gnAPI.getPoolAPI().getById(22L)).thenReturn(pool2);


        List<OptionVipV3> options = new ArrayList<>();
        OptionVipV3 opitionTCP = new OptionVipV3(123L, "l4_protocol", "TCP");
        options.add(opitionTCP);
        OptionVipV3 opitionHTTP = new OptionVipV3(321L, "l7_protocol", "HTTP");
        options.add(opitionHTTP);
        when(gnAPI.getOptionVipV3API().listOptions(333L)).thenReturn(options);

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(123L, gnAPI);
        when(facadeMock.getVip()).thenReturn(createMockVipV3(pool1.getId(), pool2.getId()));

        createMockVipV3(pool1.getId(), pool2.getId());

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

    private VipV3 createMockVipV3(Long poolId1, Long poolId2) {
        VipV3 vip = new VipV3();
        VipV3.Port port1 = new VipV3.Port();
        port1.setPort(80);
        VipV3.Pool p1 = new VipV3.Pool();
        p1.setPoolId(poolId1);
        port1.setPools(Collections.singletonList(p1));
        VipV3.PortOptions options = new VipV3.PortOptions();
        options.setL4ProtocolId(123L);
        options.setL7ProtocolId(321L);
        port1.setOptions(options);

        VipV3.Port port2 = new VipV3.Port();
        port1.setPort(80);
        VipV3.Pool p2 = new VipV3.Pool();
        p2.setPoolId(poolId2);
        port2.setPools(Collections.singletonList(p2));
        VipV3.PortOptions options2 = new VipV3.PortOptions();
        options2.setL4ProtocolId(123L);
        options2.setL7ProtocolId(321L);
        port2.setOptions(options);

        vip.setPorts(Arrays.asList(port1, port2));

        vip.setEnvironmentVipId(333L);
        return vip;
    }

    @Test
    public void testCreatePool() throws GloboNetworkException {
        CreatePoolCommand command = createMockCreatePoolCommand();
        PoolV3 pool = new PoolV3();
        pool.setDefaultPort(80);
        mockGetVipInfos();
        command.setL4protocol(Protocol.L4.TCP);
        command.setL7protocol(Protocol.L7.OTHERS);


        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.hasVip()).thenReturn(true);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);
        when(gnAPI.getPoolAPI().save(any(PoolV3.class))).thenReturn(pool);

        GloboNetworkPoolResponse answer = (GloboNetworkPoolResponse) _resource.executeRequest(command);

        assertTrue(answer.getResult());
        assertNotNull(answer.getPool());
        verify(gnAPI.getPoolAPI()).save(any(PoolV3.class));
        verify(facadeMock).addPool(any(VipEnvironment.class), eq(80), eq("TCP"), eq((String)"Outros"), any(PoolV3.class));
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
        verify(gnAPI.getPoolAPI(), times(0)).deleteV3(any(Long.class));
        verify(facadeMock, times(0)).addPool(any(VipEnvironment.class), eq(80), eq("TCP"), eq((String)null), any(PoolV3.class));
    }

    @Test
    public void testCreatePoolGivenVipDeployError() throws GloboNetworkException {
        CreatePoolCommand command = createMockCreatePoolCommand();
        PoolV3 pool = new PoolV3();
        pool.setId(1L);
        pool.setDefaultPort(80);
        mockGetVipInfos();
        command.setL4protocol(Protocol.L4.TCP);
        command.setL7protocol(Protocol.L7.OTHERS);

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        when(facadeMock.hasVip()).thenReturn(true);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);
        doThrow(new GloboNetworkException("")).when(facadeMock).addPool(any(VipEnvironment.class), eq(80), eq("TCP"), eq((String)"Outros"),  any(PoolV3.class));
        when(gnAPI.getPoolAPI().save(any(PoolV3.class))).thenReturn(pool);

        Answer answer = _resource.executeRequest(command);

        assertFalse(answer.getResult());
        verify(gnAPI.getPoolAPI()).save(any(PoolV3.class));
        verify(facadeMock).addPool(any(VipEnvironment.class), eq(80), eq("TCP"), eq((String)"Outros"), any(PoolV3.class));
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
        verify(facadeMock, times(0)).addPool(any(VipEnvironment.class), eq(80), eq("TCP"), eq((String)null), any(PoolV3.class));
    }

    @Test
    public void testDeletePool() throws GloboNetworkException {
        PoolV3 pool = new PoolV3();
        pool.setId(1L);
        pool.setPoolCreated(true);

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);
        when(gnAPI.getPoolAPI().getById(1L)).thenReturn(pool);

        Answer answer = _resource.executeRequest(new DeletePoolCommand(1L, 1L, 80));

        assertTrue(answer.getResult());
        verify(facadeMock).removePool(1L);
        verify(gnAPI.getPoolAPI()).getById(1L);
        verify(gnAPI.getPoolAPI()).undeployV3(1L);
        verify(gnAPI.getPoolAPI()).deleteV3(1L);
    }

    @Test
    public void testDeletePoolGivenNotDeployedPool() throws GloboNetworkException {
        PoolV3 pool = new PoolV3();
        pool.setId(1L);
        pool.setPoolCreated(false);

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);
        when(gnAPI.getPoolAPI().getById(1L)).thenReturn(pool);

        Answer answer = _resource.executeRequest(new DeletePoolCommand(1L, 1L, 80));

        assertTrue(answer.getResult());
        verify(facadeMock).removePool(1L);
        verify(gnAPI.getPoolAPI()).getById(1L);
        verify(gnAPI.getPoolAPI(), times(0)).undeployV3(1L);
        verify(gnAPI.getPoolAPI()).deleteV3(1L);
    }

    @Test
    public void testDeletePoolGivenError() throws GloboNetworkException {
        PoolAPI poolAPI = gnAPI.getPoolAPI();
        PoolV3 pool = new PoolV3();
        pool.setId(1L);
        pool.setPoolCreated(true);
        PoolV3.Healthcheck healthcheck = new PoolV3.Healthcheck();
        healthcheck.setHealthcheck("TCP", null, null);
        pool.setHealthcheck(healthcheck);

        VipV3 vip = new VipV3();
        vip.setEnvironmentVipId(99L);

        VipAPIFacade facadeMock = mock(VipAPIFacade.class);
        doReturn(facadeMock).when(_resource).createVipAPIFacade(1L, gnAPI);
        when(facadeMock.getVip()).thenReturn(vip);
        when(poolAPI.getById(1L)).thenReturn(pool);
        doThrow(new RuntimeException("")).when(poolAPI).deleteV3(1L);
        when(gnAPI.getVipEnvironmentAPI().search(99L, null, null, null)).thenReturn(new VipEnvironment());

        Answer answer = _resource.executeRequest(new DeletePoolCommand(1L, 1L, 80));

        assertFalse(answer.getResult());
        verify(facadeMock).removePool(1L);
        verify(poolAPI, times(2)).getById(1L);
        verify(poolAPI).undeployV3(1L);
        verify(poolAPI).deleteV3(1L);
        // verify rollback
        verify(gnAPI.getVipEnvironmentAPI()).search(99L, null, null, null);
        verify(facadeMock).addPool(any(VipEnvironment.class), eq(80), eq(pool.getHealthcheck().getHealthcheckType()), eq((String)"Outros"), eq(pool));
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
        command.setL4protocol(Protocol.L4.TCP);
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

    public static PoolV3 mockPool(Long poolId, String identifier, int port, String lbmethod, String healthheckType, String healthcheck, String expectedHealthcheck, String destination, Integer maxconn) {
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
                cmd.getServiceDownAction(), gnAPI);


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
        real1.setLimit(10);
        poolMembers.add(real1);
        PoolV3.PoolMember real2 = mockPoolMember(null, 8080, 2L, "10.0.0.2", 2L, "vm-02");
        poolMembers.add(real2);
        real1.setLimit(10);

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
        poolv3GetById.setMaxconn(10);
        PoolV3.PoolMember poolM = mockPoolMember(200L, 8080, 1L, "10.0.0.1", 1L, "vm-01");
        poolM.setLimit(10);
        poolv3GetById.getPoolMembers().add(poolM);

        when(gnAPI.getPoolAPI().getById(12L)).thenReturn(poolv3GetById);
        when(gnAPI.getPoolAPI().getByIdsV3(Collections.singletonList(12L))).thenReturn(Collections.singletonList(poolv3GetById));

        //mock 2 - Pool save pool
        PoolV3 poolToSave = mockPool(12L, "ACS_POOL_", 8080, "round-robin", build.getHealthCheckType(), build.getHealthCheck(), build.getExpectedHealthCheck(), "*", 5);
        poolToSave.setMaxconn(10);

        PoolV3.PoolMember poolMSaved = mockPoolMember(200L, 8080, 1L, "10.0.0.1", 1L, "vm-01");
        poolMSaved.setLimit(10);
        poolToSave.getPoolMembers().add(poolMSaved);
        PoolV3.PoolMember poolM2Saved = mockPoolMember(null, 8080, 2L, "10.0.0.2", 2L, "vm-02");
        poolM2Saved.setLimit(10);
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

    protected VipV3 createVipV3(Long vipId, String name, List<String> ports) {
        VipV3 vip = new VipV3();
        vip.setId(vipId);
        vip.setName(name);
        vip.setIpv4Id(1L);
        VipV3.VipOptions option = new VipV3.VipOptions();
        option.setCacheGroupId(1L);
        option.setPersistenceId(2L);
        vip.setOptions(option);

        vip.setPorts(new ArrayList<VipV3.Port>());
        for(int i=0; i < ports.size(); i++){
            String vipPort = ports.get(i).split(":")[0];

            VipV3.Port port  = new VipV3.Port();
            port.setPort(new Integer(vipPort));
            port.setPools(new ArrayList<VipV3.Pool>());

            VipV3.Pool p = new VipV3.Pool();
            p.setPoolId(new Long(i));
            port.getPools().add(p);

            vip.getPorts().add(port);
        }
        return vip;
    }

    private Ipv4 createIpv4(int oct1, int oct2, int oct3, int oct4) {
        Ipv4 ip = new Ipv4();
        ip.setOct1(oct1);
        ip.setOct2(oct2);
        ip.setOct3(oct3);
        ip.setOct4(oct4);
        return ip;
    }
}
