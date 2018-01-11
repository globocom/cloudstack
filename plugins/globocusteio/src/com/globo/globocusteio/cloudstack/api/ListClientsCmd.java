package com.globo.globocusteio.cloudstack.api;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.globo.globocusteio.cloudstack.api.response.ClientResponse;
import com.globo.globocusteio.cloudstack.client.model.Client;
import com.globo.globocusteio.cloudstack.manager.GloboCusteioService;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@APICommand(name = "listClients", description = "Lists client", responseObject = ClientResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListClientsCmd extends BaseCmd {

    public static final Logger s_logger = Logger.getLogger(ListClientsCmd.class);
    private static final String s_name = "listclientsresponse";

    @Inject
    private GloboCusteioService globoCusteioService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.STRING, description = "the ID of client")
    private String id;

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        ListResponse<ClientResponse> response = new ListResponse<>();
        List<ClientResponse> clientResponses = new ArrayList<>();

        if(id != null){
            Client client = globoCusteioService.getClient(id);
            if(client != null){
                clientResponses.add(createResponse(client));
            }
        }else {
            List<Client> clients = globoCusteioService.listClients();
            for (Client client : clients) {
                clientResponses.add(createResponse(client));
            }
        }

        response.setResponses(clientResponses);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    private ClientResponse createResponse(Client client) {
        ClientResponse clientResponse = new ClientResponse(client.getId(), client.getName());
        clientResponse.setObjectName("client");
        return clientResponse;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }
}
