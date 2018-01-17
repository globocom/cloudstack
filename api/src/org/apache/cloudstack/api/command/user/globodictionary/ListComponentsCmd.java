package org.apache.cloudstack.api.command.user.globodictionary;

import com.cloud.globodictionary.GloboDictionaryService;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.response.ComponentResponse;

@APICommand(name = "listComponents", description = "Lists components", responseObject = ComponentResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListComponentsCmd extends BaseDictionaryCmd {

    private static final String s_name = "listcomponentsresponse";
    private static final String response_name = "component";

    @Override
    GloboDictionaryService.GloboDictionaryEntityType getEntity() {
        return GloboDictionaryService.GloboDictionaryEntityType.COMPONENT;
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
