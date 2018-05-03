// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.globo.globodns.cloudstack.element;

import com.cloud.network.element.DnsServiceProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.naming.ConfigurationException;


import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationDao;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.StartupCommand;
import com.cloud.dc.DataCenter;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.offering.NetworkOffering;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceStateAdapter;
import com.cloud.resource.ServerResource;
import com.cloud.resource.UnableDeleteHostException;
import com.cloud.utils.component.AdapterBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallbackWithException;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.globo.globodns.cloudstack.api.AddGloboDnsHostCmd;
import com.globo.globodns.cloudstack.commands.CreateLbRecordAndReverseCommand;
import com.globo.globodns.cloudstack.commands.CreateOrUpdateDomainCommand;
import com.globo.globodns.cloudstack.commands.CreateOrUpdateRecordAndReverseCommand;
import com.globo.globodns.cloudstack.commands.RemoveDomainCommand;
import com.globo.globodns.cloudstack.commands.RemoveRecordCommand;
import com.globo.globodns.cloudstack.commands.SignInCommand;
import com.globo.globodns.cloudstack.commands.ValidateLbRecordCommand;
import com.globo.globodns.cloudstack.resource.GloboDnsResource;

@Component
public class GloboDnsElement extends AdapterBase implements ResourceStateAdapter, DnsServiceProvider, GloboDnsElementService, Configurable {

    private static final Logger s_logger = Logger.getLogger(GloboDnsElement.class);

    private static final Map<Service, Map<Capability, String>> capabilities = setCapabilities();

    private static final ConfigKey<Long> GloboDNSTemplateId = new ConfigKey<Long>("Advanced", Long.class, "globodns.domain.templateid", "1",
            "Template id to be used when creating domains in GloboDNS", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Boolean> GloboDNSOverride = new ConfigKey<Boolean>("Advanced", Boolean.class, "globodns.override.entries", "true",
            "Allow GloboDns to override entries that already exist", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Boolean> GloboDNSLbOverride = new ConfigKey<Boolean>("Advanced", Boolean.class, "globodns.override.lb.entries", "false",
            "Allow GloboDns to override entries for load balancer names that already exist", true, ConfigKey.Scope.Global);
    private static final ConfigKey<String> GloboDNSLbBlacklistedDomains = new ConfigKey<>("Advanced", String.class, "globodns.lb.domain.blacklist", "",
            "List of comma separated domains that should be blacklisted for load balancers", true, ConfigKey.Scope.Global);

    private static final ConfigKey<Boolean> GloboDNSVmSkipDnsError = new ConfigKey<Boolean>("Advanced", Boolean.class, "globodns.vm.skipdnserror", "false",
            "when false if dns integration failed, the vm will not be created and throw an exception, else vm will be created and dns it could be register later", true, ConfigKey.Scope.Global);
    // DAOs
    @Inject
    DataCenterDao _dcDao;
    @Inject
    HostDao _hostDao;
    @Inject
    PhysicalNetworkDao _physicalNetworkDao;

    @Inject
    GloboResourceConfigurationDao _globoResourceConfigDao;

    // Managers
    @Inject
    AgentManager _agentMgr;
    @Inject
    ResourceManager _resourceMgr;

    protected boolean isTypeSupported(VirtualMachine.Type type) {
        return type == VirtualMachine.Type.User || type == VirtualMachine.Type.ConsoleProxy || type == VirtualMachine.Type.DomainRouter;
    }

    @Override
    @DB
    public boolean implement(final Network network, NetworkOffering offering, DeployDestination dest, ReservationContext context) throws ConcurrentOperationException,
            ResourceUnavailableException, InsufficientCapacityException {

        Long zoneId = network.getDataCenterId();
        DataCenter zone = _dcDao.findById(zoneId);
        if (zone == null) {
            throw new CloudRuntimeException("Could not find zone associated to this network");
        }
        CreateOrUpdateDomainCommand cmd = new CreateOrUpdateDomainCommand(network.getNetworkDomain(), GloboDNSTemplateId.value());
        callCommand(cmd, zoneId);
        return true;
    }

    @Override
    @DB
    public boolean prepare(final Network network, final NicProfile nic, final VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {

        if (!isTypeSupported(vm.getType())) {
            s_logger.info("GloboDNS only manages records for VMs of type User, ConsoleProxy and DomainRouter. VM " + vm + " is " + vm.getType());
            return false;
        }

        Long zoneId = network.getDataCenterId();
        final DataCenter zone = _dcDao.findById(zoneId);
        if (zone == null) {
            throw new CloudRuntimeException("Could not find zone associated to this network");
        }

        /* Create new A record in GloboDNS */
        // We allow only lower case names in DNS, so force lower case names for VMs
        String vmName = vm.getHostName();
        String vmHostname = vm.getHostName().toLowerCase();
        if (!vmName.equals(vmHostname) && vm.getType() == VirtualMachine.Type.User) {
            throw new InvalidParameterValueException("VM name should contain only lower case letters and digits: " + vmName + " - " + vm);
        }

        boolean isIpv6 = nic.getIPv6Address() != null;
        String ipAddress = isIpv6 ? nic.getIPv6Address() : nic.getIPv4Address();


        GloboResourceConfigurationVO forceConfig = _globoResourceConfigDao.getFirst(GloboResourceType.VM_NIC, nic.getUuid(), GloboResourceKey.skipDnsError);
        boolean forceDoaminRegister = forceConfig != null ? forceConfig.getBooleanValue() : GloboDNSVmSkipDnsError.value(); // false while the config is not persisted by vm flow

        return registerVmDomain(zoneId, nic.getUuid(), vmHostname, ipAddress, network.getNetworkDomain(), isIpv6, forceDoaminRegister);
    }

    @Override
    public boolean registerVmDomain(Long zoneId, String nicUuid, String hostName, String ipAddress, String networkDomain, boolean isIpv6, boolean skipDnsError) {
        GloboResourceConfigurationVO dnsRegisteredConfig = _globoResourceConfigDao.getFirst(GloboResourceType.VM_NIC, nicUuid, GloboResourceKey.isDNSRegistered);
        boolean isDnsRegistered = dnsRegisteredConfig != null ? dnsRegisteredConfig.getBooleanValue() : false;

        if (isDnsRegistered) {
            return true;
        }

        CreateOrUpdateRecordAndReverseCommand cmd = new CreateOrUpdateRecordAndReverseCommand(hostName, ipAddress, networkDomain,
                GloboDNSTemplateId.value(), GloboDNSOverride.value(), isIpv6);


        return registerRecord(cmd, zoneId, nicUuid, GloboResourceType.VM_NIC, skipDnsError);

    }

    @Override
    @DB
    public boolean release(final Network network, NicProfile nic, final VirtualMachineProfile vm, ReservationContext context) throws ConcurrentOperationException,
            ResourceUnavailableException {

        if (!isTypeSupported(vm.getType())) {
            s_logger.info("GloboDNS only manages records for VMs of type User, ConsoleProxy and DomainRouter. VM " + vm + " is " + vm.getType());
            return false;
        }

        Long zoneId = network.getDataCenterId();
        final DataCenter zone = _dcDao.findById(zoneId);
        if (zone == null) {
            throw new CloudRuntimeException("Could not find zone associated to this network");
        }


        boolean isIpv6 = nic.getIPv6Address() != null;
        String ipAddress = isIpv6 ? nic.getIPv6Address() : nic.getIPv4Address();

        RemoveRecordCommand cmd = new RemoveRecordCommand(hostNameOfVirtualMachine(vm), ipAddress, network.getNetworkDomain(), isIpv6);

        callCommand(cmd, zoneId);

        List<GloboResourceConfigurationVO> configurationList = _globoResourceConfigDao.getConfiguration(GloboResourceType.VM_NIC, nic.getUuid(), GloboResourceKey.isDNSRegistered);
        for (GloboResourceConfigurationVO globoResourceConfigurationVO : configurationList){
            _globoResourceConfigDao.remove(String.valueOf(globoResourceConfigurationVO.getId()));
        }

        return true;
    }

    @Override
    public boolean shutdown(Network network, ReservationContext context, boolean cleanup) throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    protected String hostNameOfVirtualMachine(VirtualMachineProfile vm) {
        return vm.getHostName().toLowerCase();
    }

    @Override
    @DB
    public boolean destroy(final Network network, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
        Long zoneId = network.getDataCenterId();
        final DataCenter zone = _dcDao.findById(zoneId);
        if (zone == null) {
            throw new CloudRuntimeException("Could not find zone associated to this network");
        }

        RemoveDomainCommand cmd = new RemoveDomainCommand(network.getNetworkDomain(), GloboDNSOverride.value());
        callCommand(cmd, zoneId);
        return true;
    }

    @Override
    public Map<Service, Map<Capability, String>> getCapabilities() {
        return capabilities;
    }

    private static Map<Service, Map<Capability, String>> setCapabilities() {
        Map<Service, Map<Capability, String>> caps = new HashMap<Service, Map<Capability, String>>();
        Map<Capability, String> dnsCapabilities = new HashMap<Capability, String>();
        // FIXME
        dnsCapabilities.put(Capability.AllowDnsSuffixModification, "true");
        caps.put(Service.Dns, dnsCapabilities);
        return caps;
    }

    @Override
    public Provider getProvider() {
        return Provider.GloboDns;
    }

    @Override
    public boolean isReady(PhysicalNetworkServiceProvider provider) {
        return true;
    }

    @Override
    public boolean shutdownProviderInstances(PhysicalNetworkServiceProvider provider, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
        PhysicalNetwork pNtwk = _physicalNetworkDao.findById(provider.getPhysicalNetworkId());
        Host host = getGloboDnsHost(pNtwk.getDataCenterId());
        if (host != null) {
            _resourceMgr.deleteHost(host.getId(), true, false);
        }
        return true;
    }

    @Override
    public boolean canEnableIndividualServices() {
        return true;
    }

    @Override
    public boolean verifyServicesCombination(Set<Service> services) {
        return true;
    }

    ////// Configurable methods /////////////
    @Override
    public String getConfigComponentName() {
        return GloboDnsElement.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {GloboDNSTemplateId, GloboDNSOverride, GloboDNSLbOverride, GloboDNSLbBlacklistedDomains, GloboDNSVmSkipDnsError};
    }

    ////////// Resource/Host methods ////////////
    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(AddGloboDnsHostCmd.class);
        return cmdList;
    }

    @Override
    public HostVO createHostVOForConnectedAgent(HostVO host, StartupCommand[] cmd) {
        return null;
    }

    @Override
    public HostVO createHostVOForDirectConnectAgent(HostVO host, StartupCommand[] startup, ServerResource resource, Map<String, String> details, List<String> hostTags) {
        if (!(startup[0] instanceof StartupCommand && resource instanceof GloboDnsResource)) {
            return null;
        }
        host.setType(Host.Type.L2Networking);
        return host;
    }

    @Override
    public DeleteHostAnswer deleteHost(HostVO host, boolean isForced, boolean isForceDeleteStorage) throws UnableDeleteHostException {
        if (!(host.getType() == Host.Type.L2Networking)) {
            return null;
        }
        return new DeleteHostAnswer(true);
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        _resourceMgr.registerResourceStateAdapter(name, this);
        return true;
    }

    private HostVO getGloboDnsHost(Long zoneId) {
        HostVO host = _hostDao.findByTypeNameAndZoneId(zoneId, Provider.GloboDns.getName(), Type.L2Networking);
        if (host == null) {
            throw new CloudRuntimeException("Could not find the GloboDNS resource");
        }
        return host;
    }

    @Override
    @DB
    public Host addGloboDnsHost(Long physicalNetworkId, final String username, final String password, String url) {

        if (username == null || username.trim().isEmpty()) {
            throw new InvalidParameterValueException("Invalid username: " + username);
        }

        if (password == null || password.trim().isEmpty()) {
            throw new InvalidParameterValueException("Invalid password: " + password);
        }

        if (url == null || url.trim().isEmpty()) {
            throw new InvalidParameterValueException("Invalid url: " + url);
        }

        // validate physical network and zone
        // Check if physical network exists
        PhysicalNetwork pNtwk = null;
        if (physicalNetworkId != null) {
            pNtwk = _physicalNetworkDao.findById(physicalNetworkId);
            if (pNtwk == null) {
                throw new InvalidParameterValueException("Unable to find a physical network having the specified physical network id");
            }
        } else {
            throw new InvalidParameterValueException("Invalid physicalNetworkId: " + physicalNetworkId);
        }

        final Long zoneId = pNtwk.getDataCenterId();

        final Map<String, String> params = new HashMap<String, String>();
        params.put("guid", "globodns-" + String.valueOf(zoneId));
        params.put("zoneId", String.valueOf(zoneId));
        params.put("name", Provider.GloboDns.getName());

        params.put("url", url);
        params.put("username", username);
        params.put("password", password);

        final Map<String, Object> hostDetails = new HashMap<String, Object>();
        hostDetails.putAll(params);

        Host host = Transaction.execute(new TransactionCallbackWithException<Host, CloudRuntimeException>() {

            @Override
            public Host doInTransaction(TransactionStatus status) throws CloudRuntimeException {
                try {
                    GloboDnsResource resource = new GloboDnsResource();
                    resource.configure(Provider.GloboDns.getName(), hostDetails);

                    Host host = _resourceMgr.addHost(zoneId, resource, resource.getType(), params);

                    if (host == null) {
                        throw new CloudRuntimeException("Failed to add GloboDNS host");
                    }

                    // Validate username and password by logging in
                    SignInCommand cmd = new SignInCommand(username, password);
                    Answer answer = callCommand(cmd, zoneId);
                    if (answer == null || !answer.getResult()) {
                        // Could not sign in on GloboDNS
                        throw new ConfigurationException("Could not sign in on GloboDNS. Please verify URL, username and password.");
                    }

                    return host;
                } catch (ConfigurationException e) {
                    throw new CloudRuntimeException(e);
                }
            }
        });

        return host;
    }

    // Load Balancing methods
    @Override
    public boolean validateDnsRecordForLoadBalancer(String lbDomain, String lbRecord, String lbRecordContent, Long zoneId, boolean skipDnsError) {
        s_logger.debug("Validating LB DNS record " + lbRecord + " in domain " + lbDomain);
        if (lbRecord.contains("_")) {
            throw new InvalidParameterValueException("Underscore(_) is not allowed for load balancer name");
        }

        this.checkForBlacklistedDomain(lbDomain, lbRecord);

        DataCenter zone = _dcDao.findById(zoneId);
        if (zone == null) {
            throw new CloudRuntimeException("Could not find zone with ID " + zoneId);
        }

        ValidateLbRecordCommand cmd = new ValidateLbRecordCommand(lbRecord, lbRecordContent, lbDomain, GloboDNSLbOverride.value(), skipDnsError);
        Answer answer = easyCallCommand(cmd, zoneId);
        if (answer == null || !answer.getResult()) {
            // Could not sign in on GloboDNS
            throw new InvalidParameterValueException("Could not validate LB record " + lbRecord + ". " + (answer == null ? "" : answer.getDetails()));
        }
        return answer.getResult();
    }

    protected void checkForBlacklistedDomain(String lbDomain, String lbRecord) {
        List<String> blacklist = getBlackListedDomains();

        String lbName = lbRecord + "." + lbDomain;
        for(String blacklistedDomain : blacklist){
            if(!blacklistedDomain.equals("") && lbName.contains(blacklistedDomain)){
                throw new InvalidParameterValueException("Invalid load balancer name, it cannot contain the the domain '"+ blacklistedDomain +"'");
            }
        }
    }

    protected List<String> getBlackListedDomains() {
        return Arrays.asList(GloboDNSLbBlacklistedDomains.value().split(","));
    }

    @Override
    @Deprecated
    public boolean createDnsRecordForLoadBalancer(String lbDomain, String lbRecord, String lbIpAddress, Long zoneId) {
        s_logger.debug("Creating LB DNS record " + lbRecord + " in domain " + lbDomain);
        DataCenter zone = _dcDao.findById(zoneId);
        if (zone == null) {
            throw new CloudRuntimeException("Could not find zone with ID " + zoneId);
        }

        CreateLbRecordAndReverseCommand cmd = new CreateLbRecordAndReverseCommand(lbRecord, lbIpAddress, lbDomain, GloboDNSTemplateId.value(), GloboDNSLbOverride.value());
        callCommand(cmd, zoneId);
        return true;
    }

    @Override
    public boolean createDnsRecordForLoadBalancer(GloboDnsTO globoDns, boolean skipDnsError) {
        s_logger.debug("Creating LB DNS record " + globoDns.getRecord() + " in domain " + globoDns.getDomain());
        DataCenter zone = _dcDao.findById(globoDns.getZoneId());
        if (zone == null) {
            throw new CloudRuntimeException("Could not find zone with ID " + globoDns.getZoneId());
        }

        CreateLbRecordAndReverseCommand cmd = new CreateLbRecordAndReverseCommand(globoDns,
                                                                                  GloboDNSTemplateId.value(),
                                                                                  GloboDNSLbOverride.value());

        return registerRecord(cmd, globoDns.getZoneId(), globoDns.getResourceId(), GloboResourceType.LOAD_BALANCER, skipDnsError);
    }


    @Override
    public boolean removeDnsRecordForLoadBalancer(String lbUuid, String lbDomain, String lbRecord, String lbIpAddress, Long zoneId) {
        s_logger.debug("Removing LB DNS record " + lbRecord + " from domain " + lbDomain);
        DataCenter zone = _dcDao.findById(zoneId);
        if (zone == null) {
            throw new CloudRuntimeException("Could not find zone with ID " + zoneId);
        }

        RemoveRecordCommand cmd = new RemoveRecordCommand(lbRecord, lbIpAddress, lbDomain, false);
        callCommand(cmd, zoneId);


        List<GloboResourceConfigurationVO> configurationList = _globoResourceConfigDao.getConfiguration(GloboResourceType.LOAD_BALANCER, lbUuid, GloboResourceKey.isDNSRegistered);
        for (GloboResourceConfigurationVO globoResourceConfigurationVO : configurationList){
            _globoResourceConfigDao.remove(String.valueOf(globoResourceConfigurationVO.getId()));
        }

        return true;
    }

    private boolean registerRecord(Command cmd, Long zoneId, String resourceId, GloboResourceType resourceType, boolean skipDnsError){
        GloboResourceConfigurationVO dnsRegisteredConfig = _globoResourceConfigDao.getFirst(resourceType, resourceId, GloboResourceKey.isDNSRegistered);
        boolean isDnsRegistered = dnsRegisteredConfig != null ? dnsRegisteredConfig.getBooleanValue() : false;

        if (isDnsRegistered) {
            return true;
        }

        HostVO globoDnsHost = getGloboDnsHost(zoneId);
        Answer answer = _agentMgr.easySend(globoDnsHost.getId(), cmd);

        if (answer == null) {
            throw new CloudRuntimeException("Error create " + resourceType.toString() +  " record while send command to dns resource");

        }

        if (!answer.getResult()){
            if (Answer.AnswerTypeError.DNS_IO_ERROR.equals(answer.getTypeError()) && skipDnsError) {
                if (dnsRegisteredConfig == null) {
                    dnsRegisteredConfig = new GloboResourceConfigurationVO(resourceType, resourceId, GloboResourceKey.isDNSRegistered, Boolean.FALSE.toString());
                    _globoResourceConfigDao.persist(dnsRegisteredConfig);
                } else {
                    dnsRegisteredConfig.setBoolValue(false);
                    _globoResourceConfigDao.updateValue(dnsRegisteredConfig);
                }

            } else {
                throw new CloudRuntimeException("Error trying register " + resourceType.toString() +  " record. Error: " + answer.getDetails());
            }
        } else {
            if (dnsRegisteredConfig == null) {
                dnsRegisteredConfig = new GloboResourceConfigurationVO(resourceType, resourceId, GloboResourceKey.isDNSRegistered, Boolean.TRUE.toString());
                _globoResourceConfigDao.persist(dnsRegisteredConfig);
            } else {
                dnsRegisteredConfig.setBoolValue(true);
                _globoResourceConfigDao.updateValue(dnsRegisteredConfig);
            }

        }

        return answer.getResult();
    }

    ///////// Provider control methods ////////////
    private Answer easyCallCommand(Command cmd, Long zoneId) {
        HostVO globoDnsHost = getGloboDnsHost(zoneId);

        return _agentMgr.easySend(globoDnsHost.getId(), cmd);
    }
    private Answer callCommand(Command cmd, Long zoneId) {

        Answer answer = easyCallCommand(cmd, zoneId);
        if (answer == null || !answer.getResult()) {
            String msg = "Error executing command " + cmd;
            msg = answer == null ? msg : answer.getDetails();
            throw new CloudRuntimeException(msg);
        }

        return answer;
    }

    @Override
    public boolean addDnsEntry(Network network, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context) throws ConcurrentOperationException, InsufficientCapacityException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean configDnsSupportForSubnet(Network network, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context) throws ConcurrentOperationException, InsufficientCapacityException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean removeDnsSupportForSubnet(Network network) throws ResourceUnavailableException {
        return true;
    }
}
