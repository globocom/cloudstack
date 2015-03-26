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

import com.cloud.agent.AgentManager;
import com.cloud.network.as.AutoScaleManager;
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
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class AutoScaleMonitor extends ManagedContextRunnable {

    @Inject
    protected AgentManager _agentMgr;
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
    protected ServiceOfferingDao _serviceOfferingDao;
    @Inject
    protected AutoScaleStatsCollectorFactory autoScaleStatsCollectorFactory;

    private static final String SCALE_UP_ACTION = "scaleup";
    private static final String AUTO_SCALE_ENABLED = "enabled";

    public static final Logger s_logger = Logger.getLogger(AutoScaleMonitor.class.getName());

    @Override
    protected void runInContext() {
        try {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("[AutoScale] AutoScaling Monitor is running");
            }
            // list all AS VMGroups
            List<AutoScaleVmGroupVO> asGroups = _asGroupDao.listAll();
            for (AutoScaleVmGroupVO asGroup : asGroups) {
                // check group state
                if (asGroup.getState().equals(AUTO_SCALE_ENABLED) && isNative(asGroup.getId())) {
                    // check minimum vm of group
                    Integer currentVMcount = _asGroupVmDao.countByGroup(asGroup.getId());
                    if (currentVMcount < asGroup.getMinMembers()) {
                        _asManager.doScaleUp(asGroup.getId(), asGroup.getMinMembers() - currentVMcount);
                        continue;
                    }

                    // check maximum vm of group
                    if (currentVMcount > asGroup.getMaxMembers()) {
                        _asManager.doScaleDown(asGroup.getId(), currentVMcount - asGroup.getMaxMembers());
                        continue;
                    }

                    //check interval
                    long now = new Date().getTime();
                    Date lastInterval = asGroup.getLastInterval();
                    if (lastInterval != null && (now - lastInterval.getTime()) < asGroup.getInterval() * 1000) {
                        continue;
                    }

                    // update last_interval
                    asGroup.setLastInterval(new Date());
                    _asGroupDao.persist(asGroup);

                    List<VMInstanceVO> vmList = new ArrayList<>();
                    List<AutoScaleVmGroupVmMapVO> asGroupVmVOs = _asGroupVmDao.listByGroup(asGroup.getId());
                    for(AutoScaleVmGroupVmMapVO asGroupVmVO : asGroupVmVOs){
                        vmList.add(_vmInstance.findById(asGroupVmVO.getInstanceId()));
                    }

                    try{
                        AutoScaleStatsCollector statsCollector = autoScaleStatsCollectorFactory.getStatsCollector();
                        Map<String, Double> counterSummary = statsCollector.retrieveMetrics(asGroup, vmList);

                        if(counterSummary != null && !counterSummary.keySet().isEmpty()) {
                            String scaleAction = this.getAutoScaleAction(counterSummary, asGroup, currentVMcount);
                            if (scaleAction != null) {
                                s_logger.debug("[AutoScale] Doing scale action: " + scaleAction + " for group " + asGroup.getId());

                                if (scaleAction.equals(SCALE_UP_ACTION)) {
                                    _asManager.doScaleUp(asGroup.getId(), 1);
                                } else {
                                    _asManager.doScaleDown(asGroup.getId(), 1);
                                }
                            }
                        }
                    }catch (Exception e){
                        s_logger.error("[AutoScale] Error while processing AutoScale group "+ asGroup.getId() +" Stats", e);
                    }
                }
            }
        } catch (Throwable t) {
            s_logger.error("[AutoScale] Error trying to monitor autoscaling", t);
        }
    }

    private boolean isNative(long groupId) {
        List<AutoScaleVmGroupPolicyMapVO> vos = _asGroupPolicyDao.listByVmGroupId(groupId);
        for (AutoScaleVmGroupPolicyMapVO vo : vos) {
            List<AutoScalePolicyConditionMapVO> ConditionPolicies = _asConditionMapDao.findByPolicyId(vo.getPolicyId());
            for (AutoScalePolicyConditionMapVO ConditionPolicy : ConditionPolicies) {
                ConditionVO condition = _asConditionDao.findById(ConditionPolicy.getConditionId());
                CounterVO counter = _asCounterDao.findById(condition.getCounterid());
                if (counter.getSource() == Counter.Source.cpu || counter.getSource() == Counter.Source.memory)
                    return true;
            }
        }
        return false;
    }

    private String getAutoScaleAction(Map<String, Double> counterSummary, AutoScaleVmGroupVO asGroup, long currentVMcount) {
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
                    // list all condition of this policy
                    boolean isPolicyValid = true;
                    List<ConditionVO> conditions = getConditionsByPolicyId(policy.getId());

                    if (conditions != null && !conditions.isEmpty()) {
                        // check whole conditions of this policy
                        for (ConditionVO conditionVO : conditions) {
                            long thresholdValue = conditionVO.getThreshold();
                            Double thresholdPercent = (double) thresholdValue / 100;
                            CounterVO counter = _asCounterDao.findById(conditionVO.getCounterid());

                            Double avg = counterSummary.get(counter.getSource().name());
                            if(avg == null){
                                isPolicyValid = false;
                                break;
                            }
                            Condition.Operator op = conditionVO.getRelationalOperator();

                            boolean isConditionValid = ((op == Condition.Operator.EQ) && (thresholdPercent.equals(avg)))
                                    || ((op == Condition.Operator.GE) && (avg >= thresholdPercent))
                                    || ((op == Condition.Operator.GT) && (avg > thresholdPercent))
                                    || ((op == Condition.Operator.LE) && (avg <= thresholdPercent))
                                    || ((op == Condition.Operator.LT) && (avg < thresholdPercent));

                            if (!isConditionValid) {
                                isPolicyValid = false;
                                break;
                            }
                        }
                        if (isPolicyValid) {
                            return policy.getAction();
                        }
                    }
                }
            }
        }
        return null;
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
}
