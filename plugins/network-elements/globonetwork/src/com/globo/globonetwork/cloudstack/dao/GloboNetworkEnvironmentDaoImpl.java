/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.globo.globonetwork.cloudstack.dao;

import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Op;
import com.globo.globonetwork.cloudstack.GloboNetworkEnvironmentVO;

@Component
@DB
public class GloboNetworkEnvironmentDaoImpl extends GenericDaoBase<GloboNetworkEnvironmentVO, Long> implements GloboNetworkEnvironmentDao {

    final SearchBuilder<GloboNetworkEnvironmentVO> physicalNetworkIdSearch;
    final SearchBuilder<GloboNetworkEnvironmentVO> physicalNetworkIdAndEnvironmentIdSearch;

    protected GloboNetworkEnvironmentDaoImpl() {
        super();

        physicalNetworkIdSearch = createSearchBuilder();
        physicalNetworkIdSearch.and("physical_network_id", physicalNetworkIdSearch.entity().getPhysicalNetworkId(), Op.EQ);
        physicalNetworkIdSearch.done();

        physicalNetworkIdAndEnvironmentIdSearch = createSearchBuilder();
        physicalNetworkIdAndEnvironmentIdSearch.and("physical_network_id", physicalNetworkIdAndEnvironmentIdSearch.entity().getPhysicalNetworkId(), Op.EQ);
        physicalNetworkIdAndEnvironmentIdSearch.and("napi_environment_id", physicalNetworkIdAndEnvironmentIdSearch.entity().getGloboNetworkEnvironmentId(), Op.EQ);
        physicalNetworkIdAndEnvironmentIdSearch.done();

    }

    @Override
    public List<GloboNetworkEnvironmentVO> listByPhysicalNetworkId(long physicalNetworkId) {
        SearchCriteria<GloboNetworkEnvironmentVO> sc = physicalNetworkIdSearch.create();
        sc.setParameters("physical_network_id", physicalNetworkId);
        return listBy(sc);
    }

    @Override
    public GloboNetworkEnvironmentVO findByPhysicalNetworkIdAndEnvironmentId(long physicalNetworkId, long environmentId) {
        SearchCriteria<GloboNetworkEnvironmentVO> sc = physicalNetworkIdAndEnvironmentIdSearch.create();
        sc.setParameters("physical_network_id", physicalNetworkId);
        sc.setParameters("napi_environment_id", environmentId);
        return findOneBy(sc);
    }

}
