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
@Table(name = "globonetwork_loadbalancer_environments")
public class GloboNetworkLoadBalancerEnvironment implements InternalIdentity {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "name")
    private String name;

    @Column(name = "globonetwork_environment_ref_id")
    private long globoNetworkEnvironmentRefId;

    @Column(name = "globonetwork_lb_environment_id")
    private long globoNetworkLoadBalancerEnvironmentId;

    public GloboNetworkLoadBalancerEnvironment() {
    }

    public GloboNetworkLoadBalancerEnvironment(String name, long globoNetworkEnvironmentId, long globoNetworkLoadBalancerEnvironmentId) {
        this.name = name;
        this.globoNetworkEnvironmentRefId = globoNetworkEnvironmentId;
        this.globoNetworkLoadBalancerEnvironmentId = globoNetworkLoadBalancerEnvironmentId;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getGloboNetworkEnvironmentRefId() {
        return globoNetworkEnvironmentRefId;
    }

    public long getGloboNetworkLoadBalancerEnvironmentId() {
        return globoNetworkLoadBalancerEnvironmentId;
    }

}
