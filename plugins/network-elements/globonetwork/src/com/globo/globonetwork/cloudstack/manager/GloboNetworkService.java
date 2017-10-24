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

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.exception.InsufficientVirtualNetworkCapacityException;
import com.cloud.host.HostVO;
import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.utils.Pair;
import com.globo.globonetwork.cloudstack.GloboNetworkIpDetailVO;
import com.globo.globonetwork.cloudstack.api.CreateGloboNetworkPoolCmd;
import com.globo.globonetwork.cloudstack.api.DeleteGloboNetworkPoolCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboLbNetworksCmd;
import com.globo.globonetwork.cloudstack.response.GloboNetworkExpectHealthcheckResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import java.util.List;

import javax.naming.ConfigurationException;

import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolOptionResponse;
import org.apache.cloudstack.acl.ControlledEntity.ACLType;

import com.cloud.dc.DataCenter;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host;
import com.cloud.network.Network;
import com.cloud.network.addr.PublicIp;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.vm.NicProfile;
import com.cloud.vm.VirtualMachineProfile;
import com.globo.globonetwork.client.model.Vlan;
import com.globo.globonetwork.cloudstack.GloboNetworkEnvironmentVO;
import com.globo.globonetwork.cloudstack.GloboNetworkLoadBalancerEnvironment;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAllEnvironmentResponse.Environment;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;

public interface GloboNetworkService {

    /**
     * Add a GloboNetwork Environment to an specific zone.
     * @param physicalNetworkId
     * @param name Name of the relationship, for example, BACKEND, FRONTEND
     * @param napiEnvironmentId
     * @return
     */
    public GloboNetworkEnvironmentVO addGloboNetworkEnvironment(Long physicalNetworkId, String name, Long napiEnvironmentId);

    /**
      * Add a GloboNetwork VIP Environment to a GloboNetwork Environment.
     * @param name Name of the relationship
     * @param physicalNetworkId
     * @param napiEnvironmentId
     * @param globoNetworkLBEnvironmentId
     * @return
     */
    public GloboNetworkLoadBalancerEnvironment addGloboNetworkLBEnvironment(String name, Long physicalNetworkId, Long napiEnvironmentId, Long globoNetworkLBEnvironmentId)
            throws ResourceAllocationException;

    /**
    * Create a new network in sync with GloboNetwork.
    *
    * @param zoneId
    * @param networkOfferingId
    * @param physicalNetworkId
    * @param networkDomain
    * @param aclType
    * @param accountName
    * @param projectId
    * @param domainId
    * @param subdomainAccess
    * @param displayNetwork
    * @param aclId
    * @return
    */
    public Network createNetwork(String name, String displayText, Long zoneId, Long networkOfferingId, Long napiEnvironmentId, String networkDomain, ACLType aclType,
            String accountName, Long projectId, Long domainId, Boolean subdomainAccess, Boolean displayNetwork, Long aclId, Boolean isIpv6, Long subnet) throws ResourceAllocationException,
            ResourceUnavailableException, ConcurrentOperationException, InsufficientCapacityException;

    /**
     * Validate if nicProfile in compatible with Network and destination dest.
     *
     * @param NicProfile
     * @param network
     * @return
     * @throws InsufficientVirtualNetworkCapacityException
     * @throws InsufficientAddressCapacityException
     */
    public Network validateNic(NicProfile nicProfile, VirtualMachineProfile vm, Network network) throws InsufficientVirtualNetworkCapacityException,
            InsufficientAddressCapacityException;

    /**
     * Ensure network is created and active in GloboNetwork. If network is not created, create it.
     * @param network
     * @throws ConfigurationException
     */
    public void implementNetwork(Network network) throws ConfigurationException;

    /**
     * Remove network from GloboNetwork and inactivate the vlan
     * @param network
     */
    public void removeNetworkFromGloboNetwork(Network network);

    /**
     * Deallocate Vlan from GloboNetwork.
     * The vlan must have been previously inactivated with the 'removeNetworkFromGloboNetwork' method.
     * @param network
     */
    public void deallocateVlanFromGloboNetwork(Network network);

    /**
     * List GloboNetwork environments from database with optional parameter physicalNetworkId or zoneId. If all parameters are null
     * all GloboNetwork environments from database are returned.
     * @param physicalNetworkId
     * @param zoneId
     */
    public List<GloboNetworkEnvironmentVO> listGloboNetworkEnvironmentsFromDB(Long physicalNetworkId, Long zoneId);

    /**
     * List GloboNetwork VIP environments from database according to a specific GloboNetwork Environment ID.
     * If this parameter is not passed, all GloboNetwork VIP environments from database are returned.
     * @param physicalNetworkId
     * @param globoNetworkEnvironmentId
     */
    public List<GloboNetworkLoadBalancerEnvironment> listGloboNetworkLBEnvironmentsFromDB(Long physicalNetworkId, Long networkId, Long globoNetworkEnvironmentId);

    /**
    * List all environments from GloboNetwork
    * @param zoneId
    * @return
    */
    public List<Environment> listAllEnvironmentsFromGloboNetwork(Long zoneId);

    boolean canEnable(Long physicalNetworkId);

    /**
     * Removes the relationship between physical network and GloboNetwork environment
     * @param physicalNetworkId
     * @param globoNetworkEnvironmentId
     * @return
     */
    public boolean removeGloboNetworkEnvironment(Long physicalNetworkId, Long globoNetworkEnvironmentId);

    /**
     * Removes the relationship between VIP Environment and GloboNetwork environment
     * @param physicalNetworkId
     * @param globoNetworkEnvironmentId
     * @param globoNetworkLBNetworkId
     * @return
     */
    public boolean removeGloboNetworkLBEnvironment(Long physicalNetworkId, Long globoNetworkEnvironmentId, Long globoNetworkLBEnvironmentId);

    /**
    * Add GloboNetwork host details (provider) to CloudStack
    * @param physicalNetworkId
    * @param username
    * @param password
    * @param url
    */
    public Host addGloboNetworkHost(Long physicalNetworkId, String username, String password, String url);

    /**
     * Retrieve VLAN info from GloboNetwork
     * @param network
     * @return
     */
    public Vlan getVlanInfoFromGloboNetwork(Network network);

    /**
     * Register VM NIC in GloboNetwork
     * @param nic
     * @param vm
     * @param network
     */
    public void registerNicInGloboNetwork(NicProfile nic, VirtualMachineProfile vm, Network network);

    /**
     * Unregister NIC in GloboNetwork
     * @param nic
     * @param vm
     */
    public void unregisterNicInGloboNetwork(NicProfile nic, VirtualMachineProfile vm);

    public boolean removeVmFromLoadBalancer(VirtualMachineProfile vm);

    /**
     * Domain suffix for all networks.
     * @return
     */
    public String getDomainSuffix();

    /**
    * List all GloboNetwork VIPs associated to an account/project
    * @param projectId
    * @return
    */
    public List<GloboNetworkVipResponse> listGloboNetworkVips(Long projectId);

    /**
     * If custom network domain is supported
     * @return
     */
    public boolean isSupportedCustomNetworkDomain();

    public List<DataCenter> getAllZonesThatProviderAreEnabled();

    public List<String> listAllowedLbSuffixes();

    public boolean destroyGloboNetwork(long networkId, boolean forced);

    public List<GloboNetworkVipResponse.Real> listGloboNetworkReals(Long vipId);

    public boolean applyLbRuleInGloboNetwork(Network network, LoadBalancingRule rule) throws ResourceUnavailableException;

    public PublicIp acquireLbIp(Long networkId, Long projectId, Long lbEnvironmentId) throws ResourceAllocationException, ResourceUnavailableException,
            ConcurrentOperationException, InvalidParameterValueException, InsufficientCapacityException;

    public boolean disassociateIpAddrFromGloboNetwork(long ipId);

    GloboResourceConfigurationVO getGloboResourceConfiguration(String uuid, GloboResourceType resourceType, GloboResourceKey key);

    public List<GloboResourceConfigurationVO> getGloboResourceConfigs(String uuid, GloboResourceType resourceType);

    public boolean validateLBRule(Network network, LoadBalancingRule rule);

    public LoadBalancer importGloboNetworkLoadBalancer(Long lbId, Long networkId, Long projectId);

    public List<String> listGloboNetworkLBCacheGroups(Long lbEnvironmentId, Long networkId);

    public List<GloboNetworkPoolOptionResponse.PoolOption> listPoolOptions(Long lbEnvironmentId, Long networkId, String type);

    public List<GloboNetworkPoolResponse.Pool> listAllPoolByVipId(Long id, Long zoneUuid);

    public GloboNetworkPoolResponse.Pool getPoolById(Long poolId, Long zoneId);

    public List<GloboNetworkPoolResponse.Pool> updatePools(List<Long> poolIds, Long vipId, Long zoneId, String healthcheckType,
                                                           String healthcheck, String expectedHealthcheck, Integer maxConn,
                                                           Protocol.L4 l4protocol, Protocol.L7 l7protocol, boolean redeploy);

    public List<GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck> listAllExpectedHealthchecks();

    Pair<List<? extends Network>,Integer> searchForLbNetworks(ListGloboLbNetworksCmd listGloboLbNetworksCmd);

    public void registerDnsForResource(String id, GloboResourceType resourceType);

    GloboNetworkPoolResponse.Pool createPool(CreateGloboNetworkPoolCmd createGloboNetworkPoolCmd);

    void deletePool(DeleteGloboNetworkPoolCmd deleteGloboNetworkPoolCmd);

    List<LoadBalancerVO> listLinkableLoadBalancers(Long lbid, Long projectId);

    GloboNetworkIpDetailVO getNetworkApiVipIp(LoadBalancer lb);

    HostVO getGloboNetworkHost(Long zoneId);

    Answer callCommand(Command cmd, Long zoneId);

}
