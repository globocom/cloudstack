package org.apache.cloudstack.api.command.user.vm;

import com.cloud.utils.Pair;
import com.cloud.vm.VirtualMachine;
import java.util.List;
import javax.inject.Inject;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandJobType;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseListTaggedResourcesCmd;

import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.response.ListResponse;

import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.query.QueryService;

@APICommand(name = "listGloboVirtualMachines", description = "List the virtual machines by usage.", responseObject = UserVmResponse.class, responseView = ResponseObject.ResponseView.Restricted, entityType = {VirtualMachine.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = true)
public class ListGloboVirtualMachinesCmd extends BaseListTaggedResourcesCmd {

    private static final String s_name = "listglobovirtualmachinesresponse";

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = UserVmResponse.class, description = "the ID of the virtual machine")
    private Long id;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "name of the virtual machine (a substring match is made against the parameter value, data for all matching VMs will be returned)")
    private String name;

    @Inject
    public QueryService _queryService;
    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////
    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.VirtualMachine;
    }

    @Override
    public void execute() {
        ListResponse<UserVmResponse> response = new ListResponse<>();
        Pair<List<UserVmResponse>, Integer> result = _queryService.listGloboVm(id, name, getProjectId(), getTags());

        response.setResponses(result.first(), result.second());
        response.setResponseName(getCommandName());
        response.setObjectName("virtualmachine");
        setResponseObject(response);
    }
}
