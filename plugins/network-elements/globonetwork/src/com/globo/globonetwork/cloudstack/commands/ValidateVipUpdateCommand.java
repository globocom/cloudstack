package com.globo.globonetwork.cloudstack.commands;

import com.cloud.agent.api.Command;

public class ValidateVipUpdateCommand  extends Command {

    private Long vipId;

    private String name;

    private String ip;

    public ValidateVipUpdateCommand(Long vipId, String name, String ip) {
        this.vipId = vipId;
        this.name = name;
        this.ip = ip;
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public Long getVipId() {
        return vipId;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }
}
