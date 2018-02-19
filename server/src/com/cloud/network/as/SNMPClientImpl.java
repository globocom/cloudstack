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

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.log4j.Logger;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class SNMPClientImpl implements SNMPClient, Configurable{

    private final Snmp snmp;

    protected static final String CPU_SYSTEM_OID = "1.3.6.1.4.1.2021.11.10.0";
    protected static final String CPU_USER_OID = "1.3.6.1.4.1.2021.11.9.0";
    protected static final String MEMORY_FREE_OID = "1.3.6.1.4.1.2021.4.6.0";
    protected static final String MEMORY_TOTAL_OID = "1.3.6.1.4.1.2021.4.5.0";

    private static final ConfigKey<Integer> SnmpTimeout = new ConfigKey<>("Advanced", Integer.class, "autoscale.snmp.timeout", "500", "Auto scale snmp client max timeout", false, ConfigKey.Scope.Global);
    private static final ConfigKey<String> SnmpCommunity = new ConfigKey<>("Advanced", String.class, "autoscale.snmp.community", "DataCenter", "SNMP community", false, ConfigKey.Scope.Global);
    private static final ConfigKey<String> SnmpPort = new ConfigKey<>("Advanced", String.class, "autoscale.snmp.port", "161", "SNMP port", false, ConfigKey.Scope.Global);
    private static final ConfigKey<String> SnmpVersion = new ConfigKey<>("Advanced", String.class, "autoscale.snmp.version", "2c", "SNMP version (1, 2c or 3)", false, ConfigKey.Scope.Global);

    public static Logger s_logger = Logger.getLogger(SNMPClientImpl.class.getName());

    public SNMPClientImpl(Snmp snmp) throws IOException {
        this.snmp = snmp;
        this.snmp.listen();
    }

    @Override
    public Map<String, Double> read(String ipAddress, Map<String, String> counters) {
        try {
            s_logger.debug("Sending GET SNMP to Server: " + ipAddress);

            ResponseEvent responseEvent = snmp.get(createPDU(counters), createTarget(ipAddress));
            if (responseEvent != null && responseEvent.getResponse() != null) {
                if (responseEvent.getResponse().getErrorStatus() == PDU.noError) {
                    return parseResponse(counters, responseEvent.getResponse().getVariableBindings());
                } else {
                    s_logger.error("Error Status status/text: " + responseEvent.getResponse().getErrorStatus() + "/" + responseEvent.getResponse().getErrorStatusText());
                }
            } else {
                s_logger.info("SNMP agent did not respond. Possible causes: vm SNMP agent not ready / request timed out");
            }
        } catch (IOException e) {
            s_logger.error("Error querying SNMP on: " + ipAddress, e);
        } catch (SnmpAgentNotReadyException e){
            s_logger.info("The SNMP agent was not ready on the VM " + ipAddress + ". Error: " + e.getMessage());
        } catch (Exception e) {
            s_logger.error("Unexpected error", e);
        }
        return null;
    }

    private Map<String, Double> parseResponse(Map<String, String> counters, Vector<VariableBinding> variableBindings) throws SnmpAgentNotReadyException {
        Map<String, Double> metrics = new HashMap<>();
        for(String counterName : counters.keySet()){
            if(counterName.equals(Counter.Source.cpu_used.name())){
                metrics.put(counterName, calculateCpuUsed(variableBindings));
            }else if(counterName.equals(Counter.Source.memory_used.name())){
                metrics.put(counterName, calculateMemoryUsed(variableBindings));
            }else if (counterName.equals(Counter.Source.memory_free.name())) {
                metrics.put(counterName, calculateMemoryFree(variableBindings));
            }else{
                metrics.put(counterName, getValueByOID(variableBindings, counters.get(counterName)));
            }
        }
        return metrics;
    }

    private Double getValueByOID(Vector<VariableBinding> variableBindings, String oid) throws SnmpAgentNotReadyException {
        for(VariableBinding binding : variableBindings){
            if(binding.getOid().toString().equals(oid)){
                if(binding.getVariable() instanceof Null){
                    throw new SnmpAgentNotReadyException("Result was " + binding.toString());
                }
                return new Double(binding.toValueString());
            }
        }
        return null;
    }

    private Double calculateMemoryUsed(Vector<VariableBinding> variableBindings) throws SnmpAgentNotReadyException {
        Double memoryFree = getValueByOID(variableBindings, MEMORY_FREE_OID);
        Double memoryTotal = getValueByOID(variableBindings, MEMORY_TOTAL_OID);
        if(memoryFree != null && memoryTotal != null) {
            return (1.0 - (memoryFree / memoryTotal)) * 100;
        }else{
            return null;
        }
    }

    private Double calculateMemoryFree(Vector<VariableBinding> variableBindings) throws SnmpAgentNotReadyException {
        Double memoryFree = getValueByOID(variableBindings, MEMORY_FREE_OID);
        Double memoryTotal = getValueByOID(variableBindings, MEMORY_TOTAL_OID);
        if(memoryFree != null && memoryTotal != null) {
            return (memoryFree / memoryTotal) * 100;
        }else{
            return null;
        }
    }

    private Double calculateCpuUsed(Vector<VariableBinding> variableBindings) throws SnmpAgentNotReadyException {
        Double cpuSystem = getValueByOID(variableBindings, CPU_SYSTEM_OID);
        Double cpuUser = getValueByOID(variableBindings, CPU_USER_OID);
        if(cpuSystem != null && cpuUser != null) {
            return cpuSystem + cpuUser;
        }else{
            return null;
        }
    }

    protected PDU createPDU(Map<String, String> counters) {
        PDU pdu = new PDU();
        for (String counterName : counters.keySet()) {
            if(counterName.equals(Counter.Source.cpu_used.name())){
                pdu.add(createVariable(CPU_SYSTEM_OID));
                pdu.add(createVariable(CPU_USER_OID));
            }else if(counterName.equals(Counter.Source.memory_used.name())){
                pdu.add(createVariable(MEMORY_FREE_OID));
                pdu.add(createVariable(MEMORY_TOTAL_OID));
            }else if (counterName.equals(Counter.Source.memory_free.name())) {
                pdu.add(createVariable(MEMORY_FREE_OID));
                pdu.add(createVariable(MEMORY_TOTAL_OID));
            }else{
                pdu.add(createVariable(counters.get(counterName)));
            }
        }
        pdu.setType(PDU.GET);
        return pdu;
    }

    private VariableBinding createVariable(String oid){
        return new VariableBinding(new OID(oid));
    }

    protected CommunityTarget createTarget(String ipAddress) {
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(SnmpCommunity.value()));
        target.setVersion(getSnmpVersion());
        target.setAddress(new UdpAddress(ipAddress + "/" + SnmpPort.value()));
        target.setRetries(2);
        target.setTimeout(SnmpTimeout.value());
        return target;
    }

    private int getSnmpVersion() {
        String version = SnmpVersion.value();
        if("1".equals(version)){
            return SnmpConstants.version1;
        }else if("2c".equals(version)){
            return SnmpConstants.version2c;
        }else if("3".equals(version)){
            return SnmpConstants.version3;
        }else{
            return SnmpConstants.version2c;
        }
    }

    @Override
    public String getConfigComponentName() {
        return SNMPClientImpl.class.getName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{ SnmpTimeout, SnmpCommunity, SnmpPort, SnmpVersion};
    }

    protected class SnmpAgentNotReadyException extends Exception {
        public SnmpAgentNotReadyException(String message) {
            super(message);
        }
    }
}
