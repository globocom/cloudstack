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

import org.apache.cloudstack.api.InternalIdentity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name=("load_balancer_port_map"))
public class LoadBalancerPortMapVO implements InternalIdentity {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id")
    private Long id;

    @Column(name="lb_id")
    private long lbId;

    @Column(name="public_port")
    private int publicPort;

    @Column(name="private_port")
    private int privatePort;

    public LoadBalancerPortMapVO() {
    }

    public LoadBalancerPortMapVO(long lbId, int publicPort, int privatePort) {
        this.lbId = lbId;
        this.publicPort = publicPort;
        this.privatePort = privatePort;
    }

    @Override
    public long getId() {
        return id;
    }

    public long getLoadBalancerId() {
        return lbId;
    }

    public int getPublicPort() {
        return publicPort;
    }

    public int getPrivatePort() {
        return privatePort;
    }

    public void setLbId(long lbId) {
        this.lbId = lbId;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
