package com.globo.globonetwork.cloudstack.api;

import com.cloud.network.Network;
import com.cloud.utils.Pair;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.command.user.network.ListNetworksCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.NetworkResponse;

@APICommand(name = "listGloboLbNetworks", description = "Lists all networks that support lb services.", responseObject = NetworkResponse.class, responseView = ResponseObject.ResponseView.Full, entityType = {Network.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListGloboLbNetworksCmd extends ListNetworksCmd {

    @Inject
    GloboNetworkManager _globoNetworkManager;

    @Override
    public void execute() {
        Pair<List<? extends Network>, Integer> networks = _globoNetworkManager.searchForLbNetworks(this);

        ListResponse<NetworkResponse> response = new ListResponse<NetworkResponse>();
        List<NetworkResponse> networkResponses = new ArrayList<NetworkResponse>();
        for (Network network : networks.first()) {

            NetworkResponse networkResponse = _responseGenerator.createNetworkResponse(ResponseObject.ResponseView.Restricted, network);
            networkResponses.add(networkResponse);

        }
        response.setResponses(networkResponses, networks.second());
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}
