package com.globo.globocusteio.cloudstack.api;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.globo.globocusteio.cloudstack.api.response.BusinessServiceResponse;
import com.globo.globocusteio.cloudstack.client.model.BusinessService;
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

@APICommand(name = "listBusinessServices", description = "Lists business services", responseObject = BusinessServiceResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListBusinessServicesCmd extends BaseCmd {

    public static final Logger s_logger = Logger.getLogger(ListBusinessServicesCmd.class);
    private static final String s_name = "listbusinessservicesresponse";

    @Inject
    private GloboCusteioService globoCusteioService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.STRING, description = "the ID of business service")
    private String id;

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        ListResponse<BusinessServiceResponse> response = new ListResponse<>();
        List<BusinessServiceResponse> businessServiceResponses = new ArrayList<>();

        if(id != null){
            BusinessService businessService = globoCusteioService.getBusinessService(id);
            if(businessService != null){
                businessServiceResponses.add(createResponse(businessService));
            }
        }else {
            List<BusinessService> businessServices = globoCusteioService.listBusinessServices();
            for (BusinessService businessService : businessServices) {
                businessServiceResponses.add(createResponse(businessService));
            }
        }

        response.setResponses(businessServiceResponses);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    private BusinessServiceResponse createResponse(BusinessService businessService) {
        BusinessServiceResponse businessServiceResponse = new BusinessServiceResponse(businessService.getId(), businessService.getName());
        businessServiceResponse.setObjectName("businessservice");
        return businessServiceResponse;
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
