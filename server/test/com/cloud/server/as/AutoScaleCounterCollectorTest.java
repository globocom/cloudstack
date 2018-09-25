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
import com.cloud.network.as.AutoScalePolicy;
import com.cloud.network.as.AutoScalePolicyConditionMapVO;
import com.cloud.network.as.AutoScalePolicyVO;
import com.cloud.network.as.AutoScaleVmGroupPolicyMapVO;
import com.cloud.network.as.AutoScaleVmGroupVO;
import com.cloud.network.as.AutoScaleVmGroupVmMapVO;
import com.cloud.network.as.Condition;
import com.cloud.network.as.ConditionVO;
import com.cloud.network.as.Counter;
import com.cloud.network.as.CounterVO;
import com.cloud.network.as.AutoScaleCounterProcessor;
import com.cloud.network.as.dao.AutoScalePolicyConditionMapDao;
import com.cloud.network.as.dao.AutoScalePolicyDao;
import com.cloud.network.as.dao.AutoScaleVmGroupDao;
import com.cloud.network.as.dao.AutoScaleVmGroupPolicyMapDao;
import com.cloud.network.as.dao.AutoScaleVmGroupVmMapDao;
import com.cloud.network.as.dao.ConditionDao;
import com.cloud.network.as.dao.CounterDao;
import com.cloud.vm.NicVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.config.impl.ConfigurationVO;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class AutoScaleCounterCollectorTest {

    AutoScaleCounterCollector autoScaleCounterCollector;

    @Mock
    ConfigurationDao configurationDao;
    @Mock
    AutoScaleVmGroupDao autoScaleVmGroupDao;
    @Mock
    NicDao nicDao;
    @Mock
    AutoScaleVmGroupVmMapDao autoScaleVmGroupVmMapDao;
    @Mock
    VMInstanceDao vmInstanceDao;
    @Mock
    AutoScaleVmGroupPolicyMapDao asPolicyMapDao;
    @Mock
    AutoScalePolicyDao asPolicyDao;
    @Mock
    AutoScalePolicyConditionMapDao asPolicyConditionMapDao;
    @Mock
    ConditionDao conditionDao;
    @Mock
    CounterDao counterDao;
    @Mock
    AutoScaleCounterProcessor counterProcessor;
    @Mock
    Logger logger;
    @Mock
    ConfigKey<Long> ManagementServerId;

    @Before
    public void setUp(){
        MockitoAnnotations.initMocks(this);
        autoScaleCounterCollector = new AutoScaleCounterCollector();
        autoScaleCounterCollector.configurationDao = configurationDao;
        autoScaleCounterCollector.autoScaleVmGroupDao = autoScaleVmGroupDao;
        autoScaleCounterCollector.autoScaleVmMapDao = autoScaleVmGroupVmMapDao;
        autoScaleCounterCollector.vmInstanceDao = vmInstanceDao;
        autoScaleCounterCollector.nicDao = nicDao;
        autoScaleCounterCollector.asPolicyMapDao = asPolicyMapDao;
        autoScaleCounterCollector.asPolicyDao = asPolicyDao;
        autoScaleCounterCollector.asPolicyConditionMapDao = asPolicyConditionMapDao;
        autoScaleCounterCollector.conditionDao = conditionDao;
        autoScaleCounterCollector.counterDao = counterDao;
        autoScaleCounterCollector.counterProcessor = counterProcessor;
        autoScaleCounterCollector.s_logger = logger;
        autoScaleCounterCollector.managementServer = ManagementServerId;
        when(ManagementServerId.value()).thenReturn(autoScaleCounterCollector.currentManagementServerId);
    }

    @Test
    public void testRunInContext(){
        mockConfigurationDaoDataSource("elasticsearch");
        mockListEnabledAutoScaleGroups();
        mockFindCounters(Arrays.asList("cpu"), Arrays.asList("1.1.1.1.1"));

        when(autoScaleVmGroupVmMapDao.listByGroup(anyLong())).thenReturn(Arrays.asList(new AutoScaleVmGroupVmMapVO(1L, 1L)));
        when(vmInstanceDao.findById(1L)).thenReturn(createVM(20L));
        mockFindNIC(20L, "172.168.10.5", false);

        autoScaleCounterCollector.runInContext();

        Map<String, String> counters = new HashMap<>();
        counters.put("cpu", "1.1.1.1.1");
        verify(counterProcessor, times(1)).process(any(AutoScaleVmGroupVO.class), eq(Arrays.asList(new VirtualMachineAddress("172.168.10.5", "vm-20"))), eq(counters));
    }

    @Test
    public void testRunInContextGivenEmptyAutoScaleGroup(){
        mockConfigurationDaoDataSource("elasticsearch");
        mockListEnabledAutoScaleGroups();

        when(autoScaleVmGroupVmMapDao.listByGroup(anyLong())).thenReturn(null);
        when(vmInstanceDao.findById(1L)).thenReturn(null);

        autoScaleCounterCollector.runInContext();

        verifyZeroInteractions(counterProcessor);
    }

    @Test
    public void testRunInContextGivenErrorOnSnmpReader(){
        mockConfigurationDaoDataSource("elasticsearch");
        mockListEnabledAutoScaleGroups();

        when(autoScaleVmGroupVmMapDao.listByGroup(anyLong())).thenReturn(Arrays.asList(new AutoScaleVmGroupVmMapVO(1L, 20L)));
        when(vmInstanceDao.findById(20L)).thenReturn(createVM(20L));
        mockFindNIC(20L, "172.168.10.5", false);
        stub(counterProcessor.process(any(AutoScaleVmGroupVO.class), anyList(), anyMap())).toThrow(new RuntimeException());

        autoScaleCounterCollector.runInContext();

        verify(logger).error(anyString(), any(Exception.class));
    }

    @Test
    public void testRunInContextGivenElasticSearchNotEnabled(){
        mockConfigurationDaoDataSource("rrd");
        autoScaleCounterCollector.runInContext();
        verify(logger).debug("[AutoScale] Elasticsearch stats datasource not enabled or management server not configured");
    }

    @Test
    public void testGetOneCounter(){
        mockFindCounters(Arrays.asList("cpu"), Arrays.asList("1.1.1.1.1"));
        Map<String, String> counters = autoScaleCounterCollector.getCountersFrom(createAutoScaleGroup());

        assertEquals(1, counters.keySet().size());
        assertEquals("1.1.1.1.1", counters.get("cpu"));
    }

    @Test
    public void testGetMoreThanOneCounter(){
        mockFindCounters(Arrays.asList("cpu", "memory"), Arrays.asList("1.1.1.1.1", "1.1.1.1.2"));
        Map<String, String> counters = autoScaleCounterCollector.getCountersFrom(createAutoScaleGroup());

        assertEquals(2, counters.keySet().size());
        assertEquals("1.1.1.1.1", counters.get("cpu"));
        assertEquals("1.1.1.1.2", counters.get("memory"));
    }

    @Test
    public void testGetCountersWithRepeatedCounters(){
        mockFindCounters(Arrays.asList("cpu", "cpu"), Arrays.asList("1.1.1.1.1", "1.1.1.1.1"));
        Map<String, String> counters = autoScaleCounterCollector.getCountersFrom(createAutoScaleGroup());

        assertEquals(1, counters.keySet().size());
        assertEquals("1.1.1.1.1", counters.get("cpu"));
    }

    @Test
    public void testGetIpAddressGivenVmWithIpv4(){
        mockFindNIC(1L, "172.168.10.5", false);
        String ipAddress = autoScaleCounterCollector.getIpAddressesFrom(createVM(1L));

        assertEquals("172.168.10.5", ipAddress);
    }

    @Test
    public void testGetIpAddressGivenVmWithIpv6(){
        mockFindNIC(1L, "FE80:0000:0000:0000:0202:B3FF:FE1E:8329", true);
        String ipAddress = autoScaleCounterCollector.getIpAddressesFrom(createVM(1L));

        assertEquals("FE80:0000:0000:0000:0202:B3FF:FE1E:8329", ipAddress);
    }

    @Test
    public void testGetIpAddressGivenVmWithMoreThanOneNic(){
        VMInstanceVO vm = createVM(1L);
        NicVO defaultNic = createNIC("172.168.10.5", false);
        defaultNic.setDefaultNic(true);
        NicVO nonDefaultNic = createNIC("172.168.10.6", false);
        nonDefaultNic.setDefaultNic(false);

        when(nicDao.listByVmId(vm.getId())).thenReturn(Arrays.asList(defaultNic, nonDefaultNic));

        String ipAddressesFrom = autoScaleCounterCollector.getIpAddressesFrom(vm);
        assertEquals("IP should be from the non default NIC if VM has more than one NIC", nonDefaultNic.getIPv4Address(), ipAddressesFrom);
    }

    private void mockConfigurationDaoDataSource(String datasource) {
        when(configurationDao.findByName("autoscale.stats.datasource")).thenReturn(new ConfigurationVO("", "", "", "", datasource, ""));
    }

    private void mockListEnabledAutoScaleGroups() {
        when(autoScaleVmGroupDao.listAllEnabled()).thenReturn(Arrays.asList(createAutoScaleGroup()));
    }

    private void mockFindNIC(Long vmId, String address, boolean ipv6) {
        when(nicDao.listByVmId(vmId)).thenReturn(Arrays.asList(createNIC(address, ipv6)));
    }

    private void mockFindCounters(List<String> counterNames, List<String> counterValues) {
        when(asPolicyMapDao.listByVmGroupId(anyLong())).thenReturn(Arrays.asList(new AutoScaleVmGroupPolicyMapVO(1L, 1L)));
        when(asPolicyDao.findById(1L)).thenReturn(createAutoScalePolicy());

        List<AutoScalePolicyConditionMapVO> policyConditionMap = new ArrayList<>();
        for (int i = 0; i < counterNames.size(); i++) {
            policyConditionMap.add(new AutoScalePolicyConditionMapVO(1L, new Long(i+1)));
        }
        when(asPolicyConditionMapDao.findByPolicyId(anyLong())).thenReturn(policyConditionMap);

        for (int i = 0; i < counterNames.size(); i++) {
            when(conditionDao.findById(new Long(i+1))).thenReturn(new ConditionVO((i + 1), 50, 1, 1, Condition.Operator.GT));
            when(counterDao.findById(new Long(i+1))).thenReturn(new CounterVO(Counter.Source.valueOf(counterNames.get(i)),counterNames.get(i), counterValues.get(i)));
        }
    }

    private NicVO createNIC(String address, boolean isIpv6) {
        NicVO nic = new NicVO("", 1L, 1, VirtualMachine.Type.Instance);
        if(isIpv6){
            nic.setIPv6Address(address);
        } else {
            nic.setIPv4Address(address);
        }
        return nic;
    }

    private VMInstanceVO createVM(long id){
        return new VMInstanceVO(id, 1, "vm-" + id, "vm-" + id, VirtualMachine.Type.Instance, 1L, Hypervisor.HypervisorType.Simulator, 1, 1, 1, 1, true, true, 1l);
    }

    private AutoScaleVmGroupVO createAutoScaleGroup() {
        return new AutoScaleVmGroupVO(1L,1l, 1L, 1L, 1, 3, 80, 30, new Date(), 1, "enabled", "as-group");
    }

    private AutoScalePolicyVO createAutoScalePolicy() {
        return new AutoScalePolicyVO(1, 1, 1, 120, new Date(), "scaleup", 1, AutoScalePolicy.LogicalOperator.AND);
    }
}
