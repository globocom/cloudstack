package com.globo.globonetwork.cloudstack.commands;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.globo.globonetwork.client.api.GloboNetworkAPI;

public abstract class GloboNetworkResourceCommand  extends Command {

    @Override
    public boolean executeInSequence() {
        return false;
    }


    public abstract Answer execute(GloboNetworkAPI api);

}
