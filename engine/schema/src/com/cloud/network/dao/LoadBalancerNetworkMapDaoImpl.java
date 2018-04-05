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
package com.cloud.network.dao;

import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Op;

@Component
public class LoadBalancerNetworkMapDaoImpl extends GenericDaoBase<LoadBalancerNetworkMapVO, Long> implements LoadBalancerNetworkMapDao {

    final SearchBuilder<LoadBalancerNetworkMapVO> allFieldsSearch;

    public LoadBalancerNetworkMapDaoImpl() {
        allFieldsSearch = createSearchBuilder();
        allFieldsSearch.and("loadbalancerId", allFieldsSearch.entity().getLoadBalancerId(), Op.EQ);
        allFieldsSearch.and("networkid", allFieldsSearch.entity().getNetworkId(), Op.EQ);
        allFieldsSearch.done();
    }

    @Override
    public void removeByLoadBalancer(long loadBalancerId) {
        SearchCriteria<LoadBalancerNetworkMapVO> sc = allFieldsSearch.create();
        sc.setParameters("loadbalancerId", loadBalancerId);
        expunge(sc);
    }

    @Override
    public List<LoadBalancerNetworkMapVO> listByLoadBalancerId(long loadBalancerId) {
        SearchCriteria<LoadBalancerNetworkMapVO> sc = allFieldsSearch.create();
        sc.setParameters("loadbalancerId", loadBalancerId);
        return listBy(sc);
    }

    @Override
    public LoadBalancerNetworkMapVO findByLoadBalancerIdAndNetworkId(long loadBalancerId, long networkId) {
        SearchCriteria<LoadBalancerNetworkMapVO> sc = allFieldsSearch.create();
        sc.setParameters("loadbalancerId", loadBalancerId);
        sc.setParameters("networkid", networkId);
        return findOneBy(sc);
    }

    @Override
    public List<LoadBalancerNetworkMapVO> listByNetworkId(long networkId) {
        SearchCriteria<LoadBalancerNetworkMapVO> sc = allFieldsSearch.create();
        sc.setParameters("networkid", networkId);
        return listBy(sc);
    }
}
