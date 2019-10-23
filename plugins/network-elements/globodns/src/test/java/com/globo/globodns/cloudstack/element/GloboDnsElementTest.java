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
package com.globo.globodns.cloudstack.element;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationDaoImpl;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host.Type;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.Network.Provider;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.ReservationContextImpl;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.globo.globodns.cloudstack.commands.CreateOrUpdateRecordAndReverseCommand;
import com.globo.globodns.cloudstack.commands.RemoveRecordCommand;

import java.util.ArrayList;
import java.util.Arrays;

public class GloboDnsElementTest {

    private static long zoneId = 5L;
    private static long globoDnsHostId = 7L;
    private static long domainId = 10L;
    private AccountVO acct = null;
    private UserVO user = null;

    GloboDnsElement _globodnsElement;

    @Mock
    DataCenterDao _datacenterDao;

    @Mock
    HostDao _hostDao;

    @Mock
    AgentManager _agentMgr;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        _globodnsElement = Mockito.spy(new GloboDnsElement());

        _globodnsElement._dcDao = _datacenterDao;
        _globodnsElement._hostDao = _hostDao;
        _globodnsElement._agentMgr = _agentMgr;

        acct = new AccountVO(200L);
        acct.setType(Account.ACCOUNT_TYPE_NORMAL);
        acct.setAccountName("user");
        acct.setDomainId(domainId);

        user = new UserVO();
        user.setUsername("user");
        user.setAccountId(acct.getAccountId());

        CallContext.register(user, acct);
    }

    @After
    public void tearDown() throws Exception {
        CallContext.unregister();
        acct = null;
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testUpperCaseCharactersAreNotAllowed() throws ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {
        Network network = mock(Network.class);
        when(network.getDataCenterId()).thenReturn(zoneId);
        when(network.getId()).thenReturn(1l);
        NicProfile nic = new NicProfile();
        VirtualMachineProfile vm = mock(VirtualMachineProfile.class);
        when(vm.getHostName()).thenReturn("UPPERCASENAME");
        when(vm.getType()).thenReturn(VirtualMachine.Type.User);
        when(_datacenterDao.findById(zoneId)).thenReturn(mock(DataCenterVO.class));
        DeployDestination dest = new DeployDestination();
        ReservationContext context = new ReservationContextImpl(null, null, user);
        boolean result = _globodnsElement.prepare(network, nic, vm, dest, context);

        assertTrue(result);
        verify(_agentMgr, times(1)).easySend(eq(globoDnsHostId), isA(CreateOrUpdateRecordAndReverseCommand.class));
    }

    @Test
    public void testPrepareMethodCallGloboDnsToRegisterHostName() throws Exception {
        Network network = mock(Network.class);
        when(network.getDataCenterId()).thenReturn(zoneId);
        when(network.getId()).thenReturn(1l);
        NicProfile nic = new NicProfile();
        nic.setUuid("123123");
        nic.setIPv4Address("10.11.12.13");
        VirtualMachineProfile vm = mock(VirtualMachineProfile.class);
        when(vm.getHostName()).thenReturn("vm-name");
        when(vm.getType()).thenReturn(VirtualMachine.Type.User);
        DataCenterVO dataCenterVO = mock(DataCenterVO.class);
        when(dataCenterVO.getId()).thenReturn(zoneId);
        when(_datacenterDao.findById(zoneId)).thenReturn(dataCenterVO);
        DeployDestination dest = new DeployDestination();
        ReservationContext context = new ReservationContextImpl(null, null, user);

        HostVO hostVO = mock(HostVO.class);
        when(hostVO.getId()).thenReturn(globoDnsHostId);
        when(_hostDao.findByTypeNameAndZoneId(eq(zoneId), eq(Provider.GloboDns.getName()), eq(Type.L2Networking))).thenReturn(hostVO);

        when(_agentMgr.easySend(eq(globoDnsHostId), isA(CreateOrUpdateRecordAndReverseCommand.class))).then(new org.mockito.stubbing.Answer<Answer>() {

            @Override
            public Answer answer(InvocationOnMock invocation) throws Throwable {
                Command cmd = (Command)invocation.getArguments()[1];
                return new Answer(cmd);
            }
        });

        GloboResourceConfigurationDaoImpl mock = mock(GloboResourceConfigurationDaoImpl.class);
        GloboResourceConfigurationVO globoResourceConfigurationVO = new GloboResourceConfigurationVO();
        globoResourceConfigurationVO.setBoolValue(false);
        when(mock.getConfiguration(GloboResourceType.VM_NIC, nic.getUuid(), GloboResourceKey.isDNSRegistered)).thenReturn(new ArrayList<GloboResourceConfigurationVO>());
        _globodnsElement._globoResourceConfigDao = mock;

        boolean result = _globodnsElement.prepare(network, nic, vm, dest, context);
        assertTrue(result);
        verify(_agentMgr, times(1)).easySend(eq(globoDnsHostId), isA(CreateOrUpdateRecordAndReverseCommand.class));
    }

    @Test
    public void testReleaseMethodCallResource() throws Exception {
        Network network = mock(Network.class);
        when(network.getDataCenterId()).thenReturn(zoneId);
        when(network.getId()).thenReturn(1l);
        NicProfile nic = new NicProfile();
        nic.setIPv4Address("10.11.12.13");
        VirtualMachineProfile vm = mock(VirtualMachineProfile.class);
        when(vm.getHostName()).thenReturn("vm-name");
        when(vm.getType()).thenReturn(VirtualMachine.Type.User);
        DataCenterVO dataCenterVO = mock(DataCenterVO.class);
        when(dataCenterVO.getId()).thenReturn(zoneId);
        when(_datacenterDao.findById(zoneId)).thenReturn(dataCenterVO);
        ReservationContext context = new ReservationContextImpl(null, null, user);

        HostVO hostVO = mock(HostVO.class);
        when(hostVO.getId()).thenReturn(globoDnsHostId);
        when(_hostDao.findByTypeNameAndZoneId(eq(zoneId), eq(Provider.GloboDns.getName()), eq(Type.L2Networking))).thenReturn(hostVO);

        when(_agentMgr.easySend(eq(globoDnsHostId), isA(RemoveRecordCommand.class))).then(new org.mockito.stubbing.Answer<Answer>() {

            @Override
            public Answer answer(InvocationOnMock invocation) throws Throwable {
                Command cmd = (Command)invocation.getArguments()[1];
                return new Answer(cmd);
            }
        });
        GloboResourceConfigurationDaoImpl mock = mock(GloboResourceConfigurationDaoImpl.class);
        GloboResourceConfigurationVO globoResourceConfigurationVO = new GloboResourceConfigurationVO();
        globoResourceConfigurationVO.setBoolValue(false);
        when(mock.getConfiguration(GloboResourceType.VM_NIC, nic.getUuid(), GloboResourceKey.isDNSRegistered)).thenReturn(new ArrayList<GloboResourceConfigurationVO>());
        _globodnsElement._globoResourceConfigDao = mock;

        boolean result = _globodnsElement.release(network, nic, vm, context);
        assertTrue(result);
        verify(_agentMgr, times(1)).easySend(eq(globoDnsHostId), isA(RemoveRecordCommand.class));
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testUnderscoreInLoadBalancerNameNotAllowed() {
        String lbDomain = "lb.globo.com";
        String lbRecord = "test_underscore";
        String lbRecordContent = "10.0.0.1";
        boolean result = _globodnsElement.validateDnsRecordForLoadBalancer(lbDomain, lbRecord, lbRecordContent, zoneId, true);
    }

    @Test
    public void testCheckLbNameGivenNotBlacklistedName(){
        Mockito.when(_globodnsElement.getBlackListedDomains()).thenReturn(new ArrayList<String>());
        _globodnsElement.checkForBlacklistedDomain("domain.com", "name");
    }


    @Test
    public void testCheckLbNameGivenBlacklistedName(){
        Mockito.when(_globodnsElement.getBlackListedDomains()).thenReturn(Arrays.asList("dev.domain.com"));
        try {
            _globodnsElement.checkForBlacklistedDomain("domain.com", "name.dev");
        }catch(InvalidParameterValueException e){
            assertEquals("Invalid load balancer name, it cannot contain the the domain 'dev.domain.com'", e.getMessage());
        }
    }
}