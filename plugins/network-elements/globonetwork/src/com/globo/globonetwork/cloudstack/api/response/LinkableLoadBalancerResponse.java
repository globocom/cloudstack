package com.globo.globonetwork.cloudstack.api.response;

import org.apache.cloudstack.api.BaseResponse;

public class LinkableLoadBalancerResponse  extends BaseResponse {

    private String uuid;

    private String name;

    private String lbenv;

    private String network;

    public LinkableLoadBalancerResponse() {
        setObjectName("linkableloadbalancerresponse");
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLbenv() {
        return lbenv;
    }

    public void setLbenv(String lbenv) {
        this.lbenv = lbenv;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }
}
