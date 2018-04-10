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
package com.cloud.network.as;

import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.cloud.event.ActionEventUtils;
import com.cloud.network.as.dao.AutoScaleVmProfileNetworkMapDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.server.ResourceTag;
import com.cloud.server.TaggedResourceService;
import com.cloud.tags.ResourceTagVO;
import com.cloud.tags.dao.ResourceTagDao;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.cloudstack.acl.ControlledEntity;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd.HTTPMethod;
import org.apache.cloudstack.api.BaseListAccountResourcesCmd;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.autoscale.CreateCounterCmd;
import org.apache.cloudstack.api.command.user.autoscale.CreateAutoScalePolicyCmd;
import org.apache.cloudstack.api.command.user.autoscale.CreateAutoScaleVmGroupCmd;
import org.apache.cloudstack.api.command.user.autoscale.CreateAutoScaleVmProfileCmd;
import org.apache.cloudstack.api.command.user.autoscale.CreateConditionCmd;
import org.apache.cloudstack.api.command.user.autoscale.ListAutoScalePoliciesCmd;
import org.apache.cloudstack.api.command.user.autoscale.ListAutoScaleVmGroupsCmd;
import org.apache.cloudstack.api.command.user.autoscale.ListAutoScaleVmProfilesCmd;
import org.apache.cloudstack.api.command.user.autoscale.ListConditionsCmd;
import org.apache.cloudstack.api.command.user.autoscale.ListCountersCmd;
import org.apache.cloudstack.api.command.user.autoscale.UpdateAutoScalePolicyCmd;
import org.apache.cloudstack.api.command.user.autoscale.UpdateAutoScaleVmGroupCmd;
import org.apache.cloudstack.api.command.user.autoscale.UpdateAutoScaleVmProfileCmd;
import org.apache.cloudstack.api.command.user.vm.DeployVMCmd;
import org.apache.cloudstack.config.ApiServiceConfiguration;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;

import com.cloud.api.ApiDBUtils;
import com.cloud.api.dispatch.DispatchChainFactory;
import com.cloud.api.dispatch.DispatchTask;
import com.cloud.configuration.ConfigurationManager;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InsufficientServerCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceInUseException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.IpAddresses;
import com.cloud.network.as.AutoScaleCounter.AutoScaleCounterParam;
import com.cloud.network.as.dao.AutoScalePolicyConditionMapDao;
import com.cloud.network.as.dao.AutoScalePolicyDao;
import com.cloud.network.as.dao.AutoScaleVmGroupDao;
import com.cloud.network.as.dao.AutoScaleVmGroupPolicyMapDao;
import com.cloud.network.as.dao.AutoScaleVmGroupVmMapDao;
import com.cloud.network.as.dao.AutoScaleVmProfileDao;
import com.cloud.network.as.dao.ConditionDao;
import com.cloud.network.as.dao.CounterDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.LoadBalancerDao;
import com.cloud.network.dao.LoadBalancerVMMapDao;
import com.cloud.network.dao.LoadBalancerVMMapVO;
import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.dao.LoadBalancerNetworkMapVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.LoadBalancerNetworkMapDao;
import com.cloud.vm.dao.NicDao;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.network.lb.LoadBalancingRulesService;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.offering.ServiceOffering;
import com.cloud.projects.Project.ListProjectResourcesCriteria;
import com.cloud.template.TemplateManager;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountService;
import com.cloud.user.User;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.uservm.UserVm;
import com.cloud.utils.Pair;
import com.cloud.utils.Ternary;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GenericDao;
import com.cloud.utils.db.JoinBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.UserVmService;
import com.cloud.vm.Nic;


public class AutoScaleManagerImpl<Type> extends ManagerBase implements AutoScaleManager, AutoScaleService, Configurable {
    private static final Logger s_logger = Logger.getLogger(AutoScaleManagerImpl.class);
    protected ScheduledExecutorService _executor = Executors.newScheduledThreadPool(1);

    @Inject
    protected DispatchChainFactory dispatchChainFactory = null;
    @Inject
    EntityManager _entityMgr;
    @Inject
    AccountDao _accountDao;
    @Inject
    AccountManager _accountMgr;
    @Inject
    ConfigurationManager _configMgr;
    @Inject
    TemplateManager _templateMgr;
    @Inject
    LoadBalancingRulesManager _lbRulesMgr;
    @Inject
    NetworkDao _networkDao;
    @Inject
    CounterDao _counterDao;
    @Inject
    ConditionDao _conditionDao;
    @Inject
    LoadBalancerVMMapDao _lb2VmMapDao;
    @Inject
    LoadBalancerDao _lbDao;
    @Inject
    AutoScaleVmProfileDao _autoScaleVmProfileDao;
    @Inject
    AutoScaleVmProfileNetworkMapDao _autoScaleVmProfileNetworkMapDao;
    @Inject
    AutoScalePolicyDao _autoScalePolicyDao;
    @Inject
    AutoScalePolicyConditionMapDao _autoScalePolicyConditionMapDao;
    @Inject
    AutoScaleVmGroupDao _autoScaleVmGroupDao;
    @Inject
    AutoScaleVmGroupPolicyMapDao _autoScaleVmGroupPolicyMapDao;
    @Inject
    AutoScaleVmGroupVmMapDao _autoScaleVmGroupVmMapDao;
    @Inject
    DataCenterDao _dcDao = null;
    @Inject
    UserDao _userDao;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    IPAddressDao _ipAddressDao;
    @Inject
    AccountService _accountService;
    @Inject
    UserVmService _userVmService;
    @Inject
    UserVmManager _userVmManager;
    @Inject
    LoadBalancerVMMapDao _lbVmMapDao;
    @Inject
    NicDao _nicDao;
    @Inject
    LoadBalancerNetworkMapDao _lbNetMapDao;
    @Inject
    LoadBalancingRulesService _loadBalancingRulesService;
    @Inject
    ResourceTagDao _resourceTagDao;
    @Inject
    UserVmDao _userVmDao;
    @Inject
    TaggedResourceService _taggedResourceService;

    private static final int MAX_HTTP_POST_LENGTH = 16 * 2048;

    private static final ConfigKey<String> AutoScaledVmPrefix = new ConfigKey<String>("Advanced", String.class, "autoscale.vm.prefix", "as-vm-",
            "Auto scaled virtual machine name prefix", true, ConfigKey.Scope.Global);

    public List<AutoScaleCounter> getSupportedAutoScaleCounters(long networkid) {
        String capability = _lbRulesMgr.getLBCapability(networkid, Capability.AutoScaleCounters.getName());
        if (capability == null) {
            return null;
        }
        Gson gson = new Gson();
        java.lang.reflect.Type listType = new TypeToken<List<AutoScaleCounter>>() {
        }.getType();
        List<AutoScaleCounter> result = gson.fromJson(capability, listType);
        return result;
    }

    public void validateAutoScaleCounters(long networkid, List<Counter> counters, List<Pair<String, String>> counterParamPassed) {
        List<AutoScaleCounter> supportedCounters = getSupportedAutoScaleCounters(networkid);
        if (supportedCounters == null) {
            throw new InvalidParameterException("AutoScale is not supported in the network");
        }
        for (Counter counter : counters) {
            String counterName = counter.getSource().name().toString();
            boolean isCounterSupported = false;
            for (AutoScaleCounter autoScaleCounter : supportedCounters) {
                if (autoScaleCounter.getName().equals(counterName)) {
                    isCounterSupported = true;
                    List<AutoScaleCounterParam> counterParams = autoScaleCounter.getParamList();
                    for (AutoScaleCounterParam autoScaleCounterParam : counterParams) {
                        boolean isRequiredParameter = autoScaleCounterParam.getRequired();
                        if (isRequiredParameter) {
                            boolean isRequiredParamPresent = false;
                            for (Pair<String, String> pair : counterParamPassed) {
                                if (pair.first().equals(autoScaleCounterParam.getParamName()))
                                    isRequiredParamPresent = true;

                            }
                            if (!isRequiredParamPresent) {
                                throw new InvalidParameterException("Parameter " + autoScaleCounterParam.getParamName() + " has to be set in AutoScaleVmProfile's " +
                                    ApiConstants.COUNTERPARAM_LIST);
                            }
                        }
                    }
                    break;
                }
            }
            if (!isCounterSupported) {
                throw new InvalidParameterException("AutoScale counter with source='" + counter.getSource().name() + "' is not supported " + "in the network");
            }
        }
    }

    private <VO extends ControlledEntity> VO getEntityInDatabase(Account caller, String paramName, Long id, GenericDao<VO, Long> dao) {

        VO vo = dao.findById(id);

        if (vo == null) {
            throw new InvalidParameterValueException("Unable to find " + paramName);
        }

        _accountMgr.checkAccess(caller, null, false, (ControlledEntity)vo);

        return vo;
    }

    private boolean isAutoScaleScaleUpPolicy(AutoScalePolicy policyVO) {
        return policyVO.getAction().equals("scaleup");
    }

    private List<AutoScalePolicyVO> getAutoScalePolicies(String paramName, List<Long> policyIds, List<Counter> counters, int interval, boolean scaleUpPolicies) {
        SearchBuilder<AutoScalePolicyVO> policySearch = _autoScalePolicyDao.createSearchBuilder();
        policySearch.and("ids", policySearch.entity().getId(), Op.IN);
        policySearch.done();
        SearchCriteria<AutoScalePolicyVO> sc = policySearch.create();

        sc.setParameters("ids", policyIds.toArray(new Object[0]));
        List<AutoScalePolicyVO> policies = _autoScalePolicyDao.search(sc, null);

        int prevQuietTime = 0;

        for (AutoScalePolicyVO policy : policies) {
            int quietTime = policy.getQuietTime();
            if (prevQuietTime == 0) {
                prevQuietTime = quietTime;
            }
            int duration = policy.getDuration();
            if (duration < interval) {
                throw new InvalidParameterValueException("duration : " + duration + " specified in a policy cannot be less than vm group's interval : " + interval);
            }

            if (quietTime != prevQuietTime) {
                throw new InvalidParameterValueException("quietTime should be same for all the policies specified in " + paramName);
            }

            if (scaleUpPolicies) {
                if (!isAutoScaleScaleUpPolicy(policy)) {
                    throw new InvalidParameterValueException("Only scaleup policies can be specified in scaleuppolicyids");
                }
            } else {
                if (isAutoScaleScaleUpPolicy(policy)) {
                    throw new InvalidParameterValueException("Only scaledown policies can be specified in scaledownpolicyids");
                }
            }
            List<AutoScalePolicyConditionMapVO> policyConditionMapVOs = _autoScalePolicyConditionMapDao.listByAll(policy.getId(), null);
            for (AutoScalePolicyConditionMapVO policyConditionMapVO : policyConditionMapVOs) {
                long conditionid = policyConditionMapVO.getConditionId();
                Condition condition = _conditionDao.findById(conditionid);
                Counter counter = _counterDao.findById(condition.getCounterid());
                counters.add(counter);
            }
        }
        return policies;
    }

    @DB
    protected AutoScaleVmProfileVO checkValidityAndPersist(final AutoScaleVmProfileVO vmProfile, final List<Long> networkIds, final boolean removeNetworks) {
        long templateId = vmProfile.getTemplateId();
        long autoscaleUserId = vmProfile.getAutoScaleUserId();
        int destroyVmGraceperiod = vmProfile.getDestroyVmGraceperiod();

        VirtualMachineTemplate template = _entityMgr.findById(VirtualMachineTemplate.class, templateId);
        // Make sure a valid template ID was specified
        if (template == null) {
            throw new InvalidParameterValueException("Unable to use the given template.");
        }

        if (destroyVmGraceperiod < 0) {
            throw new InvalidParameterValueException("Destroy Vm Grace Period cannot be less than 0.");
        }

        User user = _userDao.findById(autoscaleUserId);
        //Validate auto scale user against the vmprofile account is not needed anymore,
        //because the profile may be associated with the project account not an user account.
        /*if (user.getAccountId() != vmProfile.getAccountId()) {
            throw new InvalidParameterValueException("AutoScale User id does not belong to the same account");
        }*/

        String apiKey = user.getApiKey();
        String secretKey = user.getSecretKey();
        String csUrl = ApiServiceConfiguration.ApiServletPath.value();

        if (apiKey == null) {
            throw new InvalidParameterValueException("apiKey for user: " + user.getUsername() + " is empty. Please generate it");
        }

        if (secretKey == null) {
            throw new InvalidParameterValueException("secretKey for user: " + user.getUsername() + " is empty. Please generate it");
        }

        if (csUrl == null || csUrl.contains("localhost")) {
            throw new InvalidParameterValueException("Global setting endpointe.url has to be set to the Management Server's API end point");
        }

        validateUserData(vmProfile.getUserData());

        return Transaction.execute(new TransactionCallback<AutoScaleVmProfileVO>() {

            @Override
            public AutoScaleVmProfileVO doInTransaction(TransactionStatus status) {
                AutoScaleVmProfileVO newVmProfile = _autoScaleVmProfileDao.persist(vmProfile);

                if (networkIds != null && !networkIds.isEmpty()) {
                    SearchBuilder<NetworkVO> networkSearch = _networkDao.createSearchBuilder();
                    networkSearch.and("ids", networkSearch.entity().getId(), Op.IN);
                    networkSearch.done();
                    SearchCriteria<NetworkVO> sc = networkSearch.create();

                    sc.setParameters("ids", networkIds.toArray(new Object[0]));
                    List<NetworkVO> networks = _networkDao.search(sc, null);

                    ControlledEntity[] sameOwnerEntities = networks.toArray(new ControlledEntity[networks.size() + 1]);
                    sameOwnerEntities[sameOwnerEntities.length - 1] = newVmProfile;
                    _accountMgr.checkAccess(CallContext.current().getCallingAccount(), null, true, sameOwnerEntities);

                    if (networkIds.size() != networks.size()) {
                        throw new InvalidParameterValueException("Unable to find the network specified");
                    }

                    /* For update case remove the existing mappings and create fresh ones */
                    _autoScaleVmProfileNetworkMapDao.removeByVmProfileId(newVmProfile.getId());

                    Set<Long> networkSet = new LinkedHashSet<>(networkIds); //convert to set to remove duplicates
                    for (Long networkId : networkSet) {
                        _autoScaleVmProfileNetworkMapDao.persist(new AutoScaleVmProfileNetworkMapVO(newVmProfile.getId(), networkId));
                    }
                }else if(removeNetworks){
                    _autoScaleVmProfileNetworkMapDao.removeByVmProfileId(newVmProfile.getId());
                }

                return newVmProfile;
            }
        });
    }

    protected void validateUserData(String userData) {
        byte[] decodedUserData;
        if (userData != null) {
            if (!Base64.isBase64(userData)) {
                throw new InvalidParameterValueException("User data is not base64 encoded");
            }
            if (userData.length() >= MAX_HTTP_POST_LENGTH) {
                throw new InvalidParameterValueException("User data is too long for an http POST request");
            }
            decodedUserData = Base64.decodeBase64(userData.getBytes());
            if (decodedUserData.length > MAX_HTTP_POST_LENGTH) {
                throw new InvalidParameterValueException("User data is too long for POST request");
            }

            if (decodedUserData.length < 1) {
                throw new InvalidParameterValueException("User data is too short");
            }
        }
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEVMPROFILE_CREATE, eventDescription = "creating autoscale vm profile", create = true)
    public AutoScaleVmProfile createAutoScaleVmProfile(CreateAutoScaleVmProfileCmd cmd) {

        Account owner = _accountDao.findById(cmd.getAccountId());
        Account caller = CallContext.current().getCallingAccount();
        _accountMgr.checkAccess(caller, null, true, owner);

        long zoneId = cmd.getZoneId();
        long serviceOfferingId = cmd.getServiceOfferingId();
        long autoscaleUserId = cmd.getAutoscaleUserId();

        DataCenter zone = _entityMgr.findById(DataCenter.class, zoneId);

        if (zone == null) {
            throw new InvalidParameterValueException("Unable to find zone by id");
        }

        ServiceOffering serviceOffering = _entityMgr.findById(ServiceOffering.class, serviceOfferingId);
        if (serviceOffering == null) {
            throw new InvalidParameterValueException("Unable to find service offering by id");
        }

        // validations
        HashMap<String, String> deployParams = cmd.getDeployParamMap();
        if (deployParams.containsKey("networks") && deployParams.get("networks").length() > 0) {
            throw new InvalidParameterValueException(
                "'networks' is not a valid parameter, network for an AutoScaled VM is chosen automatically. An autoscaled VM is deployed in the loadbalancer's network");
        }
        /*
         * Just for making sure the values are right in other deploy params.
         * For ex. if projectId is given as a string instead of an long value, this
         * will be throwing an error.
         */
        dispatchChainFactory.getStandardDispatchChain().dispatch(new DispatchTask(ComponentContext.inject(DeployVMCmd.class), deployParams));

        AutoScaleVmProfileVO profileVO =
            new AutoScaleVmProfileVO(cmd.getZoneId(), cmd.getDomainId(), cmd.getAccountId(), cmd.getServiceOfferingId(), cmd.getTemplateId(), cmd.getOtherDeployParams(),
                cmd.getCounterParamList(), cmd.getDestroyVmGraceperiod(), autoscaleUserId, cmd.getUserData());

        if (cmd.getDisplay() != null) {
            profileVO.setDisplay(cmd.getDisplay());
        }

        profileVO = checkValidityAndPersist(profileVO, cmd.getNetworkIds(), cmd.isRemoveNetworks());
        s_logger.info("Successfully create AutoScale Vm Profile with Id: " + profileVO.getId());

        return profileVO;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEVMPROFILE_UPDATE, eventDescription = "updating autoscale vm profile")
    public AutoScaleVmProfile updateAutoScaleVmProfile(UpdateAutoScaleVmProfileCmd cmd) {
        Long profileId = cmd.getId();
        Long templateId = cmd.getTemplateId();
        Long autoscaleUserId = cmd.getAutoscaleUserId();
        Map counterParamList = cmd.getCounterParamList();

        Integer destroyVmGraceperiod = cmd.getDestroyVmGraceperiod();

        AutoScaleVmProfileVO vmProfile = getEntityInDatabase(CallContext.current().getCallingAccount(), "Auto Scale Vm Profile", profileId, _autoScaleVmProfileDao);

        boolean physicalParameterUpdate = (templateId != null || autoscaleUserId != null || counterParamList != null || destroyVmGraceperiod != null);

        if (templateId != null) {
            vmProfile.setTemplateId(templateId);
        }

        if (autoscaleUserId != null) {
            vmProfile.setAutoscaleUserId(autoscaleUserId);
        }

        if (counterParamList != null) {
            vmProfile.setCounterParamsForUpdate(counterParamList);
        }

        if (destroyVmGraceperiod != null) {
            vmProfile.setDestroyVmGraceperiod(destroyVmGraceperiod);
        }

        if (cmd.getCustomId() != null) {
            vmProfile.setUuid(cmd.getCustomId());
        }

        if (cmd.getDisplay() != null) {
            vmProfile.setDisplay(cmd.getDisplay());
        }

        if(cmd.getUserData() != null){
            vmProfile.setUserData(cmd.getUserData());
        }

        List<AutoScaleVmGroupVO> vmGroupList = _autoScaleVmGroupDao.listByAll(null, profileId);
        for (AutoScaleVmGroupVO vmGroupVO : vmGroupList) {
            if (physicalParameterUpdate && !vmGroupVO.getState().equals(AutoScaleVmGroup.State_Disabled)) {
                throw new InvalidParameterValueException("The AutoScale Vm Profile can be updated only if the Vm Group it is associated with is disabled in state");
            }
        }

        vmProfile = checkValidityAndPersist(vmProfile, cmd.getNetworkIds(), cmd.isRemoveNetworks());
        s_logger.info("Updated Auto Scale Vm Profile id:" + vmProfile.getId());

        return vmProfile;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEVMPROFILE_DELETE, eventDescription = "deleting autoscale vm profile")
    public boolean deleteAutoScaleVmProfile(long id) {
        /* Check if entity is in database */
        getEntityInDatabase(CallContext.current().getCallingAccount(), "AutoScale Vm Profile", id, _autoScaleVmProfileDao);
        if (_autoScaleVmGroupDao.isProfileInUse(id)) {
            throw new InvalidParameterValueException("Cannot delete AutoScale Vm Profile when it is in use by one more vm groups");
        }
        boolean success = _autoScaleVmProfileDao.remove(id);
        if (success) {
            s_logger.info("Successfully deleted AutoScale Vm Profile with Id: " + id);
        }
        return success;
    }

    @Override
    public List<? extends AutoScaleVmProfile> listAutoScaleVmProfiles(ListAutoScaleVmProfilesCmd cmd) {
        Long id = cmd.getId();
        Long templateId = cmd.getTemplateId();
        String otherDeployParams = cmd.getOtherDeployParams();
        Long serviceOffId = cmd.getServiceOfferingId();
        Long zoneId = cmd.getZoneId();
        Long projectId = cmd.getProjectId();
        Boolean display = cmd.getDisplay();

        SearchWrapper<AutoScaleVmProfileVO> searchWrapper = new SearchWrapper<AutoScaleVmProfileVO>(_autoScaleVmProfileDao, AutoScaleVmProfileVO.class, cmd, cmd.getId());
        SearchBuilder<AutoScaleVmProfileVO> sb = searchWrapper.getSearchBuilder();

        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("templateId", sb.entity().getTemplateId(), SearchCriteria.Op.EQ);
        sb.and("serviceOfferingId", sb.entity().getServiceOfferingId(), SearchCriteria.Op.EQ);
        sb.and("otherDeployParams", sb.entity().getOtherDeployParams(), SearchCriteria.Op.LIKE);
        sb.and("zoneId", sb.entity().getZoneId(), SearchCriteria.Op.EQ);
        sb.and("display", sb.entity().isDisplay(), SearchCriteria.Op.EQ);
        SearchCriteria<AutoScaleVmProfileVO> sc = searchWrapper.buildSearchCriteria();

        if (id != null) {
            sc.setParameters("id", id);
        }
        if (templateId != null) {
            sc.setParameters("templateId", templateId);
        }
        if (otherDeployParams != null) {
            sc.addAnd("otherDeployParams", SearchCriteria.Op.LIKE, "%" + otherDeployParams + "%");
        }

        if (serviceOffId != null) {
            sc.setParameters("serviceOfferingId", serviceOffId);
        }

        if (zoneId != null) {
            sc.setParameters("zoneId", zoneId);
        }

        if (projectId != null) {
            sc.setParameters("accountIdIN", projectId);
        }

        if (display != null) {
            sc.setParameters("display", display);
        }

        return searchWrapper.search();
    }

    @DB
    protected AutoScalePolicyVO checkValidityAndPersist(final AutoScalePolicyVO autoScalePolicyVOFinal, final List<Long> conditionIds) {
        final int duration = autoScalePolicyVOFinal.getDuration();
        final int quietTime = autoScalePolicyVOFinal.getQuietTime();

        if (duration < 0) {
            throw new InvalidParameterValueException("duration is an invalid value: " + duration);
        }

        if (quietTime < 0) {
            throw new InvalidParameterValueException("quiettime is an invalid value: " + quietTime);
        }

        return Transaction.execute(new TransactionCallback<AutoScalePolicyVO>() {
            @Override
            public AutoScalePolicyVO doInTransaction(TransactionStatus status) {
                AutoScalePolicyVO autoScalePolicyVO = _autoScalePolicyDao.persist(autoScalePolicyVOFinal);

                if (conditionIds != null) {
                    SearchBuilder<ConditionVO> conditionsSearch = _conditionDao.createSearchBuilder();
                    conditionsSearch.and("ids", conditionsSearch.entity().getId(), Op.IN);
                    conditionsSearch.done();
                    SearchCriteria<ConditionVO> sc = conditionsSearch.create();

                    sc.setParameters("ids", conditionIds.toArray(new Object[0]));
                    List<ConditionVO> conditions = _conditionDao.search(sc, null);

                    ControlledEntity[] sameOwnerEntities = conditions.toArray(new ControlledEntity[conditions.size() + 1]);
                    sameOwnerEntities[sameOwnerEntities.length - 1] = autoScalePolicyVO;
                    _accountMgr.checkAccess(CallContext.current().getCallingAccount(), null, true, sameOwnerEntities);

                    if (conditionIds.size() != conditions.size()) {
                        // TODO report the condition id which could not be found
                        throw new InvalidParameterValueException("Unable to find the condition specified");
                    }

                    ArrayList<Long> counterIds = new ArrayList<Long>();
                    for (ConditionVO condition : conditions) {
                        if (counterIds.contains(condition.getCounterid())) {
                            throw new InvalidParameterValueException(
                                "atleast two conditions in the conditionids have the same counter. It is not right to apply two different conditions for the same counter");
                        }
                        counterIds.add(condition.getCounterid());
                    }

                    /* For update case remove the existing mappings and create fresh ones */
                    _autoScalePolicyConditionMapDao.removeByAutoScalePolicyId(autoScalePolicyVO.getId());

                    for (Long conditionId : conditionIds) {
                        AutoScalePolicyConditionMapVO policyConditionMapVO = new AutoScalePolicyConditionMapVO(autoScalePolicyVO.getId(), conditionId);
                        _autoScalePolicyConditionMapDao.persist(policyConditionMapVO);
                    }
                }

                return autoScalePolicyVO;
            }
        });
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEPOLICY_CREATE, eventDescription = "creating autoscale policy", create = true)
    public AutoScalePolicy createAutoScalePolicy(CreateAutoScalePolicyCmd cmd) {

        int duration = cmd.getDuration();
        Integer quietTime = cmd.getQuietTime();
        String action = cmd.getAction();
        Integer step = cmd.getStep();
        AutoScalePolicy.LogicalOperator operator;

        try {
            operator = AutoScalePolicy.LogicalOperator.valueOf(cmd.getLogicalOperator());
        } catch (IllegalArgumentException ex) {
            throw new InvalidParameterValueException("The logical Operator " + cmd.getLogicalOperator() + " does not exist; Unable to create policy.");
        }

        if (quietTime == null) {
            quietTime = NetUtils.DEFAULT_AUTOSCALE_POLICY_QUIET_TIME;
        }

        action = action.toLowerCase();
        if (!NetUtils.isValidAutoScaleAction(action)) {
            throw new InvalidParameterValueException("action is invalid, only 'scaleup' and 'scaledown' is supported");
        }

        AutoScalePolicyVO policyVO = new AutoScalePolicyVO(cmd.getDomainId(), cmd.getAccountId(), duration, quietTime, null, action, step, operator);

        policyVO = checkValidityAndPersist(policyVO, cmd.getConditionIds());
        s_logger.info("Successfully created AutoScale Policy with Id: " + policyVO.getId());
        return policyVO;
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEPOLICY_DELETE, eventDescription = "deleting autoscale policy")
    public boolean deleteAutoScalePolicy(final long id) {
        /* Check if entity is in database */
        getEntityInDatabase(CallContext.current().getCallingAccount(), "AutoScale Policy", id, _autoScalePolicyDao);

        if (_autoScaleVmGroupPolicyMapDao.isAutoScalePolicyInUse(id)) {
            throw new InvalidParameterValueException("Cannot delete AutoScale Policy when it is in use by one or more AutoScale Vm Groups");
        }

        return Transaction.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                boolean success = true;
                success = _autoScalePolicyDao.remove(id);
                if (!success) {
                    s_logger.warn("Failed to remove AutoScale Policy db object");
                    return false;
                }
                success = _autoScalePolicyConditionMapDao.removeByAutoScalePolicyId(id);
                if (!success) {
                    s_logger.warn("Failed to remove AutoScale Policy Condition mappings");
                    return false;
                }
                s_logger.info("Successfully deleted autoscale policy id : " + id);

                return success;
            }
        });
    }

    public void checkCallerAccess(String accountName, Long domainId) {
        Account caller = CallContext.current().getCallingAccount();
        Account owner = _accountDao.findActiveAccount(accountName, domainId);
        if (owner == null) {
            List<String> idList = new ArrayList<String>();
            idList.add(ApiDBUtils.findDomainById(domainId).getUuid());
            throw new InvalidParameterValueException("Unable to find account " + accountName + " in domain with specifed domainId");
        }
        _accountMgr.checkAccess(caller, null, false, owner);
    }

    private class SearchWrapper<VO extends ControlledEntity> {
        GenericDao<VO, Long> dao;
        SearchBuilder<VO> searchBuilder;
        SearchCriteria<VO> searchCriteria;
        Long domainId;
        boolean isRecursive;
        List<Long> permittedAccounts = new ArrayList<Long>();
        ListProjectResourcesCriteria listProjectResourcesCriteria;
        Filter searchFilter;

        public SearchWrapper(GenericDao<VO, Long> dao, Class<VO> entityClass, BaseListAccountResourcesCmd cmd, Long id)
        {
            this.dao = dao;
            this.searchBuilder = dao.createSearchBuilder();
            domainId = cmd.getDomainId();
            String accountName = cmd.getAccountName();
            isRecursive = cmd.isRecursive();
            boolean listAll = cmd.listAll();
            long startIndex = cmd.getStartIndex();
            long pageSizeVal = cmd.getPageSizeVal();
            Account caller = CallContext.current().getCallingAccount();

            Ternary<Long, Boolean, ListProjectResourcesCriteria> domainIdRecursiveListProject = new Ternary<Long, Boolean,
                    ListProjectResourcesCriteria>(domainId, isRecursive, null);
            _accountMgr.buildACLSearchParameters(caller, id, accountName, null, permittedAccounts, domainIdRecursiveListProject,
                    listAll, false);
            domainId = domainIdRecursiveListProject.first();
            isRecursive = domainIdRecursiveListProject.second();
            ListProjectResourcesCriteria listProjectResourcesCriteria = domainIdRecursiveListProject.third();
            _accountMgr.buildACLSearchBuilder(searchBuilder, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);
            searchFilter = new Filter(entityClass, "id", false, startIndex, pageSizeVal);
        }

        public SearchBuilder<VO> getSearchBuilder() {
            return searchBuilder;
        }

        public SearchCriteria<VO> buildSearchCriteria() {
            searchCriteria = searchBuilder.create();
            _accountMgr.buildACLSearchCriteria(searchCriteria, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);
            return searchCriteria;
        }

        public List<VO> search() {
            return dao.search(searchCriteria, searchFilter);
        }
    }

    @Override
    public List<? extends AutoScalePolicy> listAutoScalePolicies(ListAutoScalePoliciesCmd cmd) {
        SearchWrapper<AutoScalePolicyVO> searchWrapper = new SearchWrapper<AutoScalePolicyVO>(_autoScalePolicyDao, AutoScalePolicyVO.class, cmd, cmd.getId());
        SearchBuilder<AutoScalePolicyVO> sb = searchWrapper.getSearchBuilder();
        Long id = cmd.getId();
        Long conditionId = cmd.getConditionId();
        String action = cmd.getAction();
        Long vmGroupId = cmd.getVmGroupId();
        Long projectId = cmd.getProjectId();

        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("action", sb.entity().getAction(), SearchCriteria.Op.EQ);

        if (conditionId != null) {
            SearchBuilder<AutoScalePolicyConditionMapVO> asPolicyConditionSearch = _autoScalePolicyConditionMapDao.createSearchBuilder();
            asPolicyConditionSearch.and("conditionId", asPolicyConditionSearch.entity().getConditionId(), SearchCriteria.Op.EQ);
            sb.join("asPolicyConditionSearch", asPolicyConditionSearch, sb.entity().getId(), asPolicyConditionSearch.entity().getPolicyId(), JoinBuilder.JoinType.INNER);
        }

        if (vmGroupId != null) {
            SearchBuilder<AutoScaleVmGroupPolicyMapVO> asVmGroupPolicySearch = _autoScaleVmGroupPolicyMapDao.createSearchBuilder();
            asVmGroupPolicySearch.and("vmGroupId", asVmGroupPolicySearch.entity().getVmGroupId(), SearchCriteria.Op.EQ);
            sb.join("asVmGroupPolicySearch", asVmGroupPolicySearch, sb.entity().getId(), asVmGroupPolicySearch.entity().getPolicyId(), JoinBuilder.JoinType.INNER);
        }

        SearchCriteria<AutoScalePolicyVO> sc = searchWrapper.buildSearchCriteria();

        if (id != null) {
            sc.setParameters("id", id);
        }

        if (action != null) {
            sc.setParameters("action", action);
        }

        if (projectId != null) {
            sc.setParameters("accountIdIN", projectId);
        }

        if (conditionId != null) {
            sc.setJoinParameters("asPolicyConditionSearch", "conditionId", conditionId);
        }

        if (vmGroupId != null) {
            sc.setJoinParameters("asVmGroupPolicySearch", "vmGroupId", vmGroupId);
        }

        return searchWrapper.search();
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEPOLICY_UPDATE, eventDescription = "updating autoscale policy")
    public AutoScalePolicy updateAutoScalePolicy(UpdateAutoScalePolicyCmd cmd) {
        Long policyId = cmd.getId();
        Integer duration = cmd.getDuration();
        Integer quietTime = cmd.getQuietTime();
        Integer step = cmd.getStep();
        List<Long> conditionIds = cmd.getConditionIds();
        AutoScalePolicy.LogicalOperator operator;

        try {
            operator = AutoScalePolicy.LogicalOperator.valueOf(cmd.getLogicalOperator());
        } catch (IllegalArgumentException ex) {
            throw new InvalidParameterValueException("The logical Operator " + cmd.getLogicalOperator() + " does not exist; Unable to update policy.");
        }

        AutoScalePolicyVO policy = getEntityInDatabase(CallContext.current().getCallingAccount(), "Auto Scale Policy", policyId, _autoScalePolicyDao);

        if (duration != null) {
            policy.setDuration(duration);
        }

        if (quietTime != null) {
            policy.setQuietTime(quietTime);
        }

        if (step != null) {
            policy.setStep(step);
        }

        if(operator != null){
            policy.setLogicalOperator(operator);
        }

        List<AutoScaleVmGroupPolicyMapVO> vmGroupPolicyList = _autoScaleVmGroupPolicyMapDao.listByPolicyId(policyId);
        for (AutoScaleVmGroupPolicyMapVO vmGroupPolicy : vmGroupPolicyList) {
            AutoScaleVmGroupVO vmGroupVO = _autoScaleVmGroupDao.findById(vmGroupPolicy.getVmGroupId());
            if (vmGroupVO == null) {
                s_logger.warn("Stale database entry! There is an entry in VmGroupPolicyMap but the vmGroup is missing:" + vmGroupPolicy.getVmGroupId());

                continue;

            }
            if (!vmGroupVO.getState().equals(AutoScaleVmGroup.State_Disabled)) {
                throw new InvalidParameterValueException("The AutoScale Policy can be updated only if the Vm Group it is associated with is disabled in state");
            }
            if (policy.getDuration() < vmGroupVO.getInterval()) {
                throw new InvalidParameterValueException("duration is less than the associated AutoScaleVmGroup's interval");
            }
        }

        policy = checkValidityAndPersist(policy, conditionIds);
        s_logger.info("Successfully updated Auto Scale Policy id:" + policyId);
        return policy;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEVMGROUP_CREATE, eventDescription = "creating autoscale vm group", create = true)
    public AutoScaleVmGroup createAutoScaleVmGroup(CreateAutoScaleVmGroupCmd cmd) {
        int minMembers = cmd.getMinMembers();
        int maxMembers = cmd.getMaxMembers();
        Integer interval = cmd.getInterval();
        Boolean forDisplay = cmd.getDisplay();

        if (interval == null) {
            interval = NetUtils.DEFAULT_AUTOSCALE_POLICY_INTERVAL_TIME;
        }

        LoadBalancerVO loadBalancer = getEntityInDatabase(CallContext.current().getCallingAccount(), ApiConstants.LBID, cmd.getLbRuleId(), _lbDao);

        Long zoneId = _ipAddressDao.findById(loadBalancer.getSourceIpAddressId()).getDataCenterId();

        if (_autoScaleVmGroupDao.isAutoScaleLoadBalancer(loadBalancer.getId())) {
            throw new InvalidParameterValueException("an AutoScaleVmGroup is already attached to the lb rule, the existing vm group has to be first deleted");
        }

//        if (_lb2VmMapDao.isVmAttachedToLoadBalancer(loadBalancer.getId())) {
//            throw new InvalidParameterValueException(
//                "there are Vms already bound to the specified LoadBalancing Rule. User bound Vms and AutoScaled Vm Group cannot co-exist on a Load Balancing Rule");
//        }

        AutoScaleVmGroupVO vmGroupVO = new AutoScaleVmGroupVO(cmd.getLbRuleId(), zoneId, loadBalancer.getDomainId(), loadBalancer.getAccountId(), minMembers, maxMembers,
            loadBalancer.getDefaultPortStart(), interval, null, cmd.getProfileId(), AutoScaleVmGroup.State_New, cmd.getVmPrefixName());

        if (forDisplay != null) {
            vmGroupVO.setDisplay(forDisplay);
        }

        vmGroupVO = checkValidityAndPersist(vmGroupVO, cmd.getScaleUpPolicyIds(), cmd.getScaleDownPolicyIds());
        s_logger.info("Successfully created Autoscale Vm Group with Id: " + vmGroupVO.getId());

        return vmGroupVO;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEVMGROUP_CREATE, eventDescription = "creating autoscale vm group", async = true)
    public boolean configureAutoScaleVmGroup(CreateAutoScaleVmGroupCmd cmd) throws ResourceUnavailableException {
        return configureAutoScaleVmGroup(cmd.getEntityId(), AutoScaleVmGroup.State_Disabled);
    }

    public boolean isLoadBalancerBasedAutoScaleVmGroup(AutoScaleVmGroup vmGroup) {
        return vmGroup.getLoadBalancerId() != null;
    }

    private boolean configureAutoScaleVmGroup(long vmGroupid, String currentState) throws ResourceUnavailableException {
        AutoScaleVmGroup vmGroup = _autoScaleVmGroupDao.findById(vmGroupid);

        if (isLoadBalancerBasedAutoScaleVmGroup(vmGroup)) {
            try {
                return _lbRulesMgr.configureLbAutoScaleVmGroup(vmGroupid, currentState);
            } catch (ResourceUnavailableException re) {
                throw re;
            } catch (Exception e) {
                s_logger.warn("Exception during configureLbAutoScaleVmGroup in lb rules manager", e);
                return false;
            }
        }

        // This should never happen, because today loadbalancerruleid is manadatory for AutoScaleVmGroup.
        throw new InvalidParameterValueException("Only LoadBalancer based AutoScale is supported");
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEVMGROUP_DELETE, eventDescription = "deleting autoscale vm group", async = true)
    public boolean deleteAutoScaleVmGroup(final long id) {
        AutoScaleVmGroupVO autoScaleVmGroupVO = getEntityInDatabase(CallContext.current().getCallingAccount(), "AutoScale Vm Group", id, _autoScaleVmGroupDao);

        if (autoScaleVmGroupVO.getState().equals(AutoScaleVmGroup.State_New)) {
            /* This condition is for handling failures during creation command */
            return _autoScaleVmGroupDao.remove(id);
        }
        String bakupState = autoScaleVmGroupVO.getState();
        autoScaleVmGroupVO.setState(AutoScaleVmGroup.State_Revoke);
        _autoScaleVmGroupDao.persist(autoScaleVmGroupVO);
        boolean success = false;

        try {
            success = configureAutoScaleVmGroup(id, bakupState);
        } catch (ResourceUnavailableException e) {
            autoScaleVmGroupVO.setState(bakupState);
            _autoScaleVmGroupDao.persist(autoScaleVmGroupVO);
        } finally {
            if (!success) {
                s_logger.warn("Could not delete AutoScale Vm Group id : " + id);
                return false;
            }
        }

        //removing VMS before removing auto scale group
        this.destroyVmGroupVMs(id);

        return Transaction.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                boolean success = _autoScaleVmGroupDao.remove(id);

                if (!success) {
                    s_logger.warn("Failed to remove AutoScale Group db object");
                    return false;
                }

                success = _autoScaleVmGroupPolicyMapDao.removeByGroupId(id);
                if (!success) {
                    s_logger.warn("Failed to remove AutoScale Group Policy mappings");
                    return false;
                }

                s_logger.info("Successfully deleted autoscale vm group id : " + id);
                return success; // Successfull
            }
        });

    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEVMGROUP_DELETE, eventDescription = "deleting autoscale vm group with dependencies")
    public boolean deleteAutoScaleVmGroupWithDependencies(final long id) {
        AutoScaleVmGroupVO autoScaleVmGroupVO = getEntityInDatabase(CallContext.current().getCallingAccount(), "AutoScale Vm Group", id, _autoScaleVmGroupDao);

        if (autoScaleVmGroupVO.getState().equals(AutoScaleVmGroup.State_New)) {
            /* This condition is for handling failures during creation command */
            return _autoScaleVmGroupDao.remove(id);
        }
        String bakupState = autoScaleVmGroupVO.getState();
        autoScaleVmGroupVO.setState(AutoScaleVmGroup.State_Revoke);
        _autoScaleVmGroupDao.persist(autoScaleVmGroupVO);
        boolean success = false;

        try {
            success = configureAutoScaleVmGroup(id, bakupState);
        } catch (ResourceUnavailableException e) {
            autoScaleVmGroupVO.setState(bakupState);
            _autoScaleVmGroupDao.persist(autoScaleVmGroupVO);
        } finally {
            if (!success) {
                s_logger.warn("Could not delete AutoScale Vm Group id : " + id);
                return false;
            }
        }

        //removing VMS before removing auto scale group
        this.destroyVmGroupVMs(id);

        // Set up objects to be deleted
        final Long vmProfileId = autoScaleVmGroupVO.getProfileId();

        return Transaction.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                boolean success = _autoScaleVmGroupDao.remove(id);

                if (!success) {
                    s_logger.warn("Failed to remove AutoScale Group db object");
                    return false;
                }

                success = deleteAutoScaleVmProfile(vmProfileId);
                if (!success) {
                    s_logger.warn("Failed to remove AutoScale VM Profile associated to Autoscale VM Group");
                    return false;
                }

                // Save up policies to be deleted
                List<AutoScaleVmGroupPolicyMapVO> policiesMapList = _autoScaleVmGroupPolicyMapDao.listByVmGroupId(id);

                // Remove mapping between AutoScale VM Group and policies so policies are not in use anymore
                success = _autoScaleVmGroupPolicyMapDao.removeByGroupId(id);
                if (!success) {
                    s_logger.warn("Failed to remove AutoScale Group Policy mappings");
                    return false;
                }

                for(AutoScaleVmGroupPolicyMapVO policyMapVO : policiesMapList) {
                    Long policyId = policyMapVO.getPolicyId();
                    List<AutoScalePolicyConditionMapVO> conditionsMapList = _autoScalePolicyConditionMapDao.findByPolicyId(policyId);

                    success = deleteAutoScalePolicy(policyId);
                    if (!success) {
                        s_logger.warn("Failed to remove AutoScale Policy " + policyId);
                        return false;
                    }

                    for(AutoScalePolicyConditionMapVO conditionMapVO : conditionsMapList) {
                        Long conditionId = conditionMapVO.getConditionId();
                        try {
                            success = deleteCondition(conditionId);
                        } catch (ResourceInUseException e) {
                            s_logger.warn("Failed to remove condition " + conditionId);
                            return false;
                        }

                        if (!success) {
                            s_logger.warn("Failed to remove condition " + conditionId);
                            return false;
                        }
                    }
                }

                s_logger.info("Successfully deleted autoscale vm group id : " + id);
                return success; // Successfull
            }
        });

    }

    protected void destroyVmGroupVMs(long groupId) {
        AutoScaleVmGroupVO asGroup = _autoScaleVmGroupDao.findById(groupId);
        asGroup.setMinMembers(0);
        asGroup.setState(AutoScaleVmGroup.State_Disabled);
        _autoScaleVmGroupDao.persist(asGroup);

        for(AutoScaleVmGroupVmMapVO autoScaleVmGroupVmMap : _autoScaleVmGroupVmMapDao.listByGroup(groupId)){
            _autoScaleVmGroupVmMapDao.remove(groupId, autoScaleVmGroupVmMap.getInstanceId());
            destroyVM(autoScaleVmGroupVmMap.getInstanceId());
        }
    }

    @Override
    public List<? extends AutoScaleVmGroup> listAutoScaleVmGroups(ListAutoScaleVmGroupsCmd cmd) {
        Long id = cmd.getId();
        Long policyId = cmd.getPolicyId();
        Long loadBalancerId = cmd.getLoadBalancerId();
        Long profileId = cmd.getProfileId();
        Long zoneId = cmd.getZoneId();
        Long projectId = cmd.getProjectId();
        Boolean forDisplay = cmd.getDisplay();

        SearchWrapper<AutoScaleVmGroupVO> searchWrapper = new SearchWrapper<AutoScaleVmGroupVO>(_autoScaleVmGroupDao, AutoScaleVmGroupVO.class, cmd, cmd.getId());
        SearchBuilder<AutoScaleVmGroupVO> sb = searchWrapper.getSearchBuilder();

        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("loadBalancerId", sb.entity().getLoadBalancerId(), SearchCriteria.Op.EQ);
        sb.and("profileId", sb.entity().getProfileId(), SearchCriteria.Op.EQ);
        sb.and("zoneId", sb.entity().getZoneId(), SearchCriteria.Op.EQ);
        sb.and("display", sb.entity().isDisplay(), SearchCriteria.Op.EQ);

        if (policyId != null) {
            SearchBuilder<AutoScaleVmGroupPolicyMapVO> asVmGroupPolicySearch = _autoScaleVmGroupPolicyMapDao.createSearchBuilder();
            asVmGroupPolicySearch.and("policyId", asVmGroupPolicySearch.entity().getPolicyId(), SearchCriteria.Op.EQ);
            sb.join("asVmGroupPolicySearch", asVmGroupPolicySearch, sb.entity().getId(), asVmGroupPolicySearch.entity().getVmGroupId(), JoinBuilder.JoinType.INNER);
        }

        SearchCriteria<AutoScaleVmGroupVO> sc = searchWrapper.buildSearchCriteria();
        if (id != null) {
            sc.setParameters("id", id);
        }
        if (loadBalancerId != null) {
            sc.setParameters("loadBalancerId", loadBalancerId);
        }
        if (profileId != null) {
            sc.setParameters("profileId", profileId);
        }
        if (zoneId != null) {
            sc.setParameters("zoneId", zoneId);
        }
        if (projectId != null) {
            sc.setParameters("accountIdIN", projectId);
        }
        if (policyId != null) {
            sc.setJoinParameters("asVmGroupPolicySearch", "policyId", policyId);
        }
        if (forDisplay != null) {
            sc.setParameters("display", forDisplay);
        }
        return searchWrapper.search();
    }

    @DB
    protected AutoScaleVmGroupVO checkValidityAndPersist(final AutoScaleVmGroupVO vmGroup, final List<Long> passedScaleUpPolicyIds,
        final List<Long> passedScaleDownPolicyIds) {
        int minMembers = vmGroup.getMinMembers();
        int maxMembers = vmGroup.getMaxMembers();
        int interval = vmGroup.getInterval();
        List<Counter> counters = new ArrayList<Counter>();
        List<AutoScalePolicyVO> policies = new ArrayList<AutoScalePolicyVO>();
        final List<Long> policyIds = new ArrayList<Long>();
        List<Long> currentScaleUpPolicyIds = new ArrayList<Long>();
        List<Long> currentScaleDownPolicyIds = new ArrayList<Long>();
        if (vmGroup.getCreated() != null) {
            ApiDBUtils.getAutoScaleVmGroupPolicyIds(vmGroup.getId(), currentScaleUpPolicyIds, currentScaleDownPolicyIds);
        }

        if (minMembers < 0) {
            throw new InvalidParameterValueException(ApiConstants.MIN_MEMBERS + " is an invalid value: " + minMembers);
        }

        if (maxMembers < 0) {
            throw new InvalidParameterValueException(ApiConstants.MAX_MEMBERS + " is an invalid value: " + maxMembers);
        }

        if (minMembers > maxMembers) {
            throw new InvalidParameterValueException(ApiConstants.MIN_MEMBERS + " (" + minMembers + ")cannot be greater than " + ApiConstants.MAX_MEMBERS + " (" +
                maxMembers + ")");
        }

        if (interval < 0) {
            throw new InvalidParameterValueException("interval is an invalid value: " + interval);
        }

        if (passedScaleUpPolicyIds != null) {
            policies.addAll(getAutoScalePolicies("scaleuppolicyid", passedScaleUpPolicyIds, counters, interval, true));
            policyIds.addAll(passedScaleUpPolicyIds);
        } else {
            // Run the interval check for existing policies
            getAutoScalePolicies("scaleuppolicyid", currentScaleUpPolicyIds, counters, interval, true);
            policyIds.addAll(currentScaleUpPolicyIds);
        }

        if (passedScaleDownPolicyIds != null) {
            policies.addAll(getAutoScalePolicies("scaledownpolicyid", passedScaleDownPolicyIds, counters, interval, false));
            policyIds.addAll(passedScaleDownPolicyIds);
        } else {
            // Run the interval check for existing policies
            getAutoScalePolicies("scaledownpolicyid", currentScaleDownPolicyIds, counters, interval, false);
            policyIds.addAll(currentScaleDownPolicyIds);
        }
        AutoScaleVmProfileVO profileVO =
            getEntityInDatabase(CallContext.current().getCallingAccount(), ApiConstants.VMPROFILE_ID, vmGroup.getProfileId(), _autoScaleVmProfileDao);

        LoadBalancerVO loadBalancer = getEntityInDatabase(CallContext.current().getCallingAccount(), ApiConstants.LBID, vmGroup.getLoadBalancerId(), _lbDao);
        validateAutoScaleCounters(loadBalancer.getNetworkId(), counters, profileVO.getCounterParams());

        ControlledEntity[] sameOwnerEntities = policies.toArray(new ControlledEntity[policies.size() + 2]);
        sameOwnerEntities[sameOwnerEntities.length - 2] = loadBalancer;
        sameOwnerEntities[sameOwnerEntities.length - 1] = profileVO;
        _accountMgr.checkAccess(CallContext.current().getCallingAccount(), null, true, sameOwnerEntities);

        return Transaction.execute(new TransactionCallback<AutoScaleVmGroupVO>() {
            @Override
            public AutoScaleVmGroupVO doInTransaction(TransactionStatus status) {
                AutoScaleVmGroupVO vmGroupNew = _autoScaleVmGroupDao.persist(vmGroup);

                if (passedScaleUpPolicyIds != null || passedScaleDownPolicyIds != null) {
                    _autoScaleVmGroupPolicyMapDao.removeByGroupId(vmGroupNew.getId());

                    for (Long policyId : policyIds) {
                        _autoScaleVmGroupPolicyMapDao.persist(new AutoScaleVmGroupPolicyMapVO(vmGroupNew.getId(), policyId));
                    }
                }

                return vmGroupNew;
            }
        });

    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEVMGROUP_UPDATE, eventDescription = "updating autoscale vm group", async = true)
    public AutoScaleVmGroup updateAutoScaleVmGroup(UpdateAutoScaleVmGroupCmd cmd) {
        Long vmGroupId = cmd.getId();
        Integer minMembers = cmd.getMinMembers();
        Integer maxMembers = cmd.getMaxMembers();
        Integer interval = cmd.getInterval();
        Boolean forDisplay = cmd.getDisplay();

        List<Long> scaleUpPolicyIds = cmd.getScaleUpPolicyIds();
        List<Long> scaleDownPolicyIds = cmd.getScaleDownPolicyIds();

        AutoScaleVmGroupVO vmGroupVO = getEntityInDatabase(CallContext.current().getCallingAccount(), "AutoScale Vm Group", vmGroupId, _autoScaleVmGroupDao);

        boolean physicalParametersUpdate = (minMembers != null || maxMembers != null || interval != null);

        if (physicalParametersUpdate && !vmGroupVO.getState().equals(AutoScaleVmGroup.State_Disabled)) {
            throw new InvalidParameterValueException("An AutoScale Vm Group can be updated with minMembers/maxMembers/Interval only when it is in disabled state");
        }

        if (minMembers != null) {
            vmGroupVO.setMinMembers(minMembers);
        }

        if (maxMembers != null) {
            vmGroupVO.setMaxMembers(maxMembers);
        }

        if (maxMembers != null) {
            vmGroupVO.setInterval(interval);
        }

        if (cmd.getCustomId() != null) {
            vmGroupVO.setUuid(cmd.getCustomId());
        }

        if (forDisplay != null) {
            vmGroupVO.setDisplay(forDisplay);
        }

        vmGroupVO = checkValidityAndPersist(vmGroupVO, scaleUpPolicyIds, scaleDownPolicyIds);
        if (vmGroupVO != null) {
            s_logger.debug("Updated Auto Scale VmGroup id:" + vmGroupId);
            return vmGroupVO;
        } else
            return null;
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEVMGROUP_ENABLE, eventDescription = "enabling autoscale vm group", async = true)
    public AutoScaleVmGroup enableAutoScaleVmGroup(Long id) {
        AutoScaleVmGroupVO vmGroup = getEntityInDatabase(CallContext.current().getCallingAccount(), "AutoScale Vm Group", id, _autoScaleVmGroupDao);
        boolean success = false;
        if (!vmGroup.getState().equals(AutoScaleVmGroup.State_Disabled)) {
            throw new InvalidParameterValueException("Only a AutoScale Vm Group which is in Disabled state can be enabled.");
        }

        try {
            vmGroup.setState(AutoScaleVmGroup.State_Enabled);
            vmGroup = _autoScaleVmGroupDao.persist(vmGroup);
            success = configureAutoScaleVmGroup(id, AutoScaleVmGroup.State_Disabled);
        } catch (ResourceUnavailableException e) {
            vmGroup.setState(AutoScaleVmGroup.State_Disabled);
            _autoScaleVmGroupDao.persist(vmGroup);
        } finally {
            if (!success) {
                s_logger.warn("Failed to enable AutoScale Vm Group id : " + id);
                return null;
            }
            s_logger.info("Successfully enabled AutoScale Vm Group with Id:" + id);
        }
        return vmGroup;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_AUTOSCALEVMGROUP_DISABLE, eventDescription = "disabling autoscale vm group", async = true)
    @DB
    public AutoScaleVmGroup disableAutoScaleVmGroup(Long id) {
        AutoScaleVmGroupVO vmGroup = getEntityInDatabase(CallContext.current().getCallingAccount(), "AutoScale Vm Group", id, _autoScaleVmGroupDao);
        boolean success = false;
        if (!vmGroup.getState().equals(AutoScaleVmGroup.State_Enabled)) {
            throw new InvalidParameterValueException("Only a AutoScale Vm Group which is in Enabled state can be disabled.");
        }

        try {
            vmGroup.setState(AutoScaleVmGroup.State_Disabled);
            vmGroup = _autoScaleVmGroupDao.persist(vmGroup);
            success = configureAutoScaleVmGroup(id, AutoScaleVmGroup.State_Enabled);
        } catch (ResourceUnavailableException e) {
            vmGroup.setState(AutoScaleVmGroup.State_Enabled);
            _autoScaleVmGroupDao.persist(vmGroup);
        } finally {
            if (!success) {
                s_logger.warn("Failed to disable AutoScale Vm Group id : " + id);
                return null;
            }
            s_logger.info("Successfully disabled AutoScale Vm Group with Id:" + id);
        }
        return vmGroup;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_COUNTER_CREATE, eventDescription = "Counter", create = true)
    @DB
    public Counter createCounter(CreateCounterCmd cmd) {
        String source = cmd.getSource().toLowerCase();
        String name = cmd.getName();
        Counter.Source src;
        // Validate Source
        try {
            src = Counter.Source.valueOf(source);
        } catch (Exception ex) {
            throw new InvalidParameterValueException("The Source " + source + " does not exist; Unable to create Counter");
        }

        CounterVO counter = null;

        s_logger.debug("Adding Counter " + name);
        counter = _counterDao.persist(new CounterVO(src, name, cmd.getValue()));

        CallContext.current().setEventDetails(" Id: " + counter.getId() + " Name: " + name);
        return counter;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_CONDITION_CREATE, eventDescription = "Condition", create = true)
    public Condition createCondition(CreateConditionCmd cmd) {
        checkCallerAccess(cmd.getAccountName(), cmd.getDomainId());
        String opr = cmd.getRelationalOperator().toUpperCase();
        long cid = cmd.getCounterId();
        long threshold = cmd.getThreshold();
        Condition.Operator op;
        // Validate Relational Operator
        try {
            op = Condition.Operator.valueOf(opr);
        } catch (IllegalArgumentException ex) {
            throw new InvalidParameterValueException("The Operator " + opr + " does not exist; Unable to create Condition.");
        }
        // TODO - Validate threshold

        CounterVO counter = _counterDao.findById(cid);

        if (counter == null) {
            throw new InvalidParameterValueException("Unable to find counter");
        }
        ConditionVO condition = null;

        condition = _conditionDao.persist(new ConditionVO(cid, threshold, cmd.getEntityOwnerId(), cmd.getDomainId(), op));
        s_logger.info("Successfully created condition with Id: " + condition.getId());

        CallContext.current().setEventDetails(" Id: " + condition.getId());
        return condition;
    }

    @Override
    public List<? extends Counter> listCounters(ListCountersCmd cmd) {
        String name = cmd.getName();
        Long id = cmd.getId();
        String source = cmd.getSource();
        if (source != null)
            source = source.toLowerCase();

        Filter searchFilter = new Filter(CounterVO.class, "created", false, cmd.getStartIndex(), cmd.getPageSizeVal());

        List<CounterVO> counters = _counterDao.listCounters(id, name, source, cmd.getKeyword(), searchFilter);

        return counters;
    }

    @Override
    public List<? extends Condition> listConditions(ListConditionsCmd cmd) {
        Long id = cmd.getId();
        Long counterId = cmd.getCounterId();
        Long policyId = cmd.getPolicyId();
        Long projectId = cmd.getProjectId();
        SearchWrapper<ConditionVO> searchWrapper = new SearchWrapper<ConditionVO>(_conditionDao, ConditionVO.class, cmd, cmd.getId());
        SearchBuilder<ConditionVO> sb = searchWrapper.getSearchBuilder();
        if (policyId != null) {
            SearchBuilder<AutoScalePolicyConditionMapVO> asPolicyConditionSearch = _autoScalePolicyConditionMapDao.createSearchBuilder();
            asPolicyConditionSearch.and("policyId", asPolicyConditionSearch.entity().getPolicyId(), SearchCriteria.Op.EQ);
            sb.join("asPolicyConditionSearch", asPolicyConditionSearch, sb.entity().getId(), asPolicyConditionSearch.entity().getConditionId(),
                JoinBuilder.JoinType.INNER);
        }

        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("counterId", sb.entity().getCounterid(), SearchCriteria.Op.EQ);

        // now set the SC criteria...
        SearchCriteria<ConditionVO> sc = searchWrapper.buildSearchCriteria();

        if (id != null) {
            sc.setParameters("id", id);
        }

        if (counterId != null) {
            sc.setParameters("counterId", counterId);
        }

        if (projectId != null) {
            sc.setParameters("accountIdIN", projectId);
        }

        if (policyId != null) {
            sc.setJoinParameters("asPolicyConditionSearch", "policyId", policyId);
        }

        return searchWrapper.search();
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_COUNTER_DELETE, eventDescription = "counter")
    public boolean deleteCounter(long counterId) throws ResourceInUseException {
        // Verify Counter id
        CounterVO counter = _counterDao.findById(counterId);
        if (counter == null) {
            throw new InvalidParameterValueException("Unable to find Counter");
        }

        // Verify if it is used in any Condition

        ConditionVO condition = _conditionDao.findByCounterId(counterId);
        if (condition != null) {
            s_logger.info("Cannot delete counter " + counter.getName() + " as it is being used in a condition.");
            throw new ResourceInUseException("Counter is in use.");
        }

        boolean success = _counterDao.remove(counterId);
        if (success) {
            s_logger.info("Successfully deleted counter with Id: " + counterId);
        }

        return success;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_CONDITION_DELETE, eventDescription = "condition")
    public boolean deleteCondition(long conditionId) throws ResourceInUseException {
        /* Check if entity is in database */
        ConditionVO condition = getEntityInDatabase(CallContext.current().getCallingAccount(), "Condition", conditionId, _conditionDao);
        if (condition == null) {
            throw new InvalidParameterValueException("Unable to find Condition");
        }

        // Verify if condition is used in any autoscale policy
        if (_autoScalePolicyConditionMapDao.isConditionInUse(conditionId)) {
            s_logger.info("Cannot delete condition " + conditionId + " as it is being used in a condition.");
            throw new ResourceInUseException("Cannot delete Condition when it is in use by one or more AutoScale Policies.");
        }
        boolean success = _conditionDao.remove(conditionId);
        if (success) {
            s_logger.info("Successfully deleted condition " + condition.getId());
        }
        return success;
    }

    @Override
    public void cleanUpAutoScaleResources(Long accountId) {
        // cleans Autoscale VmProfiles, AutoScale Policies and Conditions belonging to an account
        int count = 0;
        count = _autoScaleVmProfileDao.removeByAccountId(accountId);
        if (count > 0) {
            s_logger.debug("Deleted " + count + " AutoScale Vm Profile for account Id: " + accountId);
        }
        count = _autoScalePolicyDao.removeByAccountId(accountId);
        if (count > 0) {
            s_logger.debug("Deleted " + count + " AutoScale Policies for account Id: " + accountId);
        }
        count = _conditionDao.removeByAccountId(accountId);
        if (count > 0) {
            s_logger.debug("Deleted " + count + " Conditions for account Id: " + accountId);
        }
    }

    private boolean checkConditionUp(AutoScaleVmGroupVO asGroup, Integer numVm) {
        // check maximum
        Integer currentVM = _autoScaleVmGroupVmMapDao.countByGroup(asGroup.getId());
        Integer maxVm = asGroup.getMaxMembers();
        if (currentVM + numVm > maxVm) {
            s_logger.warn("number of VM will greater than the maximum in this group if scaling up, so do nothing more");
            return false;
        }
        return true;
    }

    protected boolean checkConditionDown(AutoScaleVmGroupVO asGroup) {
        Integer currentVM = _autoScaleVmGroupVmMapDao.countByGroup(asGroup.getId());
        Integer minVm = asGroup.getMinMembers();
        if (currentVM - 1 < minVm) {
            s_logger.warn("number of VM will less than the minimum in this group if scaling down, so do nothing more");
            return false;
        }
        return true;
    }

    protected UserVm createNewVM(AutoScaleVmGroupVO asGroup) {
        AutoScaleVmProfileVO profileVo = _autoScaleVmProfileDao.findById(asGroup.getProfileId());
        long templateId = profileVo.getTemplateId();
        long serviceOfferingId = profileVo.getServiceOfferingId();
        if (templateId == -1) {
            return null;
        }
        // create new VM into DB
        try {
            //Verify that all objects exist before passing them to the service
            Account owner = _accountService.getActiveAccountById(profileVo.getAccountId());

            DataCenter zone = _entityMgr.findById(DataCenter.class, profileVo.getZoneId());
            if (zone == null) {
                throw new InvalidParameterValueException("Unable to find zone by id=" + profileVo.getZoneId());
            }

            ServiceOffering serviceOffering = _entityMgr.findById(ServiceOffering.class, serviceOfferingId);
            if (serviceOffering == null) {
                throw new InvalidParameterValueException("Unable to find service offering: " + serviceOfferingId);
            }

            VirtualMachineTemplate template = _entityMgr.findById(VirtualMachineTemplate.class, templateId);
            // Make sure a valid template ID was specified
            if (template == null) {
                throw new InvalidParameterValueException("Unable to use template " + templateId);
            }

            if (!zone.isLocalStorageEnabled()) {
                if (serviceOffering.getUseLocalStorage()) {
                    throw new InvalidParameterValueException("Zone is not configured to use local storage but service offering " + serviceOffering.getName() + " uses it");
                }
            }

            UserVm vm = null;
            IpAddresses addrs = new IpAddresses(null, null);
            String instanceName = createInstanceName(asGroup);

            if (zone.getNetworkType() == NetworkType.Basic) {
                vm = _userVmService.createBasicSecurityGroupVirtualMachine(zone, serviceOffering, template, null, owner, instanceName,
                        instanceName, null, null, null, HypervisorType.XenServer, HTTPMethod.GET, null, null, null,
                    null, true, null, null, null, null, null, null);
            } else {
                if (zone.isSecurityGroupEnabled()) {
                    vm = _userVmService.createAdvancedSecurityGroupVirtualMachine(zone, serviceOffering, template, null, null,
                        owner, instanceName,
                            instanceName, null, null, null, HypervisorType.XenServer, HTTPMethod.GET, null, null,
                        null, null, true, null, null, null, null, null, null);

                } else {
                    List<Long> networkIds = new ArrayList<>();
                    Long mainNetworkId = getDestinationNetworkId(asGroup);
                    zone = getZone(mainNetworkId);
                    networkIds.add(mainNetworkId);
                    networkIds.addAll(getAdditionalNetWorkIds(profileVo, zone));
                    vm = _userVmService.createAdvancedVirtualMachine(zone, serviceOffering, template, networkIds, owner, instanceName, instanceName,
                        null, null, null, template.getHypervisorType(), HTTPMethod.POST, profileVo.getUserData(), null, null, addrs, true, null, null, null, null, null, null);

                }
            }

            return vm;
        } catch (InsufficientCapacityException ex) {
            s_logger.warn(ex.getMessage(), ex);
            throw new ServerApiException(ApiErrorCode.INSUFFICIENT_CAPACITY_ERROR, ex.getMessage());
        } catch (ResourceUnavailableException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, ex.getMessage());
        } catch (ConcurrentOperationException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        } catch (ResourceAllocationException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.RESOURCE_ALLOCATION_ERROR, ex.getMessage());
        }
    }

    protected String createInstanceName(AutoScaleVmGroupVO asGroup) {
        String vmPrefixName = asGroup.getVmPrefixName();
        if (vmPrefixName.isEmpty() || vmPrefixName.equals("")){
            return AutoScaledVmPrefix.value() + asGroup.getId() + "-" + getCurrentTimeStampString();
        }
        return vmPrefixName + asGroup.getId() + "-" + getCurrentTimeStampString();
    }

    private List<Long> getAdditionalNetWorkIds(AutoScaleVmProfileVO profileVo, DataCenter zone) {
        List<Long> networkIds = new ArrayList<>();
        for(AutoScaleVmProfileNetworkMapVO asProfileNetMap : _autoScaleVmProfileNetworkMapDao.listByVmProfileId(profileVo.getId())){
            NetworkVO network = _networkDao.findById(asProfileNetMap.getNetworkId());
            if(network.getDataCenterId() == zone.getId()) {
                networkIds.add(asProfileNetMap.getNetworkId());
            }
        }
        return networkIds;
    }

    private DataCenter getZone(Long mainNetworkId) {
        return _entityMgr.findById(DataCenter.class, _networkDao.findById(mainNetworkId).getDataCenterId());
    }

    protected Long getDestinationNetworkId(AutoScaleVmGroup asGroup){
        LoadBalancer lb = _loadBalancingRulesService.findById(asGroup.getLoadBalancerId());
        List<LoadBalancerNetworkMapVO> lbNetMapList = _lbNetMapDao.listByLoadBalancerId(lb.getId());

        Map<Long, Integer> networkInstanceCount = new HashMap<>();
        networkInstanceCount.put(lb.getNetworkId(), 0);
        for (LoadBalancerNetworkMapVO lbNetMap : lbNetMapList) {
            networkInstanceCount.put(lbNetMap.getNetworkId(), 0);
        }

        for(LoadBalancingRule.LbDestination destination : getExistingDestinations(lb, lbNetMapList)){
            Integer currentCount = networkInstanceCount.get(destination.getNetworkId());
            networkInstanceCount.put(destination.getNetworkId(), currentCount + 1);
        }

        Map.Entry<Long, Integer> chosenNetworkId = null;
        for (Map.Entry<Long, Integer> entry : networkInstanceCount.entrySet()) {
            if (chosenNetworkId == null || chosenNetworkId.getValue() > entry.getValue()) {
                chosenNetworkId = entry;
            }
        }
        if(chosenNetworkId == null){
            return lb.getNetworkId();
        }else{
            return chosenNetworkId.getKey();
        }
    }

    private List<LoadBalancingRule.LbDestination> getExistingDestinations(LoadBalancer lb, List<LoadBalancerNetworkMapVO> lbNetMapList) {
        List<LoadBalancingRule.LbDestination> dstList = new ArrayList<>();
        List<LoadBalancerVMMapVO> lbVmMaps = _lb2VmMapDao.listByLoadBalancerId(lb.getId());

        for (LoadBalancerVMMapVO lbVmMap : lbVmMaps) {
            Nic nic = this.getLbInstanceNic(lb, lbVmMap.getInstanceId(), lbNetMapList);
            LoadBalancingRule.LbDestination lbDst = new LoadBalancingRule.LbDestination(lb.getDefaultPortStart(), lb.getDefaultPortEnd(), null, nic.getNetworkId(), lbVmMap.getInstanceId(), lbVmMap.isRevoke());
            AutoScaleVmGroupVmMapVO autoScaleVmMap = _autoScaleVmGroupVmMapDao.findByVmId(lbVmMap.getInstanceId());
            if(autoScaleVmMap != null){
                dstList.add(lbDst);
            }

        }

        return dstList;
    }

    private Nic getLbInstanceNic(LoadBalancer lb, long vmId, List<LoadBalancerNetworkMapVO> lbNetMapList) {
        Nic nic = _nicDao.findByInstanceIdAndNetworkIdIncludingRemoved(lb.getNetworkId(), vmId);
        if (nic != null) {
            return nic;
        }
        for (LoadBalancerNetworkMapVO lbNetMap : lbNetMapList) {
            nic = _nicDao.findByInstanceIdAndNetworkIdIncludingRemoved(lbNetMap.getNetworkId(), vmId);
            if (nic != null) {
                return nic;
            }
        }
        return null;
    }

    private String getCurrentTimeStampString() {
        Date current = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

        return sdf.format(current);
    }

    protected void startNewVM(long vmId) {
        try {
            CallContext.current().setEventDetails("Vm Id: " + vmId);
            _userVmManager.startVirtualMachine(vmId, null, null, null);
        } catch (final ResourceUnavailableException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, ex.getMessage());
        } catch (ConcurrentOperationException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        } catch (InsufficientCapacityException ex) {
            StringBuilder message = new StringBuilder(ex.getMessage());
            if (ex instanceof InsufficientServerCapacityException) {
                if (((InsufficientServerCapacityException)ex).isAffinityApplied()) {
                    message.append(", Please check the affinity groups provided, there may not be sufficient capacity to follow them");
                }
            }
            s_logger.info(message.toString(), ex);
            throw new ServerApiException(ApiErrorCode.INSUFFICIENT_CAPACITY_ERROR, message.toString());
        }
    }

    protected boolean assignLBruleToNewVm(UserVm vm, AutoScaleVmGroupVO asGroup) {
        long lbId = asGroup.getLoadBalancerId();

        List<LoadBalancerVMMapVO> LbVmMapVos = _lbVmMapDao.listByLoadBalancerId(lbId);
        if ((LbVmMapVos != null) && (LbVmMapVos.size() > 0)) {
            for (LoadBalancerVMMapVO LbVmMapVo : LbVmMapVos) {
                long instanceId = LbVmMapVo.getInstanceId();
                if (instanceId == vm.getId()) {
                    s_logger.warn("the new VM is already mapped to LB rule. What's wrong?");
                    return true;
                }
            }
        }
        try {
            return _loadBalancingRulesService.assignToLoadBalancer(lbId, Arrays.asList(new Long(vm.getId())), new HashMap<Long, List<String>>());
        }catch(Exception e){
            // Rolling back VM creation as it cannot be assigned to the load balancer
            destroyVM(vm.getId());
            return false;
        }
    }

    protected long removeLBrule(AutoScaleVmGroupVO asGroup) {
        long lbId = asGroup.getLoadBalancerId();
        long asGroupId = asGroup.getId();
        long instanceId = -1;
        List<AutoScaleVmGroupVmMapVO> autoScaleVmGroupVmMapVos = _autoScaleVmGroupVmMapDao.listByGroup(asGroupId);
        if ((autoScaleVmGroupVmMapVos != null) && (autoScaleVmGroupVmMapVos.size() > 0)) {
            for (AutoScaleVmGroupVmMapVO autoScaleVmGroupVmMapVO : autoScaleVmGroupVmMapVos) {
                instanceId = autoScaleVmGroupVmMapVO.getInstanceId();
            }
        }
        // take last VM out of the list
        List<Long> lstVmId = new ArrayList<Long>();
        if (instanceId != -1)
            lstVmId.add(instanceId);
        if (_loadBalancingRulesService.removeFromLoadBalancer(lbId, lstVmId, new HashMap<Long, List<String>>()))
            return instanceId;
        else
            return -1;
    }

    public void applyTagToAutoScaleGroupVm(Long groupId, ResourceTagVO resourceTagVO) {
        AutoScaleVmGroupVO asGroup = _autoScaleVmGroupDao.findById(groupId);
        if (asGroup != null) {
            List<AutoScaleVmGroupVmMapVO> autoScaleVmGroupVmMapVOs = _autoScaleVmGroupVmMapDao.listByGroup(asGroup.getId());
            List<String> resourceIds = new ArrayList();
            Map<String, String> tags = new HashMap();
            for(AutoScaleVmGroupVmMapVO autoscaleVmGroupVmMapVO: autoScaleVmGroupVmMapVOs) {
                UserVmVO userVmVO = _userVmDao.findById(autoscaleVmGroupVmMapVO.getInstanceId());
                if (userVmVO != null) {
                    resourceIds.add(String.valueOf(userVmVO.getId()));
                }
            }
            tags.put(resourceTagVO.getKey(), resourceTagVO.getValue());
            _taggedResourceService.createTags(resourceIds, ResourceTag.ResourceObjectType.UserVm, tags, resourceTagVO.getCustomer());
        }
    }

    @Override
    public void deleteTagsFromAutoScaleGroupVms(List<String> resourceIds, Map<String, String> tags) {
        for (String resourceUuid: resourceIds) {
            AutoScaleVmGroupVO autoscaleVmGroupVO = _autoScaleVmGroupDao.findByUuid(resourceUuid);
            if (autoscaleVmGroupVO != null) {
                List<AutoScaleVmGroupVmMapVO> autoScaleVmGroupVmMapVOs = _autoScaleVmGroupVmMapDao.listByGroup(autoscaleVmGroupVO.getId());
                for (AutoScaleVmGroupVmMapVO autoScaleVmGroupVmMapVO: autoScaleVmGroupVmMapVOs) {
                    List<String> vmResourceIds = new ArrayList();
                    vmResourceIds.add(String.valueOf(autoScaleVmGroupVmMapVO.getInstanceId()));
                    try {
                        _taggedResourceService.deleteTags(vmResourceIds, ResourceTag.ResourceObjectType.UserVm, tags);
                    } catch(InvalidParameterValueException ex) {
                        String message = ex.getMessage();
                        s_logger.info(message + " Tags were already removed for the resource: "+vmResourceIds.get(0));
                    }
                }
            }
        }
    }

    @Override
    public void doScaleUp(long groupId, Integer numVm) {
        AutoScaleVmGroupVO asGroup = _autoScaleVmGroupDao.findById(groupId);
        if (asGroup == null) {
            s_logger.error("Can not find the groupid " + groupId + " for scaling up");
            return;
        }

        try {
            for (int i = 0; i < numVm; i++) {
                if (!checkConditionUp(asGroup, numVm)) {
                    return;
                }

                UserVm vm = createNewVM(asGroup);
                if (vm == null) {
                    s_logger.error("Can not deploy new VM for scaling up in the group " + asGroup.getId() + ". Waiting for next round");
                    break;
                }

                addVirtualMachineTags(asGroup, vm);
                try {
                    startNewVM(vm.getId());
                } catch(Exception ex){
                    destroyVM(vm.getId());
                    throw ex;
                }

                if (assignLBruleToNewVm(vm, asGroup)) {
                    _autoScaleVmGroupVmMapDao.persist(new AutoScaleVmGroupVmMapVO(asGroup.getId(), vm.getId()));
                    updateLastQuietTime(groupId, "scaleup");
                    createScaleUpSuccessEvent(asGroup.getUuid(), "VM " + vm.getDisplayName() + " was created as result of a scale up action. Auto Scale Id: " + asGroup.getUuid());
                } else {
                    createScaleUpFailedEvent(asGroup.getUuid(), "VM could not be assigned to LB for Auto Scale Id: " + asGroup.getUuid());
                    s_logger.error("Can not assign LB rule for this new VM");
                    break;
                }
            }
        } catch(ServerApiException ex) {
            String message = "It was not possible do start VM for Auto Scale Id: " + asGroup.getUuid() + " Reason: " + ex.getDescription();
            createScaleUpFailedEvent(asGroup.getUuid(), message);
            s_logger.error(message, ex);
            throw ex;
        } catch(Exception ex){
            String message = "It was not possible do start VM for Auto Scale Id: " + asGroup.getUuid() + " Reason: unexpected error during execution";
            createScaleUpFailedEvent(asGroup.getUuid(), message);
            s_logger.error(message, ex);
            throw ex;
        }
    }

    private void addVirtualMachineTags(AutoScaleVmGroupVO asGroup, UserVm vm) {
        List<? extends ResourceTag> resourceTags = _resourceTagDao.listBy(asGroup.getId(), ResourceTag.ResourceObjectType.AutoScaleVmGroup);

        for (ResourceTag resourceTag : resourceTags) {
            ResourceTagVO tag = new ResourceTagVO(resourceTag.getKey(), resourceTag.getValue(), resourceTag.getAccountId(),
                    resourceTag.getDomainId(), vm.getId(), ResourceTag.ResourceObjectType.UserVm, resourceTag.getCustomer(),
                    vm.getUuid());
            _resourceTagDao.persist(tag);
        }
    }

    @Override
    public void doScaleDown(final long groupId, Integer numVm) {
        final AutoScaleVmGroupVO asGroup = _autoScaleVmGroupDao.findById(groupId);
        for(int i = 0; i < numVm; i++) {
            try{
                if (asGroup == null) {
                    s_logger.error("Can not find the groupid " + groupId + " for scaling up");
                    return;
                }
                if (!checkConditionDown(asGroup)) {
                    return;
                }
                final long vmId = removeLBrule(asGroup);
                if (vmId != -1) {
                    _autoScaleVmGroupVmMapDao.remove(groupId, vmId);

                    updateLastQuietTime(groupId, "scaledown");

                    Integer destroyVmGracePeriod = getDestroyVmGracePeriod(asGroup);
                    if (destroyVmGracePeriod >= 0) {
                        _executor.schedule(new Runnable() {
                            @Override
                            public void run() {
                                if(destroyVM(vmId)){
                                    createScaleDownSuccessEvent(asGroup.getUuid(), "VM was destroyed as result of a scale down action. Auto Scale Id: " + groupId);
                                }else{
                                    createScaleDownFailedEvent(asGroup.getUuid(), "Scale down action failed for Auto Scale group id: " + groupId);
                                }
                            }
                        }, destroyVmGracePeriod, TimeUnit.SECONDS);
                    }
                } else {
                    String message = "Failed to remove LB rule for the VM being destroyed.";
                    createScaleDownFailedEvent(asGroup.getUuid(), "Scale down action failed for Auto Scale group id: " + groupId + ". Reason: " + message);
                    s_logger.error(message);
                }
            }catch(Exception ex){
                String message = "Scale down action failed for Auto Scale group id: " + groupId;
                createScaleDownFailedEvent(asGroup.getUuid(), message);
                s_logger.error(message, ex);
                throw ex;
            }
        }
    }

    private Integer getDestroyVmGracePeriod(AutoScaleVmGroupVO asGroup) {
        AutoScaleVmProfileVO asProfile = _autoScaleVmProfileDao.findById(asGroup.getProfileId());
        return asProfile.getDestroyVmGraceperiod();
    }

    protected void updateLastQuietTime(long groupId, String action) {
        for (AutoScaleVmGroupPolicyMapVO groupPolicyMap : _autoScaleVmGroupPolicyMapDao.listByVmGroupId(groupId)) {
            AutoScalePolicyVO policy = _autoScalePolicyDao.findById(groupPolicyMap.getPolicyId());
            if (policy.getAction().equals(action)) {
                policy.setLastQuiteTime(new Date());
                _autoScalePolicyDao.persist(policy);
                break;
            }
        }
    }

    private boolean destroyVM(long vmId) {
        try {
            _userVmManager.destroyVm(vmId, true);
            return true;
        } catch (ResourceUnavailableException | ConcurrentOperationException e) {
            s_logger.error("It was not possible to destroy VM id: " + vmId, e);
        }
        return false;
    }

    public void createScaleUpSuccessEvent(String asGroupId, String description){
        createEvent(asGroupId, EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEUP, description);
    }

    public void createScaleUpFailedEvent(String asGroupId, String description){
        createEvent(asGroupId, EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEUP_FAILED, description);
    }

    public void createScaleDownSuccessEvent(String asGroupId, String description){
        createEvent(asGroupId, EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEDOWN, description);
    }

    public void createScaleDownFailedEvent(String asGroupId, String description){
        createEvent(asGroupId, EventTypes.EVENT_AUTOSCALEVMGROUP_SCALEDOWN_FAILED, description);
    }

    protected void createEvent(String asGroupId, String eventType, String description) {
        CallContext callContext = CallContext.current();
        AutoScaleVmGroupVO asGroup = _autoScaleVmGroupDao.findByUuid(asGroupId);
        AutoScaleVmProfileVO profile = _autoScaleVmProfileDao.findById(asGroup.getProfileId());
        User user = _userDao.getUser(profile.getAutoScaleUserId());
        callContext.getContextParameters().put(AutoScaleVmGroup.class.getName(), asGroupId);
        ActionEventUtils.onActionEvent(user.getId(), user.getAccountId(), asGroup.getDomainId(), eventType, description);
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] { AutoScaledVmPrefix };
    }

    @Override
    public String getConfigComponentName() {
        return AutoScaleManagerImpl.class.getSimpleName();
    }
}
