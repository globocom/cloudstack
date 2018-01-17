package org.apache.cloudstack.api.command.user.globodictionary;

import com.cloud.globodictionary.GloboDictionaryService;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.response.SubComponentResponse;

@APICommand(name = "listSubComponents", description = "Lists sub-components", responseObject = SubComponentResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListSubComponentsCmd extends BaseDictionaryCmd {

    private static final String s_name = "listsubcomponentsresponse";
    private static final String response_name = "subcomponent";

    @Override
    GloboDictionaryService.GloboDictionaryEntityType getEntity() {
        return GloboDictionaryService.GloboDictionaryEntityType.SUB_COMPONENT;
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
