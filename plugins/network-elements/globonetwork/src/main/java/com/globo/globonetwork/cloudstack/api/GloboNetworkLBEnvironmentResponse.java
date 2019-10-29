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
package com.globo.globonetwork.cloudstack.api;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.serializer.Param;
import com.globo.globonetwork.cloudstack.GloboNetworkLoadBalancerEnvironment;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = GloboNetworkLoadBalancerEnvironment.class)
public class GloboNetworkLBEnvironmentResponse extends BaseResponse {

    @SerializedName("id")
    @Param(description = "ID of the relationship between environment ID and LB network")
    private Long id;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "name of the LB network")
    private String name;

    @SerializedName("globonetworkenvironmentid")
    @Param(description = "id of environment in GloboNetwork")
    private Long globoNetworkEnvironmentId;

    @SerializedName("globonetworklbenvironmentid")
    @Param(description = "id of LB environment in GloboNetwork")
    private Long globoNetworkLBEnvironmentId;

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setGloboNetworkEnvironmentId(Long globoNetworkEnvironmentId) {
        this.globoNetworkEnvironmentId = globoNetworkEnvironmentId;
    }

    public void setGloboNetworkLBEnvironmentId(Long globoNetworkLBEnvironmentId) {
        this.globoNetworkLBEnvironmentId = globoNetworkLBEnvironmentId;
    }

}
