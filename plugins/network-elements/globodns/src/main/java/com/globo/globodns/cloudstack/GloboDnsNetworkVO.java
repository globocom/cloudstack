package com.globo.globodns.cloudstack;

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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.cloudstack.api.InternalIdentity;

@Entity
@Table(name = "dnsapi_network_ref")
public class GloboDnsNetworkVO implements InternalIdentity {

    private static final long serialVersionUID = 6194042778939974188L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "network_id")
    private long networkId;

    @Column(name = "dnsapi_domain_id")
    private long globoDnsDomainId;

    @Column(name = "dnsapi_reverse_domain_id")
    private long globoDnsReverseDomainId;

    public GloboDnsNetworkVO() {
    }

    public GloboDnsNetworkVO(long networkId, long globoDnsDomainId, long globoDnsReverseDomainId) {
        this.networkId = networkId;
        this.globoDnsDomainId = globoDnsDomainId;
        this.globoDnsReverseDomainId = globoDnsReverseDomainId;
    }

    public long getId() {
        return id;
    }

    public long getNetworkId() {
        return networkId;
    }

    public long getGloboDnsDomainId() {
        return globoDnsDomainId;
    }

    public long getGloboDnsReverseDomainId() {
        return globoDnsReverseDomainId;
    }
}
