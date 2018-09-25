package com.globo.globonetwork.cloudstack.element;

//Licensed to the Apache Software Foundation (ASF) under one
//or more contributor license agreements.  See the NOTICE file
//distributed with this work for additional information
//regarding copyright ownership.  The ASF licenses this file
//to you under the Apache License, Version 2.0 (the
//"License"); you may not use this file except in compliance
//with the License.  You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing,
//software distributed under the License is distributed on an
//"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//KIND, either express or implied.  See the License for the
//specific language governing permissions and limitations
//under the License.
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import com.cloud.network.as.AutoScaleCounter;
import com.cloud.network.dao.NetworkDao;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.to.LoadBalancerTO;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.ExternalLoadBalancerDeviceManager;
import com.cloud.network.ExternalLoadBalancerDeviceManagerImpl;
import com.cloud.network.Network;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkModel;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.PublicIpAddress;
import com.cloud.network.dao.NetworkServiceMapDao;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.element.IpDeployer;
import com.cloud.network.element.LoadBalancingServiceProvider;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.network.rules.LbStickinessMethod;
import com.cloud.network.rules.LbStickinessMethod.StickinessMethodType;
import com.cloud.network.rules.LoadBalancerContainer;
import com.cloud.offering.NetworkOffering;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ServerResource;
import com.cloud.resource.UnableDeleteHostException;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachineProfile;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;
import com.globo.globonetwork.cloudstack.resource.GloboNetworkResource;
import com.google.gson.Gson;

@Component
public class GloboNetworkElement extends ExternalLoadBalancerDeviceManagerImpl implements LoadBalancingServiceProvider, IpDeployer, ExternalLoadBalancerDeviceManager {
    private static final Logger s_logger = Logger.getLogger(GloboNetworkElement.class);

    private static final Map<Service, Map<Capability, String>> capabilities = setCapabilities();

    @Inject
    DataCenterDao _dcDao;
    @Inject
    NetworkModel _networkManager;
    @Inject
    LoadBalancingRulesManager _lbManager;
    @Inject
    NetworkServiceMapDao _ntwkSrvcDao;
    @Inject
    GloboNetworkService _globoNetworkService;
    @Inject
    ResourceManager _resourceMgr;
    @Inject
    HostDao _hostDao;

    @Inject
    NetworkDao networkDao;

    @Inject
    LoadBalancingRulesManager _lbMgr;

    @Inject
    PhysicalNetworkDao _physicalNetworkDao;

    @Override
    public Map<Service, Map<Capability, String>> getCapabilities() {
        return capabilities;
    }

    private static Map<Service, Map<Capability, String>> setCapabilities() {
        // Set capabilities for LB service
        Map<Capability, String> lbCapabilities = new HashMap<Capability, String>();
        lbCapabilities.put(Capability.SupportedLBAlgorithms, "leastconn, roundrobin");
        lbCapabilities.put(Capability.SupportedLBIsolation, "dedicated, shared");
        lbCapabilities.put(Capability.SupportedProtocols, "tcp,udp,http");

        // Specifies that load balancing rules can only be made with public IPs that aren't source NAT IPs
        lbCapabilities.put(Capability.LoadBalancingSupportedIps, "additional");

        // Support inline mode with firewall
        lbCapabilities.put(Capability.InlineMode, "true");

        //support only for public lb
        lbCapabilities.put(Capability.LbSchemes, LoadBalancerContainer.Scheme.Public.toString());

        LbStickinessMethod method;
        List<LbStickinessMethod> methodList = new ArrayList<LbStickinessMethod>();
        method = new LbStickinessMethod(new StickinessMethodType("Cookie"), "This is cookie based sticky method");
        methodList.add(method);
        method = new LbStickinessMethod(new StickinessMethodType("Source-ip"), "This is source based sticky method");
        methodList.add(method);
        method = new LbStickinessMethod(new StickinessMethodType("Source-ip with persistence between ports"), "This is source based sticky method with stickiness between ports");
        methodList.add(method);
        method = new LbStickinessMethod(new StickinessMethodType("Priority Failover"), "This is a priority failover stickiness");
        methodList.add(method);

        Gson gson = new Gson();
        String stickyMethodList = gson.toJson(methodList);
        lbCapabilities.put(Capability.SupportedStickinessMethods, stickyMethodList);

        // Add auto scale capability to load balancer
        AutoScaleCounter.AutoScaleCounterType AutoScaleCounterCpu = new AutoScaleCounter.AutoScaleCounterType("cpu_used");
        AutoScaleCounter.AutoScaleCounterType AutoScaleCounterMemory = new AutoScaleCounter.AutoScaleCounterType("memory_used");
        AutoScaleCounter.AutoScaleCounterType AutoScaleCounterConnections = new AutoScaleCounter.AutoScaleCounterType("active_connections");
        AutoScaleCounter.AutoScaleCounterType AutoScaleCounterMemoryNoCache = new AutoScaleCounter.AutoScaleCounterType("memory_free");


        List<AutoScaleCounter> counterList = new ArrayList<AutoScaleCounter>();
        counterList.add(new AutoScaleCounter(AutoScaleCounterCpu));
        counterList.add(new AutoScaleCounter(AutoScaleCounterMemory));
        counterList.add(new AutoScaleCounter(AutoScaleCounterConnections));
        counterList.add(new AutoScaleCounter(AutoScaleCounterMemoryNoCache));
        String autoScaleCounterList = gson.toJson(counterList);
        lbCapabilities.put(Capability.AutoScaleCounters, autoScaleCounterList);

        // Healthcheck
        lbCapabilities.put(Capability.HealthCheckPolicy, "true");

        Map<Service, Map<Capability, String>> capabilities = new HashMap<Service, Map<Capability, String>>();
        capabilities.put(Service.Lb, lbCapabilities);
        return capabilities;
    }

    @Override
    public Provider getProvider() {
        return Provider.GloboNetwork;
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        return true;
    }

    protected boolean canHandle(Network network, Service service) {
        s_logger.trace("Checking if GloboNetwork can handle service " + service.getName() + " on network " + network.getDisplayText());

        DataCenter zone = _dcDao.findById(network.getDataCenterId());
        boolean handleInAdvanceZone = (zone.getNetworkType() == NetworkType.Advanced && network.getGuestType() == Network.GuestType.Shared && network.getTrafficType() == TrafficType.Guest);

        if (!handleInAdvanceZone) {
            s_logger.trace("Not handling network with Type  " + network.getGuestType() + " and traffic type " + network.getTrafficType() + " in zone of type "
                    + zone.getNetworkType());
            return false;
        }

        return (_networkManager.isProviderForNetwork(getProvider(), network.getId()) && _ntwkSrvcDao.canProviderSupportServiceInNetwork(network.getId(), service,
                Network.Provider.GloboNetwork));
    }

    @Override
    public boolean implement(Network network, NetworkOffering offering, DeployDestination dest, ReservationContext context) throws ConcurrentOperationException,
            ResourceUnavailableException, InsufficientCapacityException {
        return true;
    }

    @Override
    public boolean prepare(Network network, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context) throws ConcurrentOperationException,
            ResourceUnavailableException, InsufficientCapacityException {
        return true;
    }

    @Override
    public boolean release(Network network, NicProfile nic, VirtualMachineProfile vm, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean shutdown(Network network, ReservationContext context, boolean cleanup) throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean destroy(Network network, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
        if (!canHandle(network, Service.Lb)) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isReady(PhysicalNetworkServiceProvider provider) {
        return _globoNetworkService.canEnable(provider.getPhysicalNetworkId());
    }

    @Override
    public boolean shutdownProviderInstances(PhysicalNetworkServiceProvider provider, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
        PhysicalNetwork pNtwk = _physicalNetworkDao.findById(provider.getPhysicalNetworkId());
        Host host = _hostDao.findByTypeNameAndZoneId(pNtwk.getDataCenterId(), Provider.GloboNetwork.getName(), Type.L2Networking);
        if (host != null) {
            _resourceMgr.deleteHost(host.getId(), true, false);
        }
        return true;
    }

    @Override
    public IpDeployer getIpDeployer(Network network) {
        return this;
    }

    @Override
    public boolean applyLBRules(Network config, List<LoadBalancingRule> rules) throws ResourceUnavailableException {
        boolean returnValue = true;
        boolean result = false;
        for (LoadBalancingRule loadBalancingRule : rules) {
            result = _globoNetworkService.applyLbRuleInGloboNetwork(config, loadBalancingRule);
            // Make sure the method returns false if there is at least one false return
            returnValue = returnValue && result;
        }
        return returnValue;
    }

    @Override
    public boolean validateLBRule(Network network, LoadBalancingRule rule) {
        return _globoNetworkService.validateLBRule(network, rule);
    }

    @Override
    public boolean applyIps(Network network, List<? extends PublicIpAddress> ipAddress, Set<Service> services) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public List<LoadBalancerTO> updateHealthChecks(Network network, List<LoadBalancingRule> lbrules) {
        return null;
    }

    @Override
    public boolean handlesOnlyRulesInTransitionState() {
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

    @Override
    public HostVO createHostVOForDirectConnectAgent(HostVO host, final StartupCommand[] startup, ServerResource resource, Map<String, String> details, List<String> hostTags) {
        if (!(startup[0] instanceof StartupCommand && resource instanceof GloboNetworkResource)) {
            return null;
        }
        host.setType(Host.Type.L2Networking);
        return host;
    }

    @Override
    public DeleteHostAnswer deleteHost(HostVO host, boolean isForced, boolean isForceDeleteStorage) throws UnableDeleteHostException {
        if (!host.getName().equals(Provider.GloboNetwork.getName())) {
            return null;
        }
        return new DeleteHostAnswer(true);
    }

    @Override
    public HostVO createHostVOForConnectedAgent(HostVO host, StartupCommand[] cmd) {
        return null;
    }

}
