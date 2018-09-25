/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.globo.globoaclapi.cloudstack.manager;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.FirewallRuleVO;
import com.cloud.resource.ResourceManager;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallbackWithException;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globoaclapi.cloudstack.api.AddGloboAclApiHostCmd;
import com.globo.globoaclapi.cloudstack.api.CreateGloboACLRuleCmd;
import com.globo.globoaclapi.cloudstack.api.ListGloboACLRulesCmd;
import com.globo.globoaclapi.cloudstack.api.RemoveGloboACLRuleCmd;
import com.globo.globoaclapi.cloudstack.commands.CreateACLRuleCommand;
import com.globo.globoaclapi.cloudstack.commands.ListACLRulesCommand;
import com.globo.globoaclapi.cloudstack.commands.RemoveACLRuleCommand;
import com.globo.globoaclapi.cloudstack.resource.GloboAclApiResource;
import com.globo.globoaclapi.cloudstack.response.GloboACLRulesResponse;
import com.globo.globonetwork.client.model.Vlan;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkNetworkDao;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GloboACLManager implements GloboACLService, Configurable, PluggableService {

    @Inject
    protected HostDao _hostDao;
    @Inject
    protected AgentManager _agentMgr;
    @Inject
    protected GloboNetworkService _globoNetworkService;
    @Inject
    protected GloboNetworkNetworkDao _globoNetworkDao;
    @Inject
    protected ResourceManager _resourceMgr;
    @Inject
    protected PhysicalNetworkDao _physicalNetworkDao;

    public static final String VLAN_NOT_ACTIVATED_MESSAGE = "3006:ACL nao cadastrada na ACL::API!";

    private static final ConfigKey<Boolean> GloboAclTrustSSL = new ConfigKey<>("ACL", Boolean.class, "globoaclapi.trust.ssl", "true",
            "Set true to trust ACL API SSL certificate", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Integer> GloboAclTimeout = new ConfigKey<>("ACL", Integer.class, "globoaclapi.timeout", "60000",
            "Globo ACL API connection timeout in", true, ConfigKey.Scope.Global);

    private static final Logger s_logger = Logger.getLogger(GloboACLManager.class);

    @Override
    public List<FirewallRuleVO> listACLRules(Network network) {
        Long environmentId = this.getEnvironmentId(network);
        Vlan vlan = this.getVlan(network);

        s_logger.debug("Listing ACL rules vlan: " + vlan.getVlanNum() + " env:" + environmentId);

        List<FirewallRuleVO> rules = new ArrayList<>();
        try {
            ListACLRulesCommand cmd = new ListACLRulesCommand(environmentId, vlan.getVlanNum(), network.getId());
            GloboACLRulesResponse response = (GloboACLRulesResponse) callCommand(cmd, network.getDataCenterId());
            for(GloboACLRulesResponse.ACLRule r : response.getRules()){
                rules.add((createFirewallRuleVO(network.getId(), network.getCidr(), r)));
            }
        }catch(CloudRuntimeException ex){
            // ACL API returns an error when VLAN is inactive
            if(!VLAN_NOT_ACTIVATED_MESSAGE.equals(ex.getMessage())){
                throw ex;
            }
        }
        return rules;
    }

    private FirewallRuleVO createFirewallRuleVO(Long networkId,String cidr, GloboACLRulesResponse.ACLRule rule) {
        List<String> destinations = Arrays.asList(rule.getDestination());
        List<String> sources = Arrays.asList(cidr); //TODO check this data
        return new FirewallRuleVO(
            rule.getId(), null, rule.getPortStart(), rule.getPortEnd(), rule.getProtocol(), networkId,
            CallContext.current().getCallingAccountId(), 0L,
            FirewallRule.Purpose.Firewall, sources, destinations, rule.getIcmpCode(), rule.getIcmpType(), null,
            FirewallRule.TrafficType.Egress, FirewallRule.FirewallRuleType.User
        );
    }

    @Override
    public void createACLRule(Network network, FirewallRule rule) {
        String destinationCidr = rule.getSourceCidrList().get(0);
        Integer portStart = rule.getSourcePortStart();
        Integer portEnd = rule.getSourcePortEnd();
        String protocol = rule.getProtocol();
        Integer icmpCode = rule.getIcmpCode();
        Integer icmpType = rule.getIcmpType();

        if (destinationCidr == null || destinationCidr.trim().isEmpty()) {
            throw new InvalidParameterValueException("Invalid source CIDR, value should not be empty.");
        }

        if (protocol.equals("tcp") || protocol.equals("udp")) {
            if (portStart == null) {
                throw new InvalidParameterValueException("Start port should not be empty.");
            }
            if (portEnd != null && portEnd < portStart) {
                throw new InvalidParameterValueException("End port should not be greater than start port.");
            }
        } else if (protocol.equals("icmp")) {
            if (icmpCode == null || icmpType == null) {
                throw new InvalidParameterValueException("ICMP type and code should not be empty");
            }
        }

        CreateACLRuleCommand cmd = createACLRuleCommand(network, rule);

        s_logger.debug("Creating ACL rule" + cmd.getAclRuleDescription());

        callCommand(cmd, network.getDataCenterId());
    }

    protected CreateACLRuleCommand createACLRuleCommand(Network network, FirewallRule rule) {
        CreateACLRuleCommand cmd = new CreateACLRuleCommand();
        cmd.setProtocol(rule.getProtocol());
        cmd.setDestinationCidr(rule.getSourceCidrList().get(0));
        cmd.setSourceCidr(network.getCidr());
        cmd.setStartPort(rule.getSourcePortStart());
        cmd.setEndPort(rule.getSourcePortEnd());
        cmd.setIcmpCode(rule.getIcmpCode());
        cmd.setIcmpType(rule.getIcmpType());
        cmd.setVlanNumber(getVlan(network).getVlanNum());
        cmd.setEnvironmentId(getEnvironmentId(network));
        cmd.setAclOwner(getCallingUser());
        return cmd;
    }

    @Override
    public void removeACLRule(Network network, Long ruleId) {
        RemoveACLRuleCommand cmd = new RemoveACLRuleCommand(ruleId, getEnvironmentId(network), getVlan(network).getVlanNum(), getCallingUser());
        s_logger.debug("Removing ACL rule" + ruleId);
        callCommand(cmd, network.getDataCenterId());
    }

    protected String getCallingUser() {
        return CallContext.current().getCallingUser().getUsername().split("@")[0];
    }

    private Vlan getVlan(Network network) {
        return _globoNetworkService.getVlanInfoFromGloboNetwork(network);
    }

    private Long getEnvironmentId(Network network) {
        return _globoNetworkDao.findByNetworkId(network.getId()).getGloboNetworkEnvironmentId();
    }

    private Answer callCommand(Command cmd, Long zoneId) {
        Answer answer = _agentMgr.easySend(getHost(zoneId).getId(), cmd);
        if (answer == null || !answer.getResult()) {
            String msg = "Error executing command " + cmd;
            throw new CloudRuntimeException(answer == null ? msg : answer.getDetails());
        }
        return answer;
    }

    private HostVO getHost(Long zoneId) {
        HostVO aclApiHost =  _hostDao.findByTypeNameAndZoneId(zoneId, Network.Provider.GloboAclApi.getName(), Host.Type.L2Networking);
        if (aclApiHost == null) {
            throw new CloudRuntimeException("Could not find the Globo ACL API resource");
        }
        return aclApiHost;
    }

    public Host addGloboAclApiHost(Long physicalNetworkId, String url, String username, String password) {
        if (url == null || url.trim().isEmpty()) {
            throw new InvalidParameterValueException("Invalid ACL API URL");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new InvalidParameterValueException("Invalid ACL API username");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new InvalidParameterValueException("Invalid ACL API password");
        }

        PhysicalNetwork physicalNetwork;
        if (physicalNetworkId != null) {
            physicalNetwork = _physicalNetworkDao.findById(physicalNetworkId);
            if (physicalNetwork == null) {
                throw new InvalidParameterValueException("Unable to find a physical network having the specified physical network id");
            }
        } else {
            throw new InvalidParameterValueException("Invalid physicalNetworkId");
        }

        final Map<String, String> params = new HashMap<>();
        final long zoneId = physicalNetwork.getDataCenterId();
        params.put("guid", "globoaclapi-" + String.valueOf(zoneId));
        params.put("zoneId", String.valueOf(zoneId));
        params.put("name", Network.Provider.GloboAclApi.getName());
        params.put("url", url);
        params.put("username", username);
        params.put("password", password);
        params.put("trustssl", GloboAclTrustSSL.value().toString());
        params.put("timeout", GloboAclTimeout.value().toString());

        final Map<String, Object> hostDetails = new HashMap<>();
        hostDetails.putAll(params);

        return Transaction.execute(new TransactionCallbackWithException<Host, CloudRuntimeException>() {

            @Override
            public Host doInTransaction(TransactionStatus status) throws CloudRuntimeException {
                try {
                    GloboAclApiResource resource = new GloboAclApiResource();
                    resource.configure(Network.Provider.GloboAclApi.getName(), hostDetails);

                    Host host = _resourceMgr.addHost(zoneId, resource, resource.getType(), params);

                    if (host == null) {
                        throw new CloudRuntimeException("Failed to add Globo ACL API host");
                    }

                    return host;
                } catch (ConfigurationException e) {
                    s_logger.error("Error configuring Globo ACL API resource", e);
                    throw new CloudRuntimeException(e);
                }
            }
        });
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(AddGloboAclApiHostCmd.class);
        cmdList.add(ListGloboACLRulesCmd.class);
        cmdList.add(CreateGloboACLRuleCmd.class);
        cmdList.add(RemoveGloboACLRuleCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return this.getClass().getName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{ GloboAclTrustSSL, GloboAclTimeout };
    }
}
