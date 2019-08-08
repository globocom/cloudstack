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

package com.globo.globonetwork.cloudstack.manager;

import com.cloud.domain.Domain;
import com.cloud.exception.InsufficientVirtualNetworkCapacityException;
import com.cloud.network.dao.LoadBalancerDao;
import com.cloud.network.dao.LoadBalancerOptionsDao;
import com.cloud.network.dao.LoadBalancerOptionsVO;
import com.cloud.network.dao.LoadBalancerPortMapDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.LoadBalancerPortMapVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkServiceMapDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.dao.UserIpv6AddressDao;
import com.cloud.utils.exception.UserCloudRuntimeException;
import com.cloud.utils.net.Ip;
import com.cloud.vm.NicVO;

import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;
import com.globo.globodns.cloudstack.element.GloboDnsTO;
import com.globo.globonetwork.cloudstack.api.CreateGloboNetworkPoolCmd;
import com.globo.globonetwork.cloudstack.api.DeleteGloboNetworkPoolCmd;
import com.globo.globonetwork.cloudstack.api.GetGloboNetworkPoolCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboLbNetworksCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboNetworkExpectedHealthchecksCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboNetworkPoolsCmd;
import com.globo.globonetwork.cloudstack.api.UpdateGloboNetworkPoolCmd;
import com.globo.globonetwork.cloudstack.api.loadbalancer.CreateGloboLoadBalancerCmd;
import com.globo.globonetwork.cloudstack.api.loadbalancer.DeleteGloboLoadBalancerCmd;
import com.globo.globonetwork.cloudstack.commands.ApplyVipInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.CheckDSREnabled;
import com.globo.globonetwork.cloudstack.commands.CreatePoolCommand;
import com.globo.globonetwork.cloudstack.commands.DeletePoolCommand;
import com.globo.globonetwork.cloudstack.commands.GetPoolLBByIdCommand;
import com.globo.globonetwork.cloudstack.commands.ListExpectedHealthchecksCommand;
import com.globo.globonetwork.cloudstack.commands.ListPoolLBCommand;
import com.globo.globonetwork.cloudstack.commands.UpdatePoolCommand;
import com.globo.globonetwork.cloudstack.commands.ValidateVipUpdateCommand;
import com.globo.globonetwork.cloudstack.response.CheckDSREnabledResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkExpectHealthcheckResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import java.math.BigInteger;
import java.net.URI;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import com.globo.globonetwork.cloudstack.api.ListGloboNetworkLBCacheGroupsCmd;
import com.globo.globonetwork.cloudstack.api.RegisterDnsForResourceCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboNetworkPoolOptionsCmd;
import com.globo.globonetwork.cloudstack.commands.ListGloboNetworkLBCacheGroupsCommand;
import com.globo.globonetwork.cloudstack.commands.ListPoolOptionsCommand;
import com.globo.globonetwork.cloudstack.response.GloboNetworkCacheGroupsResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolOptionResponse;
import com.globo.globonetwork.cloudstack.api.GetGloboResourceConfigurationCmd;
import org.apache.cloudstack.acl.ControlledEntity.ACLType;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationDao;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.apache.cloudstack.region.PortableIp;
import org.apache.cloudstack.region.PortableIpDao;
import org.apache.cloudstack.region.PortableIpRange;
import org.apache.cloudstack.region.PortableIpRangeDao;
import org.apache.cloudstack.region.PortableIpRangeVO;
import org.apache.cloudstack.region.PortableIpVO;
import org.apache.log4j.Logger;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.configuration.Config;
import com.cloud.configuration.ConfigurationManager;
import com.cloud.configuration.Resource.ResourceType;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.VlanVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.dc.dao.VlanDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.exception.CloudException;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.IpAddressManager;
import com.cloud.network.Network;
import com.cloud.network.Network.GuestType;
import com.cloud.network.Network.Provider;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkService;
import com.cloud.network.Networks.BroadcastDomainType;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.UserIpv6AddressVO;
import com.cloud.network.addr.PublicIp;
import com.cloud.network.guru.NetworkGuru;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.lb.LoadBalancingRule.LbDestination;
import com.cloud.network.lb.LoadBalancingRule.LbHealthCheckPolicy;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.network.lb.LoadBalancingRulesService;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.offerings.NetworkOfferingVO;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.org.Grouping;
import com.cloud.projects.Project;
import com.cloud.projects.ProjectManager;
import com.cloud.resource.ResourceManager;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.DomainManager;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.Journal;
import com.cloud.utils.Pair;
import com.cloud.utils.StringUtils;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionCallbackWithException;
import com.cloud.utils.db.TransactionCallbackWithExceptionNoReturn;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.Nic;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.ReservationContextImpl;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.globo.globodns.cloudstack.element.GloboDnsElementService;
import com.globo.globonetwork.client.model.Vlan;
import com.globo.globonetwork.cloudstack.GloboNetworkEnvironmentVO;
import com.globo.globonetwork.cloudstack.GloboNetworkIpDetailVO;
import com.globo.globonetwork.cloudstack.GloboNetworkLoadBalancerEnvironment;
import com.globo.globonetwork.cloudstack.GloboNetworkNetworkVO;
import com.globo.globonetwork.cloudstack.GloboNetworkVipAccVO;
import com.globo.globonetwork.cloudstack.api.AcquireNewIpForLbInGloboNetworkCmd;
import com.globo.globonetwork.cloudstack.api.AddGloboNetworkEnvironmentCmd;
import com.globo.globonetwork.cloudstack.api.AddGloboNetworkHostCmd;
import com.globo.globonetwork.cloudstack.api.AddGloboNetworkLBEnvironmentCmd;
import com.globo.globonetwork.cloudstack.api.AddNetworkViaGloboNetworkCmd;
import com.globo.globonetwork.cloudstack.api.DeleteNetworkInGloboNetworkCmd;
import com.globo.globonetwork.cloudstack.api.DisassociateIpAddrFromGloboNetworkCmd;
import com.globo.globonetwork.cloudstack.api.ImportGloboNetworkLoadBalancerCmd;
import com.globo.globonetwork.cloudstack.api.ListAllEnvironmentsFromGloboNetworkCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboNetworkCapabilitiesCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboNetworkEnvironmentsCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboNetworkLBEnvironmentsCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboNetworkRealsCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboNetworkVipsCmd;
import com.globo.globonetwork.cloudstack.api.RemoveGloboNetworkEnvironmentCmd;
import com.globo.globonetwork.cloudstack.api.RemoveGloboNetworkLBEnvironmentCmd;
import com.globo.globonetwork.cloudstack.commands.AcquireNewIpForLbCommand;
import com.globo.globonetwork.cloudstack.commands.ActivateNetworkCommand;

import com.globo.globonetwork.cloudstack.commands.CreateNewVlanInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.DeallocateVlanFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetVipInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetVlanInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GloboNetworkErrorAnswer;
import com.globo.globonetwork.cloudstack.commands.ListAllEnvironmentsFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RegisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.ReleaseIpFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveNetworkInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveVipFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.UnregisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkEnvironmentDao;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkIpDetailDao;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkLoadBalancerEnvironmentDAO;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkNetworkDao;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkVipAccDao;
import com.globo.globonetwork.cloudstack.exception.CloudstackGloboNetworkException;
import com.globo.globonetwork.cloudstack.resource.GloboNetworkResource;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAllEnvironmentResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAllEnvironmentResponse.Environment;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAndIPResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse.Real;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVlanResponse;

@Component
public class GloboNetworkManager implements GloboNetworkService, PluggableService, Configurable {

    private static final Logger s_logger = Logger.getLogger(GloboNetworkManager.class);

    static final int NUMBER_OF_RESERVED_IPS_FROM_START = 1;
    static final int NUMBER_OF_RESERVED_IPS_BEFORE_END = 2;
    static final int NUMBER_OF_RESERVED_IPS_FOR_LB_FROM_START = 1;
    static final int NUMBER_OF_RESERVED_IPS_FOR_LB_BEFORE_END = 2;

    private static final ConfigKey<String> GloboNetworkConnectionTimeout = new ConfigKey<String>("Network", String.class, "globonetwork.connectiontimeout", "120000",
            "GloboNetwork connection timeout (in milliseconds)", true, ConfigKey.Scope.Global);
    private static final ConfigKey<String> GloboNetworkReadTimeout = new ConfigKey<String>("Network", String.class, "globonetwork.readtimeout", "120000",
            "GloboNetwork read timeout (in milliseconds)", true, ConfigKey.Scope.Global);
    private static final ConfigKey<String> GloboNetworkNumberOfRetries = new ConfigKey<String>("Network", String.class, "globonetwork.numberofretries", "0",
            "GloboNetwork number of retries", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Long> GloboNetworkVmEquipmentGroup = new ConfigKey<Long>("Network", Long.class, "globonetwork.vm.equipmentgroup", "",
            "Equipment group to be used when registering a VM NIC in GloboNetwork", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Long> GloboNetworkModelVmUser = new ConfigKey<Long>("Network", Long.class, "globonetwork.model.vm.user", "83",
            "GloboNetwork model id to be used for User VMs", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Long> GloboNetworkModelVmDomainRouter = new ConfigKey<Long>("Network", Long.class, "globonetwork.model.vm.domain.router", "84",
            "GloboNetwork model id to be used for Domain Router VMs", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Long> GloboNetworkModelVmConsoleProxy = new ConfigKey<Long>("Network", Long.class, "globonetwork.model.vm.console.proxy", "85",
            "GloboNetwork model id to be used for Console Proxy VMs", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Long> GloboNetworkModelVmSecondaryStorageVm = new ConfigKey<Long>("Network", Long.class, "globonetwork.model.vm.secondary.storage", "86",
            "GloboNetwork model id to be used for Secondary Storage VMs", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Long> GloboNetworkModelVmElasticIpVm = new ConfigKey<Long>("Network", Long.class, "globonetwork.model.vm.elastic.ip", "87",
            "GloboNetwork model id to be used for Elastic IP VMs", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Long> GloboNetworkModelVmElasticLoadBalancerVm = new ConfigKey<Long>("Network", Long.class, "globonetwork.model.vm.elastic.load.balancer", "88",
            "GloboNetwork model id to be used for Elastic Load Balancer VMs", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Long> GloboNetworkModelVmInternalLoadBalancerVm = new ConfigKey<Long>("Network", Long.class, "globonetwork.model.vm.internal.load.balancer",
            "89", "GloboNetwork model id to be used for Internal Load Balancer VMs", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Long> GloboNetworkModelVmUserBareMetal = new ConfigKey<Long>("Network", Long.class, "globonetwork.model.vm.user.bare.metal", "90",
            "GloboNetwork model id to be used for User Bare Metal", true, ConfigKey.Scope.Global);
    private static final ConfigKey<String> GloboNetworkDomainPattern = new ConfigKey<String>("Network", String.class, "globonetwork.domain.pattern", "",
            "Domain pattern to ensure in all networks created with GloboNetwork", true, ConfigKey.Scope.Global);
    private static final ConfigKey<String> GloboNetworkDomainSuffix = new ConfigKey<String>("Network", String.class, "globonetwork.domain.suffix", "",
            "Domain suffix to ensure in all networks created with GloboNetwork (empty you are free to create in any domain)", true, ConfigKey.Scope.Global);
    private static final ConfigKey<String> GloboNetworkVIPServerUrl = new ConfigKey<String>("Network", String.class, "globonetwork.vip.server.url", "",
            "Server URL to generate a new VIP request", true, ConfigKey.Scope.Global);
    private static final ConfigKey<Integer> GloboNetworkLBLockTimeout = new ConfigKey<Integer>("Network", Integer.class, "globonetwork.loadbalancer.lock.timeout", "60",
            "GloboNetwork Loadbalancer lock timeout (in seconds). This option avoid concurrent operations.", true, ConfigKey.Scope.Global);
    private static final ConfigKey<String> GloboNetworkLBAllowedSuffixes = new ConfigKey<String>("Network", String.class, "globonetwork.lb.allowed.suffixes", "",
            "Allowed domain suffixes for load balancers created with GloboNetwork. List of domain names separated by commas", true, ConfigKey.Scope.Global);

    private static final ConfigKey<Integer> minSubnetMaskInBIts = new ConfigKey<Integer>("Network", Integer.class, "globonetwork.min.subnetinbits", "24",
            "Min subnet mask in bits for allowed to create network in globonetwork, use 24 for a /24 subnet", true, ConfigKey.Scope.Global);

    private static final ConfigKey<Integer> maxSubnetMaskInBIts = new ConfigKey<Integer>("Network", Integer.class, "globonetwork.min.subnetinbits", "29",
            "Max subnet mask in bits for allowed to create network in globonetwork, use 29 for a /29 subnet", true, ConfigKey.Scope.Global);

    public static final ConfigKey<String> GloboNetworkRegion = new ConfigKey<String>("Network", String.class, "globonetwork.region", "",
            "Current region", true, ConfigKey.Scope.Global);


    // DAOs
    @Inject
    DomainDao _domainDao;
    @Inject
    HostDao _hostDao;
    @Inject
    DataCenterDao _dcDao;
    @Inject
    HostPodDao _hostPodDao;
    @Inject
    VlanDao _vlanDao;
    @Inject
    PhysicalNetworkDao _physicalNetworkDao;
    @Inject
    NetworkOfferingDao _networkOfferingDao;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    UserDao _userDao;
    @Inject
    NetworkDao _ntwkDao;
    @Inject
    NicDao _nicDao;
    @Inject
    NetworkServiceMapDao _ntwkSrvcDao;
    @Inject
    GloboNetworkNetworkDao _globoNetworkNetworkDao;
    @Inject
    GloboNetworkEnvironmentDao _globoNetworkEnvironmentDao;
    @Inject
    GloboNetworkVipAccDao _globoNetworkVipAccDao;
    @Inject
    GloboNetworkLoadBalancerEnvironmentDAO _globoNetworkLBEnvironmentDao;
    @Inject
    VMInstanceDao _vmDao;

    @Inject
    UserVmDao _userVmDao;

    @Inject
    IPAddressDao _ipAddrDao;
    @Inject
    PortableIpRangeDao _portableIpRangeDao;
    @Inject
    GloboNetworkIpDetailDao _globoNetworkIpDetailDao;
    @Inject
    PortableIpDao _portableIpDao;
    @Inject
    UserIpv6AddressDao _ipv6AddrDao;
    @Inject
    LoadBalancerPortMapDao _lbPortMapDao;
    @Inject
    GloboResourceConfigurationDao _globoResourceConfigurationDao;
    @Inject
    LoadBalancerDao _loadBalancerDao;

    // Managers
    @Inject
    NetworkModel _networkManager;
    @Inject
    AgentManager _agentMgr;
    @Inject
    ConfigurationManager _configMgr;
    @Inject
    ResourceManager _resourceMgr;
    @Inject
    DomainManager _domainMgr;
    @Inject
    NetworkOrchestrationService _networkMgr;
    @Inject
    AccountManager _accountMgr;
    @Inject
    ProjectManager _projectMgr;
    @Inject
    NetworkService _ntwSvc;
    @Inject
    IpAddressManager _ipAddrMgr;
    @Inject
    LoadBalancingRulesManager _lbMgr;
    @Inject
    LoadBalancingRulesService _lbService;
    @Inject
    GloboDnsElementService _globoDnsService;
    @Inject
    LoadBalancerOptionsDao _lbOptionsDao;

    @Override
    public boolean canEnable(Long physicalNetworkId) {
        if (physicalNetworkId == null) {
            return false;
        }
        List<GloboNetworkEnvironmentVO> list = _globoNetworkEnvironmentDao.listByPhysicalNetworkId(physicalNetworkId);
        if (list.isEmpty()) {
            throw new CloudRuntimeException("Before enabling GloboNetwork you must add an environment to your physical interface");
        }
        return true;
    }

    @Override
    @DB
    public Network createNetwork(String name, String displayText, Long zoneId, Long networkOfferingId, Long napiEnvironmentId, String networkDomain, ACLType aclType,
            String accountName, Long projectId, Long domainId, Boolean subdomainAccess, Boolean displayNetwork, Long aclId, Boolean isIpv6, Long subnet) throws ResourceAllocationException,
            ResourceUnavailableException, ConcurrentOperationException, InsufficientCapacityException {

        if ( isIpv6 && subnet != null ){
            throw new CloudRuntimeException("Subnet should be null when IPv6 is true! subnet: " + subnet + " isIPv6: " + isIpv6);
        }

        Account caller = CallContext.current().getCallingAccount();

        if ((accountName != null && domainId != null) || projectId != null) {
            _accountMgr.finalizeOwner(caller, accountName, domainId, projectId);
        }

        DataCenter zone = _dcDao.findById(zoneId);
        if (zone == null) {
            throw new InvalidParameterValueException("Specified zone id was not found");
        }
        int min = minSubnetMaskInBIts.value();
        int max = maxSubnetMaskInBIts.value();
        if ( subnet != null && (subnet <  min || subnet > max)){
            throw new CloudRuntimeException("Subnet should be equals or greater than " + min + " and equals or less than " + max + " (" + min + " => subnet =< " + max + "). Value: " + subnet);
        }

        Long physicalNetworkId = null;
        if (napiEnvironmentId != null) {
            GloboNetworkEnvironmentVO napiEnvironmentVO = null;
            for (PhysicalNetwork pNtwk : _physicalNetworkDao.listByZone(zoneId)) {
                napiEnvironmentVO = _globoNetworkEnvironmentDao.findByPhysicalNetworkIdAndEnvironmentId(pNtwk.getId(), napiEnvironmentId);
                if (napiEnvironmentVO != null) {
                    break;
                }
            }
            if (napiEnvironmentVO == null) {
                throw new InvalidParameterValueException("Unable to find a relationship between GloboNetwork environment and physical network");
            }
            physicalNetworkId = napiEnvironmentVO.getPhysicalNetworkId();
        } else {
            throw new InvalidParameterValueException("GloboNetwork enviromentId was not found");
        }

        // to avoid create a new vlan in GloboNetworkAPI, check first if networkDomain is valid
        if (isSupportedCustomNetworkDomain()) {
            String domainSuffix = GloboNetworkDomainSuffix.value();
            if (StringUtils.isNotBlank(domainSuffix) && !networkDomain.endsWith(domainSuffix)) {
                throw new InvalidParameterValueException("Network domain need ends with " + domainSuffix);
            }
        }

        Answer answer = createNewVlan(zoneId, name, displayText, napiEnvironmentId, isIpv6, subnet);

        GloboNetworkVlanResponse response = (GloboNetworkVlanResponse)answer;
        Long napiVlanId = response.getVlanId();

        Network network = null;

        try {
            network = this.createNetworkFromGloboNetworkVlan(napiVlanId, napiEnvironmentId, zoneId, networkOfferingId, physicalNetworkId, networkDomain, aclType, accountName,
                    projectId, domainId, subdomainAccess, displayNetwork, aclId);
        } catch (Exception e) {
            // Exception when creating network in Cloudstack. Roll back transaction in GloboNetwork
            s_logger.error("Reverting network creation in GloboNetwork due to error creating network", e);
            this.deallocateVlanFromGloboNetwork(zoneId, napiVlanId);

            throw new ResourceAllocationException(e.getLocalizedMessage(), ResourceType.network);
        }

        // if the network offering has persistent set to true, implement the
        // network
        NetworkOfferingVO ntwkOff = _networkOfferingDao.findById(networkOfferingId);
        if (ntwkOff.getIsPersistent()) {
            try {
                if (network.getState() == Network.State.Setup) {
                    s_logger.debug("Network id=" + network.getId() + " is already provisioned");
                    return network;
                }
                DeployDestination dest = new DeployDestination(zone, null, null, null);
                UserVO callerUser = _userDao.findById(CallContext.current().getCallingUserId());
                Journal journal = new Journal.LogJournal("Implementing " + network, s_logger);
                ReservationContext context = new ReservationContextImpl(UUID.randomUUID().toString(), journal, callerUser, caller);
                s_logger.debug("Implementing network " + network + " as a part of network provision for persistent network");
                @SuppressWarnings("unchecked")
                Pair<NetworkGuru, NetworkVO> implementedNetwork = (Pair<NetworkGuru, NetworkVO>)_networkMgr.implementNetwork(network.getId(), dest, context);
                if (implementedNetwork.first() == null) {
                    s_logger.warn("Failed to provision the network " + network);
                }
                network = implementedNetwork.second();
            } catch (ResourceUnavailableException ex) {
                s_logger.warn("Failed to implement persistent guest network " + network + "due to ", ex);
                CloudRuntimeException e = new CloudRuntimeException("Failed to implement persistent guest network");
                e.addProxyObject(network.getUuid(), "networkId");
                throw e;
            }
        }

        return network;
    }

    private Network createNetworkFromGloboNetworkVlan(final Long vlanId, final Long napiEnvironmentId, Long zoneId, final Long networkOfferingId, final Long physicalNetworkId,
            final String networkDomain, final ACLType aclType, String accountName, Long projectId, final Long domainId, final Boolean subdomainAccess,
            final Boolean displayNetwork, Long aclId) throws CloudException, ResourceUnavailableException, ResourceAllocationException, ConcurrentOperationException,
            InsufficientCapacityException {

        final Account caller = CallContext.current().getCallingAccount();

        final Account owner;
        if ((accountName != null && domainId != null) || projectId != null) {
            owner = _accountMgr.finalizeOwner(caller, accountName, domainId, projectId);
        } else {
            owner = caller;
        }

        // Only domain and account ACL types are supported in Action.
        if (aclType == null || !(aclType == ACLType.Domain || aclType == ACLType.Account)) {
            throw new InvalidParameterValueException("AclType should be " + ACLType.Domain + " or " + ACLType.Account + " for network of type " + Network.GuestType.Shared);
        }

        final boolean isDomainSpecific = true;

        // Validate network offering
        final NetworkOfferingVO ntwkOff = _networkOfferingDao.findById(networkOfferingId);
        if (ntwkOff == null || ntwkOff.isSystemOnly()) {
            InvalidParameterValueException ex = new InvalidParameterValueException("Unable to find network offering by specified id");
            if (ntwkOff != null) {
                ex.addProxyObject(ntwkOff.getUuid(), "networkOfferingId");
            }
            throw ex;
        } else if (GuestType.Shared != ntwkOff.getGuestType()) {
            InvalidParameterValueException ex = new InvalidParameterValueException("GloboNetwork can handle only network offering with guest type shared");
            if (ntwkOff != null) {
                ex.addProxyObject(ntwkOff.getUuid(), "networkOfferingId");
            }
            throw ex;
        }
        // validate physical network and zone
        // Check if physical network exists
        final PhysicalNetwork pNtwk;
        if (physicalNetworkId != null) {
            pNtwk = _physicalNetworkDao.findById(physicalNetworkId);
            if (pNtwk == null) {
                throw new InvalidParameterValueException("Unable to find a physical network having the specified physical network id");
            }
        } else {
            throw new InvalidParameterValueException("invalid physicalNetworkId " + physicalNetworkId);
        }

        if (zoneId == null) {
            zoneId = pNtwk.getDataCenterId();
        }

        final DataCenter zone = _dcDao.findById(zoneId);
        if (zone == null) {
            throw new InvalidParameterValueException("Specified zone id was not found");
        }

        if (Grouping.AllocationState.Disabled == zone.getAllocationState() && !_accountMgr.isAdmin(caller.getAccountId())) {
            // See DataCenterVO.java
            PermissionDeniedException ex = new PermissionDeniedException("Cannot perform this operation since specified Zone is currently disabled");
            ex.addProxyObject(zone.getUuid(), "zoneId");
            throw ex;
        }

        if (domainId != null) {
            DomainVO domain = _domainDao.findById(domainId);
            if (domain == null) {
                throw new InvalidParameterValueException("Unable to find domain by specified id");
            }
            _accountMgr.checkAccess(caller, domain);
        }

        if (_configMgr.isOfferingForVpc(ntwkOff)) {
            throw new InvalidParameterValueException("Network offering can't be used for VPC networks");
        }

        // CallContext.register(CallContext.current().getCallingUserId(), owner.getAccountId());

        /////// GloboNetwork specific code ///////

        // Get VlanInfo from GloboNetwork
        GetVlanInfoFromGloboNetworkCommand cmd = new GetVlanInfoFromGloboNetworkCommand();
        cmd.setVlanId(vlanId);

        final GloboNetworkVlanResponse vlanResponse = (GloboNetworkVlanResponse)callCommand(cmd, zoneId);

        String networkAddress = vlanResponse.getNetworkAddress();
        final String netmask = vlanResponse.getMask();
        // FIXME This does not work with IPv6!
        final String cidr = networkAddress + "/" + vlanResponse.getBlock();

        String gatewayStr = null;
        String startIPStr = null;
        String endIPStr = null;
        final Long vlanNum = vlanResponse.getVlanNum();

        String startIPv6Str = null;
        String endIPv6Str = null;
        String ip6GatewayStr = null;

        if (vlanResponse.isv6()) {
            com.googlecode.ipv6.IPv6Network ipv6Network = com.googlecode.ipv6.IPv6Network.fromString(cidr);
            com.googlecode.ipv6.IPv6Address ipv6Start = ipv6Network.getFirst().add(1);
            ip6GatewayStr = ipv6Start.toString();
            startIPv6Str = ipv6Start.add(NUMBER_OF_RESERVED_IPS_FROM_START).toString();

            com.googlecode.ipv6.IPv6Address ipv6End = ipv6Network.getLast();
            endIPv6Str = ipv6End.subtract(NUMBER_OF_RESERVED_IPS_BEFORE_END).toString();
        } else {
            String ranges[] = NetUtils.ipAndNetMaskToRange(networkAddress, netmask);
            gatewayStr = ranges[0];
            startIPStr = NetUtils.long2Ip(NetUtils.ip2Long(ranges[0]) + NUMBER_OF_RESERVED_IPS_FROM_START);
            endIPStr = NetUtils.long2Ip(NetUtils.ip2Long(ranges[1]) - NUMBER_OF_RESERVED_IPS_BEFORE_END);
        }

        final String startIP = startIPStr;
        final String endIP = endIPStr;
        final String gateway = gatewayStr;

        final String startIPv6 = startIPv6Str;
        final String endIPv6 = endIPv6Str;
        final String ip6Gateway = ip6GatewayStr;
        final String ip6Cidr = cidr;

        s_logger.info("Creating network with name " + vlanResponse.getVlanName() + " (" + vlanResponse.getVlanId() + "), network " + networkAddress + " gateway " + gateway
                + " startIp " + startIP + " endIp " + endIP + " cidr " + cidr + " startIPv6 " + startIPv6 + " end IPv6 " + endIPv6 + " IPv6 gateway " + ip6Gateway);
        /////// End of GloboNetwork specific code ///////

        Network network = Transaction.execute(new TransactionCallbackWithException<Network, CloudException>() {

            @Override
            public Network doInTransaction(TransactionStatus status) throws CloudException {
                Boolean newSubdomainAccess = subdomainAccess;
                Long sharedDomainId = null;
                Domain domain = null;
                if (isDomainSpecific) {
                    if (domainId != null) {
                        sharedDomainId = domainId;
                        domain = _domainDao.findById(domainId);
                    } else {
                        sharedDomainId = owner.getDomainId();
                        newSubdomainAccess = true;
                    }
                }

                String newNetworkDomain = networkDomain;
                if (!isSupportedCustomNetworkDomain()) {
                    // overwrite networkDomain
                    newNetworkDomain = generateNetworkDomain(zone, vlanResponse);
                }

                Network network = _networkMgr.createGuestNetwork(networkOfferingId.longValue(), vlanResponse.getVlanName(), vlanResponse.getVlanDescription(), gateway, cidr,
                        String.valueOf(vlanResponse.getVlanNum()), false, newNetworkDomain, owner, sharedDomainId, pNtwk, zone.getId(), aclType, newSubdomainAccess, null, // vpcId,
                        ip6Gateway, ip6Cidr, displayNetwork, null, null // isolatedPvlan
                        );

                // Save relashionship with napi and network
                GloboNetworkNetworkVO napiNetworkVO = new GloboNetworkNetworkVO(vlanId, network.getId(), napiEnvironmentId);
                napiNetworkVO = _globoNetworkNetworkDao.persist(napiNetworkVO);

                // if (caller.getType() == Account.ACCOUNT_TYPE_ADMIN || caller.getType() == Account.ACCOUNT_TYPE_DOMAIN_ADMIN) {
                // Create vlan ip range
                _configMgr.createVlanAndPublicIpRange(pNtwk.getDataCenterId(), network.getId(), physicalNetworkId, false, false, (Long)null, startIP, endIP, gateway, netmask,
                        vlanNum.toString(), false, domain, null, startIPv6, endIPv6, ip6Gateway, ip6Cidr);
                // }
                return network;
            }
        });
        return network;
    }

    protected String generateNetworkDomain(DataCenter zone, GloboNetworkVlanResponse vlan) {
        Map<String, Object> context = new HashMap<String, Object>();
        context.put("zone", zone);
        context.put("vlan", vlan);

        String newNetworkDomain = formatter(GloboNetworkDomainPattern.value(), context);
        newNetworkDomain += GloboNetworkDomainSuffix.value().startsWith(".") ? GloboNetworkDomainSuffix.value() : "." + GloboNetworkDomainSuffix.value();
        return newNetworkDomain;
    }

    /**
     * Replace variables in a string template: #{obj.property}.
     * @see {@linktourl http://docs.spring.io/spring/docs/current/spring-framework-reference/html/expressions.html}
     * @param template
     * @param context
     * @return
     */
    protected String formatter(String template, Map<String, Object> context) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext(context);
        evalContext.addPropertyAccessor(new MapAccessor());

        ExpressionParser parser = new SpelExpressionParser();
        Expression exp = parser.parseExpression(template, new TemplateParserContext());
        String formatted = exp.getValue(evalContext, String.class);
        return formatted;
    }

    protected GloboNetworkVlanResponse createNewVlan(Long zoneId, String name, String description, Long globoNetworkEnvironmentId, Boolean isIpv6, Long subnet) {

        CreateNewVlanInGloboNetworkCommand cmd = new CreateNewVlanInGloboNetworkCommand();
        cmd.setVlanName(name);
        cmd.setVlanDescription(description);
        cmd.setGloboNetworkEnvironmentId(globoNetworkEnvironmentId);
        cmd.setIsIpv6(isIpv6);
        cmd.setSubnet(subnet);

        return (GloboNetworkVlanResponse)callCommand(cmd, zoneId);
    }
    @Override
    public Answer callCommand(Command cmd, Long zoneId) {
        return callCommand(cmd, zoneId, true);
    }

    private Answer callCommand(Command cmd, Long zoneId, boolean raisesExceptionWhenNoAnswer) {
        HostVO napiHost = getGloboNetworkHost(zoneId);
        if (napiHost == null) {
            throw new CloudstackGloboNetworkException("Could not find the GloboNetwork resource");
        }

        Answer answer = _agentMgr.easySend(napiHost.getId(), cmd);
        if (answer == null || !answer.getResult()) {

            if (answer instanceof GloboNetworkErrorAnswer) {
                GloboNetworkErrorAnswer napiAnswer = (GloboNetworkErrorAnswer)answer;
                throw new CloudstackGloboNetworkException(napiAnswer.getNapiCode(), napiAnswer.getNapiDescription(), napiAnswer.getNdcContext());
            } else {
                if (raisesExceptionWhenNoAnswer) {
                    String msg = "Error executing command " + cmd + ". Maybe GloboNetwork Host is down";
                    msg = answer == null ? msg : answer.getDetails();
                    throw new CloudRuntimeException(msg);
                }
            }
        }

        return answer;
    }
    @Override
    public HostVO getGloboNetworkHost(Long zoneId) {
        return _hostDao.findByTypeNameAndZoneId(zoneId, Provider.GloboNetwork.getName(), Type.L2Networking);
    }

    @Override
    public Network validateNic(NicProfile nicProfile, VirtualMachineProfile vm, Network network) throws InsufficientVirtualNetworkCapacityException,
            InsufficientAddressCapacityException {

        GetVlanInfoFromGloboNetworkCommand cmd = new GetVlanInfoFromGloboNetworkCommand();
        cmd.setVlanId(getGloboNetworkVlanId(network.getId()));

        String msg = "Unable to get VLAN " + getGloboNetworkVlanId(network.getId()) + " info from GloboNetwork";
        Answer answer = this.callCommand(cmd, network.getDataCenterId());
        if (answer == null || !answer.getResult()) {
            msg = answer == null ? msg : answer.getDetails();
            throw new InsufficientVirtualNetworkCapacityException(msg, Nic.class, nicProfile.getId());
        }

        GloboNetworkVlanResponse vlanResponse = (GloboNetworkVlanResponse) answer;

        String networkAddress = vlanResponse.getNetworkAddress();
        String netmask = vlanResponse.getMask();

        BigInteger ip;
        BigInteger ipRangeStart;
        BigInteger ipRangeEnd;

        if (vlanResponse.isv6()) {
            com.googlecode.ipv6.IPv6Address ipv6 = com.googlecode.ipv6.IPv6Address.fromString(nicProfile.getIPv6Address());
            try {
                ip = new BigInteger(ipv6.toInetAddress().getAddress());

                com.googlecode.ipv6.IPv6Network ipv6Network = com.googlecode.ipv6.IPv6Network.fromAddressAndMask(
                        com.googlecode.ipv6.IPv6Address.fromString(networkAddress),
                        com.googlecode.ipv6.IPv6NetworkMask.fromAddress(com.googlecode.ipv6.IPv6Address.fromString(netmask)));

                com.googlecode.ipv6.IPv6Address ipv6Start = ipv6Network.getFirst();
                ipRangeStart = new BigInteger(ipv6Start.toInetAddress().getAddress());

                com.googlecode.ipv6.IPv6Address ipv6End = ipv6Network.getLast();
                ipRangeEnd = new BigInteger(ipv6End.toInetAddress().getAddress());
            } catch (UnknownHostException ex) {
                throw new InvalidParameterValueException("Nic IP " + nicProfile.getIPv6Address() + " is invalid");
            }
        } else {
            ip = BigInteger.valueOf(NetUtils.ip2Long(nicProfile.getIPv4Address()));
            String ranges[] = NetUtils.ipAndNetMaskToRange(networkAddress, netmask);
            ipRangeStart = BigInteger.valueOf(NetUtils.ip2Long(ranges[0]));
            ipRangeEnd = BigInteger.valueOf(NetUtils.ip2Long(ranges[1]));
        }

        if (!(ip.compareTo(ipRangeStart) >= 0  && ip.compareTo(ipRangeEnd) <= 0)) {
            throw new InvalidParameterValueException("Nic IP " + nicProfile.getIPv4Address() + " does not belong to network " + networkAddress + " in vlanId " + cmd.getVlanId());
        }

        // everything is ok
        return network;
    }

    @Override
    public void implementNetwork(Network network) throws ConfigurationException {
        Long vlanId = getGloboNetworkVlanId(network.getId());
        if (vlanId == null) {
            throw new CloudRuntimeException("Inconsistency. Network " + network.getName() + " there is not relation with GloboNetwork");
        }

        GloboNetworkVlanResponse vlanResponse = getVlanFromGloboNetwork(network, vlanId);
        if (!vlanResponse.isActive()) {
            // Create network in equipment
            ActivateNetworkCommand activateCmd = new ActivateNetworkCommand(vlanResponse.getNetworkId(), vlanResponse.isv6());
            Answer cmdAnswer = callCommand(activateCmd, network.getDataCenterId());
            if (cmdAnswer == null || !cmdAnswer.getResult()) {
                throw new CloudRuntimeException("Unable to create network in GloboNetwork: VlanId " + vlanId + " networkId " + vlanResponse.getNetworkId());
            }
            s_logger.info("Network ready to use: VlanId " + vlanId + " networkId " + vlanResponse.getNetworkId());
        } else {
            s_logger.warn("Network already created in GloboNetwork: VlanId " + vlanId + " networkId " + vlanResponse.getNetworkId());
        }
    }

    /**
     * Returns VlanId (in GloboNetwork) given an Network. If network is not
     * associated with GloboNetwork, <code>null</code> will be returned.
     *
     * @param networkId
     * @return
     */
    private Long getGloboNetworkVlanId(Long networkId) {
        if (networkId == null) {
            return null;
        }
        GloboNetworkNetworkVO vo = _globoNetworkNetworkDao.findByNetworkId(networkId);
        if (vo == null) {
            return null;
        }
        return vo.getGloboNetworkVlanId();
    }

    /**
     * Get the number of vlan associate with {@code network}.
     *
     * @param broadcastUri
     * @return
     */
    private Integer getVlanNum(URI broadcastUri) {
        if (broadcastUri == null) {
            return null;
        }
        try {
            Integer vlanNum = Integer.valueOf(broadcastUri.getHost());
            return vlanNum;
        } catch (NumberFormatException nfe) {
            String msg = "Invalid Vlan number in broadcast URI " + broadcastUri;
            s_logger.error(msg);
            throw new CloudRuntimeException(msg, nfe);
        }
    }

    @Override
    @DB
    public GloboNetworkEnvironmentVO addGloboNetworkEnvironment(Long physicalNetworkId, String name, Long globoNetworkEnvironmentId) {

        if (name == null || name.trim().isEmpty()) {
            throw new InvalidParameterValueException("Invalid name: " + name);
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

        Long zoneId = pNtwk.getDataCenterId();

        // now, check if environment exists in GloboNetwork
        if (globoNetworkEnvironmentId != null) {
            Environment environment = getEnvironment(physicalNetworkId, globoNetworkEnvironmentId);
            if (environment == null) {
                throw new InvalidParameterValueException("Unable to find in GloboNetwork an enviroment having the specified environment id");
            }
        } else {
            throw new InvalidParameterValueException("Invalid GloboNetwork environmentId: " + globoNetworkEnvironmentId);
        }

        // Check if there is a environment with same id or name in this zone.
        List<GloboNetworkEnvironmentVO> globoNetworkEnvironments = listGloboNetworkEnvironmentsFromDB(null, zoneId);
        for (GloboNetworkEnvironmentVO globoNetworkEnvironment : globoNetworkEnvironments) {
            if (globoNetworkEnvironment.getName().equalsIgnoreCase(name)) {
                throw new InvalidParameterValueException("GloboNetwork environment with name " + name + " already exists in zone " + zoneId);
            }
            if (globoNetworkEnvironment.getGloboNetworkEnvironmentId() == globoNetworkEnvironmentId) {
                throw new InvalidParameterValueException("GloboNetwork environment with environmentId " + globoNetworkEnvironmentId + " already exists in zoneId " + zoneId);
            }
        }

        GloboNetworkEnvironmentVO napiEnvironmentVO = new GloboNetworkEnvironmentVO(physicalNetworkId, name, globoNetworkEnvironmentId);
        _globoNetworkEnvironmentDao.persist(napiEnvironmentVO);
        return napiEnvironmentVO;
    }

    @Override
    @DB
    public GloboNetworkLoadBalancerEnvironment addGloboNetworkLBEnvironment(final String name, Long physicalNetworkId, Long globoNetworkEnvironmentId,
            final Long globoNetworkLBEnvironmentId) throws ResourceAllocationException {

        if (name == null || name.trim().isEmpty()) {
            throw new InvalidParameterValueException("Invalid name: " + name);
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

        // Check if there is a environment with same id or name in this zone.
        final GloboNetworkEnvironmentVO globoNetworkEnvironment = _globoNetworkEnvironmentDao.findByPhysicalNetworkIdAndEnvironmentId(pNtwk.getId(), globoNetworkEnvironmentId);
        if (globoNetworkEnvironment == null) {
            throw new InvalidParameterValueException("Could not find a relationship between GloboNetwork Environment " + globoNetworkEnvironmentId + " and physical network "
                    + physicalNetworkId);
        }

        // Check if there is a LB environment with same id or name in this zone.
        List<GloboNetworkLoadBalancerEnvironment> globoNetworkLBEnvironments = listGloboNetworkLBEnvironmentsFromDB(pNtwk.getId(), null, globoNetworkEnvironmentId);
        for (GloboNetworkLoadBalancerEnvironment globoNetworkLBEnvironment : globoNetworkLBEnvironments) {
            if (globoNetworkLBEnvironment.getName().equalsIgnoreCase(name)) {
                throw new InvalidParameterValueException("LB environment with name " + name + " already exists.");
            }
            if (globoNetworkLBEnvironment.getGloboNetworkLoadBalancerEnvironmentId() == globoNetworkLBEnvironmentId) {
                throw new InvalidParameterValueException("Relationship between Environment " + globoNetworkEnvironmentId + " and LB Network " + globoNetworkLBEnvironmentId
                        + " already exists.");
            }
        }

        try {
            GloboNetworkLoadBalancerEnvironment globoNetworkLBEnvironmentVO = Transaction
                    .execute(new TransactionCallbackWithException<GloboNetworkLoadBalancerEnvironment, CloudException>() {

                        @Override
                        public GloboNetworkLoadBalancerEnvironment doInTransaction(TransactionStatus status) throws CloudException {
                            GloboNetworkLoadBalancerEnvironment globoNetworkLBEnvironmentVO = new GloboNetworkLoadBalancerEnvironment(name, globoNetworkEnvironment.getId(),
                                    globoNetworkLBEnvironmentId);
                            _globoNetworkLBEnvironmentDao.persist(globoNetworkLBEnvironmentVO);
                            return globoNetworkLBEnvironmentVO;
                        }
                    });

            return globoNetworkLBEnvironmentVO;

        } catch (CloudException e) {
            // Exception when defining IP ranges in Cloudstack
            throw new ResourceAllocationException(e.getLocalizedMessage(), ResourceType.public_ip);
        }
    }

    @Override
    @DB
    public Host addGloboNetworkHost(Long physicalNetworkId, String username, String password, String url) {

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
        params.put("guid", "globonetwork-" + String.valueOf(zoneId));
        params.put("zoneId", String.valueOf(zoneId));
        params.put("name", Provider.GloboNetwork.getName());

        String readTimeout = GloboNetworkReadTimeout.value();
        String connectTimeout = GloboNetworkConnectionTimeout.value();
        String numberOfRetries = GloboNetworkNumberOfRetries.value();

        params.put("url", url);
        params.put("username", username);
        params.put("password", password);
        params.put("readTimeout", readTimeout);
        params.put("connectTimeout", connectTimeout);
        params.put("numberOfRetries", numberOfRetries);

        final Map<String, Object> hostDetails = new HashMap<String, Object>();
        hostDetails.putAll(params);

        Host host = Transaction.execute(new TransactionCallback<Host>() {

            @Override
            public Host doInTransaction(TransactionStatus status) {
                GloboNetworkResource resource = new GloboNetworkResource();

                try {
                    resource.configure(Provider.GloboNetwork.getName(), hostDetails);

                    Host host = _resourceMgr.addHost(zoneId, resource, resource.getType(), params);
                    return host;
                } catch (ConfigurationException e) {
                    throw new CloudRuntimeException(e);
                }
            }
        });
        return host;
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(AcquireNewIpForLbInGloboNetworkCmd.class);
        cmdList.add(CreateGloboLoadBalancerCmd.class);
        cmdList.add(DeleteGloboLoadBalancerCmd.class);
        cmdList.add(AddGloboNetworkEnvironmentCmd.class);
        cmdList.add(AddGloboNetworkHostCmd.class);
        cmdList.add(AddGloboNetworkLBEnvironmentCmd.class);
        cmdList.add(AddNetworkViaGloboNetworkCmd.class);
        cmdList.add(DeleteNetworkInGloboNetworkCmd.class);
        cmdList.add(DisassociateIpAddrFromGloboNetworkCmd.class);
        cmdList.add(ImportGloboNetworkLoadBalancerCmd.class);
        cmdList.add(ListAllEnvironmentsFromGloboNetworkCmd.class);
        cmdList.add(ListGloboNetworkCapabilitiesCmd.class);
        cmdList.add(ListGloboNetworkEnvironmentsCmd.class);
        cmdList.add(ListGloboNetworkLBEnvironmentsCmd.class);
        cmdList.add(ListGloboNetworkRealsCmd.class);
        cmdList.add(ListGloboNetworkPoolOptionsCmd.class);
        cmdList.add(ListGloboNetworkVipsCmd.class);
        cmdList.add(RemoveGloboNetworkEnvironmentCmd.class);
        cmdList.add(RemoveGloboNetworkLBEnvironmentCmd.class);
        cmdList.add(ListGloboNetworkLBCacheGroupsCmd .class);
        cmdList.add(ListGloboNetworkPoolsCmd.class);
        cmdList.add(ListGloboNetworkExpectedHealthchecksCmd.class);
        cmdList.add(GetGloboNetworkPoolCmd.class);
        cmdList.add(UpdateGloboNetworkPoolCmd.class);
        cmdList.add(CreateGloboNetworkPoolCmd.class);
        cmdList.add(DeleteGloboNetworkPoolCmd.class);
        cmdList.add(ListGloboLbNetworksCmd.class);
        cmdList.add(RegisterDnsForResourceCmd.class);
        cmdList.add(GetGloboResourceConfigurationCmd.class);
        return cmdList;
    }

    @Override
    public List<Environment> listAllEnvironmentsFromGloboNetwork(Long physicalNetworkId) {

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

        Long zoneId = pNtwk.getDataCenterId();

        ListAllEnvironmentsFromGloboNetworkCommand cmd = new ListAllEnvironmentsFromGloboNetworkCommand();

        Answer answer = callCommand(cmd, zoneId);

        List<Environment> environments = ((GloboNetworkAllEnvironmentResponse)answer).getEnvironmentList();
        return environments;
    }

    /**
     * Get <code>Environment</code> object from environmentId.
     * @param environmentId
     * @return Return null if environment was not found.
     */
    protected Environment getEnvironment(Long physicaNetworkId, Long environmentId) {
        if (environmentId == null) {
            return null;
        }

        Environment resultEnvironment = null;
        for (Environment environment : listAllEnvironmentsFromGloboNetwork(physicaNetworkId)) {
            if (environmentId.equals(environment.getId())) {
                resultEnvironment = environment;
                break;
            }
        }
        return resultEnvironment;
    }

    private void handleNetworkUnavailableError(CloudstackGloboNetworkException e) {
        if (e.getNapiCode() == 116) {
            // If this is the return code, it means that the vlan/network no longer exists in GloboNetwork
            // and we should continue to remove it from CloudStack
            s_logger.warn("Inconsistency between CloudStack and GloboNetwork");
            return;
        } else {
            // Otherwise, there was a different error and we should abort the operation
            throw e;
        }
    }

    public boolean destroyGloboNetwork(long networkId, boolean forced) {

        Network network = _ntwSvc.getNetwork(networkId);
        if (network == null) {
            InvalidParameterValueException ex = new InvalidParameterValueException("unable to find network with specified id");
            ex.addProxyObject(String.valueOf(networkId), "networkId");
            throw ex;
        }

        GloboNetworkNetworkVO napiNetworkVO = _globoNetworkNetworkDao.findByNetworkId(network.getId());
        if (napiNetworkVO == null) {
            InvalidParameterValueException ex = new InvalidParameterValueException("Only networks managed by GloboNetwork may be deleted by this method");
            ex.addProxyObject(String.valueOf(networkId), "networkId");
            throw ex;
        }

        Account caller = CallContext.current().getCallingAccount();
        User userCaller = CallContext.current().getCallingUser();
        String contextId = CallContext.current().getContextId();

        // Perform permission check
        _accountMgr.checkAccess(caller, null, true, network);

        // ACS with allow exclusion of shared network by admin users, so after check
        // permission, let's change to system account to perform destroy network

        try {
            CallContext.register(_accountMgr.getSystemUser(), _accountMgr.getSystemAccount(), contextId);
            CallContext.current().putContextParameter(Network.class.getName(), network.getUuid());
            return _ntwSvc.deleteNetwork(networkId, forced);
        } finally {
            // restore context
            CallContext.register(userCaller, caller, contextId);
        }
    }

    @Override
    public void removeNetworkFromGloboNetwork(Network network) {
        try {
            // Make sure the VLAN is valid
            Vlan vlan = this.getVlanInfoFromGloboNetwork(network);
            //Get Network type (ipv4 or ipv6) and network ID from Globo Network API
            GloboNetworkVlanResponse vlanResponse = getVlanFromGloboNetwork(network, vlan.getId());
            RemoveNetworkInGloboNetworkCommand cmd = new RemoveNetworkInGloboNetworkCommand();
            cmd.setNetworkId(vlanResponse.getNetworkId());
            cmd.setIsIpv6(vlanResponse.isv6());

            this.callCommand(cmd, network.getDataCenterId());
        } catch (CloudstackGloboNetworkException e) {
            handleNetworkUnavailableError(e);
        }
    }

    protected GloboNetworkVlanResponse getVlanFromGloboNetwork(Network network, Long vlanId) {
        GetVlanInfoFromGloboNetworkCommand cmd = new GetVlanInfoFromGloboNetworkCommand();
        cmd.setVlanId(vlanId);
        return (GloboNetworkVlanResponse) callCommand(cmd, network.getDataCenterId());
    }

    @Override
    @DB
    public void deallocateVlanFromGloboNetwork(Network network) {

        try {
            GloboNetworkNetworkVO napiNetworkVO = _globoNetworkNetworkDao.findByNetworkId(network.getId());
            if (napiNetworkVO != null) {
                this.deallocateVlanFromGloboNetwork(network.getDataCenterId(), napiNetworkVO.getGloboNetworkVlanId());
                _globoNetworkNetworkDao.remove(napiNetworkVO.getId());
            }

        } catch (CloudstackGloboNetworkException e) {
            handleNetworkUnavailableError(e);
        }
    }

    public void deallocateVlanFromGloboNetwork(Long zoneId, Long vlanId) {

        DeallocateVlanFromGloboNetworkCommand cmd = new DeallocateVlanFromGloboNetworkCommand();
        cmd.setVlanId(vlanId);

        this.callCommand(cmd, zoneId);
    }

    @Override
    public List<GloboNetworkEnvironmentVO> listGloboNetworkEnvironmentsFromDB(Long physicalNetworkId, Long zoneId) {
        List<GloboNetworkEnvironmentVO> globoNetworkEnvironmentsVOList;

        if (physicalNetworkId != null) {
            // Check if physical network exists
            PhysicalNetwork pNtwk = _physicalNetworkDao.findById(physicalNetworkId);
            if (pNtwk == null) {
                throw new InvalidParameterValueException("Unable to find a physical network having the specified physical network id");
            }

            globoNetworkEnvironmentsVOList = _globoNetworkEnvironmentDao.listByPhysicalNetworkId(physicalNetworkId);

        } else if (zoneId != null) {
            // Check if zone exists
            DataCenter zone = _dcDao.findById(zoneId);
            if (zone == null) {
                throw new InvalidParameterValueException("Specified zone id was not found");
            }

            globoNetworkEnvironmentsVOList = new ArrayList<GloboNetworkEnvironmentVO>();
            for (PhysicalNetworkVO physicalNetwork : _physicalNetworkDao.listByZone(zoneId)) {
                List<GloboNetworkEnvironmentVO> partialResult = _globoNetworkEnvironmentDao.listByPhysicalNetworkId(physicalNetwork.getId());
                if (partialResult != null) {
                    globoNetworkEnvironmentsVOList.addAll(partialResult);
                }
            }
        } else {
            globoNetworkEnvironmentsVOList = _globoNetworkEnvironmentDao.listAll();
        }

        return globoNetworkEnvironmentsVOList;
    }

    @Override
    public List<GloboNetworkLoadBalancerEnvironment> listGloboNetworkLBEnvironmentsFromDB(Long physicalNetworkId, Long networkId, Long globoNetworkEnvironmentId) {

        Long glbEnvId = null;
        if (networkId != null) {
            // Retrieve glbEnvId from network
            GloboNetworkNetworkVO globoNetworkNetworkVO = _globoNetworkNetworkDao.findByNetworkId(networkId);
            if (globoNetworkNetworkVO == null) {
                throw new InvalidParameterValueException("Unable to find mapping for networkId " + networkId);
            }
            glbEnvId = globoNetworkNetworkVO.getGloboNetworkEnvironmentId();
        } else if (globoNetworkEnvironmentId != null) {
            glbEnvId = globoNetworkEnvironmentId;
        } else {
            throw new InvalidParameterValueException("NetworkId or GloboNetworkEnvironmentId is required");
        }

        // Retrieve napiEnvironment from DB
        GloboNetworkEnvironmentVO globoNetworkEnvironment = _globoNetworkEnvironmentDao.findByPhysicalNetworkIdAndEnvironmentId(physicalNetworkId, glbEnvId);

        if (globoNetworkEnvironment == null) {
            // No physical network/environment pair registered in the database.
            throw new InvalidParameterValueException("Unable to find a relationship between physical network=" + physicalNetworkId + " and GloboNetwork environment="
                    + globoNetworkEnvironmentId);
        }

        List<GloboNetworkLoadBalancerEnvironment> globoNetworkLBEnvironmentVOList;

        if (glbEnvId != null) {
            globoNetworkLBEnvironmentVOList = _globoNetworkLBEnvironmentDao.listByEnvironmentRefId(globoNetworkEnvironment.getId());

        } else {
            globoNetworkLBEnvironmentVOList = _globoNetworkLBEnvironmentDao.listAll();
        }

        return globoNetworkLBEnvironmentVOList;
    }

    @Override
    @DB
    public boolean removeGloboNetworkEnvironment(Long physicalNetworkId, Long globoNetworkEnvironmentId) {

        // Check if there are any networks in this GloboNetwork environment
        List<GloboNetworkNetworkVO> associationList = _globoNetworkNetworkDao.listByEnvironmentId(globoNetworkEnvironmentId);

        if (!associationList.isEmpty()) {
            throw new InvalidParameterValueException("There are active networks on environment " + globoNetworkEnvironmentId
                    + ". Please delete them before removing this environment.");
        }

        // Retrieve napiEnvironment from DB
        GloboNetworkEnvironmentVO globoNetworkEnvironment = _globoNetworkEnvironmentDao.findByPhysicalNetworkIdAndEnvironmentId(physicalNetworkId, globoNetworkEnvironmentId);

        if (globoNetworkEnvironment == null) {
            // No physical network/environment pair registered in the database.
            throw new InvalidParameterValueException("Unable to find a relationship between physical network=" + physicalNetworkId + " and GloboNetwork environment="
                    + globoNetworkEnvironmentId);
        }

        boolean result = _globoNetworkEnvironmentDao.remove(globoNetworkEnvironment.getId());

        return result;
    }

    @Override
    @DB
    public boolean removeGloboNetworkLBEnvironment(Long physicalNetworkId, Long globoNetworkEnvironmentId, Long globoNetworkLBEnvironmentId) {

        // Retrieve napiEnvironment from DB
        GloboNetworkEnvironmentVO globoNetworkEnvironment = _globoNetworkEnvironmentDao.findByPhysicalNetworkIdAndEnvironmentId(physicalNetworkId, globoNetworkEnvironmentId);

        if (globoNetworkEnvironment == null) {
            // No physical network/environment pair registered in the database.
            throw new InvalidParameterValueException("Unable to find a relationship between physical network=" + physicalNetworkId + " and GloboNetwork environment="
                    + globoNetworkEnvironmentId);
        }

        // Retrieve LB Environment from DB
        final GloboNetworkLoadBalancerEnvironment globoNetworkLBEnvironmentVO = _globoNetworkLBEnvironmentDao.findByEnvironmentRefAndLBNetwork(globoNetworkEnvironment.getId(),
                globoNetworkLBEnvironmentId);
        if (globoNetworkLBEnvironmentVO == null) {
            throw new InvalidParameterValueException("Unable to find a relationship between environment " + globoNetworkEnvironmentId + " and LB network "
                    + globoNetworkLBEnvironmentId);
        }

        List<GloboNetworkIpDetailVO> ipDetailVOList = _globoNetworkIpDetailDao.listByLBEnvironmentRef(globoNetworkLBEnvironmentVO.getGloboNetworkLoadBalancerEnvironmentId());
        if (!ipDetailVOList.isEmpty()) {
            throw new InvalidParameterValueException("There are Load Balancers in environment '" + globoNetworkLBEnvironmentVO.getName() + "'");
        }

        Transaction.execute(new TransactionCallbackNoReturn() {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status) {
                _globoNetworkLBEnvironmentDao.remove(globoNetworkLBEnvironmentVO.getId());

                // never remove portable ip range because can be used in others zones.
//               if (_portableIpRangeDao.findById(globoNetworkLBNetworkVO.getPortableIpRangeId()) != null) {
//                    if portable ip range was removed, is ok.
//                   _configMgr.deletePortableIpRange(globoNetworkLBNetworkVO.getPortableIpRangeId());
//               }
            }
        });
        return true;
    }

    @Override
    public Vlan getVlanInfoFromGloboNetwork(Network network) {
        GetVlanInfoFromGloboNetworkCommand cmd = new GetVlanInfoFromGloboNetworkCommand();
        Long vlanId = getGloboNetworkVlanId(network.getId());
        cmd.setVlanId(vlanId);

        GloboNetworkVlanResponse response = (GloboNetworkVlanResponse)callCommand(cmd, network.getDataCenterId());

        Vlan vlan = new Vlan();
        vlan.setId(response.getVlanId());
        vlan.setName(response.getVlanName());
        vlan.setVlanNum(response.getVlanNum());
        vlan.setDescription(response.getVlanDescription());

        return vlan;
    }

    @Override
    public void registerNicInGloboNetwork(NicProfile nic, VirtualMachineProfile vm, Network network) {

        String msg = "Unable to register nic " + nic + " from VM " + vm + ".";
        if (vm == null || nic == null) {
            throw new CloudRuntimeException(msg + " Invalid nic, virtual machine or network.");
        }

        GloboNetworkNetworkVO globoNetworkNetworkVO = _globoNetworkNetworkDao.findByNetworkId(network.getId());
        if (globoNetworkNetworkVO == null) {
            throw new CloudRuntimeException(msg + " Could not obtain mapping for network in GloboNetwork.");
        }

        Long equipmentGroup = GloboNetworkVmEquipmentGroup.value();
        if (equipmentGroup == null || "".equals(equipmentGroup)) {
            throw new CloudRuntimeException(msg + " Invalid equipment group for VM. Check your GloboNetwork global options.");
        }

        Long equipmentModel = null;
        switch (vm.getType()) {
        case DomainRouter:
            equipmentModel = GloboNetworkModelVmDomainRouter.value();
            break;
        case ConsoleProxy:
            equipmentModel = GloboNetworkModelVmConsoleProxy.value();
            break;
        case SecondaryStorageVm:
            equipmentModel = GloboNetworkModelVmSecondaryStorageVm.value();
            break;
        case ElasticIpVm:
            equipmentModel = GloboNetworkModelVmElasticIpVm.value();
            break;
        case ElasticLoadBalancerVm:
            equipmentModel = GloboNetworkModelVmElasticLoadBalancerVm.value();
            break;
        case InternalLoadBalancerVm:
            equipmentModel = GloboNetworkModelVmInternalLoadBalancerVm.value();
            break;
        case UserBareMetal:
            equipmentModel = GloboNetworkModelVmUserBareMetal.value();
            break;
        default:
            equipmentModel = GloboNetworkModelVmUser.value();
            break;
        }
        if (equipmentModel == null) {
            throw new CloudRuntimeException(msg + " Invalid equipment model for VM of type " + vm.getType() + ". Check your GloboNetwork global options.");
        }

        RegisterEquipmentAndIpInGloboNetworkCommand cmd = new RegisterEquipmentAndIpInGloboNetworkCommand();
        if (nic.getIPv4Address() != null) {
            cmd.setNicIp(nic.getIPv4Address());
        } else {
            cmd.setNicIp(nic.getIPv6Address());
        }

        SimpleDateFormat format = new SimpleDateFormat("dd-MM-YYYY-HH:mm:ss");
        String description = "ACS_IP_" + GloboNetworkRegion.value()+ "_"+ format.format(new Date());

        cmd.setNicDescription(description);
        cmd.setVmName(getEquipNameFromUuid(vm.getUuid()));
        cmd.setVlanId(globoNetworkNetworkVO.getGloboNetworkVlanId());
        cmd.setEnvironmentId(globoNetworkNetworkVO.getGloboNetworkEnvironmentId());
        cmd.setEquipmentGroupId(Long.valueOf(equipmentGroup));
        cmd.setEquipmentModelId(Long.valueOf(equipmentModel));

        Answer answer = this.callCommand(cmd, vm.getVirtualMachine().getDataCenterId());
        if (answer == null || !answer.getResult()) {
            msg = answer == null ? msg : answer.getDetails();
            throw new CloudRuntimeException(msg);
        }
    }

    private String getEquipNameFromUuid(String uuid) {
        String instanceNamePrefix = _configDao.getValue(Config.InstanceName.key());
        String equipName = "";
        if (instanceNamePrefix == null || instanceNamePrefix.equals("")) {
            equipName = uuid;
        } else {
            equipName = instanceNamePrefix + "-" + uuid;
        }
        return equipName;
    }

    @Override
    public void unregisterNicInGloboNetwork(NicProfile nic, VirtualMachineProfile vm) {

        String msg = "Unable to unregister nic " + nic + " from VM " + vm + ".";
        if (vm == null || nic == null) {
            throw new CloudRuntimeException(msg + " Invalid nic or virtual machine.");
        }

        Long equipmentGroup = GloboNetworkVmEquipmentGroup.value();
        if (equipmentGroup == null) {
            throw new CloudRuntimeException(msg + " Invalid equipment group for VM. Check your GloboNetwork global options.");
        }

        UnregisterEquipmentAndIpInGloboNetworkCommand cmd = new UnregisterEquipmentAndIpInGloboNetworkCommand();
        if (nic.getIPv4Address() != null) {
            cmd.setNicIp(nic.getIPv4Address());
            cmd.setIsv6(false);
        } else {
            cmd.setNicIp(nic.getIPv6Address());
            cmd.setIsv6(true);
        }
        cmd.setVmName(getEquipNameFromUuid(vm.getUuid()));

        GloboNetworkNetworkVO globoNetworkNetworkVO = _globoNetworkNetworkDao.findByNetworkId(nic.getNetworkId());
        if (globoNetworkNetworkVO != null) {
            cmd.setEnvironmentId(globoNetworkNetworkVO.getGloboNetworkEnvironmentId());
        }

        Answer answer = this.callCommand(cmd, vm.getVirtualMachine().getDataCenterId());
        if (answer == null || !answer.getResult()) {
            msg = answer == null ? msg : answer.getDetails();
            throw new CloudRuntimeException(msg);
        }
    }

    @Override
    public boolean removeVmFromLoadBalancer(VirtualMachineProfile vm) {
        return _lbMgr.removeVmFromLoadBalancers(vm.getId());
    }

    @Override
    public List<GloboNetworkVipResponse> listGloboNetworkVips(Long projectId) {

        Account caller = CallContext.current().getCallingAccount();
        List<Long> permittedAccounts = new ArrayList<Long>();

        permittedAccounts.add(caller.getId());

        if (projectId != null) {
            Project project = _projectMgr.getProject(projectId);
            if (project == null) {
                throw new InvalidParameterValueException("Unable to find project by specified id");
            }
            if (!_projectMgr.canAccessProjectAccount(caller, project.getProjectAccountId())) {
                // getProject() returns type ProjectVO.
                InvalidParameterValueException ex = new InvalidParameterValueException("Account " + caller + " cannot access specified project id");
                ex.addProxyObject(project.getUuid(), "projectId");
                throw ex;
            }

            permittedAccounts.add(project.getProjectAccountId());
        }

        // FIXME Improve this search by creating a custom criteria
        List<DataCenterVO> zones = _dcDao.listAllZones();
        List<NetworkVO> allowedNetworks = new ArrayList<NetworkVO>();
        for (DataCenterVO zone : zones) {
            for (Long accountId : permittedAccounts) {
                allowedNetworks.addAll(_ntwkDao.listNetworksByAccount(accountId, zone.getId(), Network.GuestType.Shared, false));
            }
        }

        List<Long> networkIds = new ArrayList<Long>();
        for (NetworkVO networkVO : allowedNetworks) {
            networkIds.add(networkVO.getId());
        }

        if (networkIds.isEmpty()) {
            return new ArrayList<GloboNetworkVipResponse>();
        }

        // Get all vip Ids related to networks
        List<GloboNetworkVipAccVO> globoNetworkVipAccList = _globoNetworkVipAccDao.listByNetworks(networkIds);

        Map<Long, GloboNetworkVipResponse> vips = new HashMap<Long, GloboNetworkVipResponse>();
        for (GloboNetworkVipAccVO globoNetworkVipAcc : globoNetworkVipAccList) {

            Network network = _ntwkDao.findById(globoNetworkVipAcc.getNetworkId());

            GloboNetworkVipResponse vip;

            if (vips.get(globoNetworkVipAcc.getGloboNetworkVipId()) == null) {

                // Vip is not in the returning map yet, get all info from GloboNetwork
                boolean isv6 = this.isNetworkv6(network);

                GetVipInfoFromGloboNetworkCommand cmd = new GetVipInfoFromGloboNetworkCommand(globoNetworkVipAcc.getGloboNetworkVipId(), isv6);
                Answer answer = this.callCommand(cmd, network.getDataCenterId());
                String msg = "Could not list VIPs from GloboNetwork";
                if (answer == null || !answer.getResult()) {
                    msg = answer == null ? msg : answer.getDetails();
                    throw new CloudRuntimeException(msg);
                }
                vip = ((GloboNetworkVipResponse)answer);

                vips.put(globoNetworkVipAcc.getGloboNetworkVipId(), vip);

            } else {
                // Vip is already in the returning map
                vip = vips.get(globoNetworkVipAcc.getGloboNetworkVipId());
            }

            if (vip.getNetworkIds() == null) {
                vip.setNetworkIds(new ArrayList<String>());
            }
            vip.getNetworkIds().add(network.getUuid());

        }
        return new ArrayList<GloboNetworkVipResponse>(vips.values());
    }

    @Override
    public List<GloboNetworkVipResponse.Real> listGloboNetworkReals(Long vipId) {
        if (vipId == null) {
            throw new InvalidParameterValueException("Invalid VIP id");
        }

        List<GloboNetworkVipAccVO> globoNetworkVips = _globoNetworkVipAccDao.findByVipId(vipId);

        if (globoNetworkVips == null) {
            throw new CloudRuntimeException("Could not find VIP " + vipId);
        }

        List<GloboNetworkVipResponse.Real> reals = new ArrayList<GloboNetworkVipResponse.Real>();

        if (globoNetworkVips.isEmpty()) {
            return reals;
        }

        // We need a network to call commands, any network associated to this VIP will do
        Network network = _ntwkDao.findById(globoNetworkVips.get(0).getNetworkId());

        if (network == null) {
            throw new CloudRuntimeException("Could not find network with networkId " + globoNetworkVips.get(0).getNetworkId());
        }

        boolean isv6 = this.isNetworkv6(network);

        GetVipInfoFromGloboNetworkCommand cmd = new GetVipInfoFromGloboNetworkCommand(vipId, isv6);
        Answer answer = this.callCommand(cmd, network.getDataCenterId());
        String msg = "Could not find VIP from GloboNetwork";
        if (answer == null || !answer.getResult()) {
            msg = answer == null ? msg : answer.getDetails();
            throw new CloudRuntimeException(msg);
        }
        GloboNetworkVipResponse vip = ((GloboNetworkVipResponse)answer);

        for (Real real : vip.getReals()) {
            for (GloboNetworkVipAccVO globoNetworkVipVO : globoNetworkVips) {
                network = _ntwkDao.findById(globoNetworkVipVO.getNetworkId());

                // If real's IP is not within network range, skip it
                if (isv6) {
                    if (!NetUtils.isIp6InNetwork(real.getIp(), network.getCidr())); {
                        continue;
                    }
                } else {
                    if (!NetUtils.isIpWithInCidrRange(real.getIp(), network.getCidr())) {
                        continue;
                    }
                }

                VMInstanceVO realVM = _vmDao.findByUuid(real.getVmName());
                if (realVM != null) {
                    Nic nic = _nicDao.findByNtwkIdAndInstanceId(globoNetworkVipVO.getNetworkId(), realVM.getId());
                    if (nic != null) {
                        real.setNic(String.valueOf(nic.getId()));
                        real.setNetwork(network.getName());

                        // User VM name rather than UUID
                        real.setVmName(realVM.getHostName());
                    }
                }
            }

            reals.add(real);
        }

        return reals;
    }

    @Override
    public String getConfigComponentName() {
        return GloboNetworkManager.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {GloboNetworkVIPServerUrl, GloboNetworkConnectionTimeout, GloboNetworkLBLockTimeout, GloboNetworkReadTimeout, GloboNetworkNumberOfRetries,
                GloboNetworkVmEquipmentGroup, GloboNetworkModelVmUser, GloboNetworkModelVmDomainRouter, GloboNetworkModelVmConsoleProxy, GloboNetworkModelVmSecondaryStorageVm,
                GloboNetworkModelVmElasticIpVm, GloboNetworkModelVmElasticLoadBalancerVm, GloboNetworkModelVmInternalLoadBalancerVm, GloboNetworkModelVmUserBareMetal,
                GloboNetworkDomainSuffix, GloboNetworkDomainPattern, GloboNetworkLBAllowedSuffixes, GloboNetworkRegion};
    }

    protected PortableIpRange getPortableIpRange(Long zoneId, Integer vlanNumber, String networkCidr, String networkGateway) throws ResourceAllocationException,
            ConcurrentOperationException, InvalidParameterValueException, InsufficientCapacityException {

        // There is no relationship between zone and regin yet
        Integer regionId = 1;
        for (PortableIpRange portableIpRange : _portableIpRangeDao.listByRegionId(regionId)) {
            // compare only gateway because vlan doesn't matters for public ip range
            if (portableIpRange.getGateway().equals(networkGateway)) {
                return portableIpRange;
            }
        }
        return null;
    }

    protected PortableIpRangeVO createPortableIpRange(Long zoneId, Integer vlanNumber, String networkCidr, String networkGateway) throws ResourceAllocationException,
            ResourceUnavailableException, ConcurrentOperationException, InvalidParameterValueException, InsufficientCapacityException {
        // FIXME! Portable IP and Portable IP Range are not IPv6 ready

        // There is not relationship between zoneId and region yet.
        //DataCenter dc = _dcDao.findById(zoneId);
        Integer regionId = 1;

        String vlan = BroadcastDomainType.Vlan.toUri(vlanNumber).toString();
        String networkMask = NetUtils.getCidrNetmask(networkCidr);
        String networkAddress = NetUtils.getCidr(networkCidr).first();
        long size = NetUtils.getCidrSize(networkMask);
        String startIP = NetUtils.getIpRangeStartIpFromCidr(networkAddress, size);
        String endIP = NetUtils.getIpRangeEndIpFromCidr(networkAddress, size);

        // shift start ips by NUMBER_OF_RESERVED_IPS_FROM_START and NUMBER_OF_RESERVED_IPS_BEFORE_END
        startIP = NetUtils.long2Ip(NetUtils.ip2Long(startIP) + NUMBER_OF_RESERVED_IPS_FOR_LB_FROM_START);
        endIP = NetUtils.long2Ip(NetUtils.ip2Long(endIP) - NUMBER_OF_RESERVED_IPS_FOR_LB_BEFORE_END);

        PortableIpRangeVO portableIpRange = _configMgr.createPortableIpRange(regionId, startIP, endIP, networkGateway, networkMask, vlan);
        return portableIpRange;
    }

    @Override
    public PublicIp acquireLbIp(final Long networkId, Long projectId, Long lbEnvironmentId) throws ResourceAllocationException, ResourceUnavailableException,
            ConcurrentOperationException, InvalidParameterValueException, InsufficientCapacityException {

        // First of all, check user permission
        final Account caller = CallContext.current().getCallingAccount();

        final Network network;
        if (networkId != null) {
            network = _ntwkDao.findById(networkId);
            if (network == null) {
                throw new InvalidParameterValueException("Unable to find a network having the specified network id");
            }
        } else {
            throw new InvalidParameterValueException("Invalid networkId: " + networkId);
        }
        // Perform account permission check on network
        _accountMgr.checkAccess(caller, null, true, network);

        // If project was set, this IP belongs to that project
        // Otherwise, the caller is the owner
        final Account owner;
        if (projectId != null) {
            Project project = _projectMgr.getProject(projectId);
            if (project != null) {
                owner = _accountMgr.getAccount(project.getProjectAccountId());
            } else {
                throw new InvalidParameterValueException("Could not find project with id " + projectId);
            }
        } else {
            owner = caller;
        }

        // Retrieve glbEnvId from network
        GloboNetworkNetworkVO globoNetworkNetworkVO = _globoNetworkNetworkDao.findByNetworkId(networkId);
        if (globoNetworkNetworkVO == null) {
            throw new InvalidParameterValueException("Unable to find mapping for networkId " + networkId);
        }
        Long glbEnvId = globoNetworkNetworkVO.getGloboNetworkEnvironmentId();

        // Retrieve napiEnvironment from DB
        GloboNetworkEnvironmentVO globoNetworkEnvironment = _globoNetworkEnvironmentDao.findByPhysicalNetworkIdAndEnvironmentId(network.getPhysicalNetworkId(), glbEnvId);

        if (globoNetworkEnvironment == null) {
            // No physical network/environment pair registered in the database.
            throw new InvalidParameterValueException("Unable to find a relationship between physical network=" + network.getPhysicalNetworkId() + " and GloboNetwork environment="
                    + glbEnvId);
        }

        final GloboNetworkLoadBalancerEnvironment globoNetworkLBEnvironment = _globoNetworkLBEnvironmentDao.findById(lbEnvironmentId);
        if (globoNetworkLBEnvironment == null) {
            throw new InvalidParameterValueException("Could not find LB network " + lbEnvironmentId);
        }

        boolean isv6 = this.isNetworkv6(network);

        Long gnLoadBalancerEnvironment = globoNetworkLBEnvironment.getGloboNetworkLoadBalancerEnvironmentId();
        AcquireNewIpForLbCommand cmd = new AcquireNewIpForLbCommand(gnLoadBalancerEnvironment, isv6);
        String description = getLbIpDescription(projectId, owner);
        cmd.setDescription(description);

        final GloboNetworkAndIPResponse globoNetwork = (GloboNetworkAndIPResponse)this.callCommand(cmd, network.getDataCenterId());

        try {
            // FIXME! PublicIp class is not IPv6 ready
            PublicIp publicIp = Transaction.execute(new TransactionCallbackWithException<PublicIp, CloudException>() {

                @Override
                public PublicIp doInTransaction(TransactionStatus status) throws CloudException {
                    Long zoneId = network.getDataCenterId();

                    // ensure portable ip range exists
                    String networkGateway = GloboNetworkManager.this.getNetworkGateway(globoNetwork.getNetworkAddress(), globoNetwork.getNetworkMask(), globoNetwork.isv6());
                    PortableIpRange portableIpRange = getPortableIpRange(zoneId, globoNetwork.getVlanNum(), globoNetwork.getNetworkCidr(), networkGateway);
                    if (portableIpRange == null) {
                        portableIpRange = createPortableIpRange(zoneId, globoNetwork.getVlanNum(), globoNetwork.getNetworkCidr(), networkGateway);
                    }

                    IPAddressVO ip = (IPAddressVO)_ipAddrMgr.allocatePortableIp(owner, caller, zoneId, networkId, null, globoNetwork.getIp());

                    GloboNetworkIpDetailVO gnIpDetail = new GloboNetworkIpDetailVO(ip.getId(), globoNetwork.getIpId());
                    gnIpDetail.setGloboNetworkEnvironmentRefId(globoNetworkLBEnvironment.getId());
                    _globoNetworkIpDetailDao.persist(gnIpDetail);

                    VlanVO vlan = _vlanDao.findById(ip.getVlanId());
                    return PublicIp.createFromAddrAndVlan(ip, vlan);
                }
            });
            return publicIp;

        } catch (CloudException e) {
            // Exception when allocating new IP in Cloudstack. Roll back transaction in GloboNetwork
            s_logger.error("[LB_PORTABLE_IP] Reverting IP allocation in GloboNetwork due to error allocating IP", e);
            ReleaseIpFromGloboNetworkCommand cmdRelease = new ReleaseIpFromGloboNetworkCommand(globoNetwork.getIp(), gnLoadBalancerEnvironment, globoNetwork.isv6());
            this.callCommand(cmdRelease, network.getDataCenterId());
            throw new ResourceAllocationException(e.getLocalizedMessage(), ResourceType.public_ip);
        }
    }

    private String getLbIpDescription(Long projectId, Account owner) {
        SimpleDateFormat format = new SimpleDateFormat("dd-MM-YYYY");
        String id = projectId != null ? projectId.toString() : "";
        return "ACS_LB_IP_" + GloboNetworkRegion.value()+ "_A"+  owner.getId() + "_P" + id + "_" + format.format(new Date());
    }

    private String getNetworkGateway(String network, String mask, boolean isv6) {
        if (network == null || mask == null) {
            return null;
        }

        if (isv6) {
            com.googlecode.ipv6.IPv6Network ipv6Network = com.googlecode.ipv6.IPv6Network.fromAddressAndMask(
                    com.googlecode.ipv6.IPv6Address.fromString(network),
                    com.googlecode.ipv6.IPv6NetworkMask.fromAddress(com.googlecode.ipv6.IPv6Address.fromString(mask)));
            return ipv6Network.getFirst().toString();
        } else {
            return NetUtils.getIpRangeStartIpFromCidr(network, NetUtils.getCidrSize(mask));
        }
    }

    @Override
    public boolean disassociateIpAddrFromGloboNetwork(final long ipId) {
        final IPAddressVO ipVO = _ipAddrDao.findById(ipId);
        final UserIpv6AddressVO ipv6VO = _ipv6AddrDao.findById(ipId);
        if (ipVO == null && ipv6VO == null) {
            throw new InvalidParameterValueException("Unable to find ip address with id=" + ipId);
        }

        final boolean isv6 = ipv6VO != null;

        try {
            boolean result = Transaction.execute(new TransactionCallbackWithExceptionNoReturn<CloudException>() {

                @Override
                public void doInTransactionWithoutResult(TransactionStatus status) throws CloudException {
                    GloboNetworkIpDetailVO gnIpDetail = _globoNetworkIpDetailDao.findByIp(ipId);

                    _ipAddrMgr.releasePortableIpAddress(ipId); // FIXME This will not deallocate portable IPv6 ips. PortableIp is not IPv6 ready

                    String ipAddress = isv6 ? ipv6VO.getAddress() : ipVO.getAddress().addr();
                    long dataCenterId = isv6 ? ipv6VO.getDataCenterId() : ipVO.getDataCenterId();

                    if (gnIpDetail != null && gnIpDetail.getGloboNetworkEnvironmentRefId() != null) {
                        GloboNetworkLoadBalancerEnvironment gnLbNetworkVO = _globoNetworkLBEnvironmentDao.findById(gnIpDetail.getGloboNetworkEnvironmentRefId());
                        if (gnLbNetworkVO == null) {
                            throw new InvalidParameterValueException("Could not find mapping between lb environment " + gnIpDetail.getGloboNetworkEnvironmentRefId());
                        }

                        ReleaseIpFromGloboNetworkCommand cmdRelease = new ReleaseIpFromGloboNetworkCommand(ipAddress, gnLbNetworkVO
                                .getGloboNetworkLoadBalancerEnvironmentId(), isv6);
                        GloboNetworkManager.this.callCommand(cmdRelease, dataCenterId);
                        _globoNetworkIpDetailDao.remove(gnIpDetail.getId());
                    } else {
                        s_logger.warn("Ip " + ipAddress + " is not associate with GloboNetwork");
                    }
                }
            });

            return result;
        } catch (CloudException e) {
            throw new CloudRuntimeException("Could not disassociate IP address", e);
        }
    }

    @Override
    public boolean applyLbRuleInGloboNetwork(final Network network, final LoadBalancingRule rule) throws ResourceUnavailableException {
        if (network == null || rule == null) {
            return false;
        }

        String lockName = "globonetworklb-" + rule.getSourceIp().addr();
        final ReentrantLock lock = GlobalLock.getReentrantLock(lockName);
        try {
            if (!lock.tryLock(GloboNetworkLBLockTimeout.value(), TimeUnit.SECONDS)) {
                throw new ResourceUnavailableException(String.format("Failed to acquire lock for load balancer %s", rule.getUuid()), DataCenter.class, network.getDataCenterId());
            }

            IPAddressVO ipAddress = _ipAddrDao.findByIpAndNetworkId(rule.getNetworkId(), rule.getSourceIp().addr());
            if (ipAddress == null) {
                throw new InvalidParameterValueException("Ip " + rule.getSourceIp().addr() + " is not associate with network " + rule.getNetworkId());
            }

            GloboNetworkIpDetailVO globoNetworkIpDetail = _globoNetworkIpDetailDao.findByIp(ipAddress.getId());
            if (globoNetworkIpDetail == null) {
                throw new InvalidParameterValueException("Ip " + rule.getSourceIp().addr() + " (id " + ipAddress.getId() + ") is not associate with globo network");
            } else if (globoNetworkIpDetail.getGloboNetworkEnvironmentRefId() == null) {
                throw new InvalidParameterValueException("Ip " + rule.getSourceIp().addr() + " (id " + ipAddress.getId() + ") can't be used to load balancing");
            }

            GloboNetworkLoadBalancerEnvironment lbEnvironment = _globoNetworkLBEnvironmentDao.findById(globoNetworkIpDetail.getGloboNetworkEnvironmentRefId());
            if (lbEnvironment == null) {
                throw new InvalidParameterValueException("Could not find mapping between lb environment " + globoNetworkIpDetail.getGloboNetworkEnvironmentRefId());
            }

            Account account = _accountMgr.getAccount(network.getAccountId());

            if (rule.getState().equals(FirewallRule.State.Revoke)) {
                removeLBFromGloboNetwork(network, rule, globoNetworkIpDetail);
            } else {
                saveLBInGloboNetwork(network, rule, account, globoNetworkIpDetail, lbEnvironment);
            }

        } catch (CloudstackGloboNetworkException ex) {
            String context = CallContext.current().getNdcContext();
            String integrationContext =  ex.getContext();
            String msg = "Integration problem with NetworkAPI, please contact your system administrator. Context: " + context + ", Integration context: " + integrationContext + ", name=" + rule.getName();
            throw new UserCloudRuntimeException(msg);
        } catch (InvalidParameterValueException e){
            throw new UserCloudRuntimeException(e.getMessage(), e);
        } catch (Exception e) {
            // Convert all exceptions to ResourceUnavailable to user have feedback of what happens. All others exceptions only show 'error'
            s_logger.error("[load balancer "+ rule.getName()+ "] Error applying load balancer rules. Uuid: " + rule.getUuid(), e);
            String context = CallContext.current().getNdcContext();
            throw new UserCloudRuntimeException("Error applying load balancer. Context: "+ context+ ", lb name=" + rule.getName(), e);
        } finally {
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            GlobalLock.releaseReentrantLock(lockName);
        }

        return true;
    }

    private void saveLBInGloboNetwork(Network network, LoadBalancingRule rule, Account account, GloboNetworkIpDetailVO globoNetworkIpDetail, GloboNetworkLoadBalancerEnvironment lbEnvironment) throws ResourceUnavailableException {
        if(rule.isDsr()){
            validateEnvironmentSupportsDSR(network, lbEnvironment);
        }
        validateSticknessPolicy(rule);
        validateHealthCheckPolicies(rule);
        List<String> ports = getServicePorts(rule);
        List<Real> realList = getReals(rule.getDestinations(), ports);

        if (isDnsProviderEnabledFor(network)) {
            manageLoadBalancerDomainNameRegistry(network, rule);
        } else {
            s_logger.warn("Creating Load Balancer without registering DNS because network offering does not have GloboDNS as provider");
        }

        final ApplyVipInGloboNetworkCommand cmd = createApplyVipInGloboNetworkCommand(rule, account, globoNetworkIpDetail, lbEnvironment, ports, realList);

        GloboNetworkVipResponse answer = (GloboNetworkVipResponse) this.callCommand(cmd, network.getDataCenterId());
        if (globoNetworkIpDetail.getGloboNetworkVipId() == null) {
            globoNetworkIpDetail.setGloboNetworkVipId(answer.getId());
            _globoNetworkIpDetailDao.persist(globoNetworkIpDetail);
        }
    }

    private void validateEnvironmentSupportsDSR(Network network, GloboNetworkLoadBalancerEnvironment lbEnvironment) {
        CheckDSREnabled cmd = new CheckDSREnabled(lbEnvironment.getGloboNetworkLoadBalancerEnvironmentId());
        CheckDSREnabledResponse answer = (CheckDSREnabledResponse) this.callCommand(cmd, network.getDataCenterId());
        if(!answer.getResult() || !answer.isDsrEnabled()){
            throw new InvalidParameterValueException(answer.getDetails());
        }
    }

    protected void validateSticknessPolicy(LoadBalancingRule rule) {
        if (rule.getStickinessPolicies() == null || rule.getStickinessPolicies().size() > 1) {
            throw new InvalidParameterValueException("Invalid stickness policy, list should contain only one");
        }
    }

    protected void validateHealthCheckPolicies(LoadBalancingRule rule) {
        if (rule.getHealthCheckPolicies() != null) {
            int numberOfpolicies = 0;
            for (LbHealthCheckPolicy healthcheckpolicy : rule.getHealthCheckPolicies()) {
                if (!healthcheckpolicy.isRevoked()) {
                    numberOfpolicies++;
                }
            }
            if (numberOfpolicies > 1) {
                throw new InvalidParameterValueException("Invalid healthcheck policy, list should contain at maximum one");
            }
        }
    }

    protected List<String> getServicePorts(LoadBalancingRule rule) {
        List<String> ports = new ArrayList<>();
        ports.add(rule.getSourcePortStart() + ":" + rule.getDefaultPortStart());
        if (rule.getAdditionalPortMap() != null) {
            ports.addAll(rule.getAdditionalPortMap());
        }
        return ports;
    }

    protected List<Real> getReals(List<LbDestination> destinations, List<String> ports) {
        List<Real> realList = new ArrayList<>();
        if(destinations != null) {
            for (LbDestination destination : destinations) {
                VMInstanceVO vm = _vmDao.findById(destination.getInstanceId());
                if (vm != null) {
                    Real real = new Real();
                    real.setIp(destination.getIpAddress());
                    real.setVmName(getEquipNameFromUuid(vm.getUuid()));
                    real.setPorts(ports);
                    real.setRevoked(destination.isRevoked());

                    GloboNetworkNetworkVO destinationNetwork = _globoNetworkNetworkDao.findByNetworkId(destination.getNetworkId());
                    if (destinationNetwork == null) {
                        throw new InvalidParameterValueException("Could not obtain mapping for network " + destination.getNetworkId() + " and VM " + destination.getInstanceId() + " in GloboNetwork.");
                    }
                    real.setEnvironmentId(destinationNetwork.getGloboNetworkEnvironmentId());
                    realList.add(real);
                } else {
                    throw new InvalidParameterValueException("Could not find VM with id " + destination.getInstanceId());
                }
            }
        }
        return realList;
    }

    protected ApplyVipInGloboNetworkCommand createApplyVipInGloboNetworkCommand(LoadBalancingRule rule, Account account, GloboNetworkIpDetailVO globoNetworkIpDetail, GloboNetworkLoadBalancerEnvironment lbEnvironment, List<String> ports, List<Real> realList) {
        final ApplyVipInGloboNetworkCommand cmd = new ApplyVipInGloboNetworkCommand();
        cmd.setRegion(GloboNetworkRegion.value());
        buildHealthcheck(cmd, rule);

        cmd.setVipId(globoNetworkIpDetail.getGloboNetworkVipId()); // Vip Id null means new vip, otherwise vip will be updated.
        cmd.setHost(rule.getName());
        cmd.setCache(rule.getCache());
        cmd.setServiceDownAction(rule.getServiceDownAction());
        cmd.setDsr(rule.isDsr());
        cmd.setHealthCheckDestination(rule.getHealthcheckDestination());
        cmd.setIpv4(rule.getSourceIp().addr());
        cmd.setVipEnvironmentId(lbEnvironment.getGloboNetworkLoadBalancerEnvironmentId());
        cmd.setPorts(ports);
        cmd.setBusinessArea(account.getAccountName());
        cmd.setServiceName(rule.getName());
        cmd.setMethodBal(rule.getAlgorithm());
        cmd.setPersistencePolicy(rule.getStickinessPolicies() == null || rule.getStickinessPolicies().isEmpty() ? null : rule.getStickinessPolicies().get(0));
        cmd.setRuleState(rule.getState());
        cmd.setRealList(realList);
        return cmd;
    }

    private void removeLBFromGloboNetwork(Network network, LoadBalancingRule rule, GloboNetworkIpDetailVO globoNetworkIpDetail) throws ResourceUnavailableException {
        if (isDnsProviderEnabledFor(network)) {
            manageLoadBalancerDomainNameRegistry(network, rule);
        } else {
            s_logger.warn("Creating Load Balancer without registering DNS because network offering does not have GloboDNS as provider");
        }

        final RemoveVipFromGloboNetworkCommand cmd = new RemoveVipFromGloboNetworkCommand();
        cmd.setVipId(globoNetworkIpDetail.getGloboNetworkVipId());
        cmd.setKeepIp(rule.isKeepIp());
        _globoResourceConfigurationDao.removeConfigurations(rule.getUuid(), GloboResourceType.LOAD_BALANCER);
        this.callCommand(cmd, network.getDataCenterId());

        globoNetworkIpDetail.setGloboNetworkVipId(null);
        _globoNetworkIpDetailDao.persist(globoNetworkIpDetail);
    }

    private void buildHealthcheck(ApplyVipInGloboNetworkCommand cmd, LoadBalancingRule rule) {
        LbHealthCheckPolicy lbHealthCheckPolicy = rule.getHealthCheckPolicies() == null || rule.getHealthCheckPolicies().isEmpty() ? null : rule.getHealthCheckPolicies().get(0);
        String healthcheck = lbHealthCheckPolicy != null ? lbHealthCheckPolicy.getpingpath() : null;

        HealthCheckHelper healthcheckBuilder = HealthCheckHelper.build(rule.getName(), rule.getHealthCheckType(), healthcheck, rule.getExpectedHealthCheck());

        if (healthcheckBuilder.getExpectedHealthCheck() != null && !healthcheckBuilder.getExpectedHealthCheck().equals(rule.getExpectedHealthCheck()) ||
                healthcheckBuilder.getHealthCheckType() != null &&  !healthcheckBuilder.getHealthCheckType().equals(rule.getHealthCheckType())
                ) {
            s_logger.info("ExpectedHealthcheck Actual: " + cmd.getExpectedHealthcheck() + " Old " + rule.getExpectedHealthCheck());
            s_logger.info("Type Actual: " + cmd.getHealthcheckType() + " Old " + rule.getHealthCheckType());

            List<LoadBalancerOptionsVO> lbOptions = _lbOptionsDao.listByLoadBalancerId(rule.getId());
            if (lbOptions.size() > 0) {
                for (LoadBalancerOptionsVO lbOpt : lbOptions) {
                    if (lbOpt.getLoadBalancerId() == rule.getId()) {
                        lbOpt.setExpectedHealthCheck(healthcheckBuilder.getExpectedHealthCheck());
                        lbOpt.setHealthCheckType(healthcheckBuilder.getHealthCheckType());
                        _lbOptionsDao.persist(lbOpt);
                    }
                }
            }
        }

        cmd.setExpectedHealthcheck(healthcheckBuilder.getExpectedHealthCheck());
        cmd.setHealthcheckType(healthcheckBuilder.getHealthCheckType());
        cmd.setHealthcheck(healthcheckBuilder.getHealthCheck());
    }

    private boolean isDnsProviderEnabledFor(Network network) {
        return _networkManager.isProviderForNetwork(Provider.GloboDns, network.getId()) &&
               _networkManager.isProviderEnabledInPhysicalNetwork(network.getPhysicalNetworkId(), Provider.GloboDns.getName());
    }

    public void registerLoadbalancerDomainName(String resourceUuId) {
        LoadBalancerVO lb = this._loadBalancerDao.findByUuid(resourceUuId);

        Network network = _ntwkDao.findById(lb.getNetworkId());
        Long sourceIpAddressId = lb.getSourceIpAddressId();
        IPAddressVO ipAddress = _ipAddrDao.findById(sourceIpAddressId);
        String lbDomain = getLbDomain(lb.getName());
        String lbRecord = getLbRecord(lb.getName(), lbDomain);
        Ip ip = ipAddress.getAddress();
        try {
            GloboDnsTO globoDns = new GloboDnsTO(network.getDataCenterId(), lb.getUuid(), lbRecord, lbDomain, ip.addr());
            _globoDnsService.createDnsRecordForLoadBalancer(globoDns, false);

        } catch (Exception ex) {
            s_logger.error("Error while registering load balancer's domain name", ex);
            throw new CloudRuntimeException("Error while registering load balancer's domain name", ex);
        }
    }

    @Override
    public void registerDnsForResource(String uuid, GloboResourceType resourceType) {
        if (GloboResourceType.LOAD_BALANCER.equals(resourceType)){
            registerLoadbalancerDomainName(uuid);
        } else if (GloboResourceType.VM_NIC.equals(resourceType)) {
            registerNicVmDomain(uuid);
        }
    }

    private void registerNicVmDomain(String uuid) {
        NicVO nic = _nicDao.findByUuid(uuid);
        UserVmVO vm = _userVmDao.findById(nic.getInstanceId());
        Network network = _networkService.getNetwork(nic.getNetworkId());

        s_logger.debug("VM : " + vm.getHostName() + "nic: " + nic.getIPv4Address());

        boolean isIpv6 = nic.getIPv4Address() == null;
        String ipAddress = isIpv6 ? nic.getIPv6Address() : nic.getIPv4Address();


        boolean forceDomainRegister = true; //here user is forcing to register, so if fail, the user should get exception
        boolean result = _globoDnsService.registerVmDomain(network.getDataCenterId(), nic.getUuid(),
                                                            vm.getHostName(), ipAddress, network.getNetworkDomain(), isIpv6, forceDomainRegister);

        if (!result) {
            throw new CloudRuntimeException("Error during register vm domain for nic " + uuid + ". Contact the System Administrator.");
        }

    }

    @Override
    public GloboResourceConfigurationVO getGloboResourceConfiguration(String uuid, GloboResourceType resourceType, GloboResourceKey key) {

        List<GloboResourceConfigurationVO> globoConfigurations = _globoResourceConfigurationDao.getConfiguration(resourceType, uuid, key);
        if (globoConfigurations.size() > 0) {
            return globoConfigurations.get(0);
        }

        return null;
    }

    public List<GloboResourceConfigurationVO> getGloboResourceConfigs(String uuid, GloboResourceType resourceType) {
        List<GloboResourceConfigurationVO> configs = _globoResourceConfigurationDao.getConfiguration(resourceType, uuid);
        return  configs;
    }

    protected void manageLoadBalancerDomainNameRegistry(Network network, LoadBalancingRule rule) throws ResourceUnavailableException {
        try {
            GloboResourceConfigurationVO skipDnsErrorCmd = _globoResourceConfigurationDao.getFirst(GloboResourceType.LOAD_BALANCER, rule.getUuid(), GloboResourceKey.skipDnsError);
            boolean skipDnsError = skipDnsErrorCmd != null ? skipDnsErrorCmd.getBooleanValue() : false;

            // First of all, find the correct LB domain and LB record
            String lbDomain = getLbDomain(rule.getName());

            if (lbDomain == null) {
                throw new ResourceUnavailableException("Load balancer name/domain is invalid", DataCenter.class, network.getDataCenterId());
            }

            String lbRecord = getLbRecord(rule.getName(), lbDomain);

            if ((rule.getState() == FirewallRule.State.Add || rule.getState() == FirewallRule.State.Active)) {
                if (_globoDnsService.validateDnsRecordForLoadBalancer(lbDomain, lbRecord, rule.getSourceIp().addr(), network.getDataCenterId(), skipDnsError)) {
                    GloboDnsTO globoDns = new GloboDnsTO(network.getDataCenterId(), rule.getUuid(), lbRecord, lbDomain, rule.getSourceIp().addr());
                    _globoDnsService.createDnsRecordForLoadBalancer(globoDns, skipDnsError);
                }
            } else if (rule.getState() == FirewallRule.State.Revoke) {
                _globoDnsService.removeDnsRecordForLoadBalancer(rule.getUuid(), lbDomain, lbRecord, rule.getSourceIp().addr(), network.getDataCenterId());
            }
        } catch(Exception ex){
            s_logger.error("Error while registering load balancer's domain name", ex);
            throw new CloudRuntimeException("Error while registering load balancer's domain name", ex);
        }
    }

    protected String getLbDomain(String lbFullName) {
        List<String> allowedDomains = listAllowedLbSuffixes();
        String lbDomain = null;
        for (String allowedDomain : allowedDomains) {
            // Remove any whitespaces
            allowedDomain = allowedDomain.trim();
            // Insert the '.' before the domain, if it's not there yet
            allowedDomain = allowedDomain.startsWith(".") ? allowedDomain : "." + allowedDomain;
            if (lbFullName.endsWith(allowedDomain)) {
                lbDomain = allowedDomain.substring(1); // Remove '.' again for later use in GloboDNS provider
                break;
            }
        }
        return lbDomain;
    }

    private String getLbRecord(String fullLbName, String lbDomain) {
        return fullLbName.substring(0, fullLbName.indexOf(lbDomain) - 1); // -1 is to remove '.' between record and domain
    }

    @Override
    public boolean validateLBRule(Network network, LoadBalancingRule rule) {
        // Validate params
        if (network == null || rule == null) {
            return false;
        }

        GloboNetworkNetworkVO globoNetworkNetworkVO = _globoNetworkNetworkDao.findByNetworkId(network.getId());
        if (globoNetworkNetworkVO == null) {
            throw new InvalidParameterValueException("GloboNetwork Load Balancer work only with networks managed by GloboNetwork. NetworkId=" + network.getId());
        }

        // check additional networks
        if (rule.getAdditionalNetworks() != null) {
            for (Long networkId : rule.getAdditionalNetworks()) {
                GloboNetworkNetworkVO additionalGNNetworkVO = _globoNetworkNetworkDao.findByNetworkId(networkId);
                if (additionalGNNetworkVO == null) {
                    throw new InvalidParameterValueException("GloboNetwork Load Balancer work only with networks managed by GloboNetwork. NetworkId=" + network.getId());
                }
            }
        }

        this.validatePortMaps(rule);

        IPAddressVO ipVO = _ipAddrDao.findByIpAndNetworkId(rule.getNetworkId(), rule.getSourceIp().addr());
        if (ipVO == null) {
            throw new InvalidParameterValueException("Ip " + rule.getSourceIp().addr() + " is not associate with network " + rule.getNetworkId());
        }

        GloboNetworkIpDetailVO gnIpDetail = _globoNetworkIpDetailDao.findByIp(ipVO.getId());
        if (gnIpDetail == null) {
            throw new InvalidParameterValueException("Ip " + rule.getSourceIp().addr() + " (id " + ipVO.getId() + ") is not associate with globo network");
        } else if (gnIpDetail.getGloboNetworkEnvironmentRefId() == null) {
            throw new InvalidParameterValueException("Ip " + rule.getSourceIp().addr() + " (id " + ipVO.getId() + ") can't be used to load balancing");
        }

        // Stickness/Persistence
        if (rule.getStickinessPolicies() != null && rule.getStickinessPolicies().size() > 1) {
            throw new InvalidParameterValueException("Invalid stickness policy, list should contain only one");
        }

        // Healthcheck
        if (rule.getHealthCheckPolicies() != null && rule.getHealthCheckPolicies().size() > 1) {
            throw new InvalidParameterValueException("Invalid healthcheck policy, list should contain only one");
        }

        // Get VIP info
        if (gnIpDetail.getGloboNetworkVipId() != null) {
            ValidateVipUpdateCommand cmd = new ValidateVipUpdateCommand(gnIpDetail.getGloboNetworkVipId(), rule.getName(), rule.getSourceIp().addr());
            Answer answer = this.callCommand(cmd, network.getDataCenterId(), false);
            if(!answer.getResult()){
                throw new InvalidParameterValueException(answer.getDetails());
            }
        }

        if (isDnsProviderEnabledFor(network)) {
            // First of all, find the correct LB domain and LB record
            String lbDomain = getLbDomain(rule.getName());

            if (lbDomain == null) {
                // That means there was no match
                // LB cannot be created
                throw new InvalidParameterValueException("Load balancer name " + rule.getName() + " is not in the allowed list for domains.");
            }

            checkLbName(rule.getName(), lbDomain);

            // Finally, validate LB record in GloboDNS
            String lbRecord = getLbRecord(rule.getName(), lbDomain);

            return _globoDnsService.validateDnsRecordForLoadBalancer(lbDomain, lbRecord, rule.getSourceIp().addr(), network.getDataCenterId(), rule.isSkipDnsError());
        } else {
            s_logger.warn("Allowing creation of Load Balancer without registering DNS because network offering does not have GloboDNS as provider");
        }

        return true;
    }

    public void checkLbName(String name, String lbDomain) {
        if (name.contains(".." + lbDomain)) {
            throw new InvalidParameterValueException("You mustn't use 2 points before lb domain.");
        }
    }

    @Override
    public List<String> listGloboNetworkLBCacheGroups(Long lbEnvironmentIdRef, Long networkId) {
        if (lbEnvironmentIdRef == null) {
            throw new InvalidParameterValueException("Invalid LB Environment ID");
        }

        GloboNetworkLoadBalancerEnvironment gnLbEnv = _globoNetworkLBEnvironmentDao.findById(lbEnvironmentIdRef);
        if (gnLbEnv == null) {
            throw new InvalidParameterValueException("Could not find mapping between lb environment " + gnLbEnv.getGloboNetworkEnvironmentRefId());
        }

        if (networkId == null) {
            throw new InvalidParameterValueException("Invalid Network ID");
        }

        Network network = _networkManager.getNetwork(networkId);
        if (network == null) {
            throw new InvalidParameterValueException("Cannot find network with ID : " + networkId);
        }

        ListGloboNetworkLBCacheGroupsCommand cmd = new ListGloboNetworkLBCacheGroupsCommand();
        cmd.setLBEnvironmentId(gnLbEnv.getGloboNetworkLoadBalancerEnvironmentId());

        Answer answer = callCommand(cmd, network.getDataCenterId());

        List<String> lbCacheGroups = ((GloboNetworkCacheGroupsResponse)answer).getCacheGroups();
        return lbCacheGroups;

    }

    protected void validatePortMaps(LoadBalancingRule rule) {
        List<Integer> portsAlreadyMapped = new ArrayList<>();
        portsAlreadyMapped.add(rule.getSourcePortStart());
        if (rule.getAdditionalPortMap() != null) {
            for (String portMap : rule.getAdditionalPortMap()) {
                String[] portMapArray = portMap.split(":");
                if (portMapArray.length != 2) {
                    throw new InvalidParameterValueException("Additional port mapping is invalid, should be in the form '80:8080,443:8443'");
                }
                Integer lbPort;
                Integer realPort;
                try {
                    lbPort = Integer.valueOf(portMapArray[0].trim());
                    realPort = Integer.valueOf(portMapArray[1].trim());
                }catch(NumberFormatException e){
                    throw new InvalidParameterValueException("Additional port mapping is invalid. Only numbers are permitted");
                }
                if (portsAlreadyMapped.contains(lbPort)) {
                    throw new InvalidParameterValueException("Additional port mapping is invalid. Duplicated Load Balancer port");
                }
                portsAlreadyMapped.add(lbPort);
            }
        }
    }

    @Override
    @DB
    public LoadBalancer importGloboNetworkLoadBalancer(Long lbId, final Long networkId, Long projectId) {
        // Validate params
        if (lbId == null) {
            throw new InvalidParameterValueException("Invalid load balancer ID");
        }
        if (networkId == null) {
            throw new InvalidParameterValueException("Invalid network ID");
        }
        final Network network = _ntwkDao.findById(networkId);
        if (network == null) {
            throw new InvalidParameterValueException("Could not find a network with id " + networkId);
        }

        // First of all, check user permission
        final Account caller = CallContext.current().getCallingAccount();
        // Perform account permission check on network
        _accountMgr.checkAccess(caller, null, true, network);

        // If project was set, this LB belongs to that project
        // Otherwise, the caller is the owner
        final Account owner;
        if (projectId != null) {
            Project project = _projectMgr.getProject(projectId);
            if (project != null) {
                owner = _accountMgr.getAccount(project.getProjectAccountId());
            } else {
                throw new InvalidParameterValueException("Could not find project with id " + projectId);
            }
        } else {
            owner = caller;
        }

        // Get VIP info from GloboNetwork
        boolean isv6 = this.isNetworkv6(network);

        GetVipInfoFromGloboNetworkCommand cmd = new GetVipInfoFromGloboNetworkCommand(lbId, isv6);
        Answer answer = this.callCommand(cmd, network.getDataCenterId());

        if (answer != null && answer.getResult()) {
            final GloboNetworkVipResponse globoNetworkLB = (GloboNetworkVipResponse)answer;

            // Check LB IP address
            PortableIpVO portableVO = _portableIpDao.findByIpAddress(globoNetworkLB.getIp());
            if (portableVO == null) {
                throw new CloudRuntimeException("Portable IP " + globoNetworkLB.getIp() + " is not registered within Cloudstack");
            } else if (portableVO.getState() != PortableIp.State.Free) {
                throw new CloudRuntimeException("IP " + globoNetworkLB.getIp() + " is not free to be used in Cloudstack");
            }

            if (globoNetworkLB.getPorts() == null || globoNetworkLB.getPorts().size() == 0) {
                throw new CloudRuntimeException("Invalid port mapping for LB. It is necessary to have at least 1 port mapping for a load balancer");
            }
            final String[] globoNetworkPorts = globoNetworkLB.getPorts().get(0).split(":");
            if (globoNetworkPorts[0] == "" || globoNetworkPorts[1] == "") {
                throw new CloudRuntimeException("Invalid port mapping for LB: " + globoNetworkLB.getPorts().get(0));
            }
            final List<String> additionalPortMapList = (globoNetworkLB.getPorts().size() > 1) ? globoNetworkLB.getPorts().subList(1, globoNetworkLB.getPorts().size()) : null;

            final String algorithm;
            if ("least-conn".equals(globoNetworkLB.getMethod())) {
                algorithm = "leastconn";
            } else if ("round-robin".equals(globoNetworkLB.getMethod())) {
                algorithm = "roundrobin";
            } else {
                throw new CloudRuntimeException("Invalid balancing method: " + globoNetworkLB.getMethod());
            }

            final String cache = globoNetworkLB.getCache();

            final String lbPersistence;
            if ("cookie".equals(globoNetworkLB.getPersistence())) {
                lbPersistence = "Cookie";
            } else if ("source-ip".equals(globoNetworkLB.getPersistence())) {
                lbPersistence = "Source-ip";
            } else if ("source-ip com persist. entre portas".equals(globoNetworkLB.getPersistence())) {
                lbPersistence = "Source-ip with persistence between ports";
            } else {
                lbPersistence = null;
            }

            try {
                // Allocate IP in Cloudstack
                Long zoneId = network.getDataCenterId();
                IPAddressVO ip = (IPAddressVO)_ipAddrMgr.allocatePortableIp(owner, caller, zoneId, networkId, null, globoNetworkLB.getIp());
                VlanVO vlan = _vlanDao.findById(ip.getVlanId());
                PublicIp publicIp = PublicIp.createFromAddrAndVlan(ip, vlan);

                // register ip
                // Retrieve glbEnvId from network
                GloboNetworkNetworkVO globoNetworkNetworkVO = _globoNetworkNetworkDao.findByNetworkId(networkId);
                if (globoNetworkNetworkVO == null) {
                    throw new InvalidParameterValueException("Unable to find mapping for networkId " + networkId);
                }
                Long glbEnvId = globoNetworkNetworkVO.getGloboNetworkEnvironmentId();

                // Retrieve napiEnvironment from DB
                GloboNetworkEnvironmentVO globoNetworkEnvironment = _globoNetworkEnvironmentDao.findByPhysicalNetworkIdAndEnvironmentId(network.getPhysicalNetworkId(),
                        glbEnvId);

                if (globoNetworkEnvironment == null) {
                    // No physical network/environment pair registered in the database.
                    throw new InvalidParameterValueException("Unable to find a relationship between physical network=" + network.getPhysicalNetworkId()
                            + " and GloboNetwork environment=" + glbEnvId);
                }

                GloboNetworkLoadBalancerEnvironment globoNetworkLBEnvironment = _globoNetworkLBEnvironmentDao.findByEnvironmentRefAndLBNetwork(
                        globoNetworkEnvironment.getId(), globoNetworkLB.getLbEnvironmentId());
                if (globoNetworkLBEnvironment == null) {
                    throw new InvalidParameterValueException("Could not find LB environment " + globoNetworkLB.getLbEnvironmentId());
                }

                GloboNetworkIpDetailVO gnIpDetail = new GloboNetworkIpDetailVO(ip.getId(), globoNetworkLB.getIpId());
                gnIpDetail.setGloboNetworkEnvironmentRefId(globoNetworkLBEnvironment.getId());
                gnIpDetail.setGloboNetworkVipId(globoNetworkLB.getId());
                _globoNetworkIpDetailDao.persist(gnIpDetail);

                // Create LB
                LoadBalancer lb = _lbMgr.createPublicLoadBalancer(null, globoNetworkLB.getName().toLowerCase(), globoNetworkLB.getDetails(), Integer.parseInt(globoNetworkPorts[0], 10),
                        Integer.parseInt(globoNetworkPorts[1], 10), publicIp.getId(), NetUtils.TCP_PROTO, algorithm, false, CallContext.current(), null, Boolean.TRUE, additionalPortMapList, cache, null, null, null, null, true, false);

                // If healthcheck is TCP, do nothing; otherwise, create the healthcheck policy
                if (globoNetworkLB.getHealthcheckType() != null && "HTTP".equals(globoNetworkLB.getHealthcheckType())) {
                    // Default values for timeout and threshold, since those are not used by GloboNetwork
                    _lbService.validateAndPersistLbHealthcheckPolicy(lb.getId(), globoNetworkLB.getHealthcheck(), null, 2, 5, 2, 1, true);
                }

                if (lbPersistence != null) {
                    _lbService.validateAndPersistLbStickinessPolicy(lb.getId(), lbPersistence, lbPersistence, null, null, true);
                }

                // Assign VMs that are managed in Cloudstack
                List<Long> instancesToAdd = new ArrayList<Long>();
                Map<Long, List<String>> vmIdIpMap = new HashMap<Long, List<String>>();
                for (GloboNetworkVipResponse.Real real : globoNetworkLB.getReals()) {
                    // For each real, find it in Cloudstack and add it to LB
                    // If it's not found, ignore it (VM not managed by Cloudstack)
                    Nic nic = _nicDao.findByIp4AddressAndNetworkId(real.getIp(), networkId);
                    if (nic == null) {
                        continue;
                    }
                    instancesToAdd.add(nic.getInstanceId());
                    vmIdIpMap.put(nic.getInstanceId(), Arrays.asList(nic.getIPv4Address()));
                }
                _lbService.assignToLoadBalancer(lb.getId(), instancesToAdd, vmIdIpMap);


                return lb;

            } catch (CloudException e) {
                throw new CloudRuntimeException("There was an error when allocating ip " + globoNetworkLB.getIp() + " in Cloudstack");
            }
        }
        return null;
    }

    @Override
    public String getDomainSuffix() {
        return GloboNetworkDomainSuffix.value();
    }

    @Override
    public boolean isSupportedCustomNetworkDomain() {
        return !StringUtils.isNotBlank(GloboNetworkDomainPattern.value());
    }

    @Override
    public List<DataCenter> getAllZonesThatProviderAreEnabled() {
        List<DataCenter> zonesEnabled = new ArrayList<DataCenter>();
        for (DataCenter dc : _dcDao.listEnabledZones()) {
            if (_networkManager.isProviderEnabledInZone(dc.getId(), Provider.GloboNetwork.getName())) {
                zonesEnabled.add(dc);
            }
        }
        return zonesEnabled;
    }

    private boolean isNetworkv6(Network network) {
        return network.getIp6Gateway() != null;
    }

    @Override
    public List<String> listAllowedLbSuffixes() {
        String allowedDomainsOpt = GloboNetworkLBAllowedSuffixes.value();
        List<String> allowedDomains = new ArrayList<String>();

        for (String allowedDomain : allowedDomainsOpt.trim().split(",")) {
            // Remove any whitespaces
            allowedDomain = allowedDomain.trim();
            if (!allowedDomain.isEmpty()) {
                // Insert the '.' before the domain, if it's not there yet
                allowedDomain = allowedDomain.startsWith(".") ? allowedDomain : "." + allowedDomain;
                allowedDomains.add(allowedDomain);
            }
        }

        Collections.sort(allowedDomains, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return -1*((Integer)o1.length()).compareTo(o2.length());
            }
        });

        return allowedDomains;
    }

    @Override
    public List<GloboNetworkPoolOptionResponse.PoolOption> listPoolOptions(Long lbEnvironmentId, Long networkId, String type) {
        if (lbEnvironmentId == null) {
            throw new InvalidParameterValueException("Invalid LB Environment ID");
        }

        GloboNetworkLoadBalancerEnvironment lbEnvironment = _globoNetworkLBEnvironmentDao.findById(lbEnvironmentId);
        if (lbEnvironment == null) {
            throw new InvalidParameterValueException("Could not find mapping to LB environment " + lbEnvironmentId);
        }

        if (networkId == null) {
            throw new InvalidParameterValueException("Invalid Network ID");
        }

        Network network = _networkManager.getNetwork(networkId);
        if (network == null) {
            throw new InvalidParameterValueException("Cannot find network with ID : " + networkId);
        }

        Answer answer = callCommand(new ListPoolOptionsCommand(lbEnvironment.getGloboNetworkLoadBalancerEnvironmentId(), type), network.getDataCenterId());

        return ((GloboNetworkPoolOptionResponse)answer).getPoolOptions();
    }

    @Override
    public List<GloboNetworkPoolResponse.Pool> listAllPoolByVipId(Long lbId, Long zoneId) {
        if (lbId == null) {
            throw new InvalidParameterValueException("Invalid LB ID");
        }

        LoadBalancer lb = _lbService.findById(lbId);
        GloboNetworkIpDetailVO networkDetail = getNetworkApiVipIp(lb);
        //if the lb is not created in networkApi the networkDetail is not created yet
        if ( networkDetail == null || networkDetail.getGloboNetworkVipId() == null) {
            return new ArrayList<GloboNetworkPoolResponse.Pool>();
        }

        ListPoolLBCommand command = new ListPoolLBCommand(networkDetail.getGloboNetworkVipId());


        Answer answer =  callCommand(command, zoneId);

        //error
        if ( answer == null || !answer.getResult() ) {
            String msg = answer == null ? "Could not list pools lb from networkApi" : answer.getDetails();
            throw new CloudRuntimeException(msg);
        }

        GloboNetworkPoolResponse poolResponse = (GloboNetworkPoolResponse) answer;


        return poolResponse.getPools();

    }

    @Override
    public GloboNetworkPoolResponse.Pool createPool(CreateGloboNetworkPoolCmd cmd) {
        LoadBalancer lb = _lbService.findById(cmd.getLbId());
        if (lb == null){
            throw new CloudRuntimeException("Could not find Load balancer with id: " + cmd.getLbId());
        }
        validatePortPair(lb, cmd.getPublicPort(), cmd.getPrivatePort());

        GloboNetworkIpDetailVO ipDetail = getNetworkApiVipIp(lb);
        GloboNetworkLoadBalancerEnvironment lbEnvironment = _globoNetworkLBEnvironmentDao.findById(ipDetail.getGloboNetworkEnvironmentRefId());
        if (lbEnvironment == null) {
            throw new InvalidParameterValueException("Could not find mapping between lb environment " + ipDetail.getGloboNetworkEnvironmentRefId());
        }
        List<LoadBalancerOptionsVO> options = _lbOptionsDao.listByLoadBalancerId(cmd.getLbId());

        String lbIpAddress = _lbMgr.getSourceIp(lb).addr();
        String lockName = "globonetworklb-" + lbIpAddress;
        final ReentrantLock lock = GlobalLock.getReentrantLock(lockName);
        try {
            if (!lock.tryLock(GloboNetworkLBLockTimeout.value(), TimeUnit.SECONDS)) {
                throw new ResourceUnavailableException(String.format("Failed to acquire lock for load balancer %s", lb.getUuid()), DataCenter.class, cmd.getZoneId());
            }
            List<Real> reals = this.getReals(_lbMgr.getExistingDestinations(cmd.getLbId()), null);

            CreatePoolCommand command = new CreatePoolCommand();
            command.setVipId(ipDetail.getGloboNetworkVipId());
            command.setVipName(lb.getName());
            command.setVipIp(lbIpAddress);
            command.setVipEnvironment(lbEnvironment.getGloboNetworkLoadBalancerEnvironmentId());
            command.setPublicPort(cmd.getPublicPort());
            command.setPrivatePort(cmd.getPrivatePort());
            command.setBalacingAlgorithm(lb.getAlgorithm());
            command.setReals(reals);
            command.setRegion(GloboNetworkRegion.value());
            command.setL4protocol(cmd.getL4());
            command.setL7protocol(cmd.getL7());
            if(options != null && !options.isEmpty()){
                command.setServiceDownAction(options.get(0).getServiceDownAction());
            }

            Answer answer = callCommand(command, cmd.getZoneId());
            handleAnswerIfFail(answer, "Could not create pool");

            _lbPortMapDao.persist(new LoadBalancerPortMapVO(lb.getId(),cmd.getPublicPort(), cmd.getPrivatePort()));

            return ((GloboNetworkPoolResponse) answer).getPool();
        } catch (CloudstackGloboNetworkException ex) {
            String context = CallContext.current().getNdcContext();
            String integrationContext =  ex.getContext();
            String msg = "Integration problem with NetworkAPI, please contact your system administrator. Context: " + context + ", Integration context: " + integrationContext + ", name=" + lb.getName();
            throw new UserCloudRuntimeException(msg);
        } catch (Exception e) {
            throw new UserCloudRuntimeException("Error adding new pool to load balancer. Context: "+ CallContext.current().getNdcContext() + ", lb name=" + lb.getName(), e);
        } finally {
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            GlobalLock.releaseReentrantLock(lockName);
        }
    }

    @Override
    public void deletePool(DeleteGloboNetworkPoolCmd cmd) {
        LoadBalancer lb = _lbService.findById(cmd.getLbId());
        if (lb == null){
            throw new CloudRuntimeException("Could not find Load balancer with id: " + cmd.getLbId());
        }
        GloboNetworkIpDetailVO ipDetail = getNetworkApiVipIp(lb);
        GloboNetworkPoolResponse.Pool poolToRemove = findPoolById(cmd.getLbId(), cmd.getZoneId(), cmd.getPoolId());
        if(poolToRemove.getVipPort().equals(lb.getSourcePortStart()) && poolToRemove.getPort().equals(lb.getDefaultPortStart())){
            throw new InvalidParameterValueException("Default Load balancer port cannot be removed");
        }

        String lockName = "globonetworklb-" + _lbMgr.getSourceIp(lb).addr();
        final ReentrantLock lock = GlobalLock.getReentrantLock(lockName);
        try {
            if (!lock.tryLock(GloboNetworkLBLockTimeout.value(), TimeUnit.SECONDS)) {
                throw new ResourceUnavailableException(String.format("Failed to acquire lock for load balancer %s", lb.getUuid()), DataCenter.class, cmd.getZoneId());
            }

            removePortMapping(lb, poolToRemove);
            DeletePoolCommand command = new DeletePoolCommand(ipDetail.getGloboNetworkVipId(), cmd.getPoolId(), poolToRemove.getVipPort());
            Answer answer = callCommand(command, cmd.getZoneId());
            handleAnswerIfFail(answer, "Could not remove pool");
        } catch (CloudstackGloboNetworkException ex) {
            String context = CallContext.current().getNdcContext();
            String integrationContext =  ex.getContext();
            String msg = "Integration problem with NetworkAPI, please contact your system administrator. Context: " + context + ", Integration context: " + integrationContext + ", name=" + lb.getName();
            throw new UserCloudRuntimeException(msg);
        } catch (Exception e) {
            throw new UserCloudRuntimeException("Error removing port from load balancer. Context: "+ CallContext.current().getNdcContext() + ", lb name=" + lb.getName(), e);
        } finally {
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            GlobalLock.releaseReentrantLock(lockName);
        }
    }

    protected GloboNetworkPoolResponse.Pool findPoolById(Long lbId, Long zoneId, Long poolId) {
        List<GloboNetworkPoolResponse.Pool> pools = listAllPoolByVipId(lbId, zoneId);
        GloboNetworkPoolResponse.Pool poolToRemove = null;
        for(GloboNetworkPoolResponse.Pool pool : pools){
            if(pool.getId().equals(poolId)){
                poolToRemove = pool;
                break;
            }
        }
        if(poolToRemove == null){
            throw new InvalidParameterValueException("Pool not found");
        }
        return poolToRemove;
    }

    protected void removePortMapping(LoadBalancer lb, GloboNetworkPoolResponse.Pool poolToRemove) {
        List<LoadBalancerPortMapVO> portMaps = _lbPortMapDao.listByLoadBalancerId(lb.getId());
        for(LoadBalancerPortMapVO portMap : portMaps){
            if(portMap.getPublicPort() == poolToRemove.getVipPort() && portMap.getPrivatePort() == poolToRemove.getPort()){
                _lbPortMapDao.remove(portMap.getId());
            }
        }
    }

    private void validatePortPair(LoadBalancer lb, Integer publicPort, Integer privatePort) {
        List<LoadBalancerPortMapVO> lbPortMaps = _lbPortMapDao.listByLoadBalancerId(lb.getId());
        lbPortMaps.add(new LoadBalancerPortMapVO(0L, lb.getSourcePortStart(), lb.getDefaultPortStart()));

        for (LoadBalancerPortMapVO lbPortMap : lbPortMaps) {
            if(publicPort.equals(lbPortMap.getPublicPort())){
                if(privatePort.equals(lbPortMap.getPrivatePort())) {
                    throw new InvalidParameterValueException("This public/private port pair already exists.");
                }
                throw new InvalidParameterValueException("This public port already exists.");
            }
        }

        GloboResourceConfigurationVO config = _globoResourceConfigurationDao.getFirst(GloboResourceType.LOAD_BALANCER, lb.getUuid(), GloboResourceKey.dsr);
        if(config != null && config.getBooleanValue()){
            if(!publicPort.equals(privatePort)){
                throw new InvalidParameterValueException("In DSR load balancer the public port must always be the same as private port.");
            }
        }
    }

    @Override
    public List<GloboNetworkPoolResponse.Pool> updatePools(List<Long> poolIds, Long lbId, Long zoneId, String healthcheckType,
                                                           String healthcheck, String expectedHealthcheck, Integer maxConn,
                                                           Protocol.L4 l4protocol, Protocol.L7 l7protocol, boolean redeploy) {
        validateUpdatePool(poolIds, lbId, zoneId, healthcheckType, maxConn );

        LoadBalancer loadBalancer = _lbService.findById(lbId);
        if ( loadBalancer == null ){
            throw new CloudRuntimeException("Can not find Load balancer with id: " + lbId);
        }


        GloboNetworkIpDetailVO networkApiVipIp = getNetworkApiVipIp(loadBalancer);


        HealthCheckHelper healthCheckHelper = HealthCheckHelper.build(loadBalancer.getName(), healthcheckType, healthcheck, expectedHealthcheck);

        UpdatePoolCommand command = new UpdatePoolCommand(poolIds,
                healthCheckHelper.getHealthCheckType(),
                healthCheckHelper.getHealthCheck(),
                healthCheckHelper.getExpectedHealthCheck(), maxConn, loadBalancer.getName());

        command.setL4Protocol(l4protocol);
        command.setL7Protocol(l7protocol);
        command.setRedeploy(redeploy);
        command.setVipId(networkApiVipIp.getGloboNetworkVipId());
        Answer answer =  callCommand(command, zoneId);
        handleAnswerIfFail(answer, "Could not update pools " + poolIds);

        GloboNetworkPoolResponse poolResponse = (GloboNetworkPoolResponse)answer;

        return poolResponse.getPools();
    }

    private void validateUpdatePool(List<Long> poolIds, Long lbId, Long zoneId, String healthcheckType, Integer maxConn) {
        if (lbId == null) {
            throw new InvalidParameterValueException("Invalid LB ID: " + lbId);
        }
        if (zoneId == null) {
            throw new InvalidParameterValueException("Invalid zone ID: " + zoneId);
        }
        if (healthcheckType == null) {
            throw new InvalidParameterValueException("Invalid healthcheckType: " + healthcheckType);
        }
        if (maxConn == null || maxConn < 0) {
            throw new InvalidParameterValueException("Invalid maxconn: " + maxConn);
        }

        if (poolIds == null || poolIds.isEmpty()) {
            throw new InvalidParameterValueException("Invalid pools ID. poolIds: " + poolIds);
        }
    }

    private void handleAnswerIfFail(Answer answer, String genericMsg) {
        //error
        if ( answer == null || !answer.getResult() ) {
            String msg = answer == null ? genericMsg : answer.getDetails();
            throw new CloudRuntimeException(msg);
        }
    }
    @Override
    public GloboNetworkIpDetailVO getNetworkApiVipIp(LoadBalancer lb) {
        Ip sourceIp = _lbMgr.getSourceIp(lb);

        IPAddressVO ipVO = _ipAddrDao.findByIpAndNetworkId(lb.getNetworkId(), sourceIp.addr());
        if (ipVO == null) {
            throw new InvalidParameterValueException("Ip " + sourceIp.addr() + " is not associate with network " + lb.getNetworkId());
        }

        GloboNetworkIpDetailVO gnIpDetail = _globoNetworkIpDetailDao.findByIp(ipVO.getId());

        return gnIpDetail;
    }

    @Override
    public GloboNetworkPoolResponse.Pool getPoolById(Long poolId, Long zoneId) {
        if (poolId == null) {
            throw new InvalidParameterValueException("Invalid Pool ID: " + poolId);
        }
        if (zoneId == null) {
            throw new InvalidParameterValueException("Invalid Zone ID: " + zoneId);
        }

        GetPoolLBByIdCommand command = new GetPoolLBByIdCommand(poolId);

        Answer answer =  callCommand(command, zoneId);

        //error
        if ( answer == null || !answer.getResult() ) {
            String msg = answer == null ? "Coud not get pool by id: " + poolId + " in zone: " + zoneId + "from networkApi" : answer.getDetails();
            throw new CloudRuntimeException(msg);
        }

        GloboNetworkPoolResponse response = (GloboNetworkPoolResponse)answer;

        return response.getPool();
    }

    @Override
    public List<GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck> listAllExpectedHealthchecks() {

        List<DataCenterVO> dataCenterVOs = _dcDao.listEnabledZones();
        if (dataCenterVOs != null && dataCenterVOs.size() == 0) {
            throw new InvalidParameterValueException("No zones enabled to execute command listAllExpectedHealthchecks");
        }

        Long zoneId = dataCenterVOs.get(0).getId();

        ListExpectedHealthchecksCommand command = new ListExpectedHealthchecksCommand();

        GloboNetworkExpectHealthcheckResponse answer =  (GloboNetworkExpectHealthcheckResponse)callCommand(command, zoneId);

        if ( answer == null || !answer.getResult() ) {
            String msg = answer == null ? "Coud not list expected healthchecks in zone: " + zoneId + "from networkApi" : answer.getDetails();
            throw new CloudRuntimeException(msg);
        }

        return answer.getExpectedHealthchecks();
    }

    @Inject
    NetworkService _networkService;
    @Override
    public Pair<List<? extends Network>, Integer> searchForLbNetworks(ListGloboLbNetworksCmd cmd) {
        List<? extends Network> networks = _networkService.searchForAllNetworks(cmd);

        List<Network> networksToReturn = new ArrayList<Network>();
        for (Network network : networks){
            List<GloboNetworkLoadBalancerEnvironment> lbEnvs = listGloboNetworkLBEnvironmentsFromDB(network.getPhysicalNetworkId(), network.getId(), null);
            if (lbEnvs != null && lbEnvs.size() > 0) {
                networksToReturn.add(network);
            }
        }

        //Now apply pagination
        List<? extends Network> wPagination = StringUtils.applyPagination(networksToReturn, cmd.getStartIndex(), cmd.getPageSizeVal());
        if (wPagination != null) {
            Pair<List<? extends Network>, Integer> listWPagination = new Pair<List<? extends Network>, Integer>(wPagination, networksToReturn.size());
            return listWPagination;
        }

        return new Pair<List<? extends Network>, Integer>(networksToReturn, networksToReturn.size());
    }


    @Inject
    NetworkDao _networksDao = null;

    @Override
    public List<LoadBalancerVO> listLinkableLoadBalancers(Long lbid, Long projectId) {
        LoadBalancer lb = _loadBalancerDao.findById(lbid);
        if (lb == null) {
            throw new CloudRuntimeException("Load balancer that you would like to link with another does not exist!");
        }

        GloboResourceConfigurationVO resource = getGloboResourceConfiguration(lb.getUuid(), GloboResourceType.LOAD_BALANCER, GloboResourceKey.linkedLoadBalancer);

        if (resource != null) {
            String lbUuid = resource.getValue();
            LoadBalancer lblinked = _loadBalancerDao.findByUuid(lbUuid);
            throw new CloudRuntimeException("This load balancer is already linked with load balancer: " + lblinked.getName());
        }

        GloboNetworkNetworkVO globoNetworkNetworkVO = _globoNetworkNetworkDao.findByNetworkId(lb.getNetworkId());
        Long envId = globoNetworkNetworkVO.getGloboNetworkEnvironmentId();

        List<LoadBalancerVO> lbs = _loadBalancerDao.listLinkables(lb.getUuid(), envId, lb.getAccountId());
        removeLinkedLbs(lbs);

        return lbs;
    }

    private void removeLinkedLbs(List<LoadBalancerVO> lbs) {
        List<LoadBalancerVO> lbsToRemove = new ArrayList<>();
        for (LoadBalancerVO loadBalancerVO : lbs) {
            GloboResourceConfigurationVO linkedLbConfig = getGloboResourceConfiguration(loadBalancerVO.getUuid(), GloboResourceType.LOAD_BALANCER, GloboResourceKey.linkedLoadBalancer);
            GloboResourceConfigurationVO isDsr = getGloboResourceConfiguration(loadBalancerVO.getUuid(), GloboResourceType.LOAD_BALANCER, GloboResourceKey.dsr);
            if (linkedLbConfig != null || (isDsr != null && isDsr.getBooleanValue())) {
                lbsToRemove.add(loadBalancerVO);
            }
        }
        lbs.removeAll(lbsToRemove);
    }


}
