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

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.PerformanceMonitorCommand;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.utils.Pair;
import com.cloud.vm.VMInstanceVO;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RRDAutoScaleStatsCollector extends AutoScaleStatsCollector {

    public static final Logger s_logger = Logger.getLogger(RRDAutoScaleStatsCollector.class.getName());

    @Override
    public Map<String, Double> retrieveMetrics(AutoScaleVmGroup asGroup, List<VMInstanceVO> vmList) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("[AutoScale] Collecting RRDs data.");
        }

        if(vmList == null || vmList.size() == 0){
            return null;
        }

        try {
            Map<String, String> params = this.buildPerformanceMonitorParams(asGroup, vmList);
            PerformanceMonitorCommand perfMon = new PerformanceMonitorCommand(params, 20);

            // get random hostid because all vms are in a cluster
            Long receiveHost = vmList.get(0).getHostId();
            Answer answer = _agentMgr.send(receiveHost, perfMon);

            if (answer == null || "fail".equals(answer) || !answer.getResult()) {
                s_logger.debug("[AutoScale] Failed to send data to node");
                return null;
            }

            //<vm_index>.<counter_index>:<counter_value>
            //Ex.: 1.1:0.5,2.1:0.7
            String result = answer.getDetails();
            s_logger.debug("[AutoScale] RRDs collection answer: " + result);

            // extract data
            Map<String, Double> counterSummary = new HashMap<>();
            String[] counterElements = result.split(",");
            if (counterElements.length > 0) {
                for (String string : counterElements) {
                    String[] counterValues = string.split(":");
                    String[] counter_vm = counterValues[0].split("\\.");

                    Long conditionId = Long.parseLong(params.get("con" + counter_vm[1]));
                    Double counterValue = Double.parseDouble(counterValues[1]);
                    String counterName = this.getCounterNameByCondition(conditionId);

                    // Summary of all counter by counterId key
                    if (counterSummary.get(counterName) == null) {
                        /* initialize if data is not set */
                        counterSummary.put(counterName, 0D);
                    }

                    if (Counter.Source.memory.toString().equals(counterName)) {
                        // calculate memory in percent
                        AutoScaleVmProfileVO vmProfile = _asProfileDao.findById(asGroup.getProfileId());
                        ServiceOfferingVO serviceOffering = _serviceOfferingDao.findById(vmProfile.getServiceOfferingId());
                        // get current RAM percent
                        counterValue = counterValue / serviceOffering.getRamSize();
                    }

                    counterSummary.put(counterName, counterSummary.get(counterName) + counterValue);
                }

                for(String counterName : counterSummary.keySet()){
                    counterSummary.put(counterName, counterSummary.get(counterName) / vmList.size());
                }
                return counterSummary;
            }
        } catch (Exception e){
            s_logger.error("[AutoScale] Error while reading AutoScale group " + asGroup.getId() + " Stats", e);
        }
        return null;
    }

    private  Map<String, String> buildPerformanceMonitorParams(AutoScaleVmGroup asGroup, List<VMInstanceVO> vmList){
        Map<String, String> params = new HashMap<>();
        List<AutoScaleVmGroupVmMapVO> asGroupVmVOs = _asGroupVmDao.listByGroup(asGroup.getId());
        params.put("total_vm", String.valueOf(asGroupVmVOs.size()));
        for(int i = 0; i < vmList.size() ; i++){
            params.put("vmname" + String.valueOf(i + 1), vmList.get(i).getInstanceName());
            params.put("vmid" + String.valueOf(i + 1), String.valueOf(vmList.get(i).getId()));
        }

        // setup parameters phase: duration and counter
        // list pair [counter, duration]
        List<Pair<String, Integer>> lstPair = getPairOfCounterNameAndDuration(asGroup);
        int total_counter = 0;
        String[] lstCounter = new String[lstPair.size()];
        for (int i = 0; i < lstPair.size(); i++) {
            Pair<String, Integer> pair = lstPair.get(i);
            String strCounterNames = pair.first();
            Integer duration = pair.second();

            lstCounter[i] = strCounterNames.split(",")[0];
            total_counter++;
            params.put("duration" + String.valueOf(total_counter), duration.toString());
            params.put("counter" + String.valueOf(total_counter), lstCounter[i]);
            params.put("con" + String.valueOf(total_counter), strCounterNames.split(",")[1]);
        }
        params.put("total_counter", String.valueOf(total_counter));

        return params;
    }
}