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

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SNMPClientTest {

    private SNMPClientImpl snmpClient;
    @Mock
    Snmp snmp;
    @Mock
    Logger logger;

    private static final String DISK_OID = "1.3.6.1.4.1.2021.9.1.7.1";
    private static final String USED_CPU_OID = "1.3.6.1.4.1.2021.11.9.0";
    private static final String USED_MEMORY_OID = "1.3.6.1.4.1.2021.11.10.1";


    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        snmpClient = new SNMPClientImpl(snmp);
        SNMPClientImpl.s_logger = logger;
    }

    @Test
    public void testReadUsedCPU() throws IOException {
        mockSnmpGet(Arrays.asList(SNMPClientImpl.CPU_SYSTEM_OID, SNMPClientImpl.CPU_USER_OID), Arrays.asList("50", "10"));
        Map<String, Double> metrics = snmpClient.read("172.168.10.10",  createRequestCounters(Arrays.asList("cpu_used"), Arrays.asList(USED_CPU_OID)));

        assertEquals(1, metrics.size());
        assertTrue(60.0 == metrics.get("cpu_used"));
        verify(snmp).get(any(PDU.class), any(Target.class));
    }

    @Test
    public void testReadUsedMemory() throws IOException {
        mockSnmpGet(Arrays.asList(SNMPClientImpl.MEMORY_TOTAL_OID, SNMPClientImpl.MEMORY_FREE_OID), Arrays.asList("512", "256"));
        Map<String, Double> metrics = snmpClient.read("172.168.10.10",  createRequestCounters(Arrays.asList("memory_used"), Arrays.asList(USED_MEMORY_OID)));

        assertEquals(1, metrics.size());
        assertTrue(50.0 == metrics.get("memory_used"));
        verify(snmp).get(any(PDU.class), any(Target.class));
    }

    @Test
    public void testReadCpuAndMemory() throws IOException {
        mockSnmpGet(Arrays.asList(SNMPClientImpl.CPU_SYSTEM_OID, SNMPClientImpl.CPU_USER_OID, SNMPClientImpl.MEMORY_TOTAL_OID, SNMPClientImpl.MEMORY_FREE_OID),
                    Arrays.asList("50", "10", "256", "128"));
        Map<String, Double> metrics = snmpClient.read("172.168.10.10",  createRequestCounters(Arrays.asList("cpu_used", "memory_used"), Arrays.asList(USED_CPU_OID, USED_MEMORY_OID)));

        assertEquals(2, metrics.size());
        assertTrue(60.0 == metrics.get("cpu_used"));
        assertTrue(50.0 == metrics.get("memory_used"));
        verify(snmp).get(any(PDU.class), any(Target.class));
    }

    @Test
    public void testReadCpuMemoryAndDisk() throws IOException {
        mockSnmpGet(Arrays.asList(SNMPClientImpl.CPU_SYSTEM_OID, SNMPClientImpl.CPU_USER_OID, SNMPClientImpl.MEMORY_TOTAL_OID, SNMPClientImpl.MEMORY_FREE_OID, DISK_OID),
                    Arrays.asList("50", "10", "256", "128", "1000"));
        Map<String, Double> metrics = snmpClient.read("172.168.10.10",  createRequestCounters(Arrays.asList("cpu_used", "memory_used", "disk"), Arrays.asList(USED_CPU_OID, USED_MEMORY_OID, DISK_OID)));

        assertEquals(3, metrics.size());
        assertTrue(60.0 == metrics.get("cpu_used"));
        assertTrue(50.0 == metrics.get("memory_used"));
        assertTrue(1000 == metrics.get("disk"));
        verify(snmp).get(any(PDU.class), any(Target.class));
    }

    @Test
    public void testReadGivenSnmpTimeout() throws IOException {
        stub(snmp.get(any(PDU.class), any(Target.class))).toReturn(null);
        Map<String, Double> metrics = snmpClient.read("172.168.10.10",  createRequestCounters(Arrays.asList("memory_used"), Arrays.asList(USED_MEMORY_OID)));

        assertNull(metrics);
        verify(logger).info("SNMP agent did not respond. Possible causes: vm SNMP agent not ready / request timed out");
    }

    @Test
    public void testReadGivenErrorResponse() throws IOException {
        PDU response = new PDU();
        response.setErrorStatus(3);
        stub(snmp.get(any(PDU.class), any(Target.class))).toReturn(new ResponseEvent("", new IpAddress("172.168.10.10"), new PDU(), response, ""));
        Map<String, Double> metrics = snmpClient.read("172.168.10.10",  createRequestCounters(Arrays.asList("memory_used"), Arrays.asList(USED_MEMORY_OID)));

        assertNull(metrics);
        verify(logger).error("Error Status status/text: 3/Bad value");
    }

    @Test
    public void testReadGivenIOException() throws IOException {
        Exception ex = new IOException();
        stub(snmp.get(any(PDU.class), any(Target.class))).toThrow(ex);
        Map<String, Double> metrics = snmpClient.read("172.168.10.10",  createRequestCounters(Arrays.asList("memory_used"), Arrays.asList(USED_MEMORY_OID)));

        assertNull(metrics);
        verify(logger).error("Error querying SNMP on: 172.168.10.10", ex);
    }

    @Test
    public void testReadGivenSNMPAgentNotReadyOnVm() throws IOException {
        PDU response = new PDU();
        response.add(new VariableBinding(new OID(SNMPClientImpl.MEMORY_TOTAL_OID), new Null(129)));
        response.add(new VariableBinding(new OID(SNMPClientImpl.MEMORY_TOTAL_OID), new Null(129)));
        stub(snmp.get(any(PDU.class), any(Target.class))).toReturn(new ResponseEvent("", new IpAddress("172.168.10.10"), new PDU(), response, ""));

        Map<String, Double> metrics = snmpClient.read("172.168.10.10",  createRequestCounters(Arrays.asList("memory_used"), Arrays.asList(USED_MEMORY_OID)));

        assertNull(metrics);
        verify(logger).info(eq("The SNMP agent was not ready on the VM 172.168.10.10. Error: Result was 1.3.6.1.4.1.2021.4.5.0 = noSuchInstance"));
    }

    private Map<String, String> createRequestCounters(List<String> counterNames, List<String> values) {
        Map<String, String> counters = new HashMap<>();
        for (int i = 0; i < counterNames.size(); i++) {
            counters.put(counterNames.get(i), values.get(i));
        }
        return counters;
    }

    private void mockSnmpGet(List<String> oids, List<String> values) throws IOException {
        PDU response = new PDU();
        for (int i = 0; i < oids.size(); i++) {
            response.add(new VariableBinding(new OID(oids.get(i)), new Integer32(Integer.parseInt(values.get(i)))));
        }
        when(snmp.get(any(PDU.class), any(Target.class))).thenReturn(new ResponseEvent("", new IpAddress("172.168.10.10"), new PDU(), response, ""));
    }
}
