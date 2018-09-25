// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.network.as;

import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterVO;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InsufficientNetworkCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.network.Network;
import com.cloud.network.Networks;
import com.cloud.network.as.dao.AutoScalePolicyDao;
import com.cloud.network.as.dao.AutoScaleVmGroupDao;
import com.cloud.network.as.dao.AutoScaleVmGroupPolicyMapDao;
import com.cloud.network.as.dao.AutoScaleVmGroupVmMapDao;
import com.cloud.network.as.dao.AutoScaleVmProfileDao;
import com.cloud.network.as.dao.AutoScaleVmProfileNetworkMapDao;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.lb.LoadBalancingRulesManagerImpl;
import com.cloud.offering.ServiceOffering;
import com.cloud.server.ResourceTag;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.storage.Storage;
import com.cloud.tags.dao.ResourceTagDao;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;
import com.cloud.user.AccountManagerImpl;
import com.cloud.user.AccountService;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.uservm.UserVm;
import com.cloud.utils.db.EntityManager;
import com.cloud.vm.UserVmService;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine;
import org.apache.cloudstack.acl.ControlledEntity;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.context.CallContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AutoScaleManagerImplTest {

    private AutoScaleManagerImpl autoScaleManager;

    @Mock
    LoadBalancingRulesManagerImpl loadBalancingRulesManager;
    @Mock
    AutoScaleVmGroupDao autoScaleVmGroupDao;
    @Mock
    AutoScaleVmGroupVmMapDao autoScaleVmGroupVmMapDao;
    @Mock
    AutoScaleVmGroupPolicyMapDao autoScaleVmGroupPolicyMapDao;
    @Mock
    AccountManagerImpl accountManager;
    @Mock
    AutoScaleVmProfileDao autoScaleVmProfileDao;
    @Mock
    AutoScalePolicyDao autoScalePolicyDao;
    @Mock
    ScheduledExecutorService threadPool;
    @Mock
    ResourceTagDao resourceTagDao;

    private static final long AS_GROUP_ID = 10L;
    private static final String USER_DATA = "IA==";
    private static String groupUuid;

    @Before
    public void setUp(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testDeleteEmptyAutoScaleGroup() throws ResourceUnavailableException {
        autoScaleManager = createMockedScaleDownAutoScaleManager();
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        mockAutoScaleGroupFindById(asGroup);
        mockLbRulesManagerConfigure(asGroup, true);
        mockAutoScaleVmMapCountBy(0);
        mockRemoveAutoScaleGroup();
        mockRemoveAutoScalePolicyByGroup();

        assertTrue(autoScaleManager.deleteAutoScaleVmGroup(AS_GROUP_ID));
        verify(autoScaleManager, times(0)).doScaleDown(anyLong(), anyInt());
        verify(autoScaleVmGroupDao, times(1)).remove(AS_GROUP_ID);
    }

    @Test
    public void testDeleteAutoScaleGroupWithThreeVms() throws ResourceUnavailableException {
        autoScaleManager = createMockedScaleDownAutoScaleManager();
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        mockAutoScaleGroupFindById(asGroup);
        mockLbRulesManagerConfigure(asGroup, true);
        mockAutoScaleVmMapCountBy(3);
        mockRemoveAutoScaleGroup();
        mockRemoveAutoScalePolicyByGroup();

        assertTrue(autoScaleManager.deleteAutoScaleVmGroup(AS_GROUP_ID));
        verify(autoScaleManager, times(1)).destroyVmGroupVMs(anyLong());
        verify(autoScaleVmGroupDao, times(1)).remove(AS_GROUP_ID);
    }

    @Test
    public void testDeleteAutoScaleGroupGivenErrorInLbConfiguration() throws ResourceUnavailableException {
        autoScaleManager = createMockedScaleDownAutoScaleManager();
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        mockAutoScaleGroupFindById(asGroup);
        mockLbRulesManagerConfigure(asGroup, false);

        assertFalse(autoScaleManager.deleteAutoScaleVmGroup(AS_GROUP_ID));
    }

    @Test
    public void testDeleteAutoScaleGroupWithNullLbId() throws ResourceUnavailableException {
        autoScaleManager = createMockedScaleDownAutoScaleManager();
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        asGroup.setLoadBalancerId(null);
        mockAutoScaleGroupFindById(asGroup);
        mockLbRulesManagerConfigure(asGroup, false);

        assertFalse(autoScaleManager.deleteAutoScaleVmGroup(AS_GROUP_ID));
    }

    @Test
    public void testScaleUp() throws InsufficientCapacityException, ResourceUnavailableException {
        testScaleUpWith(1, true, true);
        verify(autoScaleVmGroupVmMapDao, times(1)).persist(any(AutoScaleVmGroupVmMapVO.class));
        verify(autoScaleManager, times(1)).createEvent(eq(groupUuid), eq(EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEUP), anyString());
        verify(autoScaleManager, times(1)).updateLastQuietTime(anyLong(), eq("scaleup"));
    }

    @Test
    public void testScaleUpThreeVms() throws InsufficientCapacityException, ResourceUnavailableException {
        testScaleUpWith(3, true, true);
        verify(autoScaleVmGroupVmMapDao, times(3)).persist(any(AutoScaleVmGroupVmMapVO.class));
        verify(autoScaleManager, times(3)).createEvent(eq(groupUuid), eq(EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEUP), anyString());
        verify(autoScaleManager, times(3)).updateLastQuietTime(anyLong(), eq("scaleup"));
    }

    @Test
    public void testScaleUpFailedToAssignToLB(){
        testScaleUpWith(1, true, false);
        verify(autoScaleVmGroupVmMapDao, times(0)).persist(any(AutoScaleVmGroupVmMapVO.class));
        verify(autoScaleManager, times(1)).createEvent(eq(groupUuid), eq(EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEUP_FAILED), anyString());
    }

    @Test
    public void testScaleUpFailedToStartVm() throws InsufficientCapacityException, ResourceUnavailableException {
        testScaleUpWith(1, false, false);
        verify(autoScaleVmGroupVmMapDao, times(0)).persist(any(AutoScaleVmGroupVmMapVO.class));
    }

    private void testScaleUpWith(int vmCount, boolean startVmResult, boolean assignToLbResult){
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        autoScaleManager = spy(new AutoScaleManagerImpl());
        configureMocks(autoScaleManager);

        List<Object> resourceTags = new ArrayList<>();
        when(resourceTagDao.listBy(asGroup.getId(), ResourceTag.ResourceObjectType.AutoScaleVmGroup)).thenReturn(new ArrayList());

        when(autoScaleVmGroupDao.findById(10L)).thenReturn(asGroup);
        //mocking createVm, assignLBRuleToNewVm and startVM so they can be tested in isolation
        doReturn(createUserVm(1L)).when(autoScaleManager).createNewVM(asGroup);
        doNothing().when(autoScaleManager).startNewVM(1L);
        doReturn(assignToLbResult).when(autoScaleManager).assignLBruleToNewVm(any(UserVm.class), eq(asGroup));
        if(startVmResult && assignToLbResult){
            doNothing().when(autoScaleManager).createEvent(eq(groupUuid), eq(EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEUP), anyString());
        }else {
            doNothing().when(autoScaleManager).createEvent(eq(groupUuid), eq(EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEUP_FAILED), anyString());
        }
        autoScaleManager.doScaleUp(AS_GROUP_ID, vmCount);
    }

    @Test
    public void testScaleUpGivenServerApiException(){
        testScaleUpException(new ServerApiException(ApiErrorCode.ACCOUNT_RESOURCE_LIMIT_ERROR, "Account limit exceeded"));
    }

    @Test
    public void testScaleUpGivenGenericException(){
        testScaleUpException(new ConcurrentOperationException("Error"));
    }

    private void testScaleUpException(Exception exception){
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        autoScaleManager = spy(new AutoScaleManagerImpl());
        configureMocks(autoScaleManager);

        when(autoScaleVmGroupDao.findById(10L)).thenReturn(asGroup);
        //mocking createVm, assignLBRuleToNewVm and startVM so they can be tested in isolation
        doReturn(createUserVm(1L)).when(autoScaleManager).createNewVM(asGroup);
        doThrow(exception).when(autoScaleManager).startNewVM(1L);
        doNothing().when(autoScaleManager).createEvent(eq(groupUuid), eq(EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEUP_FAILED), anyString());

        try {
            autoScaleManager.doScaleUp(AS_GROUP_ID, 1);
            fail();
        }catch(Exception ex){
            verify(autoScaleVmGroupVmMapDao, times(0)).persist(any(AutoScaleVmGroupVmMapVO.class));
            verify(autoScaleManager, times(1)).createEvent(eq(groupUuid), eq(EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEUP_FAILED), anyString());
        }
    }

    @Test
    public void testScaleDown(){
        testScaleDownWith(1L, null);

        verify(autoScaleVmGroupVmMapDao, times(1)).remove(AS_GROUP_ID, 1L);
        verify(threadPool, times(1)).schedule(any(Runnable.class), eq(120L), eq(TimeUnit.SECONDS));
    }

    @Test
    public void testScaleDownGivenErrorRemovingFromLB(){
        testScaleDownWith(-1L, null);

        verify(autoScaleVmGroupVmMapDao, times(0)).remove(AS_GROUP_ID, 1L);
        verify(autoScaleManager, times(1)).createEvent(eq(groupUuid), eq(EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEDOWN_FAILED), anyString());
    }

    @Test
    public void testScaleDownGivenError(){
        try {
            testScaleDownWith(1L, new RuntimeException());
            fail();
        }catch(Exception ex){
            verify(autoScaleVmGroupVmMapDao, times(0)).remove(AS_GROUP_ID, 1L);
            verify(autoScaleManager, times(1)).createEvent(eq(groupUuid), eq(EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEDOWN_FAILED), anyString());
        }
    }

    @Test
    public void testUpdateLastQuietTimeScaleUp(){
        autoScaleManager = spy(new AutoScaleManagerImpl());
        configureMocks(autoScaleManager);

        AutoScaleVmGroupPolicyMapVO asPolicyMap1 = new AutoScaleVmGroupPolicyMapVO(1L, 1L, false);
        AutoScaleVmGroupPolicyMapVO asPolicyMap2 = new AutoScaleVmGroupPolicyMapVO(1L, 2L, false);
        AutoScalePolicyVO policy1 = new AutoScalePolicyVO(1L, 1L, 60, 120, null, "scaleup", 1, AutoScalePolicy.LogicalOperator.AND);
        policy1.id = 1L;
        AutoScalePolicyVO policy2 = new AutoScalePolicyVO(1L, 1L, 60, 120, null, "scaledown", 1, AutoScalePolicy.LogicalOperator.AND);
        policy2.id = 2L;

        when(autoScaleVmGroupPolicyMapDao.listByVmGroupId(anyLong())).thenReturn(Arrays.asList(asPolicyMap1, asPolicyMap2));
        when(autoScalePolicyDao.findById(1L)).thenReturn(policy1);

        autoScaleManager.updateLastQuietTime(1L, "scaleup");

        assertNotNull(policy1.getLastQuiteTime());
        assertNull(policy2.getLastQuiteTime());
        verify(autoScalePolicyDao, times(1)).persist(policy1);
    }

    @Test
    public void testUpdateLastQuietTimeScaleDown(){
        autoScaleManager = spy(new AutoScaleManagerImpl());
        configureMocks(autoScaleManager);

        AutoScaleVmGroupPolicyMapVO asPolicyMap1 = new AutoScaleVmGroupPolicyMapVO(1L, 1L, false);
        AutoScaleVmGroupPolicyMapVO asPolicyMap2 = new AutoScaleVmGroupPolicyMapVO(1L, 2L, false);
        AutoScalePolicyVO policy1 = new AutoScalePolicyVO(1L, 1L, 60, 120, null, "scaleup", 1, AutoScalePolicy.LogicalOperator.AND);
        policy1.id = 1L;
        AutoScalePolicyVO policy2 = new AutoScalePolicyVO(1L, 1L, 60, 120, null, "scaledown", 1, AutoScalePolicy.LogicalOperator.AND);
        policy2.id = 2L;

        when(autoScaleVmGroupPolicyMapDao.listByVmGroupId(anyLong())).thenReturn(Arrays.asList(asPolicyMap1, asPolicyMap2));
        when(autoScalePolicyDao.findById(1L)).thenReturn(policy2);

        autoScaleManager.updateLastQuietTime(1L, "scaledown");

        assertNull(policy1.getLastQuiteTime());
        assertNotNull(policy2.getLastQuiteTime());
        verify(autoScalePolicyDao, times(1)).persist(policy2);
    }

    @Test
    public void testCreateNewVMWithAdvancedNetworkAndNoSecurityGroupAndUserData() throws ResourceUnavailableException, ResourceAllocationException, InsufficientCapacityException {
        autoScaleManager = spy(new AutoScaleManagerImpl());
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        AutoScaleVmProfileVO autoScaleVmProfile = new AutoScaleVmProfileVO(1L, 1L, 1L, 1L, 1L, null, null, 30, 1L, USER_DATA);

        mockFindAutoScaleVmProfile(autoScaleVmProfile);

        AccountVO owner = new AccountVO();
        mockGetActiveAccount(owner);

        EntityManager entityManager = mock(EntityManager.class);
        autoScaleManager._entityMgr = entityManager;

        DataCenterVO zone = createZone();
        mockFindDataCenter(entityManager, zone);
        ServiceOfferingVO serviceOffering = createServiceOffering();
        mockFindSystemOffering(entityManager, serviceOffering);
        FakeTemplate template = new FakeTemplate();
        mockFindTemplate(entityManager, template);

        mockListProfileNetworkMap(autoScaleVmProfile, new ArrayList<Long>());
        mockFindNetworkById();

        doReturn(1L).when(autoScaleManager).getDestinationNetworkId(asGroup);
        doReturn("instanceName").when(autoScaleManager).createInstanceName(asGroup);

        UserVmVO userVm = createVM();

        mockCreateUserVm(owner, zone, serviceOffering, template, userVm, Arrays.asList(1L));

        assertEquals(userVm, autoScaleManager.createNewVM(asGroup));
    }

    @Test
    public void testCreateNewVMWithTwoNICs() throws InsufficientCapacityException, ResourceUnavailableException, ResourceAllocationException {
        autoScaleManager = spy(new AutoScaleManagerImpl());
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        AutoScaleVmProfileVO autoScaleVmProfile = new AutoScaleVmProfileVO(1L, 1L, 1L, 1L, 1L, null, null, 30, 1L, USER_DATA);

        mockFindAutoScaleVmProfile(autoScaleVmProfile);

        AccountVO owner = new AccountVO();
        mockGetActiveAccount(owner);

        EntityManager entityManager = mock(EntityManager.class);
        autoScaleManager._entityMgr = entityManager;

        DataCenterVO zone = createZone();
        mockFindDataCenter(entityManager, zone);
        ServiceOfferingVO serviceOffering = createServiceOffering();
        mockFindSystemOffering(entityManager, serviceOffering);
        FakeTemplate template = new FakeTemplate();
        mockFindTemplate(entityManager, template);

        mockListProfileNetworkMap(autoScaleVmProfile, Arrays.asList(2L));
        mockFindNetworkById();

        doReturn(1L).when(autoScaleManager).getDestinationNetworkId(asGroup);
        doReturn("instanceName").when(autoScaleManager).createInstanceName(asGroup);

        UserVmVO userVm = createVM();

        mockCreateUserVm(owner, zone, serviceOffering, template, userVm, Arrays.asList(1L, 2L));

        assertEquals(userVm, autoScaleManager.createNewVM(asGroup));
    }

    @Test
    public void testCreateNewVMWithInvalidServiceOffering() throws InsufficientCapacityException, ResourceUnavailableException, ResourceAllocationException {
        autoScaleManager = spy(new AutoScaleManagerImpl());
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        AutoScaleVmProfileVO autoScaleVmProfile = new AutoScaleVmProfileVO(1L, 1L, 1L, 1L, 1L, null, null, 30, 1L, USER_DATA);

        mockFindAutoScaleVmProfile(autoScaleVmProfile);
        mockGetActiveAccount(new AccountVO());

        EntityManager entityManager = mock(EntityManager.class);
        autoScaleManager._entityMgr = entityManager;

        mockFindDataCenter(entityManager, createZone());
        mockFindSystemOffering(entityManager, null);

        try {
            autoScaleManager.createNewVM(asGroup);
            fail();
        }catch(InvalidParameterValueException e){
            assertEquals("Unable to find service offering: 1", e.getMessage());
        }
    }

    @Test
    public void testCreateNewVMWithInvalidZone(){
        autoScaleManager = spy(new AutoScaleManagerImpl());
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        AutoScaleVmProfileVO autoScaleVmProfile = new AutoScaleVmProfileVO(1L, 1L, 1L, 1L, 1L, null, null, 30, 1L, USER_DATA);

        mockFindAutoScaleVmProfile(autoScaleVmProfile);
        mockGetActiveAccount(new AccountVO());

        EntityManager entityManager = mock(EntityManager.class);
        autoScaleManager._entityMgr = entityManager;

        mockFindDataCenter(entityManager, null);

        try {
            autoScaleManager.createNewVM(asGroup);
            fail();
        }catch(InvalidParameterValueException e){
            assertEquals("Unable to find zone by id=1", e.getMessage());
        }
    }

    @Test
    public void testCreateNewVMWithInvalidTemplate(){
        autoScaleManager = spy(new AutoScaleManagerImpl());
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        AutoScaleVmProfileVO autoScaleVmProfile = new AutoScaleVmProfileVO(1L, 1L, 1L, 1L, 1L, null, null, 30, 1L, USER_DATA);

        mockFindAutoScaleVmProfile(autoScaleVmProfile);
        mockGetActiveAccount(new AccountVO());

        EntityManager entityManager = mock(EntityManager.class);
        autoScaleManager._entityMgr = entityManager;

        mockFindDataCenter(entityManager, createZone());
        mockFindSystemOffering(entityManager, createServiceOffering());
        mockFindTemplate(entityManager, null);

        try {
            autoScaleManager.createNewVM(asGroup);
            fail();
        }catch(InvalidParameterValueException e){
            assertEquals("Unable to use template 1", e.getMessage());
        }
    }

    @Test
    public void testCreateNewVMWithEmptyTemplate(){
        autoScaleManager = spy(new AutoScaleManagerImpl());
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        AutoScaleVmProfileVO autoScaleVmProfile = new AutoScaleVmProfileVO(1L, 1L, 1L, 1L, 1L, null, null, 30, 1L, USER_DATA);
        autoScaleVmProfile.setTemplateId(-1L);

        mockFindAutoScaleVmProfile(autoScaleVmProfile);
        mockGetActiveAccount(new AccountVO());

        assertNull(autoScaleManager.createNewVM(asGroup));
    }

    @Test
    public void testCreateNewVMGivenInsufficientCapacity() throws ResourceUnavailableException, ResourceAllocationException, InsufficientCapacityException {
        autoScaleManager = spy(new AutoScaleManagerImpl());
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        AutoScaleVmProfileVO autoScaleVmProfile = new AutoScaleVmProfileVO(1L, 1L, 1L, 1L, 1L, null, null, 30, 1L, USER_DATA);

        mockFindAutoScaleVmProfile(autoScaleVmProfile);

        AccountVO owner = new AccountVO();
        mockGetActiveAccount(owner);

        EntityManager entityManager = mock(EntityManager.class);
        autoScaleManager._entityMgr = entityManager;

        DataCenterVO zone = createZone();
        mockFindDataCenter(entityManager, zone);
        ServiceOfferingVO serviceOffering = createServiceOffering();
        mockFindSystemOffering(entityManager, serviceOffering);
        FakeTemplate template = new FakeTemplate();
        mockFindTemplate(entityManager, template);

        mockListProfileNetworkMap(autoScaleVmProfile, new ArrayList<Long>());
        mockFindNetworkById();

        doReturn(1L).when(autoScaleManager).getDestinationNetworkId(asGroup);
        doReturn("instanceName").when(autoScaleManager).createInstanceName(asGroup);

        UserVmService userVmService = mock(UserVmService.class);
        when(userVmService.createAdvancedVirtualMachine(eq(zone), eq(serviceOffering), eq(template),
                eq(Arrays.asList(1L)), eq(owner), eq("instanceName"), eq("instanceName"), isNull(Long.class),
                isNull(Long.class), isNull(String.class), eq(Hypervisor.HypervisorType.XenServer),
                eq(BaseCmd.HTTPMethod.POST), eq(USER_DATA), isNull(String.class), isNull(Map.class),
                any(Network.IpAddresses.class), eq(true), isNull(String.class), isNull(List.class), any(Map.class), isNull(String.class), isNull(Map.class), isNull(Map.class))).
                thenThrow(new InsufficientNetworkCapacityException("Network error", Networks.class, 1L));
        autoScaleManager._userVmService = userVmService;

        try {
            autoScaleManager.createNewVM(asGroup);
            fail();
        }catch(ServerApiException ex){
            assertEquals(ApiErrorCode.INSUFFICIENT_CAPACITY_ERROR, ex.getErrorCode());
        }
    }

    @Test
    public void testValidateUserDataGivenValidData(){
        new AutoScaleManagerImpl().validateUserData("dGVzdGU=");
    }

    @Test
    public void testValidateUserDataGivenEmptyData(){
        new AutoScaleManagerImpl().validateUserData(null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateUserDataGivenShortUserData(){
        new AutoScaleManagerImpl().validateUserData("a");
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateUserDataGivenLongUserData(){
        new AutoScaleManagerImpl().validateUserData(new String(new char[35000]));
    }

    private AccountService mockGetActiveAccount(AccountVO owner) {
        AccountService accountService = mock(AccountService.class);
        when(accountService.getActiveAccountById(1L)).thenReturn(owner);
        autoScaleManager._accountService = accountService;
        return accountService;
    }

    private UserVmVO createVM() {
        return new UserVmVO(1L, "instanceName", "instanceName", 1L, Hypervisor.HypervisorType.XenServer, 1L, true, true, 1L, 1L, 1L,  1,null, "instanceName", 1L);
    }

    private DataCenterVO createZone() {
        return new DataCenterVO(1L, "zone1", "", null, null, null, null, null, "domain", 1L, DataCenter.NetworkType.Advanced, "", "");
    }

    private UserVmService mockCreateUserVm(AccountVO owner, DataCenterVO zone, ServiceOfferingVO serviceOffering, VirtualMachineTemplate template, UserVmVO userVm, List<Long> networkIds) throws InsufficientCapacityException, ResourceUnavailableException, ResourceAllocationException {
        UserVmService userVmService = mock(UserVmService.class);
        when(userVmService.createAdvancedVirtualMachine(eq(zone), eq(serviceOffering), eq(template), eq(networkIds), eq(owner), eq("instanceName"), eq("instanceName"), isNull(Long.class), isNull(Long.class), isNull(String.class), eq(Hypervisor.HypervisorType.XenServer), eq(BaseCmd.HTTPMethod.POST), eq(USER_DATA), isNull(String.class), isNull(Map.class), any(Network.IpAddresses.class), eq(true), isNull(String.class), isNull(List.class), any(Map.class), isNull(String.class), isNull(Map.class), isNull(Map.class))).thenReturn(userVm);
        autoScaleManager._userVmService = userVmService;
        return userVmService;
    }

    private AutoScaleVmProfileDao mockFindAutoScaleVmProfile(AutoScaleVmProfileVO autoScaleVmProfile) {
        AutoScaleVmProfileDao autoScaleVmProfileDao = mock(AutoScaleVmProfileDao.class);
        when(autoScaleVmProfileDao.findById(anyLong())).thenReturn(autoScaleVmProfile);
        autoScaleManager._autoScaleVmProfileDao = autoScaleVmProfileDao;
        return autoScaleVmProfileDao;
    }

    private NetworkDao mockFindNetworkById() {
        NetworkDao networkDao = mock(NetworkDao.class);
        when(networkDao.findById(1L)).thenReturn(createNetwork());
        when(networkDao.findById(2L)).thenReturn(createNetwork());
        autoScaleManager._networkDao = networkDao;
        return networkDao;
    }

    private NetworkVO createNetwork() {
        return new NetworkVO(1L,
                Networks.TrafficType.Guest, Networks.Mode.Dhcp, Networks.BroadcastDomainType.LinkLocal, 1L, 1L, 1L, 1L, "network", "", "", Network.GuestType.Shared, 1L, 1L, ControlledEntity.ACLType.Domain, false, 1L, false);
    }

    private AutoScaleVmProfileNetworkMapDao mockListProfileNetworkMap(AutoScaleVmProfileVO autoScaleVmProfile, List<Long> networkIds) {
        AutoScaleVmProfileNetworkMapDao autoScaleVmProfileNetworkMapDao = mock(AutoScaleVmProfileNetworkMapDao.class);
        List<AutoScaleVmProfileNetworkMapVO> autoScaleVmProfileNetworkMapVOs = new ArrayList<>();
        for(Long networkId : networkIds){
            autoScaleVmProfileNetworkMapVOs.add(new AutoScaleVmProfileNetworkMapVO(autoScaleVmProfile.getId(), networkId));
        }
        when(autoScaleVmProfileNetworkMapDao.listByVmProfileId(autoScaleVmProfile.getId())).thenReturn(autoScaleVmProfileNetworkMapVOs);
        autoScaleManager._autoScaleVmProfileNetworkMapDao = autoScaleVmProfileNetworkMapDao;
        return autoScaleVmProfileNetworkMapDao;
    }

    private void mockFindTemplate(EntityManager entityManager, VirtualMachineTemplate template) {
        when(entityManager.findById(VirtualMachineTemplate.class, 1L)).thenReturn(template);
    }

    private void mockFindSystemOffering(EntityManager entityManager, ServiceOfferingVO serviceOffering) {
        when(entityManager.findById(ServiceOffering.class, 1L)).thenReturn(serviceOffering);
    }

    private ServiceOfferingVO createServiceOffering(){
        return new ServiceOfferingVO("Small instance", 1, 256, 100, 100, 100, true, "display", null, false, true, null, false, VirtualMachine.Type.Instance, false);
    }

    private void mockFindDataCenter(EntityManager entityManager, DataCenter zone) {
        when(entityManager.findById(DataCenter.class, 1L)).thenReturn(zone);
    }

    private void testScaleDownWith(Long removeLbResult, Exception exception) {
        AutoScaleVmGroupVO asGroup = createAutoScaleGroup();
        autoScaleManager = spy(new AutoScaleManagerImpl());
        configureMocks(autoScaleManager);

        when(autoScaleVmGroupDao.findById(10L)).thenReturn(asGroup);
        doReturn(true).when(autoScaleManager).checkConditionDown(asGroup);
        if(exception != null){
            doThrow(exception).when(autoScaleManager).removeLBrule(asGroup);
        }else{
            doReturn(removeLbResult).when(autoScaleManager).removeLBrule(asGroup);
        }
        if(removeLbResult == -1 || exception != null){
            doNothing().when(autoScaleManager).createEvent(eq(groupUuid), eq(EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEDOWN_FAILED), anyString());
        }
        when(autoScaleVmProfileDao.findById(asGroup.getProfileId())).thenReturn(new AutoScaleVmProfileVO());
        autoScaleManager.doScaleDown(AS_GROUP_ID, 1);
    }

    private void mockAutoScaleGroupFindById(AutoScaleVmGroupVO asGroup){
        when(autoScaleVmGroupDao.findById(asGroup.getId())).thenReturn(asGroup);
    }

    private void mockLbRulesManagerConfigure(AutoScaleVmGroupVO asGroup, boolean result) throws ResourceUnavailableException {
        when(loadBalancingRulesManager.configureLbAutoScaleVmGroup(asGroup.getId(), AutoScaleVmGroup.State_Enabled)).thenReturn(result);
    }

    private void mockAutoScaleVmMapCountBy(int vmCount){
        when(autoScaleVmGroupVmMapDao.countByGroup(AutoScaleManagerImplTest.AS_GROUP_ID)).thenReturn(vmCount);
    }

    private void mockRemoveAutoScaleGroup(){
        when(autoScaleVmGroupDao.remove(AutoScaleManagerImplTest.AS_GROUP_ID)).thenReturn(true);
    }

    private void mockRemoveAutoScalePolicyByGroup(){
        when(autoScaleVmGroupPolicyMapDao.removeByGroupId(AutoScaleManagerImplTest.AS_GROUP_ID)).thenReturn(true);
    }

    private AutoScaleVmGroupVO createAutoScaleGroup(){
        AutoScaleVmGroupVO asGroup = new AutoScaleVmGroupVO(1L,1l, 1L, 1L, 1, 3, 80, 30, new Date(), 1, "enabled", "as-group");
        asGroup.id = AutoScaleManagerImplTest.AS_GROUP_ID;
        groupUuid = asGroup.getUuid();
        return asGroup;
    }

    private UserVmVO createUserVm(Long id) {
        return new UserVmVO(id, "test", "test", 1L, Hypervisor.HypervisorType.Any, 1L, false, false, 1L, 1L, 5L,  1,"test", "test", 1L);
    }

    private AutoScaleManagerImpl createMockedScaleDownAutoScaleManager() {
        AutoScaleManagerImpl scaleManager = spy(new AutoScaleManagerImpl());
        configureMocks(scaleManager);
        doNothing().when(scaleManager).doScaleDown(anyLong(), anyInt());
        return scaleManager;
    }

    private void configureMocks(AutoScaleManagerImpl autoScaleManager) {
        autoScaleManager._lbRulesMgr = loadBalancingRulesManager;
        autoScaleManager._autoScaleVmGroupDao = autoScaleVmGroupDao;
        autoScaleManager._autoScaleVmGroupVmMapDao = autoScaleVmGroupVmMapDao;
        autoScaleManager._accountMgr = accountManager;
        autoScaleManager._autoScaleVmGroupPolicyMapDao = autoScaleVmGroupPolicyMapDao;
        autoScaleManager._autoScaleVmProfileDao = autoScaleVmProfileDao;
        autoScaleManager._autoScalePolicyDao = autoScalePolicyDao;
        autoScaleManager._executor = threadPool;
        autoScaleManager._resourceTagDao = resourceTagDao;

        AccountVO acct = new AccountVO(200L);
        acct.setType(Account.ACCOUNT_TYPE_NORMAL);
        acct.setAccountName("user");
        acct.setDomainId(1L);

        UserVO user = new UserVO();
        user.setUsername("user");
        user.setAccountId(acct.getAccountId());

        CallContext.register(user, acct);
        when(accountManager.getSystemAccount()).thenReturn(acct);
        when(accountManager.getSystemUser()).thenReturn(user);
    }

    class FakeTemplate implements VirtualMachineTemplate{

        @Override
        public State getState() {
            return null;
        }

        @Override
        public boolean isFeatured() {
            return false;
        }

        @Override
        public boolean isPublicTemplate() {
            return false;
        }

        @Override
        public boolean isExtractable() {
            return false;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Storage.ImageFormat getFormat() {
            return null;
        }

        @Override
        public boolean isRequiresHvm() {
            return false;
        }

        @Override
        public String getDisplayText() {
            return null;
        }

        @Override
        public boolean getEnablePassword() {
            return false;
        }

        @Override
        public boolean getEnableSshKey() {
            return false;
        }

        @Override
        public boolean isCrossZones() {
            return false;
        }

        @Override
        public Date getCreated() {
            return null;
        }

        @Override
        public long getGuestOSId() {
            return 0;
        }

        @Override
        public boolean isBootable() {
            return false;
        }

        @Override
        public Storage.TemplateType getTemplateType() {
            return null;
        }

        @Override
        public Hypervisor.HypervisorType getHypervisorType() {
            return Hypervisor.HypervisorType.XenServer;
        }

        @Override
        public int getBits() {
            return 0;
        }

        @Override
        public String getUniqueName() {
            return null;
        }

        @Override
        public String getUrl() {
            return null;
        }

        @Override
        public String getChecksum() {
            return null;
        }

        @Override
        public Long getSourceTemplateId() {
            return null;
        }

        @Override
        public String getTemplateTag() {
            return null;
        }

        @Override
        public Map getDetails() {
            return null;
        }

        @Override
        public boolean isDynamicallyScalable() {
            return false;
        }

        @Override
        public Long getParentTemplateId() {
            return null;
        }

        @Override
        public long getUpdatedCount() {
            return 0;
        }

        @Override
        public void incrUpdatedCount() {

        }

        @Override
        public Date getUpdated() {
            return null;
        }

        @Override
        public Class<?> getEntityType() {
            return null;
        }

        @Override
        public String getUuid() {
            return null;
        }

        @Override
        public long getId() {
            return 0;
        }

        @Override
        public long getAccountId() {
            return 0;
        }

        @Override
        public long getDomainId() {
            return 0;
        }
    }
}
