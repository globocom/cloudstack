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
package com.globo.globodns.cloudstack.dao;


import org.springframework.stereotype.Component;

import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Op;
import com.globo.globodns.cloudstack.GloboDnsVirtualMachineVO;

@Component
@DB
public class GloboDnsVirtualMachineDaoImpl extends GenericDaoBase<GloboDnsVirtualMachineVO, Long> implements GloboDnsVirtualMachineDao {

    protected final SearchBuilder<GloboDnsVirtualMachineVO> VmAndDomainSearch;

    public GloboDnsVirtualMachineDaoImpl() {
        VmAndDomainSearch = createSearchBuilder();
        VmAndDomainSearch.and("vmId", VmAndDomainSearch.entity().getVirtualMachineId(), Op.EQ);
        VmAndDomainSearch.and("domainId", VmAndDomainSearch.entity().getGloboDnsDomainId(), Op.EQ);
        VmAndDomainSearch.done();
    }

    @Override
    public GloboDnsVirtualMachineVO findByVirtualMachineIdAndDomainId(Long vmId, Long globoDnsDomainId) {
        SearchCriteria<GloboDnsVirtualMachineVO> sc = VmAndDomainSearch.create();
        sc.addAnd("vmId", Op.EQ, vmId);
//      sc.addAnd("domainId", Op.EQ, globoDnsDomainId);
//      sc.setParameters("vmId", vmId);
        sc.setParameters("domainId", globoDnsDomainId);
        return findOneBy(sc);
    }
}
