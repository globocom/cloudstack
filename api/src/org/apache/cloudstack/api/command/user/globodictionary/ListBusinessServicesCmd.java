package org.apache.cloudstack.api.command.user.globodictionary;

import com.cloud.globodictionary.GloboDictionaryService;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.response.BusinessServiceResponse;

@APICommand(name = "listBusinessServices", description = "Lists business services", responseObject = BusinessServiceResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListBusinessServicesCmd extends BaseDictionaryCmd {

    private static final String s_name = "listbusinessservicesresponse";
    private static final String response_name = "businessservice";

    @Override
    GloboDictionaryService.GloboDictionaryEntityType getEntity() {
        return GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE;
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
