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

import com.cloud.network.as.AutoScalePolicyConditionMapVO;
import com.cloud.network.as.AutoScalePolicyVO;
import com.cloud.network.as.AutoScaleVmGroupPolicyMapVO;
import com.cloud.network.as.AutoScaleVmGroupVO;
import com.cloud.network.as.AutoScaleVmGroupVmMapVO;
import com.cloud.network.as.CounterVO;
import com.cloud.network.as.AutoScaleCounterProcessor;
import com.cloud.network.as.dao.AutoScalePolicyConditionMapDao;
import com.cloud.network.as.dao.AutoScalePolicyDao;
import com.cloud.network.as.dao.AutoScaleVmGroupDao;
import com.cloud.network.as.dao.AutoScaleVmGroupPolicyMapDao;
import com.cloud.network.as.dao.AutoScaleVmGroupVmMapDao;
import com.cloud.network.as.dao.ConditionDao;
import com.cloud.network.as.dao.CounterDao;
import com.cloud.utils.net.MacAddress;
import com.cloud.vm.NicVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AutoScaleCounterCollector extends ManagedContextRunnable implements Configurable {

    @Inject
    protected AutoScaleVmGroupDao autoScaleVmGroupDao;
    @Inject
    protected AutoScaleVmGroupVmMapDao autoScaleVmMapDao;
    @Inject
    protected VMInstanceDao vmInstanceDao;
    @Inject
    protected NicDao nicDao;
    @Inject
    protected AutoScaleVmGroupPolicyMapDao asPolicyMapDao;
    @Inject
    protected AutoScalePolicyDao asPolicyDao;
    @Inject
    protected AutoScalePolicyConditionMapDao asPolicyConditionMapDao;
    @Inject
    protected ConditionDao conditionDao;
    @Inject
    protected CounterDao counterDao;
    @Inject
    protected AutoScaleCounterProcessor counterProcessor;
    @Inject
    protected ConfigurationDao configurationDao;

    private static final String ELASTIC_SEARCH_DATA_SOURCE = "elasticsearch";
    protected static ConfigKey<Long> managementServer = new ConfigKey<>("Advanced", Long.class, "autoscale.counter_agent.mgmtserverid", "0", "ID of the management server in which the SNMP agent will run", true, ConfigKey.Scope.Global);
    protected static final long currentManagementServerId = MacAddress.getMacAddress().toLong();

    public static Logger s_logger = Logger.getLogger(AutoScaleCounterCollector.class.getName());

    @Override
    protected void runInContext() {
        try {
            if (elasticSearchDataSourceEnabled() && isConfiguredManagementServer()) {
                List<AutoScaleVmGroupVO> autoScaleVmGroups = this.listEnabledAutoScaleGroups();
                for (AutoScaleVmGroupVO asGroup : autoScaleVmGroups) {
                    s_logger.debug("[AutoScale] Reading VM stats from AutoScaleGroup #" + asGroup.getId());
                    try {
                        List<VirtualMachineAddress> virtualMachines = this.getVirtualMachinesFrom(asGroup);
                        if (!virtualMachines.isEmpty()) {
                            counterProcessor.process(asGroup, virtualMachines, this.getCountersFrom(asGroup));
                        }
                    } catch (Exception ex) {
                        s_logger.error("[AutoScale] Error while reading AutoScaleGroup #" + asGroup.getId(), ex);
                    }
                }
            } else {
                s_logger.debug("[AutoScale] Elasticsearch stats datasource not enabled or management server not configured");
            }
        }catch(Exception ex){
            s_logger.error("[AutoScale] Error processing VM counters, skipping to next execution");
        }
    }

    private boolean isConfiguredManagementServer() {
        return currentManagementServerId == managementServer.value();
    }

    private boolean elasticSearchDataSourceEnabled() {
        return ELASTIC_SEARCH_DATA_SOURCE.equals(configurationDao.findByName("autoscale.stats.datasource").getValue());
    }

    protected List<AutoScaleVmGroupVO> listEnabledAutoScaleGroups(){
        return autoScaleVmGroupDao.listAllEnabled();
    }

    protected List<VirtualMachineAddress> getVirtualMachinesFrom(AutoScaleVmGroupVO asGroup) {
        List<VirtualMachineAddress> vmList = new ArrayList<>();
        List<AutoScaleVmGroupVmMapVO> autoScaleVmGroupVmMapVOs = autoScaleVmMapDao.listByGroup(asGroup.getId());
        if(autoScaleVmGroupVmMapVOs != null) {
            for (AutoScaleVmGroupVmMapVO asGroupVmVO : autoScaleVmGroupVmMapVOs) {
                VMInstanceVO vmInstanceVO = vmInstanceDao.findById(asGroupVmVO.getInstanceId());
                String ipAddress = getIpAddressesFrom(vmInstanceVO);
                if(ipAddress != null) {
                    vmList.add(new VirtualMachineAddress(ipAddress, vmInstanceVO.getHostName()));
                }
            }
        }
        return vmList;
    }

    protected String getIpAddressesFrom(VMInstanceVO vm){
        NicVO nic;
        List<NicVO> nicList = nicDao.listByVmId(vm.getId());

        if(nicList.size() == 1){
            nic = nicList.get(0);
        }else{
            nic = getNonDefaultNic(nicList);
        }

        if(nic == null)
            return null;

        return nic.getIPv4Address() != null ? nic.getIPv4Address() : nic.getIPv6Address();
    }

    private NicVO getNonDefaultNic(List<NicVO> nicList) {
        for(NicVO currentNic : nicList){
            if(!currentNic.isDefaultNic()){
                return currentNic;
            }
        }
        return null;
    }

    protected Map<String, String> getCountersFrom(AutoScaleVmGroupVO asGroup){
        Map<String, String> counters = new HashMap<>();
        for(AutoScaleVmGroupPolicyMapVO autoScaleVmGroupPolicyMapVO : asPolicyMapDao.listByVmGroupId(asGroup.getId())){
            for(AutoScalePolicyConditionMapVO asPolicyCondition : findPolicyConditionMap(autoScaleVmGroupPolicyMapVO)){
                CounterVO counter = findCounter(asPolicyCondition);
                counters.put(counter.getSource().name(), counter.getValue());
            }
        }
        return counters;
    }

    private List<AutoScalePolicyConditionMapVO> findPolicyConditionMap(AutoScaleVmGroupPolicyMapVO autoScaleVmGroupPolicyMapVO) {
        AutoScalePolicyVO autoScalePolicy = asPolicyDao.findById(autoScaleVmGroupPolicyMapVO.getPolicyId());
        return asPolicyConditionMapDao.findByPolicyId(autoScalePolicy.getId());
    }

    private CounterVO findCounter(AutoScalePolicyConditionMapVO asPolicyCondition) {
        return counterDao.findById(conditionDao.findById(asPolicyCondition.getConditionId()).getCounterid());
    }

    @Override
    public String getConfigComponentName() {
        return AutoScaleCounterCollector.class.getName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{ managementServer };
    }
}

