/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.globo.globonetwork.cloudstack.response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.cloudstack.api.BaseResponse;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;

public class GloboNetworkVipResponse extends Answer {

    private Long id;
    private String name;
    private String ip;
    private Long ipId;
    private Long lbEnvironmentId;
    private String cache;
    private String method;
    private String persistence;
    private String healthcheckType;
    private String healthcheck;
    private List<String> networkIds;
    private Integer maxConn;
    private List<String> ports;
    private List<Real> reals;
    private Boolean created;

    public GloboNetworkVipResponse(Command command, Long id, String name, String ip, Long ipId, Long lbEnvironmentId, String network, String cache, String method,
            String persistence, String healthcheckType, String healthcheck, Integer maxConn, Collection<String> ports, Collection<Real> reals, Boolean created) {
        super(command, true, null);
        this.id = id;
        this.name = name;
        this.ip = ip;
        this.ipId = ipId;
        this.lbEnvironmentId = lbEnvironmentId;
        this.cache = cache;
        this.method = method;
        this.persistence = persistence;
        this.healthcheckType = healthcheckType;
        this.healthcheck = healthcheck;
        this.maxConn = maxConn;
        this.ports = new ArrayList<String>(ports);
        this.reals = new ArrayList<Real>(reals);
        this.setCreated(created);
    }
    public GloboNetworkVipResponse() {

    }

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

    public Long getIpId() {
        return this.ipId;
    }

    public void setIpId(Long ipId) {
        this.ipId = ipId;
    }

    public void setIp(String ip) {
        this.ip = ip;
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

    public String getHealthcheckType() {
        return healthcheckType;
    }

    public void setHealthcheckType(String healthcheckType) {
        this.healthcheckType = healthcheckType;
    }

    public String getHealthcheck() {
        return healthcheck;
    }

    public void setHealthcheck(String healthcheck) {
        this.healthcheck = healthcheck;
    }

    public Integer getMaxConn() {
        return maxConn;
    }

    public void setMaxConn(Integer maxConn) {
        this.maxConn = maxConn;
    }

    public List<String> getPorts() {
        return ports;
    }

    public void setPorts(List<String> ports) {
        this.ports = ports;
    }

    public List<Real> getReals() {
        return reals;
    }

    public void setReals(List<Real> reals) {
        this.reals = reals;
    }

    public List<String> getNetworkIds() {
        return networkIds;
    }

    public void setNetworkIds(List<String> networkIds) {
        this.networkIds = networkIds;
    }

    public Boolean getCreated() {
        return created;
    }

    public void setCreated(Boolean created) {
        this.created = created;
    }

    public Long getLbEnvironmentId() {
        return lbEnvironmentId;
    }

    public void setLbEnvironmentId(Long lbEnvironmentId) {
        this.lbEnvironmentId = lbEnvironmentId;
    }

    public static class Real extends BaseResponse {
        private String vmName;

        private String ip;

        private String network;

        private Long environmentId;

        private List<String> ports;

        private Boolean state;

        private String nic;

        private boolean isRevoked;

        public String getVmName() {
            return vmName;
        }

        public void setVmName(String vmName) {
            this.vmName = vmName;
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

        public List<String> getPorts() {
            if (ports == null) {
                ports = new ArrayList<String>();
            }
            return ports;
        }

        public void setPorts(List<String> ports) {
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

        public boolean isRevoked() {
            return isRevoked;
        }

        public void setRevoked(boolean isRevoked) {
            this.isRevoked = isRevoked;
        }

        public Long getEnvironmentId() {
            return environmentId;
        }

        public void setEnvironmentId(Long environmentId) {
            this.environmentId = environmentId;
        }
    }
}
