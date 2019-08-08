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
package com.globo.globonetwork.cloudstack.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Status;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.LoadBalancerDao;
import com.cloud.network.dao.LoadBalancerOptionsDao;
import com.cloud.network.dao.LoadBalancerOptionsVO;
import com.cloud.network.dao.LoadBalancerPortMapDao;
import com.cloud.network.dao.LoadBalancerPortMapVO;
import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.utils.net.Ip;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.globo.globodns.cloudstack.element.GloboDnsTO;
import com.globo.globonetwork.cloudstack.GloboNetworkIpDetailVO;
import com.globo.globonetwork.cloudstack.api.CreateGloboNetworkPoolCmd;
import com.globo.globonetwork.cloudstack.api.DeleteGloboNetworkPoolCmd;
import com.globo.globonetwork.cloudstack.commands.ApplyVipInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.CreatePoolCommand;
import com.globo.globonetwork.cloudstack.commands.DeletePoolCommand;
import com.globo.globonetwork.cloudstack.commands.GetPoolLBByIdCommand;
import com.globo.globonetwork.cloudstack.commands.ListPoolLBCommand;
import com.globo.globonetwork.cloudstack.commands.UpdatePoolCommand;
import com.globo.globonetwork.cloudstack.response.GloboNetworkExpectHealthcheckResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.cloud.network.Network;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.client.model.Vlan;
import com.globo.globonetwork.cloudstack.GloboNetworkLoadBalancerEnvironment;
import com.globo.globonetwork.cloudstack.GloboNetworkNetworkVO;
import com.globo.globonetwork.cloudstack.commands.ActivateNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetVlanInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GloboNetworkErrorAnswer;
import com.globo.globonetwork.cloudstack.commands.ListPoolOptionsCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveNetworkInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.exception.CloudstackGloboNetworkException;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolOptionResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;
import org.apache.cloudstack.acl.ControlledEntity.ACLType;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.config.impl.ConfigDepotImpl;
import org.apache.cloudstack.framework.config.impl.ConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationDao;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.exception.CloudException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network.Provider;
import com.cloud.network.NetworkModel;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.network.lb.LoadBalancingRulesService;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ServerResource;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.vm.dao.VMInstanceDao;
import com.globo.globodns.cloudstack.element.GloboDnsElementService;
import com.globo.globonetwork.cloudstack.GloboNetworkEnvironmentVO;
import com.globo.globonetwork.cloudstack.commands.CreateNewVlanInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.DeallocateVlanFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkEnvironmentDao;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkIpDetailDao;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkLoadBalancerEnvironmentDAO;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkNetworkDao;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVlanResponse;

public class GloboNetworkManagerTest {

    private static final long zoneId = 5L;
    private static final long networkOfferingId = 10L;
    private static final long globoNetworkEnvironmentId = 120L;
    private static final long physicalNetworkId = 200L;
    private static final long globoNetworkHostId = 7L;
    private static final long domainId = 10L;
    private AccountVO acct = null;
    private UserVO user = null;

    private GloboNetworkManager _globoNetworkService;

    @Mock
    DataCenterDao _dcDao;

    @Mock
    PhysicalNetworkDao _physicalNetworkDao;

    @Mock
    GloboNetworkEnvironmentDao _globoNetworkEnvironmentDao;

    @Mock
    GloboNetworkLoadBalancerEnvironmentDAO _globoNetworkLBEnvironmentDao;

    @Mock
    GloboNetworkNetworkDao _globoNetworkNetworkDao;

    @Mock
    VMInstanceDao _vmDao;

    @Mock
    NetworkModel _networkManager;

    @Mock
    HostDao _hostDao;

    @Mock
    ConfigurationDao _configDao;

    @Mock
    AgentManager _agentMgr;

    @Mock
    ResourceManager _resourceMgr;

    @Mock
    AccountManager _acctMgr;

    @Mock
    GloboNetworkIpDetailDao _globoNetworkIpDetailDao;

    @Mock
    IPAddressDao _ipAddrDao;

    @Mock
    LoadBalancerOptionsDao _lbOptionsDao;

    @Mock
    GloboResourceConfigurationDao _globoResourceConfigurationDao;

    @Mock
    GloboDnsElementService _globoDnsService;

    @Before
    public void testSetUp() {
        MockitoAnnotations.initMocks(this);

        _globoNetworkService = spy(new GloboNetworkManager());
        acct = new AccountVO(200L);
        acct.setType(Account.ACCOUNT_TYPE_NORMAL);
        acct.setAccountName("user");
        acct.setDomainId(domainId);

        user = new UserVO();
        user.setUsername("user");
        user.setAccountId(acct.getAccountId());

        _globoNetworkService._dcDao = _dcDao;
        _globoNetworkService._physicalNetworkDao = _physicalNetworkDao;
        _globoNetworkService._globoNetworkEnvironmentDao = _globoNetworkEnvironmentDao;
        _globoNetworkService._globoNetworkLBEnvironmentDao = _globoNetworkLBEnvironmentDao;
        _globoNetworkService._globoNetworkNetworkDao = _globoNetworkNetworkDao;
        _globoNetworkService._vmDao = _vmDao;
        _globoNetworkService._networkManager = _networkManager;
        _globoNetworkService._hostDao = _hostDao;
        _globoNetworkService._configDao = _configDao;
        _globoNetworkService._agentMgr = _agentMgr;
        _globoNetworkService._resourceMgr = _resourceMgr;
        _globoNetworkService._accountMgr = _acctMgr;
        _globoNetworkService._ipAddrDao = _ipAddrDao;
        _globoNetworkService._globoNetworkIpDetailDao = _globoNetworkIpDetailDao;
        _globoNetworkService._lbOptionsDao = _lbOptionsDao;
        _globoNetworkService._globoResourceConfigurationDao = _globoResourceConfigurationDao;
        _globoNetworkService._globoDnsService = _globoDnsService;

        CallContext.register(user, acct);
        when(_acctMgr.getSystemAccount()).thenReturn(this.acct);
        when(_acctMgr.getSystemUser()).thenReturn(this.user);
    }

    @After
    public void testTearDown() {
        CallContext.unregister();
        acct = null;
    }

    @Test
    public void revertGloboNetworkCreationWhenFailureNetworkCreation() throws CloudException {

        DataCenterVO dc = new DataCenterVO(0L, null, null, null, null, null, null, null, null, null, null, null, null);
        when(_dcDao.findById(anyLong())).thenReturn(dc);

        List<PhysicalNetworkVO> pNtwList = new ArrayList<>();
        pNtwList.add(new PhysicalNetworkVO(physicalNetworkId, zoneId, null, null, null, null, null));
        when(_physicalNetworkDao.listByZone(zoneId)).thenReturn(pNtwList);
        String networkName = "MockTestNetwork";
        when(_globoNetworkEnvironmentDao.findByPhysicalNetworkIdAndEnvironmentId(physicalNetworkId, globoNetworkEnvironmentId)).thenReturn(
                new GloboNetworkEnvironmentVO(physicalNetworkId, networkName, globoNetworkEnvironmentId));

        HostVO napiHost = new HostVO(globoNetworkHostId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, zoneId, null, 0L, 0L,
                null, null, null, 0L, null);
        when(_hostDao.findByTypeNameAndZoneId(zoneId, Provider.GloboNetwork.getName(), Host.Type.L2Networking)).thenReturn(napiHost);

        Answer answer = new GloboNetworkVlanResponse(new CreateNewVlanInGloboNetworkCommand(), null, null, null, null, null, null, null, false, null, false);
        when(_agentMgr.easySend(eq(globoNetworkHostId), any(CreateNewVlanInGloboNetworkCommand.class))).thenReturn(answer);

        when(_physicalNetworkDao.findById(physicalNetworkId)).thenReturn(null);

        try {
            _globoNetworkService.createNetwork(networkName, networkName, zoneId, networkOfferingId, globoNetworkEnvironmentId, null, ACLType.Domain, null, null, null, null, true,
                    null, false, null);
            // This command must throw InvalidParameterValueException, otherwise fails
            Assert.fail();
        } catch (ResourceAllocationException e) {
            verify(_agentMgr, atLeastOnce()).easySend(eq(globoNetworkHostId), any(DeallocateVlanFromGloboNetworkCommand.class));
        }
    }

    @Test
    public void checkPermissionsBeforeCreatingVlanOnGloboNetwork() throws CloudException {
        try {
            when(_acctMgr.finalizeOwner(eq(acct), eq(acct.getAccountName()), eq(domainId), anyLong())).thenThrow(new PermissionDeniedException(""));

            acct.setDomainId(domainId + 1);
            _globoNetworkService.createNetwork("net-name", "display-name", zoneId, networkOfferingId, globoNetworkEnvironmentId, null, ACLType.Domain, acct.getAccountName(), null,
                    domainId, null, true, null, false, null);
            fail();
        } catch (PermissionDeniedException e) {
            verify(_agentMgr, never()).easySend(any(Long.class), any(Command.class));
        }
    }

    @Test(expected = InvalidParameterValueException.class)
    public void addGloboNetworkHostInvalidParameters() throws CloudException {
        CallContext.register(user, acct);
        _globoNetworkService.addGloboNetworkHost(physicalNetworkId, null, null, null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void addGloboNetworkHostEmptyParameters() throws CloudException {
        CallContext.register(user, acct);
        _globoNetworkService.addGloboNetworkHost(physicalNetworkId, "", "", "");
    }

    @Test
    public void addGloboNetworkHost() throws CloudException {

        String username = "testUser";
        String password = "testPwd";
        String url = "testUrl";

        PhysicalNetworkVO pNtwk = new PhysicalNetworkVO(physicalNetworkId, zoneId, null, null, null, null, null);
        when(_physicalNetworkDao.findById(physicalNetworkId)).thenReturn(pNtwk);

        HostVO globoNetworkHost = new HostVO(1L, "GloboNetwork", null, "Up", "L2Networking", "", null, null, "", null, null, null, null, null, null, null, null, zoneId, null, 0L,
                0L, null, null, null, 0L, null);

        when(_resourceMgr.addHost(eq(zoneId), any(ServerResource.class), eq(Host.Type.L2Networking), anyMapOf(String.class, String.class))).thenReturn(globoNetworkHost);

        TransactionLegacy tx = TransactionLegacy.open(TransactionLegacy.CLOUD_DB);
        try {
            CallContext.register(user, acct);

            Host host = _globoNetworkService.addGloboNetworkHost(physicalNetworkId, username, password, url);
            assertNotNull(host);
            assertEquals(host.getDataCenterId(), zoneId);
            assertEquals(host.getName(), "GloboNetwork");
        } finally {
            tx.rollback();
        }
    }

    @Test
    public void testLbPortMapValidationWithNoAdditionalPortMap() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        try {
            new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        }catch(Exception e){
            fail();
        }
    }

    @Test
    public void testLbPortMapValidationWithOneAdditionalPortMap() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Collections.singletonList("443:8443"));

        try {
            new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        }catch(Exception e){
            fail();
        }
    }

    @Test
    public void testLbPortMapValidationWithMoreThanOneAdditionalPortMap() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Arrays.asList("443:8443", "22:22"));

        try {
            new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        }catch(Exception e){
            fail();
        }
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithAdditionalPortMapEqualsMainPortMap() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Collections.singletonList("80:8080"));

        new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        fail();
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithDuplicatedAdditionalPortMap() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Arrays.asList("443:8443", "443:8443"));

        new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        fail();
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithNoPrivatePort() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Collections.singletonList("80:"));

        new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        fail();
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithInvalidPortMapFormat() {
        GloboNetworkManager globoNetworkManager = new GloboNetworkManager();
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Collections.singletonList("----"));

        globoNetworkManager.validatePortMaps(loadBalancingRule);
        fail();
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithEmptyPortMapValue() {
        GloboNetworkManager globoNetworkManager = new GloboNetworkManager();
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Collections.singletonList(""));

        globoNetworkManager.validatePortMaps(loadBalancingRule);
        fail();
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithNotNumericValue() {
        GloboNetworkManager globoNetworkManager = new GloboNetworkManager();
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Collections.singletonList("abc:abc"));

        globoNetworkManager.validatePortMaps(loadBalancingRule);
        fail();
    }

    @Test
    public void testListPoolOptions(){
        NetworkVO network = new NetworkVO();
        GloboNetworkPoolOptionResponse.PoolOption option = new GloboNetworkPoolOptionResponse.PoolOption(1L, "reset");
        when(_globoNetworkService._globoNetworkLBEnvironmentDao.findById(45L)).thenReturn(new GloboNetworkLoadBalancerEnvironment());
        mockAgentManagerSend(network, ListPoolOptionsCommand.class, new GloboNetworkPoolOptionResponse(null, Collections.singletonList(option)));

        List<GloboNetworkPoolOptionResponse.PoolOption> poolOptions = _globoNetworkService.listPoolOptions(45L, network.getId(), "ServiceDownAction");
        assertEquals(1, poolOptions.size());
        assertEquals(new Long(1L), poolOptions.get(0).getId());
        assertEquals("reset", poolOptions.get(0).getName());
    }

    @Test
    public void testListPoolOptionsGivenEmptyList(){
        NetworkVO network = new NetworkVO();
        when(_globoNetworkService._globoNetworkLBEnvironmentDao.findById(45L)).thenReturn(new GloboNetworkLoadBalancerEnvironment());
        mockAgentManagerSend(network, ListPoolOptionsCommand.class, new GloboNetworkPoolOptionResponse(null, new ArrayList<GloboNetworkPoolOptionResponse.PoolOption>()));
        List<GloboNetworkPoolOptionResponse.PoolOption> poolOptions = _globoNetworkService.listPoolOptions(45L, network.getId(), "ServiceDownAction");
        assertTrue(poolOptions.isEmpty());
    }

    @Test
    public void testListPoolOptionsGivenEmptyNetworkId(){
        try{
            when(_globoNetworkService._globoNetworkLBEnvironmentDao.findById(45L)).thenReturn(new GloboNetworkLoadBalancerEnvironment());
            _globoNetworkService.listPoolOptions(45L, null, "ServiceDownAction");
        }catch(InvalidParameterValueException e){
            assertEquals("Invalid Network ID", e.getMessage());
        }
    }

    @Test
    public void testListPoolOptionsGivenInvalidNetworkId(){
        try{
            when(_globoNetworkService._globoNetworkLBEnvironmentDao.findById(45L)).thenReturn(new GloboNetworkLoadBalancerEnvironment());
            when(_globoNetworkService._networkManager.getNetwork(1L)).thenReturn(null);
            _globoNetworkService.listPoolOptions(45L, 1L, "ServiceDownAction");
        }catch(InvalidParameterValueException e){
            assertEquals("Cannot find network with ID : 1", e.getMessage());
        }
    }

    @Test
    public void testListPoolOptionsGivenEmptyEnvironmentId(){
        try{
            _globoNetworkService.listPoolOptions(null, 1L, "ServiceDownAction");
        }catch(InvalidParameterValueException e){
            assertEquals("Invalid LB Environment ID", e.getMessage());
        }
    }

    @Test
    public void testListPoolOptionsGivenInvalidEnvironmentId(){
        try{
            when(_globoNetworkService._globoNetworkLBEnvironmentDao.findById(45L)).thenReturn(null);
            _globoNetworkService.listPoolOptions(45L, 1L, "ServiceDownAction");
        }catch(InvalidParameterValueException e){
            assertEquals("Could not find mapping to LB environment 45" , e.getMessage());
        }
    }

    @Test
    public void testListPoolOptionsGivenGenericError(){
        try{
            NetworkVO network = new NetworkVO();
            when(_globoNetworkService._globoNetworkLBEnvironmentDao.findById(45L)).thenReturn(new GloboNetworkLoadBalancerEnvironment());
            mockAgentManagerSend(network, ListPoolOptionsCommand.class, new Answer(new ListPoolOptionsCommand(1L, "ServiceDownAction"), false, "Error"));
            _globoNetworkService.listPoolOptions(45L, network.getId(), "ServiceDownAction");
        }catch(CloudRuntimeException e){
            assertEquals("Error" , e.getMessage());
        }
    }

    @Test
    public void testListPoolOptionsGivenNetworkApiError(){
        try{
            NetworkVO network = new NetworkVO();
            when(_globoNetworkService._globoNetworkLBEnvironmentDao.findById(45L)).thenReturn(new GloboNetworkLoadBalancerEnvironment());
            mockAgentManagerSend(network, ListPoolOptionsCommand.class, new GloboNetworkErrorAnswer(new ListPoolOptionsCommand(1L, "ServiceDownAction"), 404, "Error"));
            _globoNetworkService.listPoolOptions(45L, network.getId(), "ServiceDownAction");
        }catch(CloudstackGloboNetworkException e){
            assertEquals("Error" , e.getNapiDescription());
            assertEquals(404 , e.getNapiCode());
        }
    }

    @Test
    public void testImplementNetwork() throws javax.naming.ConfigurationException {
        Network network = new NetworkVO();
        GloboNetworkVlanResponse vlanResponse = new GloboNetworkVlanResponse(new GetVlanInfoFromGloboNetworkCommand(), 1L,  "vlan", "vlandesc", 1L, "172.20.1.0", "255.255.255.0", 1L, false, 24, false);
        GloboNetworkNetworkVO globoNetwork = new GloboNetworkNetworkVO(1L, 1L, 1L);

        when(_globoNetworkService._globoNetworkNetworkDao.findByNetworkId(network.getId())).thenReturn(globoNetwork);
        doReturn(vlanResponse).when(_globoNetworkService).getVlanFromGloboNetwork(network, 1L);
        mockAgentManagerSend(network, ActivateNetworkCommand.class, new Answer(null, true, ""));

        _globoNetworkService.implementNetwork(network);
        verify(_globoNetworkService._agentMgr, atLeastOnce()).easySend(any(Long.class), any(ActivateNetworkCommand.class));
    }

    @Test(expected = CloudRuntimeException.class)
    public void testImplementNetworkGivenInvalidVlan() throws javax.naming.ConfigurationException {
        Network network = new NetworkVO();
        when(_globoNetworkService._globoNetworkNetworkDao.findByNetworkId(network.getId())).thenReturn(null);
        _globoNetworkService.implementNetwork(network);
    }

    @Test
    public void testImplementNetworkGivenVlanAlreadyActivated() throws javax.naming.ConfigurationException {
        Network network = new NetworkVO();
        GloboNetworkVlanResponse vlanResponse = new GloboNetworkVlanResponse(new GetVlanInfoFromGloboNetworkCommand(), 1L,  "vlan", "vlandesc", 1L, "172.20.1.0", "255.255.255.0", 1L, true, 24, false);
        GloboNetworkNetworkVO globoNetwork = new GloboNetworkNetworkVO(1L, 1L, 1L);

        when(_globoNetworkService._globoNetworkNetworkDao.findByNetworkId(network.getId())).thenReturn(globoNetwork);
        doReturn(vlanResponse).when(_globoNetworkService).getVlanFromGloboNetwork(network, 1L);
        mockAgentManagerSend(network, ActivateNetworkCommand.class, new Answer(null, true, ""));

        _globoNetworkService.implementNetwork(network);
        verify(_globoNetworkService._agentMgr, never()).easySend(any(Long.class), any(ActivateNetworkCommand.class));
    }

    @Test
    public void testRemoveNetwork(){
        Network network = new NetworkVO();
        Vlan vlan = new Vlan();
        GloboNetworkVlanResponse vlanResponse = new GloboNetworkVlanResponse(new GetVlanInfoFromGloboNetworkCommand(), 1L,  "vlan", "vlandesc", 1L, "172.20.1.0", "255.255.255.0", 1L, true, 24, false);

        doReturn(vlan).when(_globoNetworkService).getVlanInfoFromGloboNetwork(network);
        doReturn(vlanResponse).when(_globoNetworkService).getVlanFromGloboNetwork(network, vlan.getId());
        mockAgentManagerSend(network, RemoveNetworkInGloboNetworkCommand.class, new Answer(null, true, ""));

        _globoNetworkService.removeNetworkFromGloboNetwork(network);

        verify(_globoNetworkService._agentMgr, atLeastOnce()).easySend(any(Long.class), any(RemoveNetworkInGloboNetworkCommand.class));
    }

    private void mockAgentManagerSend(Network network, Class<? extends Command> clazz, Answer response) {
        when(_globoNetworkService._networkManager.getNetwork(network.getId())).thenReturn(network);
        when(_globoNetworkService._hostDao.findByTypeNameAndZoneId(eq(network.getDataCenterId()), eq(Provider.GloboNetwork.getName()), eq(Host.Type.L2Networking))).thenReturn(new HostVO("guid"));
        when(_globoNetworkService._agentMgr.easySend(anyLong(), any(clazz))).thenReturn(response);
    }

    @Test
    public void testListAllPoolByVipId() {
        GloboNetworkManager manager = new GloboNetworkManager();

        HostDao mock = mock(HostDao.class);
        HostVO host = createMockHost();
        when(mock.findByTypeNameAndZoneId(10L, Provider.GloboNetwork.getName(), Host.Type.L2Networking)).thenReturn(host);
        manager._hostDao = mock;


        LoadBalancingRulesService lbServiceMock = mock(LoadBalancingRulesService.class);
        LoadBalancerVO lb = new LoadBalancerVO(null,null,null, 0L,0,0,null, 10, 0L, 0L,"");

        when(lbServiceMock.findById(123L)).thenReturn(lb);
        manager._lbService = lbServiceMock;

        mockGetNetworkApiVipIp(lb, manager, 10001L);

        AgentManager mockAgent = mock(AgentManager.class);


        List<GloboNetworkPoolResponse.Pool> lbResponses = mockPools();
        GloboNetworkPoolResponse poolResponseAnswer = new GloboNetworkPoolResponse(lbResponses);


        when(mockAgent.easySend(eq(host.getId()), any(ListPoolLBCommand.class))).thenReturn(poolResponseAnswer);
        manager._agentMgr = mockAgent;

        List<GloboNetworkPoolResponse.Pool> poolResponses = manager.listAllPoolByVipId(123L, 10L);

        assertEquals(2, poolResponses.size());
        GloboNetworkPoolResponse.Pool pool = poolResponses.get(0);
        assertEquals((Long) 123L, pool.getId());
        assertEquals("my_pool", pool.getIdentifier());
        assertEquals("leastcon", pool.getLbMethod());
        assertEquals((Integer)80, pool.getPort());

        pool = poolResponses.get(1);
        assertEquals((Long) 123L, pool.getId());
        assertEquals("my_pool_2", pool.getIdentifier());
        assertEquals("round", pool.getLbMethod());
        assertEquals((Integer)8090, pool.getPort());
    }

    @Test
    public void testListAllowedLbSuffixes() {
        registerConfigKey("globonetwork.lb.allowed.suffixes", " hmg.test.com,test.com,stagging.test.com, ");

        List<String> list = _globoNetworkService.listAllowedLbSuffixes();

        assertEquals(3, list.size());
        assertEquals(list.get(0), ".stagging.test.com");
        assertEquals(list.get(1), ".hmg.test.com");
        assertEquals(list.get(2), ".test.com");
    }

    @Test
    public void testGetLbDomain() {
        registerConfigKey("globonetwork.lb.allowed.suffixes", " test.com,hmg.test.com,stagging.test.com, ");
        String lbDomain = _globoNetworkService.getLbDomain("xpto.hmg.test.com");
        assertEquals(lbDomain, "hmg.test.com");
        String anotherLbDomain = _globoNetworkService.getLbDomain("xpto2.stagging.test.com");
        assertEquals(anotherLbDomain, "stagging.test.com");
    }

    @Test
    public void testRemoveLbDNSRecord() throws ResourceUnavailableException {
        LoadBalancingRule rule = createMockLbRule("dummy.test.com", true);
        registerConfigKey("globonetwork.lb.allowed.suffixes", "hmg.test.com,test.com,stagging.test.com");

        _globoNetworkService.manageLoadBalancerDomainNameRegistry(new NetworkVO(), rule);

        verify(_globoDnsService).removeDnsRecordForLoadBalancer(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    public void testRegisterDomainName() throws ResourceUnavailableException {
        LoadBalancingRule rule = createMockLbRule("dummy.test.com", false);
        registerConfigKey("globonetwork.lb.allowed.suffixes", "hmg.test.com,test.com,stagging.test.com");
        when(_globoDnsService.validateDnsRecordForLoadBalancer(anyString(), anyString(), anyString(), anyLong(), eq(false))).thenReturn(true);

        _globoNetworkService.manageLoadBalancerDomainNameRegistry(new NetworkVO(), rule);

        verify(_globoDnsService).createDnsRecordForLoadBalancer(any(GloboDnsTO.class), eq(false));
    }

    @Test
    public void testRegisterDomainNameGivenDomainRecordAlreadyRegistered() throws ResourceUnavailableException {
        LoadBalancingRule rule = createMockLbRule("dummy.test.com", false);
        registerConfigKey("globonetwork.lb.allowed.suffixes", "hmg.test.com,test.com,stagging.test.com");
        when(_globoDnsService.validateDnsRecordForLoadBalancer(anyString(), anyString(), anyString(), anyLong(), eq(false))).thenReturn(false);

        _globoNetworkService.manageLoadBalancerDomainNameRegistry(new NetworkVO(), rule);

        verify(_globoDnsService, times(1)).validateDnsRecordForLoadBalancer(anyString(), anyString(), anyString(), anyLong(), eq(false));
        verify(_globoDnsService, times(0)).createDnsRecordForLoadBalancer(any(GloboDnsTO.class), eq(false));
    }

    @Test(expected = CloudRuntimeException.class)
    public void testRegisterDomainNameGivenDomainRecordNotValid() throws ResourceUnavailableException {
        LoadBalancingRule rule = createMockLbRule("dummy.test.com", false);
        registerConfigKey("globonetwork.lb.allowed.suffixes", "hmg.test.com,test.com,stagging.test.com");
        when(_globoDnsService.validateDnsRecordForLoadBalancer(anyString(), anyString(), anyString(), anyLong(), eq(false))).thenThrow(new InvalidParameterValueException("invalid"));

        _globoNetworkService.manageLoadBalancerDomainNameRegistry(new NetworkVO(), rule);

        verify(_globoDnsService, times(1)).validateDnsRecordForLoadBalancer(anyString(), anyString(), anyString(), anyLong(), eq(false));
        verify(_globoDnsService, times(0)).createDnsRecordForLoadBalancer(any(GloboDnsTO.class), eq(false));
    }

    @Test(expected = CloudRuntimeException.class)
    public void testRegisterDomainNameGivenNotAllowedDomainSuffix() throws ResourceUnavailableException {
        LoadBalancingRule rule = createMockLbRule("dummy.other.com", false);
        registerConfigKey("globonetwork.lb.allowed.suffixes", "hmg.test.com,test.com,stagging.test.com");

        _globoNetworkService.manageLoadBalancerDomainNameRegistry(new NetworkVO(), rule);
        verify(_globoDnsService, times(0)).createDnsRecordForLoadBalancer(any(GloboDnsTO.class), eq(false));
    }

    @Test
    public void testPoolById() {
        GloboNetworkManager manager = new GloboNetworkManager();

        HostDao mock = mock(HostDao.class);
        HostVO host = createMockHost();
        when(mock.findByTypeNameAndZoneId(10L, Provider.GloboNetwork.getName(), Host.Type.L2Networking)).thenReturn(host);
        manager._hostDao = mock;


        LoadBalancingRulesService lbServiceMock = mock(LoadBalancingRulesService.class);
        LoadBalancerVO lb = new LoadBalancerVO(null,null,null, 0L,0,0,null, 10, 0L, 0L,"");

        when(lbServiceMock.findById(123L)).thenReturn(lb);
        manager._lbService = lbServiceMock;

        mockGetNetworkApiVipIp(lb, manager, 10001L);

        AgentManager mockAgent = mock(AgentManager.class);


        GloboNetworkPoolResponse.Pool pool1 = mockPool("pool1", "round", 8080, 123L, "TCP");
        GloboNetworkPoolResponse poolResponseAnswer = new GloboNetworkPoolResponse(pool1);


        when(mockAgent.easySend(eq(host.getId()), any(GetPoolLBByIdCommand.class))).thenReturn(poolResponseAnswer);
        manager._agentMgr = mockAgent;

        GloboNetworkPoolResponse.Pool pool = manager.getPoolById(123L, 10L);

        assertEquals((Long) 123L, pool.getId());
        assertEquals("pool1", pool.getIdentifier());
        assertEquals("round", pool.getLbMethod());
        assertEquals((Integer)8080, pool.getPort());
        assertEquals((Long) 123L, pool.getId());
        assertEquals("TCP", pool.getHealthcheckType());

        verify(mockAgent, times(1)).easySend(eq(host.getId()), any(GetPoolLBByIdCommand.class));
    }

    private void mockGetNetworkApiVipIp(LoadBalancer lb, GloboNetworkManager manager, Long networkVipId) {
        LoadBalancingRulesManager lbMgrMock = mock(LoadBalancingRulesManager.class);
        Ip ip = new Ip("75.75.75.75");
        when(lbMgrMock.getSourceIp(lb)).thenReturn(ip);
        manager._lbMgr = lbMgrMock;

        IPAddressDao ipAddrMock  = mock(IPAddressDao.class);
        IPAddressVO ipV0 = new IPAddressVO(ip, 0L, 0L, 0L, true);
        when(ipAddrMock.findByIpAndNetworkId(lb.getNetworkId(), ip.addr())).thenReturn(ipV0);
        manager._ipAddrDao = ipAddrMock;

        GloboNetworkIpDetailDao _globoNetworkIpDetailMock = mock(GloboNetworkIpDetailDao.class);
        GloboNetworkIpDetailVO detail = new GloboNetworkIpDetailVO();
        detail.setGloboNetworkVipId(networkVipId);
        when(_globoNetworkIpDetailMock.findByIp(anyLong())).thenReturn(detail);
        manager._globoNetworkIpDetailDao = _globoNetworkIpDetailMock;
    }

    @Test
    public void testCreatePool() {
        LoadBalancerVO loadBalancer = new LoadBalancerVO(null,null,null, 0L, 80, 8080,null, 10, 0L, 0L,"");

        CreateGloboNetworkPoolCmd cmd = new CreateGloboNetworkPoolCmd();
        cmd.setLbId(1L);
        cmd.setPublicPort(443);
        cmd.setPrivatePort(8443);
        cmd.setZoneId(1L);

        GloboNetworkManager manager = spy(new GloboNetworkManager());
        doReturn(new GloboNetworkIpDetailVO(1L, 1L)).when(manager).getNetworkApiVipIp(loadBalancer);
        doReturn(new GloboNetworkPoolResponse(null, true, "", new GloboNetworkPoolResponse.Pool())).when(manager).callCommand(any(CreatePoolCommand.class), eq(cmd.getZoneId()));

        manager._lbService = mock(LoadBalancingRulesService.class);
        when(manager._lbService.findById(cmd.getLbId())).thenReturn(loadBalancer);

        manager._lbPortMapDao = mock(LoadBalancerPortMapDao.class);
        when(manager._lbPortMapDao.listByLoadBalancerId(1L)).thenReturn(new ArrayList<LoadBalancerPortMapVO>());

        manager._lbMgr = mock(LoadBalancingRulesManager.class);
        when(manager._lbMgr.getSourceIp(loadBalancer)).thenReturn(new Ip("192.168.10.5"));

        manager._globoNetworkLBEnvironmentDao = mock(GloboNetworkLoadBalancerEnvironmentDAO.class);
        when(manager._globoNetworkLBEnvironmentDao.findById(anyLong())).thenReturn(new GloboNetworkLoadBalancerEnvironment());

        manager._globoResourceConfigurationDao = mock(GloboResourceConfigurationDao.class);
        manager._lbOptionsDao = mock(LoadBalancerOptionsDao.class);

        GloboNetworkPoolResponse.Pool pool = manager.createPool(cmd);

        assertNotNull(pool);
        verify(manager).callCommand(any(CreatePoolCommand.class), eq(cmd.getZoneId()));
        verify(manager._lbPortMapDao).persist(any(LoadBalancerPortMapVO.class));
    }

    @Test
    public void testCreatePoolGivenInvalidPortMapping() {
        LoadBalancerVO loadBalancer = new LoadBalancerVO(null,null,null, 0L, 80, 8080,null, 10, 0L, 0L,"");

        CreateGloboNetworkPoolCmd cmd = new CreateGloboNetworkPoolCmd();
        cmd.setLbId(1L);
        cmd.setPublicPort(80);
        cmd.setPrivatePort(8080);
        cmd.setZoneId(1L);

        GloboNetworkManager manager = spy(new GloboNetworkManager());
        doReturn(new GloboNetworkIpDetailVO(1L, 1L)).when(manager).getNetworkApiVipIp(loadBalancer);
        doReturn(new GloboNetworkPoolResponse(null, true, "", new GloboNetworkPoolResponse.Pool())).when(manager).callCommand(any(CreatePoolCommand.class), eq(cmd.getZoneId()));

        manager._lbService = mock(LoadBalancingRulesService.class);
        when(manager._lbService.findById(cmd.getLbId())).thenReturn(loadBalancer);

        manager._lbPortMapDao = mock(LoadBalancerPortMapDao.class);
        when(manager._lbPortMapDao.listByLoadBalancerId(1L)).thenReturn(new ArrayList<LoadBalancerPortMapVO>());

        try{
            manager.createPool(cmd);
        }catch(InvalidParameterValueException e){
            assertEquals("This public/private port pair already exists.", e.getMessage());
        }
    }

    @Test
    public void testCreatePoolGivenInvalidPortMappingForDSRLoadBalancer() {
        LoadBalancerVO loadBalancer = new LoadBalancerVO(null,null,null, 0L, 80, 80,null, 10, 0L, 0L,"");

        CreateGloboNetworkPoolCmd cmd = new CreateGloboNetworkPoolCmd();
        cmd.setLbId(1L);
        cmd.setPublicPort(443);
        cmd.setPrivatePort(8443);
        cmd.setZoneId(1L);

        GloboNetworkManager manager = spy(new GloboNetworkManager());
        doReturn(new GloboNetworkIpDetailVO(1L, 1L)).when(manager).getNetworkApiVipIp(loadBalancer);
        doReturn(new GloboNetworkPoolResponse(null, true, "", new GloboNetworkPoolResponse.Pool())).when(manager).callCommand(any(CreatePoolCommand.class), eq(cmd.getZoneId()));

        manager._lbService = mock(LoadBalancingRulesService.class);
        when(manager._lbService.findById(cmd.getLbId())).thenReturn(loadBalancer);

        manager._lbPortMapDao = mock(LoadBalancerPortMapDao.class);
        when(manager._lbPortMapDao.listByLoadBalancerId(1L)).thenReturn(new ArrayList<LoadBalancerPortMapVO>());

        manager._globoResourceConfigurationDao = mock(GloboResourceConfigurationDao.class);
        when(manager._globoResourceConfigurationDao.getFirst(GloboResourceType.LOAD_BALANCER, loadBalancer.getUuid(), GloboResourceKey.dsr)).thenReturn(
            new GloboResourceConfigurationVO(GloboResourceType.LOAD_BALANCER, loadBalancer.getUuid(), GloboResourceKey.dsr, "true")
        );

        try{
            manager.createPool(cmd);
        }catch(InvalidParameterValueException e){
            assertEquals("In DSR load balancer the public port must always be the same as private port.", e.getMessage());
        }
    }

    @Test
    public void testDeletePool(){
        LoadBalancerVO loadBalancer = new LoadBalancerVO(null,null,null, 0L, 80, 80,null, 10, 0L, 0L,"");
        GloboNetworkPoolResponse.Pool pool = new GloboNetworkPoolResponse.Pool();
        pool.setId(1L);
        pool.setVipPort(443);
        pool.setPort(8443);

        DeleteGloboNetworkPoolCmd cmd = new DeleteGloboNetworkPoolCmd();
        cmd.setLbId(1L);
        cmd.setPoolId(1L);
        cmd.setZoneId(1L);

        GloboNetworkManager manager = spy(new GloboNetworkManager());
        manager._lbMgr = mock(LoadBalancingRulesManager.class);
        when(manager._lbMgr.getSourceIp(loadBalancer)).thenReturn(new Ip("192.168.10.5"));
        manager._lbService = mock(LoadBalancingRulesService.class);
        when(manager._lbService.findById(cmd.getLbId())).thenReturn(loadBalancer);
        doReturn(new GloboNetworkIpDetailVO(1L, 1L)).when(manager).getNetworkApiVipIp(loadBalancer);
        doReturn(pool).when(manager).findPoolById(anyLong(), anyLong(), anyLong());
        doReturn(new Answer(null, true, "")).when(manager).callCommand(any(DeletePoolCommand.class), eq(cmd.getZoneId()));
        doNothing().when(manager).removePortMapping(eq(loadBalancer), any(GloboNetworkPoolResponse.Pool.class));

        manager.deletePool(cmd);

        verify(manager).findPoolById(anyLong(), anyLong(), anyLong());
        verify(manager).removePortMapping(eq(loadBalancer), eq(pool));
        verify(manager).callCommand(any(DeletePoolCommand.class), eq(cmd.getZoneId()));
    }

    @Test
    public void testUpdatePools() {
        GloboNetworkManager manager = new GloboNetworkManager();

        HostDao mock = mock(HostDao.class);
        HostVO host = createMockHost();
        when(mock.findByTypeNameAndZoneId(10L, Provider.GloboNetwork.getName(), Host.Type.L2Networking)).thenReturn(host);
        manager._hostDao = mock;

        LoadBalancingRulesService lbServiceMock = mock(LoadBalancingRulesService.class);
        LoadBalancerVO lb = new LoadBalancerVO(null,null,null, 0L,0,0,null, 10, 0L, 0L,"");

        when(lbServiceMock.findById(123L)).thenReturn(lb);
        manager._lbService = lbServiceMock;

        mockGetNetworkApiVipIp(lb, manager, 10001L);

        AgentManager mockAgent = mock(AgentManager.class);

        GloboNetworkPoolResponse.Pool pool1 = mockPool("pool1", "round", 8080, 12L, "HTTP");
        GloboNetworkPoolResponse.Pool pool2 = mockPool("pool2", "round", 8080, 13L, "HTTP");

        GloboNetworkPoolResponse poolResponseAnswer = new GloboNetworkPoolResponse(Arrays.asList(pool1, pool2));
        when(mockAgent.easySend(eq(host.getId()), any(UpdatePoolCommand.class))).thenReturn(poolResponseAnswer);
        manager._agentMgr = mockAgent;

        List<GloboNetworkPoolResponse.Pool> pools = manager.updatePools(Arrays.asList(12L, 13L), 123L, 10L, "HTTP", "", "", 10, null, null, false);

        GloboNetworkPoolResponse.Pool pool = pools.get(0);

        assertEquals((Long) 12L, pool.getId());
        assertEquals("HTTP", pool.getHealthcheckType());

        GloboNetworkPoolResponse.Pool pool22 = pools.get(1);
        assertEquals((Long) 13L, pool22.getId());
        assertEquals("HTTP", pool22.getHealthcheckType());

        verify(mockAgent, times(1)).easySend(eq(host.getId()), any(UpdatePoolCommand.class));
    }

    @Test
    public void testListAllExpectedHealthchecks() {
        Long zoneId = 12L;
        GloboNetworkManager manager = new GloboNetworkManager();

        //mock sendEasy command
        HostDao mock = mock(HostDao.class);
        HostVO host = createMockHost();
        when(mock.findByTypeNameAndZoneId(zoneId, Provider.GloboNetwork.getName(), Host.Type.L2Networking)).thenReturn(host);
        manager._hostDao = mock;

        DataCenterDao mock2 = mock(DataCenterDao.class);
        List<DataCenterVO> list = new ArrayList<>();
        list.add(new DataCenterVO(zoneId, null, null, null, null, null, null, null, null, null, null, null, null));
        when(mock2.listEnabledZones()).thenReturn(list);
        manager._dcDao = mock2;

        //mock result
        GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck expect1 = new GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck(1L, "OK");
        GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck expect2 = new GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck(2L, "WORKING");

        AgentManager mockAgent = mock(AgentManager.class);
        GloboNetworkExpectHealthcheckResponse expectHealthcheckResponse = new GloboNetworkExpectHealthcheckResponse(Arrays.asList(expect1, expect2));

        when(mockAgent.easySend(eq(host.getId()), any(UpdatePoolCommand.class))).thenReturn(expectHealthcheckResponse);
        manager._agentMgr = mockAgent;

        //execute
        List<GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck> expectedHealthcheckList = manager.listAllExpectedHealthchecks();

        assertNotNull(expectedHealthcheckList);
        assertEquals(2, expectedHealthcheckList.size());

        GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck expectedHealthcheck = expectedHealthcheckList.get(0);
        assertEquals((Long) 1L, expectedHealthcheck.getId());
        assertEquals("OK", expectedHealthcheck.getExpected());

        expectedHealthcheck = expectedHealthcheckList.get(1);
        assertEquals((Long) 2L, expectedHealthcheck.getId());
        assertEquals("WORKING", expectedHealthcheck.getExpected());
    }

    @Test
    public void testValidateStickinessPolicyGivenValidInput(){
        List<LoadBalancingRule.LbStickinessPolicy> policies = Collections.singletonList(new LoadBalancingRule.LbStickinessPolicy(null, null, false));
        LoadBalancingRule rule = new LoadBalancingRule(null,  null, policies, null, null);
        new GloboNetworkManager().validateSticknessPolicy(rule);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateStickinessPolicyGivenNullPolicies(){
        LoadBalancingRule rule = new LoadBalancingRule(null,  null, null, null, null);
        new GloboNetworkManager().validateSticknessPolicy(rule);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateStickinessPolicyGivenMoreThanOnePolicy(){
        List<LoadBalancingRule.LbStickinessPolicy> policies = new ArrayList<>();
        policies.add(new LoadBalancingRule.LbStickinessPolicy(null, null, false));
        policies.add(new LoadBalancingRule.LbStickinessPolicy(null, null, false));
        LoadBalancingRule rule = new LoadBalancingRule(null,  null, policies, null, null);
        new GloboNetworkManager().validateSticknessPolicy(rule);
    }

    @Test
    public void testValidateHealthCheckPoliciesGivenNoPolicyProvided(){
        LoadBalancingRule rule = new LoadBalancingRule(null,  null, null, null, null);
        new GloboNetworkManager().validateHealthCheckPolicies(rule);
    }

    @Test
    public void testValidateHealthCheckPoliciesGivenOnePolicy(){
        List<LoadBalancingRule.LbHealthCheckPolicy> policies = Collections.singletonList(new LoadBalancingRule.LbHealthCheckPolicy(null, null, 10, 10, 10 , 10));
        LoadBalancingRule rule = new LoadBalancingRule(null,  null, null, policies, null);
        new GloboNetworkManager().validateHealthCheckPolicies(rule);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateHealthCheckPoliciesGivenMoreThanOneNotRevokedPolicies(){
        List<LoadBalancingRule.LbHealthCheckPolicy> policies = new ArrayList<>();
        policies.add(new LoadBalancingRule.LbHealthCheckPolicy(null, null, 10, 10, 10 , 10));
        policies.add(new LoadBalancingRule.LbHealthCheckPolicy(null, null, 10, 10, 10 , 10));
        LoadBalancingRule rule = new LoadBalancingRule(null,  null, null, policies, null);
        new GloboNetworkManager().validateHealthCheckPolicies(rule);
    }

    @Test
    public void testValidateHealthCheckPoliciesGivenTwoPoliciesAndOneRevokedPolicy(){
        List<LoadBalancingRule.LbHealthCheckPolicy> policies = new ArrayList<>();
        policies.add(new LoadBalancingRule.LbHealthCheckPolicy(null, null, 10, 10, 10 , 10));
        policies.add(new LoadBalancingRule.LbHealthCheckPolicy(null, null, 10, 10, 10 , 10, true));
        LoadBalancingRule rule = new LoadBalancingRule(null,  null, null, policies, null);
        new GloboNetworkManager().validateHealthCheckPolicies(rule);
    }

    @Test
    public void testGetServicePortsGivenOnlyOnePortPairProvided(){
        LoadBalancerVO lb = new LoadBalancerVO(null, null, null, 1L, 80, 8080, null, 1L, 1L, 1L, null);
        LoadBalancingRule rule = new LoadBalancingRule(lb,  null, null, null, null);
        List<String> ports = new GloboNetworkManager().getServicePorts(rule);

        assertEquals(1, ports.size());
        assertEquals("80:8080", ports.get(0));
    }

    @Test
    public void testGetServicePortsGivenTwoPortPairProvided(){
        LoadBalancerVO lb = new LoadBalancerVO(null, null, null, 1L, 80, 8080, null, 1L, 1L, 1L, null);
        LoadBalancingRule rule = new LoadBalancingRule(lb,  null, null, null, null);
        rule.setAdditionalPortMap(Collections.singletonList("443:8443"));
        List<String> ports = new GloboNetworkManager().getServicePorts(rule);

        assertEquals(2, ports.size());
        assertEquals("80:8080", ports.get(0));
        assertEquals("443:8443", ports.get(1));
    }

    @Test
    public void testGetReals(){
        VMInstanceVO vm = new VMInstanceVO(1, 1, "vm-01", "vm-01", VirtualMachine.Type.Instance, 1L, Hypervisor.HypervisorType.Simulator, 1, 1, 1, 1, false, true, 1L);
        when(_vmDao.findById(1L)).thenReturn(vm);
        GloboNetworkNetworkVO network = new GloboNetworkNetworkVO(1L, 2L, 3L);
        when(_globoNetworkNetworkDao.findByNetworkId(1L)).thenReturn(network);

        LoadBalancingRule.LbDestination destination = new LoadBalancingRule.LbDestination(80, 80, "10.170.100.1", 1L, 1L, false);
        LoadBalancingRule rule = new LoadBalancingRule(null,  Collections.singletonList(destination), null, null, null);
        List<GloboNetworkVipResponse.Real> reals = _globoNetworkService.getReals(rule.getDestinations(), Collections.singletonList("80:8080"));

        GloboNetworkVipResponse.Real real = reals.get(0);
        assertEquals(1, reals.size());
        assertEquals("10.170.100.1", real.getIp());
        assertNotNull(real.getVmName());
        assertEquals(Collections.singletonList("80:8080"), real.getPorts());
        assertFalse(real.isRevoked());
        assertEquals((Long) 3L, real.getEnvironmentId());
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testGetRealsGivenVmNotFound(){
        when(_vmDao.findById(1L)).thenReturn(null);

        LoadBalancingRule.LbDestination destination = new LoadBalancingRule.LbDestination(80, 80, "10.170.100.1", 1L, 1L, false);
        LoadBalancingRule rule = new LoadBalancingRule(null,  Collections.singletonList(destination), null, null, null);
         _globoNetworkService.getReals(rule.getDestinations(), Collections.singletonList("80:8080"));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testGetRealsGivenNetworkNotFound(){
        VMInstanceVO vm = new VMInstanceVO(1, 1, "vm-01", "vm-01", VirtualMachine.Type.Instance, 1L, Hypervisor.HypervisorType.Simulator,
                1, 1, 1, 1,false, true, 1L);
        when(_vmDao.findById(1L)).thenReturn(vm);
        when(_globoNetworkNetworkDao.findByNetworkId(1L)).thenReturn(null);

        LoadBalancingRule.LbDestination destination = new LoadBalancingRule.LbDestination(80, 80, "10.170.100.1", 1L, 1L, false);
        LoadBalancingRule rule = new LoadBalancingRule(null,  Collections.singletonList(destination), null, null, null);
         _globoNetworkService.getReals(rule.getDestinations(), Collections.singletonList("80:8080"));
    }

    @Test
    public void testGetRealsGivenNoDestinationsSet(){
        LoadBalancingRule rule = new LoadBalancingRule(null, new ArrayList<LoadBalancingRule.LbDestination>(), null, null, null);
        List<GloboNetworkVipResponse.Real> reals = _globoNetworkService.getReals(rule.getDestinations(), Collections.singletonList("80:8080"));
        assertEquals(0, reals.size());
    }

    @Test
    public void testAddVip() throws ResourceUnavailableException {
        LoadBalancingRule rule = createMockLbRule("dummy.test.com", false);

        IPAddressVO ip = new IPAddressVO(new Ip(1L), 1L, 1L, 1L, false);
        when(_ipAddrDao.findByIpAndNetworkId(anyLong(), anyString())).thenReturn(ip);

        GloboNetworkIpDetailVO ipDetail = new GloboNetworkIpDetailVO() ;
        ipDetail.setGloboNetworkEnvironmentRefId(1L);
        when(_globoNetworkIpDetailDao.findByIp(anyLong())).thenReturn(ipDetail);

        GloboNetworkLoadBalancerEnvironment lbEnv = new GloboNetworkLoadBalancerEnvironment();
        when(_globoNetworkLBEnvironmentDao.findById(anyLong())).thenReturn(lbEnv);
        when(_acctMgr.getAccount(anyLong())).thenReturn(acct);
        when(_lbOptionsDao.listByLoadBalancerId(anyLong())).thenReturn(new ArrayList<LoadBalancerOptionsVO>());
        HostVO napiHost = new HostVO(globoNetworkHostId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, zoneId, null, 0L, 0L, null, null, null, 0L, null);
        when(_hostDao.findByTypeNameAndZoneId(0L, Provider.GloboNetwork.getName(), Host.Type.L2Networking)).thenReturn(napiHost);
        GloboNetworkVipResponse response = new GloboNetworkVipResponse();
        response.setId(1L);
        when(_agentMgr.easySend(eq(globoNetworkHostId), any(ApplyVipInGloboNetworkCommand.class))).thenReturn(response);

        assertTrue(_globoNetworkService.applyLbRuleInGloboNetwork(new NetworkVO(), rule));
        verify(_agentMgr).easySend(eq(globoNetworkHostId), any(ApplyVipInGloboNetworkCommand.class));
        verify(_globoNetworkIpDetailDao).persist(any(GloboNetworkIpDetailVO.class));
    }

    @Test(expected = CloudRuntimeException.class)
    public void testAddVipGivenLBEnvNotFound() throws ResourceUnavailableException {
        LoadBalancingRule rule = createMockLbRule("dummy.test.com", false);

        IPAddressVO ip = new IPAddressVO(new Ip(1L), 1L, 1L, 1L, false);
        when(_ipAddrDao.findByIpAndNetworkId(anyLong(), anyString())).thenReturn(ip);

        GloboNetworkIpDetailVO ipDetail = new GloboNetworkIpDetailVO() ;
        ipDetail.setGloboNetworkEnvironmentRefId(1L);
        when(_globoNetworkIpDetailDao.findByIp(anyLong())).thenReturn(ipDetail);

        when(_globoNetworkLBEnvironmentDao.findById(anyLong())).thenReturn(null);

        assertTrue(_globoNetworkService.applyLbRuleInGloboNetwork(new NetworkVO(), rule));
    }

    @Test(expected = CloudRuntimeException.class)
    public void testAddVipGivenIPDetailNotFound() throws ResourceUnavailableException {
        LoadBalancingRule rule = createMockLbRule("dummy.test.com", false);

        IPAddressVO ip = new IPAddressVO(new Ip(1L), 1L, 1L, 1L, false);
        when(_ipAddrDao.findByIpAndNetworkId(anyLong(), anyString())).thenReturn(ip);
        when(_globoNetworkIpDetailDao.findByIp(anyLong())).thenReturn(null);

        assertTrue(_globoNetworkService.applyLbRuleInGloboNetwork(new NetworkVO(), rule));
    }

    @Test(expected = CloudRuntimeException.class)
    public void testAddVipGivenIPAddressNotFound() throws ResourceUnavailableException {
        LoadBalancingRule rule = createMockLbRule("dummy.test.com", false);
        when(_ipAddrDao.findByIpAndNetworkId(anyLong(), anyString())).thenReturn(null);
        assertTrue(_globoNetworkService.applyLbRuleInGloboNetwork(new NetworkVO(), rule));
    }


    @Test(expected = CloudRuntimeException.class)
    public void testListLinkableLoadBalancersWhenLBDoesNotExist() {
        _globoNetworkService._loadBalancerDao = mock(LoadBalancerDao.class);
        when(_globoNetworkService._loadBalancerDao.findById(123L)).thenReturn(null);

        List<LoadBalancerVO> loadBalancerVOS = _globoNetworkService.listLinkableLoadBalancers(123L, 45L);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testListLinkableLoadBalancersWhenLBAlreadyLinked() {
        _globoNetworkService._loadBalancerDao = mock(LoadBalancerDao.class);
        LoadBalancerVO lbVo = new LoadBalancerVO();
        lbVo.setUuid("123123");
        lbVo.setName("test.lb.com");
        when(_globoNetworkService._loadBalancerDao.findById(123L)).thenReturn(lbVo);


        _globoNetworkService._globoResourceConfigurationDao = mock(GloboResourceConfigurationDao.class);
        List<GloboResourceConfigurationVO> globoConfigurations = new ArrayList<>();
        globoConfigurations.add(new GloboResourceConfigurationVO(GloboResourceType.LOAD_BALANCER, "123123", GloboResourceKey.linkedLoadBalancer, "333"));
        when(_globoNetworkService._globoResourceConfigurationDao.getConfiguration(GloboResourceType.LOAD_BALANCER,"123123", GloboResourceKey.linkedLoadBalancer)).thenReturn(globoConfigurations);

        when(_globoNetworkService._loadBalancerDao.findByUuid("333")).thenReturn(lbVo);

        _globoNetworkService.listLinkableLoadBalancers(123L, 45L);
    }


    @Test
    public void testListLinkableLoadBalancersEmpty() {
        _globoNetworkService._loadBalancerDao = mock(LoadBalancerDao.class);
        LoadBalancerVO lbVo = new LoadBalancerVO(null, "test.lb.com", "lb test", 90L, 80, 8080, "leastcon", 511L, 66L, 1L, "HTTP");
        lbVo.setUuid("123123");


        when(_globoNetworkService._loadBalancerDao.findById(123L)).thenReturn(lbVo);

        _globoNetworkService._globoNetworkNetworkDao = mock(GloboNetworkNetworkDao.class);
        GloboNetworkNetworkVO globonetworkRef = new GloboNetworkNetworkVO(111L, 511L, 120);
        when(_globoNetworkService._globoNetworkNetworkDao.findByNetworkId(511L)).thenReturn(globonetworkRef);

        List<LoadBalancerVO> linkablesLBs = new ArrayList<>();

        when(_globoNetworkService._loadBalancerDao.listLinkables("123123", 120L, 66L)).thenReturn(linkablesLBs);


        List<LoadBalancerVO> loadBalancerVOS = _globoNetworkService.listLinkableLoadBalancers(123L, 45L);
        assertNotNull(loadBalancerVOS);
        assertTrue(loadBalancerVOS.isEmpty());
        assertEquals(0, loadBalancerVOS.size());

    }


    @Test
    public void testListLinkableLoadBalancersNotEmpty() {
        _globoNetworkService._loadBalancerDao = mock(LoadBalancerDao.class);
        LoadBalancerVO lbVo = new LoadBalancerVO(null, "test.lb.com", "lb test", 90L, 80, 8080, "leastcon", 511L, 66L, 1L, "HTTP");
        lbVo.setUuid("123123");


        when(_globoNetworkService._loadBalancerDao.findById(123L)).thenReturn(lbVo);

        _globoNetworkService._globoNetworkNetworkDao = mock(GloboNetworkNetworkDao.class);
        GloboNetworkNetworkVO globonetworkRef = new GloboNetworkNetworkVO(111L, 511L, 120);
        when(_globoNetworkService._globoNetworkNetworkDao.findByNetworkId(511L)).thenReturn(globonetworkRef);

        List<LoadBalancerVO> linkablesLBs = new ArrayList<>();
        LoadBalancerVO linkablesLB = new LoadBalancerVO(null, "linkable.lb.com", "lb linkable", 90L, 80, 8080, "leastcon", 511L, 66L, 1L, "HTTP");
        linkablesLBs.add(linkablesLB);
        when(_globoNetworkService._loadBalancerDao.listLinkables("123123", 120L, 66L)).thenReturn(linkablesLBs);


        List<LoadBalancerVO> loadBalancerVOS = _globoNetworkService.listLinkableLoadBalancers(123L, 45L);
        assertNotNull(loadBalancerVOS);
        assertFalse(loadBalancerVOS.isEmpty());
        assertEquals(1, loadBalancerVOS.size());

    }

    @Test
    public void testRemoveVip() throws ResourceUnavailableException {
        LoadBalancingRule rule = createMockLbRule("dummy.test.com", true);

        IPAddressVO ip = new IPAddressVO(new Ip(1L), 1L, 1L, 1L, false);
        when(_ipAddrDao.findByIpAndNetworkId(anyLong(), anyString())).thenReturn(ip);

        GloboNetworkIpDetailVO ipDetail = new GloboNetworkIpDetailVO() ;
        ipDetail.setGloboNetworkEnvironmentRefId(1L);
        when(_globoNetworkIpDetailDao.findByIp(anyLong())).thenReturn(ipDetail);

        GloboNetworkLoadBalancerEnvironment lbEnv = new GloboNetworkLoadBalancerEnvironment();
        when(_globoNetworkLBEnvironmentDao.findById(anyLong())).thenReturn(lbEnv);
        when(_acctMgr.getAccount(anyLong())).thenReturn(acct);
        when(_lbOptionsDao.listByLoadBalancerId(anyLong())).thenReturn(new ArrayList<LoadBalancerOptionsVO>());
        HostVO napiHost = new HostVO(globoNetworkHostId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, zoneId, null, 0L, 0L, null, null, null, 0L, null);
        when(_hostDao.findByTypeNameAndZoneId(0L, Provider.GloboNetwork.getName(), Host.Type.L2Networking)).thenReturn(napiHost);
        GloboNetworkVipResponse response = new GloboNetworkVipResponse();
        response.setId(1L);

        when(_agentMgr.easySend(eq(globoNetworkHostId), any(RemoveNetworkInGloboNetworkCommand.class))).thenReturn(response);

        assertTrue(_globoNetworkService.applyLbRuleInGloboNetwork(new NetworkVO(), rule));
        verify(_agentMgr).easySend(eq(globoNetworkHostId), any(ApplyVipInGloboNetworkCommand.class));
        verify(_globoResourceConfigurationDao).removeConfigurations(any(String.class), any(GloboResourceType.class));
        verify(_globoNetworkIpDetailDao).persist(any(GloboNetworkIpDetailVO.class));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testCheckLbName() {
        _globoNetworkService.checkLbName("test..lb.globoi.com", "lb.globoi.com");
    }

    private LoadBalancingRule createMockLbRule(String name, boolean revoked) {
        LoadBalancerVO lb = new LoadBalancerVO(null, null, null, 1L, 80, 8080, null, 1L, 1L, 1L, null);
        lb.setState(FirewallRule.State.Add);
        if(revoked) {
            lb.setState(FirewallRule.State.Revoke);
        }
        lb.setName(name);
        Ip sourceIp = new Ip(1L);
        List<LoadBalancingRule.LbStickinessPolicy> policies = Collections.singletonList(new LoadBalancingRule.LbStickinessPolicy(null, null, false));
        return new LoadBalancingRule(lb, null, policies, null, sourceIp);
    }

    private List<GloboNetworkPoolResponse.Pool> mockPools() {
        ArrayList<GloboNetworkPoolResponse.Pool> pools = new ArrayList<>();
        pools.add(mockPool("my_pool", "leastcon", 80, 123L, "HTTP"));
        pools.add(mockPool("my_pool_2", "round", 8090, 123L, "TCP"));
        return pools;
    }

    private GloboNetworkPoolResponse.Pool mockPool(String name, String lbMethod, Integer port, Long id, String healthcheckType) {
        GloboNetworkPoolResponse.Pool pool2 = new GloboNetworkPoolResponse.Pool();
        pool2.setId(id);
        pool2.setIdentifier(name);
        pool2.setLbMethod(lbMethod);
        pool2.setPort(port);
        pool2.setHealthcheckType(healthcheckType);
        return pool2;
    }

    private HostVO createMockHost() {
        return new HostVO(
            10L, "Host-1", Host.Type.Routing, null, "10.0.0.0", null, null, null, null, null, null, null, null,
            Status.Up, null, null, null, 10L, 10L, 30L, 10233, null, null, null, 0, null
        );
    }

    private LoadBalancingRule createLoadBalancerRule() {
        return new LoadBalancingRule(new LoadBalancerVO("id", "lb", "lb", 1, 80, 8080, "algorithm", 1, 1, 1, "HTTP"), null, null, null, null);
    }

    private void registerConfigKey(String key, String valueMock) {
        ConfigDepotImpl mock = mock(ConfigDepotImpl.class);
        ConfigurationDao mockDAo = mock(ConfigurationDao.class);
        when(mock.global()).thenReturn(mockDAo);
        when(mockDAo.findById(key)).thenReturn(new ConfigurationVO("Network", "String", null, key, valueMock, null));
        ConfigKey.init(mock);
    }
}