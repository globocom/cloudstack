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
@Table(name=("load_balancer_options"))
public class LoadBalancerOptionsVO implements InternalIdentity {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id")
    private long id;

    @Column(name="lb_id")
    private long lbId;

    @Column(name="cache")
    private String cache;

    @Column(name="service_down_action")
    private String serviceDownAction;

    @Column(name="health_check_destination")
    private String healthCheckDestination;

    @Column(name="health_check_type")
    private String healthCheckType;

    @Column(name="expected_health_check")
    private String expectedHealthCheck;

    public LoadBalancerOptionsVO() {
    }

    public LoadBalancerOptionsVO(long lbId, String cache, String serviceDownAction, String healthCheckDestination) {
        this.lbId = lbId;
        this.cache = cache;
        this.serviceDownAction = serviceDownAction;
        this.healthCheckDestination = healthCheckDestination;
    }


    public String getHealthCheckType() {
        return healthCheckType;
    }

    public void setHealthCheckType(String healthCheckType) {
        this.healthCheckType = healthCheckType;
    }

    public String getExpectedHealthCheck() {
        return expectedHealthCheck;
    }

    public void setExpectedHealthCheck(String expectedHealthCheck) {
        this.expectedHealthCheck = expectedHealthCheck;
    }

    @Override
    public long getId() {
        return id;
    }

    public long getLoadBalancerId() {
        return lbId;
    }

    public String getCache() { return cache; }

    public String getServiceDownAction() {
        return serviceDownAction;
    }

    public String getHealthCheckDestination() {
        return healthCheckDestination;
    }

    public void setHealthCheckDestination(String healthCheckDestination) {
        this.healthCheckDestination = healthCheckDestination;
    }
}
