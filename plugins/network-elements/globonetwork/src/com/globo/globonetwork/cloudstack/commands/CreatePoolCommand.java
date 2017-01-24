package com.globo.globonetwork.cloudstack.commands;

import com.cloud.agent.api.Command;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;

import java.util.List;

public class CreatePoolCommand extends Command {

    private Long vipId;
    private String vipName;
    private String vipIp;
    private Integer publicPort;
    private Integer privatePort;
    private String balacingAlgorithm;
    private String serviceDownAction;
    private String region;
    private Long vipEnvironment;
    private List<GloboNetworkVipResponse.Real> reals;

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public void setVipId(Long vipId) {
        this.vipId = vipId;
    }

    public Long getVipId() {
        return vipId;
    }

    public void setVipName(String vipName) {
        this.vipName = vipName;
    }

    public String getVipName() {
        return vipName;
    }

    public void setPublicPort(Integer publicPort) {
        this.publicPort = publicPort;
    }

    public Integer getPublicPort() {
        return publicPort;
    }

    public void setPrivatePort(Integer privatePort) {
        this.privatePort = privatePort;
    }

    public Integer getPrivatePort() {
        return privatePort;
    }

    public void setBalacingAlgorithm(String balacingAlgorithm) {
        this.balacingAlgorithm = balacingAlgorithm;
    }

    public String getBalacingAlgorithm() {
        return balacingAlgorithm;
    }

    public void setServiceDownAction(String serviceDownAction) {
        this.serviceDownAction = serviceDownAction;
    }

    public String getServiceDownAction() {
        return serviceDownAction;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getRegion() {
        return region;
    }

    public void setVipEnvironment(Long vipEnvironment) {
        this.vipEnvironment = vipEnvironment;
    }

    public Long getVipEnvironment() {
        return vipEnvironment;
    }

    public void setReals(List<GloboNetworkVipResponse.Real> reals) {
        this.reals = reals;
    }

    public List<GloboNetworkVipResponse.Real> getReals() {
        return reals;
    }

    public void setVipIp(String vipIp) {
        this.vipIp = vipIp;
    }

    public String getVipIp() {
        return vipIp;
    }
}
