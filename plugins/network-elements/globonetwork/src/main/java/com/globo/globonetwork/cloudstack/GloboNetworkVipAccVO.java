//Licensed to the Apache Software Foundation (ASF) under one
//or more contributor license agreements.  See the NOTICE file
//distributed with this work for additional information
//regarding copyright ownership.  The ASF licenses this file
//to you under the Apache License, Version 2.0 (the
//"License"); you may not use this file except in compliance
//with the License.  You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing,
//software distributed under the License is distributed on an
//"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//KIND, either express or implied.  See the License for the
//specific language governing permissions and limitations
//under the License.
package com.globo.globonetwork.cloudstack;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.cloudstack.api.InternalIdentity;

@Entity
@Table(name = "globonetwork_vip_acc_ref")
public class GloboNetworkVipAccVO implements InternalIdentity {

    /**
     *
     */
    private static final long serialVersionUID = -6276857308303296742L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "globonetwork_vip_id")
    private long globoNetworkVipId;

    @Column(name = "account_id")
    private long accountId;

    @Column(name = "network_id")
    private long networkId;

    public GloboNetworkVipAccVO() {
    }

    public GloboNetworkVipAccVO(long globoNetworkVipId, long accountId, long networkId) {
        this.globoNetworkVipId = globoNetworkVipId;
        this.accountId = accountId;
        this.networkId = networkId;
    }

    public long getId() {
        return id;
    }

    public long getGloboNetworkVipId() {
        return globoNetworkVipId;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getNetworkId() {
        return networkId;
    }
}
