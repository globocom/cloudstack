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
package com.globo.globonetwork.cloudstack.response;

import java.util.ArrayList;
import java.util.List;

import org.apache.cloudstack.api.BaseResponse;

public class GloboNetworkVipExternalResponse extends BaseResponse {

    private Long id;

    private String name;

    private String ip;

    private List<String> networkids;

    private String cache;

    private String method;

    private String persistence;

    private String healthchecktype;

    private String healthcheck;

    private Integer maxconn;

    private List<String> ports;

    private List<Real> reals;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public List<String> getNetworkids() {
        return networkids;
    }

    public void setNetworkids(List<String> networkids) {
        this.networkids = networkids;
    }

    public String getCache() {
        return cache;
    }

    public void setCache(String cache) {
        this.cache = cache;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPersistence() {
        return persistence;
    }

    public void setPersistence(String persistence) {
        this.persistence = persistence;
    }

    public String getHealthchecktype() {
        return healthchecktype;
    }

    public void setHealthchecktype(String healthchecktype) {
        this.healthchecktype = healthchecktype;
    }

    public String getHealthcheck() {
        return healthcheck;
    }

    public void setHealthcheck(String healthcheck) {
        this.healthcheck = healthcheck;
    }

    public Integer getMaxconn() {
        return maxconn;
    }

    public void setMaxconn(Integer maxconn) {
        this.maxconn = maxconn;
    }

    public List<String> getPorts() {
        return ports;
    }

    public void setPorts(List<String> ports) {
        this.ports = ports;
    }

    public List<Real> getReals() {
        return reals == null ? new ArrayList<Real>() : reals;
    }

    public void setReals(List<Real> reals) {
        this.reals = reals;
    }

    public static class Real extends BaseResponse {
        private String vmname;

        private String ip;

        private String network;

        private String ports;

        private Boolean state;

        private String nic;

        public String getVmname() {
            return vmname;
        }

        public void setVmname(String vmname) {
            this.vmname = vmname;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getNetwork() {
            return network;
        }

        public void setNetwork(String network) {
            this.network = network;
        }

        public String getPorts() {
            return ports;
        }

        public void setPorts(String ports) {
            this.ports = ports;
        }

        public Boolean getState() {
            return state;
        }

        public void setState(Boolean state) {
            this.state = state;
        }

        public String getNic() {
            return nic;
        }

        public void setNic(String nic) {
            this.nic = nic;
        }
    }

}
