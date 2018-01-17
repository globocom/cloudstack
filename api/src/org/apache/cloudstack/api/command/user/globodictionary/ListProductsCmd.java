package org.apache.cloudstack.api.command.user.globodictionary;

import com.cloud.globodictionary.GloboDictionaryService;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.response.ProductResponse;

@APICommand(name = "listProducts", description = "Lists products", responseObject = ProductResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListProductsCmd extends BaseDictionaryCmd {

    private static final String s_name = "listproductsresponse";
    private static final String response_name = "product";

    @Override
    GloboDictionaryService.GloboDictionaryEntityType getEntity() {
        return GloboDictionaryService.GloboDictionaryEntityType.PRODUCT;
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
