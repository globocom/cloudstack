package org.apache.cloudstack.api.command.user.globodictionary;

import org.apache.cloudstack.api.response.ClientResponse;
import com.cloud.globodictionary.GloboDictionaryService;
import org.apache.cloudstack.api.APICommand;

@APICommand(name = "listClients", description = "Lists clients", responseObject = ClientResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListClientsCmd extends BaseDictionaryCmd {

    private static final String s_name = "listclientsresponse";
    private static final String response_name = "client";

    @Override
    GloboDictionaryService.GloboDictionaryEntityType getEntity() {
        return GloboDictionaryService.GloboDictionaryEntityType.CLIENT;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    String getResponseName() {
        return response_name;
    }
}
