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

import com.cloud.server.as.VirtualMachineAddress;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AutoScaleCounterProcessorTest {

    AutoScaleCounterProcessorImpl autoScaleCounterProcessor;

    @Mock
    SNMPClient snmpClient;
    @Mock
    LogStashClient logStashClient;
    @Mock
    ThreadPoolExecutor threadPoolExecutor;
    @Mock
    Logger logger;

    @Before
    public void setUp(){
        MockitoAnnotations.initMocks(this);
        AutoScaleCounterProcessorImpl.s_logger = logger;
        autoScaleCounterProcessor = new AutoScaleCounterProcessorImpl();
        autoScaleCounterProcessor.snmpClient = snmpClient;
        autoScaleCounterProcessor.logStashClient = logStashClient;
        autoScaleCounterProcessor.threadExecutorPool = threadPoolExecutor;
    }

    @Test
    public void testProcessOneIpAddress(){
        List<VirtualMachineAddress> virtualMachines = Arrays.asList(new VirtualMachineAddress("172.168.10.10","host1"));
        Map<String, String> counters = new HashMap<>();
        counters.put("cpu", "1.1.1.1.1.1.1.1");

        autoScaleCounterProcessor.process(createAutoScaleGroup(), virtualMachines, counters);
        verify(threadPoolExecutor, times(1)).execute(any(Runnable.class));
    }

    @Test
    public void testProcessWithMoreThanOneIpAddress(){
        List<VirtualMachineAddress> virtualMachines = Arrays.asList(new VirtualMachineAddress("172.168.10.10","host1"), new VirtualMachineAddress("172.168.10.9","host2"));
        Map<String, String> counters = createCountersInput(Arrays.asList("cpu"), Arrays.asList("1.1.1.1.1.1.1.1"));

        autoScaleCounterProcessor.process(createAutoScaleGroup(), virtualMachines, counters);
        verify(threadPoolExecutor, times(2)).execute(any(Runnable.class));
    }

    @Test
    public void testProcessCountersWithOneCounter(){
        Map<String, String> counters = createCountersInput(Arrays.asList("cpu"), Arrays.asList("1.1.1.1.1.1.1.1"));
        Map<String, Double> metricsResult = createMetricsResult(Arrays.asList("cpu"),Arrays.asList("0.1"));

        when(snmpClient.read("172.168.2.10", counters)).thenReturn(metricsResult);

        autoScaleCounterProcessor.processCounters(createAutoScaleGroup(), new VirtualMachineAddress("172.168.2.10", "host"), counters);
        verify(snmpClient, times(1)).read("172.168.2.10", counters);
        verify(logStashClient, times(1)).send(anyString());
    }

    @Test
    public void testProcessCountersWithMoreThanOneCounter(){
        Map<String, String> counters = createCountersInput(Arrays.asList("cpu", "memory"), Arrays.asList("1.1.1.1.1.1.1.1", "1.1.1.1.1.1.1.2"));
        Map<String, Double> metricsResult = createMetricsResult(Arrays.asList("cpu", "memory"),Arrays.asList("0.1", "0.4"));

        when(snmpClient.read("172.168.2.10", counters)).thenReturn(metricsResult);

        autoScaleCounterProcessor.processCounters(createAutoScaleGroup(), new VirtualMachineAddress("172.168.2.10","host1"), counters);
        verify(snmpClient, times(1)).read("172.168.2.10", counters);
        verify(logStashClient, times(2)).send(anyString());
    }

    @Test
    public void testProcessCountersGivenLogStashError(){
        Map<String, String> counters = createCountersInput(Arrays.asList("cpu"), Arrays.asList("1.1.1.1.1.1.1.1"));
        Map <String, Double> metricsResult = createMetricsResult(Arrays.asList("cpu"), Arrays.asList("0.1"));

        when(snmpClient.read("172.168.2.10", counters)).thenReturn(metricsResult);
        when(logStashClient.send(anyString())).thenReturn(false);

        AutoScaleVmGroupVO autoScaleGroup = createAutoScaleGroup();
        autoScaleCounterProcessor.processCounters(autoScaleGroup, new VirtualMachineAddress("172.168.2.10","host1"), counters);
        verify(logger).error("Error sending message to LogStash: {\"client\":\"cloudstack\",\"autoScaleGroupUuid\":\""+ autoScaleGroup.getUuid() +"\",\"hostname\":\"host1\",\"metric\":\"cpu\",\"value\":0.1,\"count\":1}");
    }

    @Test
    public void testCreateLogStashMessageWithOneMetric(){
        Map<String, Double> metricsResult = createMetricsResult(Arrays.asList("cpu"),Arrays.asList("0.1"));
        AutoScaleVmGroupVO autoScaleGroup = createAutoScaleGroup();
        List<String> messages = autoScaleCounterProcessor.createLogStashMessage(autoScaleGroup, metricsResult, new VirtualMachineAddress("172.168.10.10","host1"));

        assertEquals(1, messages.size());
        assertEquals("{\"client\":\"cloudstack\",\"autoScaleGroupUuid\":\""+ autoScaleGroup.getUuid() +"\",\"hostname\":\"host1\",\"metric\":\"cpu\",\"value\":0.1,\"count\":1}", messages.get(0));
    }

    @Test
    public void testCreateLogStashMessageWithMoreThanOneMetric(){
        Map<String, Double> metricsResult = createMetricsResult(Arrays.asList("cpu", "memory"),Arrays.asList("0.1", "0.5"));
        AutoScaleVmGroupVO autoScaleGroup = createAutoScaleGroup();
        List<String> messages = autoScaleCounterProcessor.createLogStashMessage(autoScaleGroup, metricsResult, new VirtualMachineAddress("172.168.10.10","host1"));

        assertEquals(2, messages.size());
        assertEquals("{\"client\":\"cloudstack\",\"autoScaleGroupUuid\":\""+ autoScaleGroup.getUuid() +"\",\"hostname\":\"host1\",\"metric\":\"cpu\",\"value\":0.1,\"count\":1}", messages.get(0));
        assertEquals("{\"client\":\"cloudstack\",\"autoScaleGroupUuid\":\""+ autoScaleGroup.getUuid() +"\",\"hostname\":\"host1\",\"metric\":\"memory\",\"value\":0.5,\"count\":1}", messages.get(1));
    }

    private Map<String, Double> createMetricsResult(List<String> metricNames, List<String> metricValues){
        Map<String, Double> metrics = new LinkedHashMap<>();
        for (int i = 0; i < metricNames.size(); i++) {
            metrics.put(metricNames.get(i), Double.parseDouble(metricValues.get(i)));
        }
        return metrics;
    }

    private Map<String, String> createCountersInput(List<String> counterNames, List<String> snmpCodes){
        Map<String, String> metrics = new HashMap<>();
        for (int i = 0; i < counterNames.size(); i++) {
            metrics.put(counterNames.get(i), snmpCodes.get(i));
        }
        return metrics;
    }

    private AutoScaleVmGroupVO createAutoScaleGroup() {
        return new AutoScaleVmGroupVO(1L,1l, 1L, 1L, 1, 3, 80, 30, new Date(), 1, "enabled", "as-group");
    }
}
