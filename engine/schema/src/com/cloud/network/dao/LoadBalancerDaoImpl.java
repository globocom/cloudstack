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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.ejb.Local;
import javax.inject.Inject;

import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.network.rules.FirewallRule.State;
import com.cloud.network.rules.LoadBalancerContainer.Scheme;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Op;

@Component
@Local(value = {LoadBalancerDao.class})
public class LoadBalancerDaoImpl extends GenericDaoBase<LoadBalancerVO, Long> implements LoadBalancerDao {
    public static final Logger s_logger = Logger.getLogger(LoadBalancerDaoImpl.class);

    private final SearchBuilder<LoadBalancerVO> ListByIp;
    protected final SearchBuilder<LoadBalancerVO> TransitionStateSearch;

    @Inject
    protected FirewallRulesCidrsDao _portForwardingRulesCidrsDao;

    protected LoadBalancerDaoImpl() {
        ListByIp = createSearchBuilder();
        ListByIp.and("ipAddressId", ListByIp.entity().getSourceIpAddressId(), SearchCriteria.Op.EQ);
        ListByIp.and("networkId", ListByIp.entity().getNetworkId(), SearchCriteria.Op.EQ);
        ListByIp.and("scheme", ListByIp.entity().getScheme(), SearchCriteria.Op.EQ);
        ListByIp.done();

        TransitionStateSearch = createSearchBuilder();
        TransitionStateSearch.and("networkId", TransitionStateSearch.entity().getNetworkId(), Op.EQ);
        TransitionStateSearch.and("state", TransitionStateSearch.entity().getState(), Op.IN);
        TransitionStateSearch.and("scheme", TransitionStateSearch.entity().getScheme(), Op.EQ);
        TransitionStateSearch.done();
    }

    @Override
    public List<LoadBalancerVO> listByIpAddress(long ipAddressId) {
        SearchCriteria<LoadBalancerVO> sc = ListByIp.create();
        sc.setParameters("ipAddressId", ipAddressId);
        return listBy(sc);
    }

    @Override
    public List<LoadBalancerVO> listByNetworkIdAndScheme(long networkId, Scheme scheme) {
        SearchCriteria<LoadBalancerVO> sc = ListByIp.create();
        sc.setParameters("networkId", networkId);
        sc.setParameters("scheme", scheme);
        return listBy(sc);
    }

    @Override
    public List<LoadBalancerVO> listInTransitionStateByNetworkIdAndScheme(long networkId, Scheme scheme) {
        SearchCriteria<LoadBalancerVO> sc = TransitionStateSearch.create();
        sc.setParameters("networkId", networkId);
        sc.setParameters("state", State.Add.toString(), State.Revoke.toString());
        sc.setParameters("scheme", scheme);
        return listBy(sc);
    }


    @Override
    public List<LoadBalancerVO> listLinkables(String uuid, long networkEnvId, long accountId) {
        try {
            TransactionLegacy txn = TransactionLegacy.currentTxn();
            String sql = "SELECT f.uuid, l.name, ref.globonetwork_environment_id, f.* FROM firewall_rules f " +
                    " INNER JOIN load_balancing_rules l on f.id = l.id " +
                    " INNER JOIN globonetwork_network_ref ref on f.network_id = ref.network_id " +
                    " WHERE f.uuid != ? " +
                    " AND f.state in ('Active', 'Add') " +
                    " AND ref.globonetwork_environment_id = ? " +
                    " AND f.account_id = ?; ";
            PreparedStatement pstmt = txn.prepareAutoCloseStatement(sql);
            pstmt.setString(1, uuid);
            pstmt.setLong(2, networkEnvId);
            pstmt.setLong(3, accountId);

            List<LoadBalancerVO> result = new ArrayList<>();

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                LoadBalancerVO lb = new LoadBalancerVO();
                lb.setUuid(rs.getString(1));
                lb.setName(rs.getString(2));
                result.add(lb);
            }
            return result;
        } catch (Exception e) {
            s_logger.error("Error trying list linkable load balancers, uuid: " + uuid + ", networkEnvId: " + networkEnvId + ", accountId: ", e);
            throw new CloudRuntimeException("Unexpected error during list linkable load balalncers.", e);
        }

    }

}
