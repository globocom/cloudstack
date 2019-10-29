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
package com.cloud.api.query.dao;

import com.cloud.server.ResourceTag;
import com.cloud.utils.Pair;
import com.cloud.utils.StringUtils;
import com.cloud.utils.db.TransactionLegacy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.cloud.vm.UserVmManager;
import org.apache.cloudstack.affinity.AffinityGroupResponse;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiConstants.VMDetails;
import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.response.NicExtraDhcpOptionResponse;
import org.apache.cloudstack.api.response.NicResponse;
import org.apache.cloudstack.api.response.NicSecondaryIpResponse;
import org.apache.cloudstack.api.response.SecurityGroupResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.query.QueryService;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.api.ApiDBUtils;
import com.cloud.api.ApiResponseHelper;
import com.cloud.api.query.vo.UserVmJoinVO;
import com.cloud.gpu.GPU;
import com.cloud.service.ServiceOfferingDetailsVO;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.User;
import com.cloud.user.dao.UserDao;
import com.cloud.uservm.UserVm;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.net.Dhcp;
import com.cloud.vm.UserVmDetailVO;
import com.cloud.vm.VirtualMachine.State;
import com.cloud.vm.VmStats;
import com.cloud.vm.dao.NicExtraDhcpOptionDao;
import com.cloud.vm.dao.NicSecondaryIpVO;
import com.cloud.vm.dao.UserVmDetailsDao;

@Component
public class UserVmJoinDaoImpl extends GenericDaoBaseWithTagInformation<UserVmJoinVO, UserVmResponse> implements UserVmJoinDao {
    public static final Logger s_logger = Logger.getLogger(UserVmJoinDaoImpl.class);

    @Inject
    private ConfigurationDao  _configDao;
    @Inject
    public AccountManager _accountMgr;
    @Inject
    private UserVmDetailsDao _userVmDetailsDao;
    @Inject
    private UserDao _userDao;
    @Inject
    private NicExtraDhcpOptionDao _nicExtraDhcpOptionDao;

    private final SearchBuilder<UserVmJoinVO> VmDetailSearch;
    private final SearchBuilder<UserVmJoinVO> activeVmByIsoSearch;
    private static final String VIEW_GLOBO_VM_COLUMNS = "id, uuid, name, display_name, instance_name, state, ha_enabled, account_id, account_name, account_type, project_name, project_id, account_id, domain_id, display_vm, service_offering_uuid, service_offering_name, os_id, os_name, host_name, data_center_id, data_center_name";

    protected UserVmJoinDaoImpl() {

        VmDetailSearch = createSearchBuilder();
        VmDetailSearch.and("idIN", VmDetailSearch.entity().getId(), SearchCriteria.Op.IN);
        VmDetailSearch.done();

        _count = "select count(distinct id) from user_vm_view WHERE ";

        activeVmByIsoSearch = createSearchBuilder();
        activeVmByIsoSearch.and("isoId", activeVmByIsoSearch.entity().getIsoId(), SearchCriteria.Op.EQ);
        activeVmByIsoSearch.and("stateNotIn", activeVmByIsoSearch.entity().getState(), SearchCriteria.Op.NIN);
        activeVmByIsoSearch.done();
    }

    @Override
    public List<UserVmJoinVO> listActiveByIsoId(Long isoId) {
        SearchCriteria<UserVmJoinVO> sc = activeVmByIsoSearch.create();
        sc.setParameters("isoId", isoId);
        State[] states = new State[2];
        states[0] = State.Error;
        states[1] = State.Expunging;
        return listBy(sc);
    }

    @Override
    public UserVmResponse newUserVmResponse(ResponseView view, String objectName, UserVmJoinVO userVm, EnumSet<VMDetails> details, Account caller) {
        UserVmResponse userVmResponse = new UserVmResponse();

        if (userVm.getHypervisorType() != null) {
            userVmResponse.setHypervisor(userVm.getHypervisorType().toString());
        }
        userVmResponse.setId(userVm.getUuid());
        userVmResponse.setName(userVm.getName());

        if (userVm.getDisplayName() != null) {
        userVmResponse.setDisplayName(userVm.getDisplayName());
        } else {
            userVmResponse.setDisplayName(userVm.getName());
        }

        if (userVm.getAccountType() == Account.ACCOUNT_TYPE_PROJECT) {
            userVmResponse.setProjectId(userVm.getProjectUuid());
            userVmResponse.setProjectName(userVm.getProjectName());
        } else {
            userVmResponse.setAccountName(userVm.getAccountName());
        }

        User user = _userDao.getUser(userVm.getUserId());
        if (user != null) {
            userVmResponse.setUserId(user.getUuid());
            userVmResponse.setUserName(user.getUsername());
        }
        userVmResponse.setDomainId(userVm.getDomainUuid());
        userVmResponse.setDomainName(userVm.getDomainName());

        userVmResponse.setCreated(userVm.getCreated());
        userVmResponse.setDisplayVm(userVm.isDisplayVm());

        if (userVm.getState() != null) {
            userVmResponse.setState(userVm.getState().toString());
        }
        userVmResponse.setHaEnable(userVm.isHaEnabled());
        if (details.contains(VMDetails.all) || details.contains(VMDetails.group)) {
            userVmResponse.setGroupId(userVm.getInstanceGroupUuid());
            userVmResponse.setGroup(userVm.getInstanceGroupName());
        }
        userVmResponse.setZoneId(userVm.getDataCenterUuid());
        userVmResponse.setZoneName(userVm.getDataCenterName());
        if (view == ResponseView.Full) {
            userVmResponse.setInstanceName(userVm.getInstanceName());
            userVmResponse.setHostId(userVm.getHostUuid());
            userVmResponse.setHostName(userVm.getHostName());
        }

        if (details.contains(VMDetails.all) || details.contains(VMDetails.tmpl)) {
            userVmResponse.setTemplateId(userVm.getTemplateUuid());
            userVmResponse.setTemplateName(userVm.getTemplateName());
            userVmResponse.setTemplateDisplayText(userVm.getTemplateDisplayText());
            userVmResponse.setPasswordEnabled(userVm.isPasswordEnabled());
        }
        if (details.contains(VMDetails.all) || details.contains(VMDetails.iso)) {
            userVmResponse.setIsoId(userVm.getIsoUuid());
            userVmResponse.setIsoName(userVm.getIsoName());
            userVmResponse.setIsoDisplayText(userVm.getIsoDisplayText());
        }
        if (details.contains(VMDetails.all) || details.contains(VMDetails.servoff)) {
            userVmResponse.setServiceOfferingId(userVm.getServiceOfferingUuid());
            userVmResponse.setServiceOfferingName(userVm.getServiceOfferingName());
        }
        if (details.contains(VMDetails.all) || details.contains(VMDetails.diskoff)) {
            userVmResponse.setDiskOfferingId(userVm.getDiskOfferingUuid());
            userVmResponse.setDiskOfferingName(userVm.getDiskOfferingName());
        }
        if (details.contains(VMDetails.all) || details.contains(VMDetails.servoff) || details.contains(VMDetails.stats)) {
            userVmResponse.setCpuNumber(userVm.getCpu());
            userVmResponse.setCpuSpeed(userVm.getSpeed());
            userVmResponse.setMemory(userVm.getRamSize());
            ServiceOfferingDetailsVO serviceOfferingDetail = ApiDBUtils.findServiceOfferingDetail(userVm.getServiceOfferingId(), GPU.Keys.vgpuType.toString());
            if (serviceOfferingDetail != null) {
                userVmResponse.setVgpu(serviceOfferingDetail.getValue());
            }
        }
        userVmResponse.setGuestOsId(userVm.getGuestOsUuid());
        if (details.contains(VMDetails.all) || details.contains(VMDetails.volume)) {
            userVmResponse.setRootDeviceId(userVm.getVolumeDeviceId());
            if (userVm.getVolumeType() != null) {
                userVmResponse.setRootDeviceType(userVm.getVolumeType().toString());
            }
        }
        userVmResponse.setPassword(userVm.getPassword());
        if (userVm.getJobId() != null) {
            userVmResponse.setJobId(userVm.getJobUuid());
            userVmResponse.setJobStatus(userVm.getJobStatus());
        }
        //userVmResponse.setForVirtualNetwork(userVm.getForVirtualNetwork());

        userVmResponse.setPublicIpId(userVm.getPublicIpUuid());
        userVmResponse.setPublicIp(userVm.getPublicIpAddress());
        userVmResponse.setKeyPairName(userVm.getKeypairName());
        userVmResponse.setOsTypeId(userVm.getGuestOsUuid());

        if (details.contains(VMDetails.all) || details.contains(VMDetails.stats)) {
            // stats calculation
            VmStats vmStats = ApiDBUtils.getVmStatistics(userVm.getId());
            if (vmStats != null) {
                userVmResponse.setCpuUsed(new DecimalFormat("#.##").format(vmStats.getCPUUtilization()) + "%");
                userVmResponse.setNetworkKbsRead((long)vmStats.getNetworkReadKBs());
                userVmResponse.setNetworkKbsWrite((long)vmStats.getNetworkWriteKBs());
                userVmResponse.setDiskKbsRead((long)vmStats.getDiskReadKBs());
                userVmResponse.setDiskKbsWrite((long)vmStats.getDiskWriteKBs());
                userVmResponse.setDiskIORead((long)vmStats.getDiskReadIOs());
                userVmResponse.setDiskIOWrite((long)vmStats.getDiskWriteIOs());
                userVmResponse.setMemoryKBs((long)vmStats.getMemoryKBs());
                userVmResponse.setMemoryIntFreeKBs((long)vmStats.getIntFreeMemoryKBs());
                userVmResponse.setMemoryTargetKBs((long)vmStats.getTargetMemoryKBs());

            }
        }

        if (details.contains(VMDetails.all) || details.contains(VMDetails.secgrp)) {
            Long securityGroupId = userVm.getSecurityGroupId();
            if (securityGroupId != null && securityGroupId.longValue() != 0) {
                SecurityGroupResponse resp = new SecurityGroupResponse();
                resp.setId(userVm.getSecurityGroupUuid());
                resp.setName(userVm.getSecurityGroupName());
                resp.setDescription(userVm.getSecurityGroupDescription());
                resp.setObjectName("securitygroup");
                if (userVm.getAccountType() == Account.ACCOUNT_TYPE_PROJECT) {
                    resp.setProjectId(userVm.getProjectUuid());
                    resp.setProjectName(userVm.getProjectName());
                } else {
                    resp.setAccountName(userVm.getAccountName());
                }
                userVmResponse.addSecurityGroup(resp);
            }
        }

        if (details.contains(VMDetails.all) || details.contains(VMDetails.nics)) {
            long nic_id = userVm.getNicId();
            if (nic_id > 0) {
                NicResponse nicResponse = new NicResponse();
                nicResponse.setId(userVm.getNicUuid());
                nicResponse.setIpaddress(userVm.getIpAddress());
                nicResponse.setGateway(userVm.getGateway());
                nicResponse.setNetmask(userVm.getNetmask());
                nicResponse.setNetworkid(userVm.getNetworkUuid());
                nicResponse.setNetworkName(userVm.getNetworkName());
                nicResponse.setMacAddress(userVm.getMacAddress());
                nicResponse.setIp6Address(userVm.getIp6Address());
                nicResponse.setIp6Gateway(userVm.getIp6Gateway());
                nicResponse.setIp6Cidr(userVm.getIp6Cidr());
                if (userVm.getBroadcastUri() != null) {
                    nicResponse.setBroadcastUri(userVm.getBroadcastUri().toString());
                }
                if (userVm.getIsolationUri() != null) {
                    nicResponse.setIsolationUri(userVm.getIsolationUri().toString());
                }
                if (userVm.getTrafficType() != null) {
                    nicResponse.setTrafficType(userVm.getTrafficType().toString());
                }
                if (userVm.getGuestType() != null) {
                    nicResponse.setType(userVm.getGuestType().toString());
                }
                nicResponse.setIsDefault(userVm.isDefaultNic());
                List<NicSecondaryIpVO> secondaryIps = ApiDBUtils.findNicSecondaryIps(userVm.getNicId());
                if (secondaryIps != null) {
                    List<NicSecondaryIpResponse> ipList = new ArrayList<NicSecondaryIpResponse>();
                    for (NicSecondaryIpVO ip : secondaryIps) {
                        NicSecondaryIpResponse ipRes = new NicSecondaryIpResponse();
                        ipRes.setId(ip.getUuid());
                        ApiResponseHelper.setResponseIpAddress(ip, ipRes);
                        ipList.add(ipRes);
                    }
                    nicResponse.setSecondaryIps(ipList);
                }
                nicResponse.setObjectName("nic");

                List<NicExtraDhcpOptionResponse> nicExtraDhcpOptionResponses = _nicExtraDhcpOptionDao.listByNicId(nic_id).stream()
                        .map(vo -> new NicExtraDhcpOptionResponse(Dhcp.DhcpOptionCode.valueOfInt(vo.getCode()).getName(), vo.getCode(), vo.getValue()))
                        .collect(Collectors.toList());
                nicResponse.setExtraDhcpOptions(nicExtraDhcpOptionResponses);

                userVmResponse.addNic(nicResponse);
            }
        }

        // update tag information
        long tag_id = userVm.getTagId();
        if (tag_id > 0 && !userVmResponse.containTag(tag_id)) {
            addTagInformation(userVm, userVmResponse);
        }

        if (details.contains(VMDetails.all) || details.contains(VMDetails.affgrp)) {
            Long affinityGroupId = userVm.getAffinityGroupId();
            if (affinityGroupId != null && affinityGroupId.longValue() != 0) {
                AffinityGroupResponse resp = new AffinityGroupResponse();
                resp.setId(userVm.getAffinityGroupUuid());
                resp.setName(userVm.getAffinityGroupName());
                resp.setDescription(userVm.getAffinityGroupDescription());
                resp.setObjectName("affinitygroup");
                resp.setAccountName(userVm.getAccountName());
                userVmResponse.addAffinityGroup(resp);
            }
        }

        // set resource details map
        // Allow passing details to end user
        // Honour the display field and only return if display is set to true
        List<UserVmDetailVO> vmDetails = _userVmDetailsDao.listDetails(userVm.getId(), true);
        if (vmDetails != null) {
            Map<String, String> resourceDetails = new HashMap<String, String>();
            for (UserVmDetailVO userVmDetailVO : vmDetails) {
                if (!userVmDetailVO.getName().startsWith(ApiConstants.OVF_PROPERTIES) ||
                        (UserVmManager.DisplayVMOVFProperties.value() && userVmDetailVO.getName().startsWith(ApiConstants.OVF_PROPERTIES))) {
                    resourceDetails.put(userVmDetailVO.getName(), userVmDetailVO.getValue());
                }
            }
            // Remove blacklisted settings if user is not admin
            if (caller.getType() != Account.ACCOUNT_TYPE_ADMIN) {
                String[] userVmSettingsToHide = QueryService.UserVMBlacklistedDetails.value().split(",");
                for (String key : userVmSettingsToHide) {
                    resourceDetails.remove(key.trim());
                }
            }
            userVmResponse.setDetails(resourceDetails);
            if (caller.getType() != Account.ACCOUNT_TYPE_ADMIN) {
                userVmResponse.setReadOnlyUIDetails(QueryService.UserVMReadOnlyUIDetails.value());
            }
        }

        userVmResponse.setObjectName(objectName);
        if (userVm.isDynamicallyScalable() == null) {
            userVmResponse.setDynamicallyScalable(false);
        } else {
            userVmResponse.setDynamicallyScalable(userVm.isDynamicallyScalable());
        }

        return userVmResponse;
    }

    /**
     * The resulting Response attempts to be in line with what is returned from
     * @see com.cloud.api.ApiResponseHelper#createNicResponse(Nic)
     */
    @Override
    public UserVmResponse setUserVmResponse(ResponseView view, UserVmResponse userVmData, UserVmJoinVO uvo) {
        Long securityGroupId = uvo.getSecurityGroupId();
        if (securityGroupId != null && securityGroupId.longValue() != 0) {
            SecurityGroupResponse resp = new SecurityGroupResponse();
            resp.setId(uvo.getSecurityGroupUuid());
            resp.setName(uvo.getSecurityGroupName());
            resp.setDescription(uvo.getSecurityGroupDescription());
            resp.setObjectName("securitygroup");
            if (uvo.getAccountType() == Account.ACCOUNT_TYPE_PROJECT) {
                resp.setProjectId(uvo.getProjectUuid());
                resp.setProjectName(uvo.getProjectName());
            } else {
                resp.setAccountName(uvo.getAccountName());
            }
            userVmData.addSecurityGroup(resp);
        }

        long nic_id = uvo.getNicId();
        if (nic_id > 0) {
            NicResponse nicResponse = new NicResponse();
            // The numbered comments are to keep track of the data returned from here and ApiResponseHelper.createNicResponse()
            // the data can't be identical but some tidying up/unifying might be possible
            /*1: nicUuid*/
            nicResponse.setId(uvo.getNicUuid());
            /*2: networkUuid*/
            nicResponse.setNetworkid(uvo.getNetworkUuid());
            /*3: vmId makes no sense on a nested nic object so it is ommited here */

            if (uvo.getTrafficType() != null) {
            /*4: trafficType*/
                nicResponse.setTrafficType(uvo.getTrafficType().toString());
            }
            if (uvo.getGuestType() != null) {
                /*5: guestType*/
                nicResponse.setType(uvo.getGuestType().toString());
            }
            /*6: ipAddress*/
            nicResponse.setIpaddress(uvo.getIpAddress());
            /*7: gateway*/
            nicResponse.setGateway(uvo.getGateway());
            /*8: netmask*/
            nicResponse.setNetmask(uvo.getNetmask());
            /*9: networkName*/
            nicResponse.setNetworkName(uvo.getNetworkName());
            /*10: macAddress*/
            nicResponse.setMacAddress(uvo.getMacAddress());
            /*11: IPv6Address*/
            nicResponse.setIp6Address(uvo.getIp6Address());
            /*12: IPv6Gateway*/
            nicResponse.setIp6Gateway(uvo.getIp6Gateway());
            /*13: IPv6Cidr*/
            nicResponse.setIp6Cidr(uvo.getIp6Cidr());
            /*14: deviceId*/
// where do we find           nicResponse.setDeviceId(
// this is probably not String.valueOf(uvo.getNicId())); as this is a db-id
            /*15: broadcastURI*/
            if (uvo.getBroadcastUri() != null) {
                nicResponse.setBroadcastUri(uvo.getBroadcastUri().toString());
            }
            /*16: isolationURI*/
            if (uvo.getIsolationUri() != null) {
                nicResponse.setIsolationUri(uvo.getIsolationUri().toString());
            }
            /*17: default*/
            nicResponse.setIsDefault(uvo.isDefaultNic());
            List<NicSecondaryIpVO> secondaryIps = ApiDBUtils.findNicSecondaryIps(uvo.getNicId());
            if (secondaryIps != null) {
                List<NicSecondaryIpResponse> ipList = new ArrayList<NicSecondaryIpResponse>();
                for (NicSecondaryIpVO ip : secondaryIps) {
                    NicSecondaryIpResponse ipRes = new NicSecondaryIpResponse();
                    ipRes.setId(ip.getUuid());
                    ApiResponseHelper.setResponseIpAddress(ip, ipRes);
                    ipList.add(ipRes);
                }
                nicResponse.setSecondaryIps(ipList);
            }

            /* 18: extra dhcp options */
            nicResponse.setObjectName("nic");
            List<NicExtraDhcpOptionResponse> nicExtraDhcpOptionResponses = _nicExtraDhcpOptionDao.listByNicId(nic_id)
                    .stream()
                    .map(vo -> new NicExtraDhcpOptionResponse(Dhcp.DhcpOptionCode.valueOfInt(vo.getCode()).getName(), vo.getCode(), vo.getValue()))
                    .collect(Collectors.toList());
            nicResponse.setExtraDhcpOptions(nicExtraDhcpOptionResponses);
            userVmData.addNic(nicResponse);
        }

        long tag_id = uvo.getTagId();
        if (tag_id > 0 && !userVmData.containTag(tag_id)) {
            addTagInformation(uvo, userVmData);
        }

        Long affinityGroupId = uvo.getAffinityGroupId();
        if (affinityGroupId != null && affinityGroupId.longValue() != 0) {
            AffinityGroupResponse resp = new AffinityGroupResponse();
            resp.setId(uvo.getAffinityGroupUuid());
            resp.setName(uvo.getAffinityGroupName());
            resp.setDescription(uvo.getAffinityGroupDescription());
            resp.setObjectName("affinitygroup");
            resp.setAccountName(uvo.getAccountName());
            userVmData.addAffinityGroup(resp);
        }

        return userVmData;
    }

    @Override
    public List<UserVmJoinVO> searchByIds(Long... vmIds) {
        // set detail batch query size
        int DETAILS_BATCH_SIZE = 2000;
        String batchCfg = _configDao.getValue("detail.batch.query.size");
        if (batchCfg != null) {
            DETAILS_BATCH_SIZE = Integer.parseInt(batchCfg);
        }
        // query details by batches
        List<UserVmJoinVO> uvList = new ArrayList<UserVmJoinVO>();
        // query details by batches
        int curr_index = 0;
        if (vmIds.length > DETAILS_BATCH_SIZE) {
            while ((curr_index + DETAILS_BATCH_SIZE) <= vmIds.length) {
                Long[] ids = new Long[DETAILS_BATCH_SIZE];
                for (int k = 0, j = curr_index; j < curr_index + DETAILS_BATCH_SIZE; j++, k++) {
                    ids[k] = vmIds[j];
                }
                SearchCriteria<UserVmJoinVO> sc = VmDetailSearch.create();
                sc.setParameters("idIN", ids);
                List<UserVmJoinVO> vms = searchIncludingRemoved(sc, null, null, false);
                if (vms != null) {
                    uvList.addAll(vms);
                }
                curr_index += DETAILS_BATCH_SIZE;
            }
        }
        if (curr_index < vmIds.length) {
            int batch_size = (vmIds.length - curr_index);
            // set the ids value
            Long[] ids = new Long[batch_size];
            for (int k = 0, j = curr_index; j < curr_index + batch_size; j++, k++) {
                ids[k] = vmIds[j];
            }
            SearchCriteria<UserVmJoinVO> sc = VmDetailSearch.create();
            sc.setParameters("idIN", ids);
            List<UserVmJoinVO> vms = searchIncludingRemoved(sc, null, null, false);
            if (vms != null) {
                uvList.addAll(vms);
            }
        }
        return uvList;
    }

    @Override
    public List<UserVmJoinVO> newUserVmView(UserVm... userVms) {

        Hashtable<Long, UserVm> userVmDataHash = new Hashtable<Long, UserVm>();
        for (UserVm vm : userVms) {
            if (!userVmDataHash.containsKey(vm.getId())) {
                userVmDataHash.put(vm.getId(), vm);
            }
        }

        Set<Long> vmIdSet = userVmDataHash.keySet();
        List<UserVmJoinVO> uvms = searchByIds(vmIdSet.toArray(new Long[vmIdSet.size()]));
        // populate transit password field from UserVm
        if (uvms != null) {
            for (UserVmJoinVO uvm : uvms) {
                UserVm v = userVmDataHash.get(uvm.getId());
                uvm.setPassword(v.getPassword());
            }
        }
        return uvms;
    }

    public Pair<List<UserVmResponse>, Integer> list(Long id, String name, Long projectId, Map<String, String> tags) {
        List<String> uuids = null;
        if ( tags != null) {
            uuids = listVmUUidsByTag(projectId, tags);
        }

        List<UserVmResponse> result = new ArrayList<UserVmResponse>();
        if ( uuids != null && uuids.size() == 0) {
            return new Pair(result, result.size());
        }

        TransactionLegacy txn = TransactionLegacy.currentTxn();
        List params = new ArrayList();
        StringBuilder sql = new StringBuilder("SELECT ").append(VIEW_GLOBO_VM_COLUMNS).append(" FROM view_globo_vm vm WHERE 1=1");

        if ( id != null ){
            sql.append(" AND vm.id = ? ");
        }

        if ( name != null ){
            sql.append(" AND vm.name = ? ");
        }

        if ( projectId != null ){
            sql.append(" AND vm.project_id = ? ");
        }

        if (uuids != null && uuids.size() > 0){
            sql.append(" AND vm.uuid IN ('" + StringUtils.join("','", uuids.toArray()) +  "')");
        }

        try {
            java.sql.PreparedStatement pstmt = txn.prepareStatement(sql.toString());
            int paramCount = 1;
            if (id != null){
                pstmt.setLong(paramCount++, id);
            }

            if (name != null){
                pstmt.setString(paramCount++, name);
            }

            if (projectId != null){
                pstmt.setLong(paramCount++, projectId);
            }

            s_logger.debug("List Globo VM SQL: " + sql + " Params: " + params);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                UserVmResponse userVmResponse = resultSetToVmResponse(rs);
                result.add(userVmResponse);
            }
        }catch (Exception e) {
            throw new RuntimeException("Error selecting vm data.", e);
        }

        return new Pair(result, result.size());
    }

    protected List<String> listVmUUidsByTag(Long projectId, Map<String, String> tags){
        TransactionLegacy txn = TransactionLegacy.currentTxn();

        List params = new ArrayList();
        String sql = createQueryByTags(projectId, tags, params);

        try {
            java.sql.PreparedStatement pstmt = txn.prepareStatement(sql);
            for (int i= 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            s_logger.debug("List Globo VM SQL: "+ sql + " Params: " + params);
            ResultSet rs = pstmt.executeQuery();
            List<String> uuids = new ArrayList();
            while(rs.next()) {
                uuids.add(rs.getString("resource_uuid"));
            }
            return uuids;
        } catch (Exception e ) {
            throw new RuntimeException("Error selecting resource_id by tag: " + sql + " params: " + params, e);
        }
    }

    protected String createQueryByTags(Long projectId, Map<String, String> tags, List<Object> params) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT tg.resource_uuid as resource_uuid FROM resource_tag_view tg WHERE tg.resource_type = '")
                .append(ResourceTag.ResourceObjectType.UserVm).append("'");

        if (projectId != null) {
            sql.append(" AND tg.project_id = ? ");
            params.add(projectId);
        }

        if (tags != null &&  tags.size() > 0) {
            sql.append(" AND (");
            int i = 0;
            for (String key : tags.keySet()) {
                String value = tags.get(key);

                if (tags.size() > 1 && i != 0) {
                    sql.append(" OR");
                }
                sql.append(" (tg.key = ? AND tg.value = ?)");

                params.add(key);
                params.add(value);
                i++;
            }
            params.add(tags.size());
            sql.append(") GROUP BY tg.resource_uuid HAVING count(tg.resource_uuid) = ?");
        }
        return sql.toString();
    }

    private UserVmResponse resultSetToVmResponse(ResultSet rs) throws SQLException {
        UserVmResponse vmResponse = new UserVmResponse();

        vmResponse.setId(rs.getString("uuid"));
        vmResponse.setDisplayName(rs.getString("display_name") != null ? rs.getString("display_name") : rs.getString("name"));
        vmResponse.setName(rs.getString("name"));
        vmResponse.setInstanceName(rs.getString("instance_name"));
        vmResponse.setState(rs.getString("state"));
        vmResponse.setHaEnable(rs.getBoolean("ha_enabled"));

        vmResponse.setProjectName(rs.getString("project_name"));
        vmResponse.setAccountName(rs.getString("account_name"));

        vmResponse.setDisplayVm(rs.getBoolean("display_vm"));
        vmResponse.setServiceOfferingName(rs.getString("service_offering_name"));
        vmResponse.setServiceOfferingId(rs.getString("service_offering_uuid"));
        vmResponse.setOsTypeName(rs.getString("os_name"));
        vmResponse.setOsTypeId(rs.getString("os_id"));
        vmResponse.setHostName(rs.getString("host_name"));
        vmResponse.setZoneId(rs.getString("data_center_id"));
        vmResponse.setZoneName(rs.getString("data_center_name"));
        vmResponse.setObjectName("virtualmachine");

        return vmResponse;
    }
}
