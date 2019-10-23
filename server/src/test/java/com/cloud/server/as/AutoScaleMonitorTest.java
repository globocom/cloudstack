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
package com.cloud.server.as;

import com.cloud.hypervisor.Hypervisor;
import com.cloud.network.as.AutoScaleManager;
import com.cloud.network.as.AutoScalePolicy;
import com.cloud.network.as.AutoScalePolicyConditionMapVO;
import com.cloud.network.as.AutoScalePolicyVO;
import com.cloud.network.as.AutoScaleStatsCollector;
import com.cloud.network.as.AutoScaleStatsCollectorFactory;
import com.cloud.network.as.AutoScaleVmGroup;
import com.cloud.network.as.AutoScaleVmGroupPolicyMapVO;
import com.cloud.network.as.AutoScaleVmGroupVO;
import com.cloud.network.as.AutoScaleVmGroupVmMapVO;
import com.cloud.network.as.Condition;
import com.cloud.network.as.ConditionVO;
import com.cloud.network.as.Counter;
import com.cloud.network.as.CounterVO;
import com.cloud.network.as.dao.AutoScalePolicyConditionMapDao;
import com.cloud.network.as.dao.AutoScalePolicyDao;
import com.cloud.network.as.dao.AutoScaleVmGroupDao;
import com.cloud.network.as.dao.AutoScaleVmGroupPolicyMapDao;
import com.cloud.network.as.dao.AutoScaleVmGroupVmMapDao;
import com.cloud.network.as.dao.ConditionDao;
import com.cloud.network.as.dao.CounterDao;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.VMInstanceDao;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

public class AutoScaleMonitorTest {

    private AutoScaleMonitor autoScaleMonitor;
    protected List<AutoScaleVmGroupVO> asGroups = new ArrayList<>();
    protected List<VMInstanceVO> vmList = new ArrayList<>();

    @Before
    public void setUp(){
        autoScaleMonitor = new AutoScaleMonitor();
        autoScaleMonitor._asCounterDao = mock(CounterDao.class);
        AutoScaleVmGroupVO asGroup = new AutoScaleVmGroupVO(1L,1l, 1L, 1L, 1, 3, 80, 30, new Date(), 1, "enabled", "as-group");
        asGroups.add(asGroup);
        VMInstanceVO vm = new VMInstanceVO(1, 1, "vm-01", "vm-01", VirtualMachine.Type.Instance, 1L, Hypervisor.HypervisorType.Simulator, 1, 1, 1, 1, false, true, 1L);
        vmList.add(vm);
    }

    @Test
    public void testScaleUp(){
        AutoScaleVmGroupVO asGroup = asGroups.get(0);
        mockIsNative("cpu", new ConditionVO(1L, 90L, 1L, 1L, Condition.Operator.GT));
        mockAutoScaleGroupDao();
        mockAutoScaleVmGroupVmMapDaoCountBy(1);
        asGroup.setMinMembers(1);
        asGroup.setMaxMembers(2);
        asGroup.setLastInterval(new Date(1));

        AutoScaleManager asManager = mock(AutoScaleManager.class);
        autoScaleMonitor._asManager = asManager;

        // Mock auto scale group VMs
        when(autoScaleMonitor._asGroupVmDao.listByGroup(anyLong())).thenReturn(Arrays.asList(new AutoScaleVmGroupVmMapVO(1, 1)));

        autoScaleMonitor._vmInstance = mock(VMInstanceDao.class);
        when(autoScaleMonitor._vmInstance.findById(1L)).thenReturn(vmList.get(0));

        // Mock Stats collector return
        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", 100.0);
        AutoScaleStatsCollector collector = mock(AutoScaleStatsCollector.class);
        when(collector.retrieveMetrics(any(AutoScaleVmGroup.class), anyList())).thenReturn(avgSummary);

        autoScaleMonitor.autoScaleStatsCollectorFactory = mock(AutoScaleStatsCollectorFactory.class);
        when(autoScaleMonitor.autoScaleStatsCollectorFactory.getStatsCollector()).thenReturn(collector);

        //Mock scale up policy
        autoScaleMonitor._asPolicyDao = mock(AutoScalePolicyDao.class);
        when(autoScaleMonitor._asPolicyDao.findById(anyLong())).thenReturn(new AutoScalePolicyVO(1, 1, 60, 120, new Date(1), "scaleup", 1, AutoScalePolicy.LogicalOperator.AND));

        autoScaleMonitor.processAutoScaleGroup(asGroup);

        verify(autoScaleMonitor._asManager).doScaleUp(asGroup.getId(), 1);
        verify(autoScaleMonitor._asGroupDao, times(3)).persist(any(AutoScaleVmGroupVO.class));
    }

    @Test
    public void testScaleDown(){
        AutoScaleVmGroupVO asGroup = asGroups.get(0);
        mockIsNative("cpu", new ConditionVO(1L, 5L, 1L, 1L, Condition.Operator.LT));
        mockAutoScaleGroupDao();
        mockAutoScaleVmGroupVmMapDaoCountBy(1);
        asGroup.setMinMembers(1);
        asGroup.setMaxMembers(2);
        asGroup.setLastInterval(new Date(1));

        AutoScaleManager asManager = mock(AutoScaleManager.class);
        autoScaleMonitor._asManager = asManager;

        // Mock auto scale group VMs
        when(autoScaleMonitor._asGroupVmDao.listByGroup(anyLong())).thenReturn(Arrays.asList(new AutoScaleVmGroupVmMapVO(1, 1)));

        autoScaleMonitor._vmInstance = mock(VMInstanceDao.class);
        when(autoScaleMonitor._vmInstance.findById(1L)).thenReturn(vmList.get(0));

        // Mock Stats collector return
        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", 0.0);
        AutoScaleStatsCollector collector = mock(AutoScaleStatsCollector.class);
        when(collector.retrieveMetrics(any(AutoScaleVmGroup.class), anyList())).thenReturn(avgSummary);

        autoScaleMonitor.autoScaleStatsCollectorFactory = mock(AutoScaleStatsCollectorFactory.class);
        when(autoScaleMonitor.autoScaleStatsCollectorFactory.getStatsCollector()).thenReturn(collector);

        //Mock scale down policy
        autoScaleMonitor._asPolicyDao = mock(AutoScalePolicyDao.class);
        when(autoScaleMonitor._asPolicyDao.findById(anyLong())).thenReturn(new AutoScalePolicyVO(1, 1, 60, 120, new Date(1), "scaledown", 1, AutoScalePolicy.LogicalOperator.AND));

        autoScaleMonitor.processAutoScaleGroup(asGroup);

        verify(autoScaleMonitor._asManager).doScaleDown(asGroup.getId(), 1);
        verify(autoScaleMonitor._asGroupDao, times(3)).persist(any(AutoScaleVmGroupVO.class));
    }

    @Test
    public void testScaleDownWithQuietTimeNotYetReached(){
        AutoScaleVmGroupVO asGroup = asGroups.get(0);
        mockIsNative("cpu", new ConditionVO(1L, 5L, 1L, 1L, Condition.Operator.LT));
        mockAutoScaleGroupDao();
        mockAutoScaleVmGroupVmMapDaoCountBy(1);
        asGroup.setMinMembers(1);
        asGroup.setMaxMembers(2);
        asGroup.setLastInterval(new Date(1));

        AutoScaleManager asManager = mock(AutoScaleManager.class);
        autoScaleMonitor._asManager = asManager;

        // Mock auto scale group VMs
        when(autoScaleMonitor._asGroupVmDao.listByGroup(anyLong())).thenReturn(Arrays.asList(new AutoScaleVmGroupVmMapVO(1, 1)));

        autoScaleMonitor._vmInstance = mock(VMInstanceDao.class);
        when(autoScaleMonitor._vmInstance.findById(1L)).thenReturn(vmList.get(0));


        // Mock Stats collector return
        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", 0.0);
        AutoScaleStatsCollector collector = mock(AutoScaleStatsCollector.class);
        when(collector.retrieveMetrics(any(AutoScaleVmGroup.class), anyList())).thenReturn(avgSummary);

        autoScaleMonitor.autoScaleStatsCollectorFactory = mock(AutoScaleStatsCollectorFactory.class);
        when(autoScaleMonitor.autoScaleStatsCollectorFactory.getStatsCollector()).thenReturn(collector);

        //Mock scale down policy with NOW as last quiet time
        Date now = new Date();
        autoScaleMonitor._asPolicyDao = mock(AutoScalePolicyDao.class);
        when(autoScaleMonitor._asPolicyDao.findById(anyLong())).thenReturn(new AutoScalePolicyVO(1, 1, 60, 120, now, "scaledown", 1, AutoScalePolicy.LogicalOperator.AND));

        autoScaleMonitor.processAutoScaleGroup(asGroup);

        verifyZeroInteractions(autoScaleMonitor._asManager);
        verify(autoScaleMonitor._asGroupDao, times(3)).persist(any(AutoScaleVmGroupVO.class));
    }

    @Test
    public void testCpuReturnedAsNull(){
        AutoScaleVmGroupVO asGroup = asGroups.get(0);
        mockIsNative("cpu");
        mockAutoScaleGroupDao();
        mockAutoScaleVmGroupVmMapDaoCountBy(1);
        asGroup.setMinMembers(1);
        asGroup.setMaxMembers(2);
        asGroup.setLastInterval(new Date(1));

        AutoScaleManager asManager = mock(AutoScaleManager.class);
        autoScaleMonitor._asManager = asManager;

        // Mock auto scale group VMs
        when(autoScaleMonitor._asGroupVmDao.listByGroup(anyLong())).thenReturn(Arrays.asList(new AutoScaleVmGroupVmMapVO(1, 1)));

        autoScaleMonitor._vmInstance = mock(VMInstanceDao.class);
        when(autoScaleMonitor._vmInstance.findById(1L)).thenReturn(vmList.get(0));

        // Mock Stats collector return - CPU metric set to NULL
        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", null);
        AutoScaleStatsCollector collector = mock(AutoScaleStatsCollector.class);
        when(collector.retrieveMetrics(any(AutoScaleVmGroup.class), anyList())).thenReturn(avgSummary);

        autoScaleMonitor.autoScaleStatsCollectorFactory = mock(AutoScaleStatsCollectorFactory.class);
        when(autoScaleMonitor.autoScaleStatsCollectorFactory.getStatsCollector()).thenReturn(collector);

        autoScaleMonitor.processAutoScaleGroup(asGroup);

        verifyZeroInteractions(autoScaleMonitor._asManager);
        verify(autoScaleMonitor._asGroupDao, times(3)).persist(any(AutoScaleVmGroupVO.class));
    }

    @Test
    public void testNoMetricsReturned(){
        AutoScaleVmGroupVO asGroup = asGroups.get(0);
        mockIsNative("cpu");
        mockAutoScaleGroupDao();
        mockAutoScaleVmGroupVmMapDaoCountBy(1);
        asGroup.setMinMembers(1);
        asGroup.setMaxMembers(2);
        asGroup.setLastInterval(new Date(1));

        AutoScaleManager asManager = mock(AutoScaleManager.class);
        autoScaleMonitor._asManager = asManager;

        // Mock auto scale group VMs
        when(autoScaleMonitor._asGroupVmDao.listByGroup(anyLong())).thenReturn(Arrays.asList(new AutoScaleVmGroupVmMapVO(1, 1)));

        autoScaleMonitor._vmInstance = mock(VMInstanceDao.class);
        when(autoScaleMonitor._vmInstance.findById(1L)).thenReturn(vmList.get(0));

        // Mock Stats collector return - No metrics returned
        Map<String, Double> avgSummary = new HashMap<>();
        AutoScaleStatsCollector collector = mock(AutoScaleStatsCollector.class);
        when(collector.retrieveMetrics(any(AutoScaleVmGroup.class), anyList())).thenReturn(avgSummary);

        autoScaleMonitor.autoScaleStatsCollectorFactory = mock(AutoScaleStatsCollectorFactory.class);
        when(autoScaleMonitor.autoScaleStatsCollectorFactory.getStatsCollector()).thenReturn(collector);

        autoScaleMonitor.processAutoScaleGroup(asGroup);

        verifyZeroInteractions(autoScaleMonitor._asManager);
        verify(autoScaleMonitor._asGroupDao, times(3)).persist(any(AutoScaleVmGroupVO.class));
    }

    @Test
    public void testMinMembersNotMet(){
        mockIsNative("cpu");
        mockAutoScaleGroupDao();
        mockAutoScaleVmGroupVmMapDaoCountBy(1);
        asGroups.get(0).setMinMembers(2);

        AutoScaleManager asManager = mock(AutoScaleManager.class);
        autoScaleMonitor._asManager = asManager;

        autoScaleMonitor.processAutoScaleGroup(asGroups.get(0));

        verify(asManager).doScaleUp(asGroups.get(0).getId(), 1);
    }

    @Test
    public void testMaxMembersExceeded(){
        mockIsNative("cpu");
        mockAutoScaleGroupDao();
        mockAutoScaleVmGroupVmMapDaoCountBy(3);
        asGroups.get(0).setMaxMembers(1);

        AutoScaleManager asManager = mock(AutoScaleManager.class);
        autoScaleMonitor._asManager = asManager;

        autoScaleMonitor.processAutoScaleGroup(asGroups.get(0));

        verify(asManager).doScaleDown(asGroups.get(0).getId(), 2);
    }

    @Test
    public void testWithoutNativeCounter(){
        mockIsNative("snmp");
        mockAutoScaleGroupDao();
        mockAutoScaleVmGroupVmMapDaoCountBy(3);
        asGroups.get(0).setMaxMembers(1);

        AutoScaleManager asManager = mock(AutoScaleManager.class);
        autoScaleMonitor._asManager = asManager;

        autoScaleMonitor.processAutoScaleGroup(asGroups.get(0));

        verifyZeroInteractions(asManager);
    }

    @Test
    public void testAutoScaleGroupLocked(){
        AutoScaleVmGroupVO asGroup = asGroups.get(0);
        asGroup.lock();
        mockAutoScaleGroupDao();

        AutoScaleManager asManager = mock(AutoScaleManager.class);
        autoScaleMonitor._asManager = asManager;

        autoScaleMonitor.processAutoScaleGroup(asGroup);

        verifyZeroInteractions(asManager);
    }

    @Test
    public void testRunInContext(){
        asGroups = Arrays.asList(new AutoScaleVmGroupVO[]{ asGroups.get(0), asGroups.get(0), asGroups.get(0) });
        AutoScaleVmGroupDao _asGroupVmDao = mock(AutoScaleVmGroupDao.class);
        when(_asGroupVmDao.listAllNotLocked()).thenReturn(asGroups);
        autoScaleMonitor._asGroupDao = _asGroupVmDao;

        autoScaleMonitor.threadExecutor = mock(ExecutorService.class);

        autoScaleMonitor.runInContext();
        verify(autoScaleMonitor.threadExecutor, times(3)).execute(any(Runnable.class));
        verify(autoScaleMonitor._asGroupDao, times(1)).listAllNotLocked();
    }

    @Test
    public void testIsPolicyValidGivenPolicyHasOneTrueCondition(){
        ConditionVO condition = new ConditionVO(1L, 30L, 1L, 1L, Condition.Operator.GT);
        when(autoScaleMonitor._asCounterDao.findById(1L)).thenReturn(new CounterVO(Counter.Source.cpu, "cpu", ""));

        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", 50.0);
        assertTrue(autoScaleMonitor.isPolicyValid(avgSummary, Collections.singletonList(condition), AutoScalePolicy.LogicalOperator.AND));
    }

    @Test
    public void testIsPolicyValidGivenPolicyHasOneFalseCondition(){
        ConditionVO condition = new ConditionVO(1L, 50, 1L, 1L, Condition.Operator.GT);
        when(autoScaleMonitor._asCounterDao.findById(1L)).thenReturn(new CounterVO(Counter.Source.cpu, "cpu", ""));

        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", 1.0);
        assertFalse(autoScaleMonitor.isPolicyValid(avgSummary, Collections.singletonList(condition), AutoScalePolicy.LogicalOperator.AND));
    }

    @Test
    public void testIsPolicyValidGivenPolicyHasTwoTrueConditionsAndLogicalOperatorAND(){
        ConditionVO condition1 = new ConditionVO(1L, 50, 1L, 1L, Condition.Operator.GT);
        ConditionVO condition2 = new ConditionVO(2L, 50, 1L, 1L, Condition.Operator.GT);
        when(autoScaleMonitor._asCounterDao.findById(1L)).thenReturn(new CounterVO(Counter.Source.cpu, "cpu", ""));
        when(autoScaleMonitor._asCounterDao.findById(2L)).thenReturn(new CounterVO(Counter.Source.memory, "memory", ""));

        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", 100.0);
        avgSummary.put("memory", 100.0);
        assertTrue(autoScaleMonitor.isPolicyValid(avgSummary, Arrays.asList(condition1, condition2), AutoScalePolicy.LogicalOperator.AND));
    }
    @Test
    public void testIsPolicyValidGivenPolicyHasTwoFalseConditionsAndLogicalOperatorAND(){
        ConditionVO condition1 = new ConditionVO(1L, 50, 1L, 1L, Condition.Operator.GT);
        ConditionVO condition2 = new ConditionVO(2L, 50, 1L, 1L, Condition.Operator.GT);
        when(autoScaleMonitor._asCounterDao.findById(1L)).thenReturn(new CounterVO(Counter.Source.cpu, "cpu", ""));
        when(autoScaleMonitor._asCounterDao.findById(2L)).thenReturn(new CounterVO(Counter.Source.memory, "memory", ""));

        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", 1.0);
        avgSummary.put("memory", 1.0);
        assertFalse(autoScaleMonitor.isPolicyValid(avgSummary, Arrays.asList(condition1, condition2), AutoScalePolicy.LogicalOperator.AND));
    }

    @Test
    public void testIsPolicyValidGivenPolicyHasTrueAndFalseConditionsAndLogicalOperatorAND(){
        ConditionVO condition1 = new ConditionVO(1L, 50, 1L, 1L, Condition.Operator.GT);
        ConditionVO condition2 = new ConditionVO(2L, 50, 1L, 1L, Condition.Operator.GT);
        when(autoScaleMonitor._asCounterDao.findById(1L)).thenReturn(new CounterVO(Counter.Source.cpu, "cpu", ""));
        when(autoScaleMonitor._asCounterDao.findById(2L)).thenReturn(new CounterVO(Counter.Source.memory, "memory", ""));

        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", 1.0);
        avgSummary.put("memory", 100.0);
        assertFalse(autoScaleMonitor.isPolicyValid(avgSummary, Arrays.asList(condition1, condition2), AutoScalePolicy.LogicalOperator.AND));
    }

    @Test
    public void testIsPolicyValidGivenPolicyHasTwoTrueConditionsAndLogicalOperatorOR(){
        ConditionVO condition1 = new ConditionVO(1L, 50, 1L, 1L, Condition.Operator.GT);
        ConditionVO condition2 = new ConditionVO(2L, 50, 1L, 1L, Condition.Operator.GT);
        when(autoScaleMonitor._asCounterDao.findById(1L)).thenReturn(new CounterVO(Counter.Source.cpu, "cpu", ""));
        when(autoScaleMonitor._asCounterDao.findById(2L)).thenReturn(new CounterVO(Counter.Source.memory, "memory", ""));

        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", 100.0);
        avgSummary.put("memory", 100.0);
        assertTrue(autoScaleMonitor.isPolicyValid(avgSummary, Arrays.asList(condition1, condition2), AutoScalePolicy.LogicalOperator.OR));
    }
    @Test
    public void testIsPolicyValidGivenPolicyHasTwoFalseConditionsAndLogicalOperatorOR(){
        ConditionVO condition1 = new ConditionVO(1L, 50, 1L, 1L, Condition.Operator.GT);
        ConditionVO condition2 = new ConditionVO(2L, 50, 1L, 1L, Condition.Operator.GT);
        when(autoScaleMonitor._asCounterDao.findById(1L)).thenReturn(new CounterVO(Counter.Source.cpu, "cpu", ""));
        when(autoScaleMonitor._asCounterDao.findById(2L)).thenReturn(new CounterVO(Counter.Source.memory, "memory", ""));

        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", 1.0);
        avgSummary.put("memory", 1.0);
        assertFalse(autoScaleMonitor.isPolicyValid(avgSummary, Arrays.asList(condition1, condition2), AutoScalePolicy.LogicalOperator.OR));
    }

    @Test
    public void testIsPolicyValidGivenPolicyHasTrueAndFalseConditionsAndLogicalOperatorOR(){
        ConditionVO condition1 = new ConditionVO(1L, 50, 1L, 1L, Condition.Operator.GT);
        ConditionVO condition2 = new ConditionVO(2L, 50, 1L, 1L, Condition.Operator.GT);
        when(autoScaleMonitor._asCounterDao.findById(1L)).thenReturn(new CounterVO(Counter.Source.cpu, "cpu", ""));
        when(autoScaleMonitor._asCounterDao.findById(2L)).thenReturn(new CounterVO(Counter.Source.memory, "memory", ""));

        Map<String, Double> avgSummary = new HashMap<>();
        avgSummary.put("cpu", 1.0);
        avgSummary.put("memory", 100.0);
        assertTrue(autoScaleMonitor.isPolicyValid(avgSummary, Arrays.asList(condition1, condition2), AutoScalePolicy.LogicalOperator.OR));
    }

    @Test
    public void testIsConditionValidGreaterThanOperator(){
        assertTrue(autoScaleMonitor.isConditionValid(50, 60.0, Condition.Operator.GT));
        assertFalse(autoScaleMonitor.isConditionValid(50, 50.0, Condition.Operator.GT));
        assertFalse(autoScaleMonitor.isConditionValid(50, 49.0, Condition.Operator.GT));
    }

    @Test
    public void testIsConditionValidGreaterThanEqualsOperator(){
        assertTrue(autoScaleMonitor.isConditionValid(50, 60.0, Condition.Operator.GE));
        assertTrue(autoScaleMonitor.isConditionValid(50, 50.0, Condition.Operator.GE));
        assertFalse(autoScaleMonitor.isConditionValid(50, 49.0, Condition.Operator.GE));
    }

    @Test
    public void testIsConditionValidEqualsOperator(){
        assertFalse(autoScaleMonitor.isConditionValid(50, 60.0, Condition.Operator.EQ));
        assertTrue(autoScaleMonitor.isConditionValid(50, 50.0, Condition.Operator.EQ));
        assertFalse(autoScaleMonitor.isConditionValid(50, 49.0, Condition.Operator.EQ));
    }

    @Test
    public void testIsConditionValidLesserThanOperator(){
        assertFalse(autoScaleMonitor.isConditionValid(50, 60.0, Condition.Operator.LT));
        assertFalse(autoScaleMonitor.isConditionValid(50, 50.0, Condition.Operator.LT));
        assertTrue(autoScaleMonitor.isConditionValid(50, 49.0, Condition.Operator.LT));
    }

    @Test
    public void testIsConditionValidLesserThanEqualsOperator(){
        assertFalse(autoScaleMonitor.isConditionValid(50, 60.0, Condition.Operator.LE));
        assertTrue(autoScaleMonitor.isConditionValid(50, 50.0, Condition.Operator.LE));
        assertTrue(autoScaleMonitor.isConditionValid(50, 49.0, Condition.Operator.LE));
    }

    protected void mockAutoScaleGroupDao(){
        AutoScaleVmGroupDao _asGroupVmDao = mock(AutoScaleVmGroupDao.class);
        when(_asGroupVmDao.listAllNotLocked()).thenReturn(asGroups);
        when(_asGroupVmDao.findById(anyLong())).thenReturn(asGroups.get(0));
        autoScaleMonitor._asGroupDao = _asGroupVmDao;
    }

    protected void mockAutoScaleVmGroupVmMapDaoCountBy(Integer count){
        AutoScaleVmGroupVmMapDao _asGroupVmDao = mock(AutoScaleVmGroupVmMapDao.class);
        when(_asGroupVmDao.countByGroup(anyInt())).thenReturn(count);
        autoScaleMonitor._asGroupVmDao = _asGroupVmDao;
    }

    protected void mockIsNative(String counterName, ConditionVO condition){
        CounterDao _asCounterDao = mock(CounterDao.class);
        CounterVO counter = new CounterVO(Counter.Source.valueOf(counterName), counterName, "");
        when(_asCounterDao.findById(1L)).thenReturn(counter);
        autoScaleMonitor._asCounterDao = _asCounterDao;

        ConditionDao _asConditionDao = mock(ConditionDao.class);

        when(_asConditionDao.findById(anyLong())).thenReturn(condition);
        autoScaleMonitor._asConditionDao = _asConditionDao;

        AutoScaleVmGroupPolicyMapDao _asGroupPolicyDao = mock(AutoScaleVmGroupPolicyMapDao.class);
        List<AutoScaleVmGroupPolicyMapVO> groupPolicymap = new ArrayList<>();
        groupPolicymap.add(new AutoScaleVmGroupPolicyMapVO(1L, 1L));
        when(_asGroupPolicyDao.listByVmGroupId(anyLong())).thenReturn(groupPolicymap);
        autoScaleMonitor._asGroupPolicyDao = _asGroupPolicyDao;

        AutoScalePolicyConditionMapDao _asConditionMapDao = mock(AutoScalePolicyConditionMapDao.class);
        List<AutoScalePolicyConditionMapVO> policiesMap = new ArrayList<>();
        policiesMap.add(new AutoScalePolicyConditionMapVO(1L, 1L));
        when(_asConditionMapDao.findByPolicyId(anyLong())).thenReturn(policiesMap);
        autoScaleMonitor._asConditionMapDao = _asConditionMapDao;
    }

    protected void mockIsNative(String counterName){
        mockIsNative(counterName, new ConditionVO(1L, 5L, 1L, 1L, Condition.Operator.LT));
    }
}