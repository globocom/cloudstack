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
import com.globo.globonetwork.cloudstack.GloboNetworkNetworkVO;

@Component
@DB
public class GloboNetworkNetworkDaoImpl extends GenericDaoBase<GloboNetworkNetworkVO, Long> implements GloboNetworkNetworkDao {

    final SearchBuilder<GloboNetworkNetworkVO> networkIdSearch;
    final SearchBuilder<GloboNetworkNetworkVO> environmentIdSearch;

    protected GloboNetworkNetworkDaoImpl() {
        super();

        networkIdSearch = createSearchBuilder();
        networkIdSearch.and("network_id", networkIdSearch.entity().getNetworkId(), Op.EQ);
        networkIdSearch.done();

        environmentIdSearch = createSearchBuilder();
        environmentIdSearch.and("napi_environment_id", environmentIdSearch.entity().getGloboNetworkEnvironmentId(), Op.EQ);
        environmentIdSearch.done();
    }

    @Override
    public GloboNetworkNetworkVO findByNetworkId(long networkId) {
        SearchCriteria<GloboNetworkNetworkVO> sc = networkIdSearch.create();
        sc.setParameters("network_id", networkId);
        return findOneBy(sc);
    }

    @Override
    public List<GloboNetworkNetworkVO> listByEnvironmentId(long napiEnvironmentId) {
        SearchCriteria<GloboNetworkNetworkVO> sc = environmentIdSearch.create();
        sc.setParameters("napi_environment_id", napiEnvironmentId);
        return listBy(sc);
    }

}
