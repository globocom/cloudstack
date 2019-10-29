// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.globo.globonetwork.cloudstack.api.loadbalancer;

import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.dao.LoadBalancerVO;
import com.globo.globonetwork.cloudstack.api.response.LinkableLoadBalancerResponse;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.FirewallRuleResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@APICommand(name = "listGloboLinkableLoadBalancers", responseObject = LinkableLoadBalancerResponse.class, description = "Lists all environments from GloboNetwork")
public class ListGloboLinkableLoadBalancersCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(ListGloboLinkableLoadBalancersCmd.class.getName());

    private static final String s_name = "listlinkableloadbalancerresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Inject
    protected GloboNetworkService globoNetworkSvc;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "Project the IP address will be associated with")
    private Long projectId;

    @Parameter(name = ApiConstants.LBID,
            type = CommandType.UUID,
            entityType = FirewallRuleResponse.class,
            required = true,
            description = "the ID of the load balancer rule")
    private Long lbid;


    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    @Override
    public void execute() throws ResourceAllocationException, ResourceUnavailableException {
        List<LoadBalancerVO> lbs = globoNetworkSvc.listLinkableLoadBalancers(lbid, projectId);


        List<LinkableLoadBalancerResponse> lbResponse = new ArrayList<>();

        for (LoadBalancerVO lb : lbs) {
            LinkableLoadBalancerResponse llb = new LinkableLoadBalancerResponse();
            llb.setName(lb.getName());
            llb.setUuid(lb.getUuid());
            lbResponse.add(llb);
        }


        ListResponse<LinkableLoadBalancerResponse> response = new ListResponse<LinkableLoadBalancerResponse>();
        response.setResponses(lbResponse, lbResponse.size());
        response.setResponseName(getCommandName());

        setResponseObject(response);
    }


    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getLbid() {
        return lbid;
    }

    public void setLbid(Long lbid) {
        this.lbid = lbid;
    }
}
