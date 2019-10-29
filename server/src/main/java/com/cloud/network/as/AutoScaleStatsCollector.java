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

import com.cloud.agent.AgentManager;
import com.cloud.network.as.dao.AutoScalePolicyConditionMapDao;
import com.cloud.network.as.dao.AutoScalePolicyDao;
import com.cloud.network.as.dao.AutoScaleVmGroupPolicyMapDao;
import com.cloud.network.as.dao.AutoScaleVmGroupVmMapDao;
import com.cloud.network.as.dao.AutoScaleVmProfileDao;
import com.cloud.network.as.dao.ConditionDao;
import com.cloud.network.as.dao.CounterDao;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.utils.Pair;
import com.cloud.vm.VMInstanceVO;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

public abstract class AutoScaleStatsCollector {

    @Inject
    protected AgentManager _agentMgr;
    @Inject
    protected AutoScaleVmGroupVmMapDao _asGroupVmDao;
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
    protected AutoScaleVmProfileDao _asProfileDao;
    @Inject
    protected ServiceOfferingDao _serviceOfferingDao;

    /**
     * @return Map - Map containing the sum of each counter. the key is the counter name and the value the respective value
     */
    public abstract Map<String, Double> retrieveMetrics(AutoScaleVmGroup asGroup, List<VMInstanceVO> vmList);

    protected List<Pair<String, Integer>> getPairOfCounterNameAndDuration(AutoScaleVmGroup groupVo) {
        if (groupVo == null)
            return null;

        List<Pair<String, Integer>> result = new ArrayList<>();
        //list policy map
        List<AutoScaleVmGroupPolicyMapVO> groupPolicyMap = _asGroupPolicyDao.listByVmGroupId(groupVo.getId());
        if (groupPolicyMap == null)
            return null;

        List<String> countersAdded = new ArrayList<>();
        for (AutoScaleVmGroupPolicyMapVO gpMap : groupPolicyMap) {
            //get duration
            AutoScalePolicyVO policyVo = _asPolicyDao.findById(gpMap.getPolicyId());
            Integer duration = policyVo.getDuration();
            //get collection of counter name
            List<AutoScalePolicyConditionMapVO> policyConditionMap = _asConditionMapDao.findByPolicyId(policyVo.getId());
            for (AutoScalePolicyConditionMapVO pcMap : policyConditionMap) {
                String counterName = getCounterNameByCondition(pcMap.getConditionId());
                //avoid the same counter being added more than once
                if(!countersAdded.contains(counterName)){
                    countersAdded.add(counterName);
                    counterName += "," + pcMap.getConditionId();
                    result.add(new Pair<>(counterName, duration));
                }
            }
        }

        return result;
    }

    protected String getCounterNameByCondition(long conditionId) {
        ConditionVO condition = _asConditionDao.findById(conditionId);
        if (condition == null)
            return "";

        long counterId = condition.getCounterid();
        CounterVO counter = _asCounterDao.findById(counterId);
        if (counter == null)
            return "";

        return counter.getSource().toString();
    }
}
