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

import com.cloud.network.as.AutoScaleManager;
import com.cloud.network.as.AutoScalePolicy;
import com.cloud.network.as.AutoScalePolicyConditionMapVO;
import com.cloud.network.as.AutoScalePolicyVO;
import com.cloud.network.as.AutoScaleStatsCollector;
import com.cloud.network.as.AutoScaleStatsCollectorFactory;
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
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class AutoScaleMonitor extends ManagedContextRunnable implements Configurable{

    @Inject
    protected AutoScaleVmGroupDao _asGroupDao;
    @Inject
    protected AutoScaleVmGroupVmMapDao _asGroupVmDao;
    @Inject
    protected AutoScaleManager _asManager;
    @Inject
    protected VMInstanceDao _vmInstance;
    @Inject
    protected AutoScaleVmGroupPolicyMapDao _asGroupPolicyDao;
    @Inject
    protected AutoScalePolicyDao _asPolicyDao;
    @Inject
    protected AutoScalePolicyConditionMapDao _asConditionMapDao;
    @Inject
    protected ConditionDao _asConditionDao;
    @Inject
    protected CounterDao _asCounterDao;
    @Inject
    protected AutoScaleStatsCollectorFactory autoScaleStatsCollectorFactory;

    protected ExecutorService threadExecutor;

    private static final String SCALE_UP_ACTION = "scaleup";
    private static final String AUTO_SCALE_ENABLED = "enabled";
    private static final Logger s_logger = Logger.getLogger(AutoScaleMonitor.class.getName());

    private static final ConfigKey<Integer> ThreadPoolSize = new ConfigKey<>("Advanced", Integer.class, "autoscale.monitor.threadpoolsize", "10", "Auto scale monitor thread pool size", true, ConfigKey.Scope.Global);

    public AutoScaleMonitor(){
        threadExecutor = Executors.newFixedThreadPool(ThreadPoolSize.value());
    }

    @Override
    protected void runInContext() {
        try {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("[AutoScale] AutoScaling Monitor is running");
            }

            for (final AutoScaleVmGroupVO asGroup : _asGroupDao.listAllNotLocked()) {
                threadExecutor.execute(new Runnable() {
                    public void run() {
                        if (s_logger.isDebugEnabled()) {
                            s_logger.debug("[AutoScale] Processing AutoScaleGroup id: " + asGroup.getId());
                        }
                        processAutoScaleGroup(asGroup);
                    }
                });
            }
        } catch (Throwable t) {
            s_logger.error("[AutoScale] Error trying to monitor auto scale", t);
        }
    }

    protected void processAutoScaleGroup(AutoScaleVmGroupVO asGroup) {
        try {
            //refresh to have the most updated version of asGroup,
            //as it can become outdated while the list is iterated
            if (asGroup.getUuid() != null)
                s_logger.debug("[AutoScale] Started processing group " + asGroup.getUuid());
            asGroup = _asGroupDao.findById(asGroup.getId());

            if(asGroup.isLocked() || !asGroup.getState().equals(AUTO_SCALE_ENABLED) || !isNative(asGroup.getId()))
                return;

            lockAutoScaleGroup(asGroup);

            // check minimum vm of group
            Integer currentVmCount = _asGroupVmDao.countByGroup(asGroup.getId());
            if (currentVmCount < asGroup.getMinMembers()) {
                _asManager.doScaleUp(asGroup.getId(), asGroup.getMinMembers() - currentVmCount);
                return;
            }

            // check maximum vm of group
            if (currentVmCount > asGroup.getMaxMembers()) {
                _asManager.doScaleDown(asGroup.getId(), currentVmCount - asGroup.getMaxMembers());
                return;
            }

            if (minimumIntervalNotMet(asGroup)) return;

            updateLasIntervalFor(asGroup);

            AutoScaleStatsCollector statsCollector = autoScaleStatsCollectorFactory.getStatsCollector();
            Map<String, Double> counterSummary = statsCollector.retrieveMetrics(asGroup, this.getVirtualMachinesFor(asGroup));

            if (counterSummaryNotEmpty(counterSummary)) {
                AutoScalePolicy policy = this.getAutoScalePolicy(counterSummary, asGroup);
                if (policy != null) {
                    s_logger.debug("[AutoScale] Doing scale action: " + policy.getAction() + " for group " + asGroup.getId());

                    if (policy.getAction().equals(SCALE_UP_ACTION)) {
                        _asManager.doScaleUp(asGroup.getId(), policy.getStep());
                    } else {
                        _asManager.doScaleDown(asGroup.getId(), policy.getStep());
                    }
                }
            }
        }catch(Exception ex){
            s_logger.error("[AutoScale] Error while processing AutoScale group id" + asGroup.getId(), ex);
        }finally{
            unlockAutoScaleGroup(asGroup);
        }
    }

    private boolean minimumIntervalNotMet(AutoScaleVmGroupVO asGroup) {
        long now = new Date().getTime();
        Date lastInterval = asGroup.getLastInterval();
        return (lastInterval != null && (now - lastInterval.getTime()) < asGroup.getInterval() * 1000);
    }


    private boolean counterSummaryNotEmpty(Map<String, Double> counterSummary) {
        return counterSummary != null && !counterSummary.keySet().isEmpty();
    }

    private void updateLasIntervalFor(AutoScaleVmGroupVO asGroup) {
        asGroup.setLastInterval(new Date());
        _asGroupDao.persist(asGroup);
    }

    private List<VMInstanceVO> getVirtualMachinesFor(AutoScaleVmGroupVO asGroup) {
        List<VMInstanceVO> vmList = new ArrayList<>();
        List<AutoScaleVmGroupVmMapVO> asGroupVmVOs = _asGroupVmDao.listByGroup(asGroup.getId());
        for(AutoScaleVmGroupVmMapVO asGroupVmVO : asGroupVmVOs){
            vmList.add(_vmInstance.findById(asGroupVmVO.getInstanceId()));
        }
        return vmList;
    }

    private void lockAutoScaleGroup(AutoScaleVmGroupVO autoScaleVmGroup){
        autoScaleVmGroup.lock();
        _asGroupDao.persist(autoScaleVmGroup);
    }

    private void unlockAutoScaleGroup(AutoScaleVmGroupVO autoScaleVmGroup){
        autoScaleVmGroup.unlock();
        _asGroupDao.persist(autoScaleVmGroup);
    }

    private boolean isNative(long groupId) {
        List<AutoScaleVmGroupPolicyMapVO> vos = _asGroupPolicyDao.listByVmGroupId(groupId);
        for (AutoScaleVmGroupPolicyMapVO vo : vos) {
            List<AutoScalePolicyConditionMapVO> ConditionPolicies = _asConditionMapDao.findByPolicyId(vo.getPolicyId());
            for (AutoScalePolicyConditionMapVO ConditionPolicy : ConditionPolicies) {
                ConditionVO condition = _asConditionDao.findById(ConditionPolicy.getConditionId());
                CounterVO counter = _asCounterDao.findById(condition.getCounterid());
                List<Counter.Source> notNativesSources = Arrays.asList(Counter.Source.snmp, Counter.Source.netscaler);
                if (notNativesSources.contains(counter.getSource()))
                    return false;
            }
        }
        return true;
    }

    private AutoScalePolicy getAutoScalePolicy(Map<String, Double> counterSummary, AutoScaleVmGroupVO asGroup) {
        List<AutoScaleVmGroupPolicyMapVO> asGroupPolicyMap = _asGroupPolicyDao.listByVmGroupId(asGroup.getId());
        if (asGroupPolicyMap == null || asGroupPolicyMap.size() == 0)
            return null;

        for (AutoScaleVmGroupPolicyMapVO asGroupPolicy : asGroupPolicyMap) {
            AutoScalePolicyVO policy = _asPolicyDao.findById(asGroupPolicy.getPolicyId());
            if (policy != null) {
                long quietTime = (long) policy.getQuietTime() * 1000;
                Date quietTimeDate = policy.getLastQuiteTime();
                long lastQuietTime = 0L;
                if (quietTimeDate != null) {
                    lastQuietTime = policy.getLastQuiteTime().getTime();
                }
                long now = (new Date()).getTime();

                // check quite time for this policy
                if (now - lastQuietTime >= quietTime) {
                    List<ConditionVO> conditions = getConditionsByPolicyId(policy.getId());

                    if (conditions != null && !conditions.isEmpty()) {
                        if (isPolicyValid(counterSummary, conditions, policy.getLogicalOperator())) {
                            return policy;
                        }
                    }
                }
            }
        }
        return null;
    }

    protected boolean isPolicyValid(Map<String, Double> counterSummary, List<ConditionVO> conditions, AutoScalePolicy.LogicalOperator logicalOperator) {
        Boolean isPolicyValid = null;
        for (ConditionVO conditionVO : conditions) {
            long counterThresholdValue = conditionVO.getThreshold();
            CounterVO counter = _asCounterDao.findById(conditionVO.getCounterid());

            Double counterAverageValue = counterSummary.get(counter.getSource().name());
            if(counterAverageValue == null){
                return false;
            }

            boolean isConditionValid = isConditionValid(counterThresholdValue, counterAverageValue, conditionVO.getRelationalOperator());

            if(isPolicyValid == null){
                isPolicyValid = isConditionValid;
            }

            if(logicalOperator == AutoScalePolicy.LogicalOperator.OR){
                isPolicyValid = isPolicyValid || isConditionValid;
            }else {
                isPolicyValid = isPolicyValid && isConditionValid;
            }
        }
        return isPolicyValid;
    }

    protected boolean isConditionValid(long counterThresholdValue, Double counterAverageValue, Condition.Operator operator) {
        if(operator == Condition.Operator.EQ)
            return counterAverageValue == counterThresholdValue;
        if(operator == Condition.Operator.GE)
            return counterAverageValue >=  counterThresholdValue;
        if(operator == Condition.Operator.GT)
            return counterAverageValue > counterThresholdValue;
        if(operator == Condition.Operator.LE)
            return counterAverageValue <= counterThresholdValue;
        if(operator == Condition.Operator.LT)
            return counterAverageValue < counterThresholdValue;
        return false;
    }

    private List<ConditionVO> getConditionsByPolicyId(long policyId) {
        List<AutoScalePolicyConditionMapVO> conditionMap = _asConditionMapDao.findByPolicyId(policyId);
        if (conditionMap == null || conditionMap.isEmpty())
            return null;

        List<ConditionVO> conditions = new ArrayList<>();
        for (AutoScalePolicyConditionMapVO policyConditionMap : conditionMap) {
            conditions.add(_asConditionDao.findById(policyConditionMap.getConditionId()));
        }

        return conditions;
    }

    @Override
    public String getConfigComponentName() {
        return AutoScaleMonitor.class.getName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{ ThreadPoolSize };
    }
}
