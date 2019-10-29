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
@Table(name = "globonetwork_ip_detail")
public class GloboNetworkIpDetailVO implements InternalIdentity {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "globonetwork_ip_id")
    private long globoNetworkIpId;

    @Column(name = "user_ip_address_id")
    private long ipAddressId;

    @Column(name = "globonetwork_lbenvironment_ref_id", nullable = true)
    private Long globoNetworkEnvironmentRefId;

    @Column(name = "globonetwork_vip_id", nullable = true)
    private Long globoNetworkVipId;

    public GloboNetworkIpDetailVO() {
    }

    public GloboNetworkIpDetailVO(long ipAddressId, Long globoNetworkIpId) {
        this.ipAddressId = ipAddressId;
        this.globoNetworkIpId = globoNetworkIpId;
    }

    public long getId() {
        return id;
    }

    public long getIpAddressId() {
        return ipAddressId;
    }

    public Long getGloboNetworkVipId() {
        return globoNetworkVipId;
    }

    public void setGloboNetworkVipId(Long globoNetworkVipId) {
        this.globoNetworkVipId = globoNetworkVipId;
    }

    public Long getGloboNetworkEnvironmentRefId() {
        return globoNetworkEnvironmentRefId;
    }

    public void setGloboNetworkEnvironmentRefId(Long globoNetworkEnvironmentRefId) {
        this.globoNetworkEnvironmentRefId = globoNetworkEnvironmentRefId;
    }

    public long getGloboNetworkIpId() {
        return globoNetworkIpId;
    }

}
