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
import com.google.gson.JsonObject;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads metrics from VMS via SNMP and writes them to logstash via UDP.
 * Each VM will have its metrics read by an isolated thread
 */
public class AutoScaleCounterProcessorImpl implements AutoScaleCounterProcessor, Configurable {

    @Inject
    LogStashClient logStashClient;
    @Inject
    SNMPClient snmpClient;
    ExecutorService threadExecutorPool;

    private static final ConfigKey<Integer> ThreadPoolSize = new ConfigKey<>("Advanced", Integer.class, "autoscale.counter.threadpoolsize", "10", "Auto scale counter reader thread pool size", false, ConfigKey.Scope.Global);

    public static Logger s_logger = Logger.getLogger(AutoScaleCounterProcessorImpl.class.getName());

    public AutoScaleCounterProcessorImpl(){
        threadExecutorPool = Executors.newFixedThreadPool(ThreadPoolSize.value());
    }

    @Override
    public boolean process(final AutoScaleVmGroup asGroup, final List<VirtualMachineAddress> virtualMachines, final Map<String, String> counters) {
        s_logger.debug("[AutoScale] Processing counters of virtual machines from AutoScaleGroup " + asGroup.getUuid() + ". Total of virtual machines to be processed is " + virtualMachines.size());
        for(final VirtualMachineAddress virtualMachine : virtualMachines){
            threadExecutorPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        processCounters(asGroup, virtualMachine, counters);
                    } catch(Exception e) {
                        s_logger.error("Error processing VM counters", e);
                    }
                }
            });
        }
        return true;
    }

    protected void processCounters(AutoScaleVmGroup asGroup, VirtualMachineAddress virtualMachine, Map<String, String> counters){
        Map<String, Double> metrics = snmpClient.read(virtualMachine.getIpAddress(), counters);
        if(metrics != null){
            List<String> messages = this.createLogStashMessage(asGroup, metrics, virtualMachine);
            for(String message : messages){
                boolean success = logStashClient.send(message);
                if(!success){
                    s_logger.error("Error sending message to LogStash: " + message);
                }
            }
        }
    }

    protected List<String> createLogStashMessage(AutoScaleVmGroup asGroup, Map<String, Double> metrics, VirtualMachineAddress virtualMachine){
        List<String> messages = new ArrayList<>();
        for(String metricName : metrics.keySet()){
            Double metricValue = metrics.get(metricName);
            JsonObject message = new JsonObject();
            message.addProperty("client", "cloudstack");
            message.addProperty("autoScaleGroupUuid", asGroup.getUuid());
            message.addProperty("hostname", virtualMachine.getHostName());
            message.addProperty("metric", metricName);
            message.addProperty("value", metricValue);
            message.addProperty("count", 1);
            messages.add(message.toString());
        }
        return messages;
    }

    @Override
    public String getConfigComponentName() {
        return AutoScaleCounterProcessorImpl.class.getName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{ ThreadPoolSize };
    }
}
