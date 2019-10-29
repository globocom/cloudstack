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
package com.globo.globonetwork.cloudstack.guru;

import java.util.List;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import com.cloud.exception.InsufficientVirtualNetworkCapacityException;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.deploy.DeployDestination;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientNetworkCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.Network;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.State;
import com.cloud.network.Networks.BroadcastDomainType;
import com.cloud.network.Networks.Mode;
import com.cloud.network.NetworkProfile;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.PhysicalNetwork.IsolationMethod;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.guru.GuestNetworkGuru;
import com.cloud.network.router.VpcVirtualNetworkApplianceManager;
import com.cloud.offering.NetworkOffering;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallbackWithException;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachineProfile;
import com.globo.globonetwork.cloudstack.GloboNetworkVipAccVO;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkVipAccDao;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkService;

@Component
public class GloboNetworkGuru extends GuestNetworkGuru {
    private static final Logger s_logger = Logger.getLogger(GloboNetworkGuru.class);

    // by default traffic type is TrafficType.Guest

    @Inject
    GloboNetworkService _globoNetworkService;
    @Inject
    VpcVirtualNetworkApplianceManager _routerMgr;
    @Inject
    AccountManager _accountMgr;
    @Inject
    GloboNetworkVipAccDao _globoNetworkVipDao;

    protected NetworkType _networkType = NetworkType.Advanced;

    public GloboNetworkGuru() {
        _isolationMethods = new IsolationMethod[] {new IsolationMethod("VLAN")};
        setName("GloboNetworkGuru");
    }

    public boolean isMyNetworkType(NetworkType networkType) {
        return this._networkType.equals(networkType);
    }

    public NetworkType getSupportedNetworkType() {
        return this._networkType;
    }

    @Override
    protected boolean canHandle(NetworkOffering offering, NetworkType networkType, PhysicalNetwork physicalNetwork) {

        if (isMyNetworkType(networkType) && offering.getGuestType() == Network.GuestType.Shared && isMyTrafficType(offering.getTrafficType())
                && isMyIsolationMethod(physicalNetwork)) {

            if (_networkModel.isProviderEnabledInZone(physicalNetwork.getDataCenterId(), Provider.GloboNetwork.getName())) {
                s_logger.debug("GloboNetwork can handle this network" + " with traffic type " + offering.getTrafficType() + " guest type " + Network.GuestType.Shared
                        + " network type " + networkType + " and physical network " + physicalNetwork);
                return true;
            } else {
                s_logger.debug("GloboNetwork is not enabled for zone" + physicalNetwork.getDataCenterId());
                return false;
            }
        } else {
            s_logger.debug("We only take care of " + getSupportedTrafficType() + " " + Network.GuestType.Shared + " networks" + " with isolation method " + getIsolationMethods()
                    + " and zone of type " + getSupportedNetworkType());
            return false;
        }
    }

    @Override
    public Network design(NetworkOffering offering, DeploymentPlan plan, Network userSpecified, Account owner) {
        DataCenter dc = _dcDao.findById(plan.getDataCenterId());
        PhysicalNetworkVO physnet = _physicalNetworkDao.findById(plan.getPhysicalNetworkId());

        if (!canHandle(offering, dc.getNetworkType(), physnet)) {
            return null;
        }

        NetworkVO config = new NetworkVO(offering.getTrafficType(), Mode.Dhcp, BroadcastDomainType.Vlan, offering.getId(), State.Allocated, plan.getDataCenterId(), plan.getPhysicalNetworkId(), false);

        if (userSpecified != null) {
            if ((userSpecified.getCidr() == null && userSpecified.getGateway() != null) || (userSpecified.getCidr() != null && userSpecified.getGateway() == null)) {
                throw new InvalidParameterValueException("cidr and gateway must be specified together.");
            }

            if ((userSpecified.getIp6Cidr() == null && userSpecified.getIp6Gateway() != null) ||
                (userSpecified.getIp6Cidr() != null && userSpecified.getIp6Gateway() == null)) {
                throw new InvalidParameterValueException("cidrv6 and gatewayv6 must be specified together.");
            }

            if (userSpecified.getCidr() != null) {
                config.setCidr(userSpecified.getCidr());
                config.setGateway(userSpecified.getGateway());
            }

            if (userSpecified.getIp6Cidr() != null) {
                config.setIp6Cidr(userSpecified.getIp6Cidr());
                config.setIp6Gateway(userSpecified.getIp6Gateway());
            }

            if (userSpecified.getBroadcastUri() != null) {
                config.setBroadcastUri(userSpecified.getBroadcastUri());
            }

            if (userSpecified.getBroadcastDomainType() != null) {
                config.setBroadcastDomainType(userSpecified.getBroadcastDomainType());
            }
        }

        return config;
    }

    @Override
    public Network implement(Network network, NetworkOffering offering, DeployDestination dest, ReservationContext context) throws InsufficientVirtualNetworkCapacityException {
        s_logger.debug("Creating network " + network.getName() + " in equipment using GloboNetwork");

        try {
            _globoNetworkService.implementNetwork(network);
        } catch (ConfigurationException e) {
            throw new CloudRuntimeException("Unable to activate network " + network, e);
        }
        return super.implement(network, offering, dest, context);
    }

    @Override
    public void updateNicProfile(NicProfile profile, Network network) {
        DataCenter dc = _dcDao.findById(network.getDataCenterId());
        if (profile != null) {
            profile.setIPv4Dns1(dc.getDns1());
            profile.setIPv4Dns2(dc.getDns2());
            profile.setIPv6Dns1(dc.getIp6Dns1());
            profile.setIPv6Dns2(dc.getIp6Dns2());
        }
    }

    @Override
    public NicProfile allocate(final Network network, final NicProfile nic, final VirtualMachineProfile vm) throws InsufficientVirtualNetworkCapacityException,
            InsufficientAddressCapacityException {

        NicProfile nicProf;
        try {
            nicProf = Transaction.execute(new TransactionCallbackWithException<NicProfile, InsufficientNetworkCapacityException>() {

                @Override
                public NicProfile doInTransaction(TransactionStatus status) throws InsufficientNetworkCapacityException {
                    NicProfile nicProf = GloboNetworkGuru.super.allocate(network, nic, vm);
                    s_logger.debug("Registering NIC " + nic.toString() + " from VM " + vm.toString() + " in GloboNetwork");
                    _globoNetworkService.registerNicInGloboNetwork(nic, vm, network);
                    return nicProf;
                }
            });
        } catch (InsufficientAddressCapacityException e) {
            throw e;
        } catch (InsufficientNetworkCapacityException e) {
            s_logger.error(e.getMessage(), e);
            throw new InsufficientVirtualNetworkCapacityException(e.getMessage(), e.getScope(), e.getId());
        }
        return nicProf;
    }

    @Override
    public void reserve(NicProfile nic, Network network, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
            throws InsufficientVirtualNetworkCapacityException, InsufficientAddressCapacityException {
        s_logger.debug("Asking GuestNetworkGuru to reserve nic " + nic.toString() + " for network " + network.getName());

        super.reserve(nic, network, vm, dest, context);

        _globoNetworkService.validateNic(nic, vm, network);

    }

    @Override
    public void deallocate(Network network, NicProfile nic, VirtualMachineProfile vm) {
        s_logger.debug("Asking GuestNetworkGuru to deallocate NIC " + nic.toString() + " from VM " + vm.getInstanceName());

        // Remove vm from any load balancer prior to deallocating it
        if (_globoNetworkService.removeVmFromLoadBalancer(vm)) {
            _globoNetworkService.unregisterNicInGloboNetwork(nic, vm);
            super.deallocate(network, nic, vm);
        } else {
            throw new CloudRuntimeException("Could not remove VM from load balancers. Please remove it from any load balancer before continuing");
        }
    }

    @Override
    public void shutdown(NetworkProfile profile, NetworkOffering offering) {
        List<GloboNetworkVipAccVO> vips = _globoNetworkVipDao.findByNetwork(profile.getId());
        if (vips != null && !vips.isEmpty()) {
            throw new CloudRuntimeException("There is VIPs related to this network. Network destroyed will be aborted. Delete VIP before.");
        }

        try {
            s_logger.debug("Removing networks from GloboNetwork");
            _globoNetworkService.removeNetworkFromGloboNetwork(profile);

            s_logger.debug("Asking GuestNetworkGuru to shutdown network " + profile.getName());
            // never call super.shutdown because it clear broadcastUri, and sometimes this
            // make same networks without vlan
            //super.shutdown(profile, offering);
        } catch (ConcurrentOperationException e) {
            throw new CloudRuntimeException(e);
        }
    }

    @Override
    public boolean trash(Network network, NetworkOffering offering) {

        s_logger.debug("Deallocating VLAN networks from GloboNetwork");
        _globoNetworkService.deallocateVlanFromGloboNetwork(network);

        s_logger.debug("VLAN networks released. Passing on to GuestNetworkGuru to trash network " + network.getName());
        return super.trash(network, offering);
    }
}
