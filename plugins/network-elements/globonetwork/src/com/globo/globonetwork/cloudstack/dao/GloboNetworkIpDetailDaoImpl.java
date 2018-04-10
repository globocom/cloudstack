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
import com.globo.globonetwork.cloudstack.GloboNetworkIpDetailVO;

@Component
@DB
public class GloboNetworkIpDetailDaoImpl extends GenericDaoBase<GloboNetworkIpDetailVO, Long> implements GloboNetworkIpDetailDao {

    final SearchBuilder<GloboNetworkIpDetailVO> byIpAddress;
    final SearchBuilder<GloboNetworkIpDetailVO> byLBEnvironmentRef;

    protected GloboNetworkIpDetailDaoImpl() {
        byIpAddress = createSearchBuilder();
        byIpAddress.and("ip_address_id", byIpAddress.entity().getIpAddressId(), Op.EQ);
        byIpAddress.done();

        byLBEnvironmentRef = createSearchBuilder();
        byLBEnvironmentRef.and("globonetwork_lbenvironment_ref_id", byLBEnvironmentRef.entity().getGloboNetworkEnvironmentRefId(), Op.EQ);
        byLBEnvironmentRef.done();
    }

    @Override
    public GloboNetworkIpDetailVO findByIp(long ipAddressId) {
        SearchCriteria<GloboNetworkIpDetailVO> sc = byIpAddress.create();
        sc.setParameters("ip_address_id", ipAddressId);
        return findOneBy(sc);
    }

    @Override
    public List<GloboNetworkIpDetailVO> listByLBEnvironmentRef(long lbEnvironmentRefId) {
        SearchCriteria<GloboNetworkIpDetailVO> sc = byLBEnvironmentRef.create();
        sc.setParameters("globonetwork_lbenvironment_ref_id", lbEnvironmentRefId);
        return listBy(sc);
    }

}
