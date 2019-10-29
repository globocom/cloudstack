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

import org.apache.cloudstack.api.BaseResponse;

import java.util.List;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

public class GloboNetworkCapabilitiesResponse extends BaseResponse {

    @SerializedName("domainSuffix")
    @Param(description = "Domain suffix of all networks")
    private String domainSuffix;

    @SerializedName("supportCustomNetworkDomain")
    @Param(description = "user can customize network domain")
    private Boolean supportCustomNetworkDomain;

    @SerializedName("enabled")
    @Param(description = "is GloboNetwork provider enabled in any zone")
    private Boolean enabled;

    @SerializedName("allowedLbSuffixes")
    @Param(description = "allowed domain suffixes for load balancers in GloboNetwork")
    private List<String> allowedLbSuffixes;

    public String getDomainSuffix() {
        return domainSuffix;
    }

    public void setDomainSuffix(String domainSuffix) {
        this.domainSuffix = domainSuffix;
    }

    public Boolean getSupportCustomNetworkDomain() {
        return supportCustomNetworkDomain;
    }

    public void setSupportCustomNetworkDomain(Boolean supportCustomNetworkDomain) {
        this.supportCustomNetworkDomain = supportCustomNetworkDomain;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedLbSuffixes() {
        return allowedLbSuffixes;
    }

    public void setAllowedLbSuffixes(List<String> allowedLbSuffixes) {
        this.allowedLbSuffixes = allowedLbSuffixes;
    }

}
