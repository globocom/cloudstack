/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.helper;

import com.cloud.agent.api.Command;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.agent.api.routing.VmDataCommand;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.network.element.UserDataServiceProvider;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.VirtualMachineProfileImpl;
import com.cloud.vm.dao.UserVmDao;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.log4j.Logger;

import org.apache.cloudstack.engine.subsystem.api.storage.EndPoint;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPointSelector;
import org.apache.cloudstack.engine.subsystem.api.storage.Scope;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.storage.command.ForgetObjectCmd;
import org.apache.cloudstack.storage.command.IntroduceObjectAnswer;
import org.apache.cloudstack.storage.command.IntroduceObjectCmd;
import org.apache.cloudstack.storage.to.VolumeObjectTO;
import org.apache.cloudstack.storage.vmsnapshot.VMSnapshotHelper;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CreateVMSnapshotAnswer;
import com.cloud.agent.api.CreateVMSnapshotCommand;
import com.cloud.agent.api.DeleteVMSnapshotCommand;
import com.cloud.agent.api.VMSnapshotTO;
import com.cloud.agent.api.to.DataTO;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.storage.GuestOSHypervisorVO;
import com.cloud.storage.GuestOSVO;
import com.cloud.storage.dao.GuestOSDao;
import com.cloud.storage.dao.GuestOSHypervisorDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.snapshot.VMSnapshot;

public class HypervisorHelperImpl implements HypervisorHelper {
    private static final Logger s_logger = Logger.getLogger(HypervisorHelperImpl.class);
    @Inject
    EndPointSelector selector;
    @Inject
    VMSnapshotHelper vmSnapshotHelper;
    @Inject
    GuestOSDao guestOSDao;
    @Inject
    GuestOSHypervisorDao guestOsHypervisorDao;
    @Inject
    ConfigurationDao configurationDao;
    @Inject
    AgentManager agentMgr;
    @Inject
    HostDao hostDao;
    @Inject
    private RouterControlHelper _routerControlHelper;
    @Inject
    private UserVmDao _vmDao;
    @Inject
    private DataCenterDao _dcDao;

    @Override
    public DataTO introduceObject(DataTO object, Scope scope, Long storeId) {
        EndPoint ep = selector.select(scope, storeId);
        IntroduceObjectCmd cmd = new IntroduceObjectCmd(object);
        Answer answer = null;
        if (ep == null) {
            String errMsg = "No remote endpoint to send command, check if host or ssvm is down?";
            s_logger.error(errMsg);
            answer = new Answer(cmd, false, errMsg);
        } else {
            answer = ep.sendMessage(cmd);
        }
        if (answer == null || !answer.getResult()) {
            String errMsg = answer == null ? null : answer.getDetails();
            throw new CloudRuntimeException("Failed to introduce object, due to " + errMsg);
        }
        IntroduceObjectAnswer introduceObjectAnswer = (IntroduceObjectAnswer)answer;
        return introduceObjectAnswer.getDataTO();
    }

    @Override
    public boolean forgetObject(DataTO object, Scope scope, Long storeId) {
        EndPoint ep = selector.select(scope, storeId);
        ForgetObjectCmd cmd = new ForgetObjectCmd(object);
        Answer answer = null;
        if (ep == null) {
            String errMsg = "No remote endpoint to send command, check if host or ssvm is down?";
            s_logger.error(errMsg);
            answer = new Answer(cmd, false, errMsg);
        } else {
            answer = ep.sendMessage(cmd);
        }
        if (answer == null || !answer.getResult()) {
            String errMsg = answer == null ? null : answer.getDetails();
            if (errMsg != null) {
                s_logger.debug("Failed to forget object: " + errMsg);
            }
            return false;
        }
        return true;
    }

    @Override
    public VMSnapshotTO quiesceVm(VirtualMachine virtualMachine) {
        String value = configurationDao.getValue("vmsnapshot.create.wait");
        int wait = NumbersUtil.parseInt(value, 1800);
        Long hostId = vmSnapshotHelper.pickRunningHost(virtualMachine.getId());
        VMSnapshotTO vmSnapshotTO = new VMSnapshotTO(1L,  UUID.randomUUID().toString(), VMSnapshot.Type.Disk, null, null, false,
                null, true);
        GuestOSVO guestOS = guestOSDao.findById(virtualMachine.getGuestOSId());
        List<VolumeObjectTO> volumeTOs = vmSnapshotHelper.getVolumeTOList(virtualMachine.getId());
        CreateVMSnapshotCommand ccmd =
            new CreateVMSnapshotCommand(virtualMachine.getInstanceName(), virtualMachine.getUuid(), vmSnapshotTO, volumeTOs, guestOS.getDisplayName());
        HostVO host = hostDao.findById(hostId);
        GuestOSHypervisorVO guestOsMapping = guestOsHypervisorDao.findByOsIdAndHypervisor(guestOS.getId(), host.getHypervisorType().toString(), host.getHypervisorVersion());
        ccmd.setPlatformEmulator(guestOsMapping.getGuestOsName());
        ccmd.setWait(wait);
        try {
            Answer answer = agentMgr.send(hostId, ccmd);
            if (answer != null && answer.getResult()) {
                CreateVMSnapshotAnswer snapshotAnswer = (CreateVMSnapshotAnswer)answer;
                vmSnapshotTO.setVolumes(snapshotAnswer.getVolumeTOs());
            } else {
                String errMsg = (answer != null) ? answer.getDetails() : null;
                throw new CloudRuntimeException("Failed to quiesce vm, due to " + errMsg);
            }
        } catch (AgentUnavailableException e) {
            throw new CloudRuntimeException("Failed to quiesce vm", e);
        } catch (OperationTimedoutException e) {
            throw new CloudRuntimeException("Failed to quiesce vm", e);
        }
        return vmSnapshotTO;
    }

    @Override
    public boolean unquiesceVM(VirtualMachine virtualMachine, VMSnapshotTO vmSnapshotTO) {
        Long hostId = vmSnapshotHelper.pickRunningHost(virtualMachine.getId());
        List<VolumeObjectTO> volumeTOs = vmSnapshotHelper.getVolumeTOList(virtualMachine.getId());
        GuestOSVO guestOS = guestOSDao.findById(virtualMachine.getGuestOSId());

        DeleteVMSnapshotCommand deleteSnapshotCommand = new DeleteVMSnapshotCommand(virtualMachine.getInstanceName(), vmSnapshotTO, volumeTOs, guestOS.getDisplayName());
        try {
            Answer answer = agentMgr.send(hostId, deleteSnapshotCommand);
            if (answer != null && answer.getResult()) {
                return true;
            } else {
                String errMsg = (answer != null) ? answer.getDetails() : null;
                throw new CloudRuntimeException("Failed to unquiesce vm, due to " + errMsg);
            }
        } catch (AgentUnavailableException e) {
            throw new CloudRuntimeException("Failed to unquiesce vm", e);
        } catch (OperationTimedoutException e) {
            throw new CloudRuntimeException("Failed to unquiesce vm", e);
        }
    }

    @Override
    public boolean updateVMMetadaInVrouter(long userVmId, Map<String, String> vmData) throws InsufficientCapacityException, ResourceUnavailableException {
        UserVmVO vm = _vmDao.findById(userVmId);

        if (vm == null) {
            throw new CloudRuntimeException("UserVm " + userVmId + " do not exists!");
        }

        VMTemplateVO template = _templateDao.findByIdIncludingRemoved(vm.getTemplateId());

        List<? extends Nic> nics = _nicDao.listByVmId(vm.getId());
        if (nics == null || nics.isEmpty()) {
            s_logger.error("unable to find any nics for vm " + vm.getUuid());
            throw new CloudRuntimeException("unable to find any nics for vm " + vm.getUuid());
        }
        boolean result = true;
        for (Nic nic : nics) {
            Network network = _networkDao.findById(nic.getNetworkId());
            NicProfile nicProfile = new NicProfile(nic, network, null, null, null, _networkModel.isSecurityGroupSupportedInNetwork(network), _networkModel.getNetworkTag(
                    template.getHypervisorType(), network));

            VirtualMachineProfile vmProfile = new VirtualMachineProfileImpl(vm);

            UserDataServiceProvider element = _networkModel.getUserDataUpdateProvider(network);
            if (element == null) {
                throw new CloudRuntimeException("Can't find network element for " + Network.Service.UserData.getName() + " provider needed for UserData update");
            }

            List<DomainRouterVO> routers = _routerDao.listByNetworkAndRole(network.getId(), Role.VIRTUAL_ROUTER);
            if (routers == null || routers.isEmpty()) {
                s_logger.debug("Can't find virtual router element in network " + network.getId());
                return false;
            }

            for (DomainRouterVO router : routers) {
                final VmDataCommand cmd = new VmDataCommand(nic.getIPv4Address(), vm.getInstanceName(), _networkModel.getExecuteInSeqNtwkElmtCmd());
                cmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, _routerControlHelper.getRouterControlIp(router.getId()));
                cmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, _routerControlHelper.getRouterIpInNetwork(nic.getNetworkId(), router.getId()));
                cmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());

                final DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
                cmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());

                for (String key : vmData.keySet()) {
                    String value = vmData.get(key);
                    cmd.addVmData("metadata", key, value);
                }
                final Commands cmds = new Commands(Command.OnError.Stop);
                cmds.addCommand("vmdata", cmd);
                boolean resultRouter = sendCommandsToRouter(router, cmds);

                if (!resultRouter) {
                    s_logger.error("Failed to update userdata for vm " + vm.getUuid() + " and nic " + nic.getUuid() + " vr " + router.getUuid());
                }
                result = resultRouter && result;
            }

        }
        return result;
    }
}
