package com.globo.globonetwork.cloudstack.manager;

import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.LoadBalancerDao;
import com.cloud.network.dao.LoadBalancerPortMapDao;
import com.cloud.network.dao.LoadBalancerPortMapVO;
import com.cloud.network.dao.LoadBalancerVMMapDao;
import com.cloud.network.dao.LoadBalancerVMMapVO;
import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.network.lb.LoadBalancingRulesService;
import com.cloud.network.rules.FirewallRuleVO;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.region.ha.GlobalLoadBalancingRulesService;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.db.DB;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.GloboNetworkIpDetailVO;
import com.globo.globonetwork.cloudstack.api.loadbalancer.LinkGloboLoadBalancerCmd;
import com.globo.globonetwork.cloudstack.api.loadbalancer.ListGloboLinkableLoadBalancersCmd;
import com.globo.globonetwork.cloudstack.api.loadbalancer.UnlinkGloboLoadBalancerCmd;
import com.globo.globonetwork.cloudstack.commands.LinkParentLbPoolsInChildLbCommand;
import com.globo.globonetwork.cloudstack.commands.UnlinkPoolsFromLbCommand;
import org.apache.cloudstack.api.command.user.loadbalancer.AssignToLoadBalancerRuleCmd;
import org.apache.cloudstack.globoconfig.GloboResourceConfiguration;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationDao;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GloboLoadBalancerManager implements GloboLoadBalancerService, PluggableService {

    private static final Logger s_logger = Logger.getLogger(GloboLoadBalancerManager.class);

    @Inject
    protected GloboNetworkService globoNetworkSvc;

    @Inject
    protected LoadBalancingRulesService lbService;

    @Inject
    LoadBalancingRulesManager _lbMgr;

    @Inject
    protected GloboResourceConfigurationDao resourceConfigDao;

    @Inject
    protected LoadBalancerVMMapDao _lb2VmMapDao;

    @Inject
    public GlobalLoadBalancingRulesService _gslbService;


    @Inject
    LoadBalancerPortMapDao _lbPortMapDao;
    @Inject
    NetworkDao networkDao;

    @Inject
    LoadBalancerDao lbDao;

    @Inject
    FirewallRulesDao frDao;

    @Inject
    GloboResourceConfigurationDao configDao;

    @Override
    @DB
    public LoadBalancer linkLoadBalancer(Long childLbid, Long parentLbid) {
        LoadBalancer childLb = checkIfLBAlreadyIsLinked(childLbid);
        checkIfSourceLBHasVms(childLb);

        LoadBalancer parentLb = checkIfLBAlreadyIsLinked(parentLbid);

        if (isLbDsr(childLb)) {
            throw new CloudRuntimeException("Load balancer " + childLb.getName() + " is dsr! Can not link lb dsr!");
        }
        if (isLbDsr(parentLb)) {
            throw new CloudRuntimeException("Load balancer " + parentLb.getName() + " is dsr! Can not be linked!");
        }

        LoadBalancingRule lbRule = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) childLb);
        LoadBalancingRule parentRule = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) parentLb);

        removeUnnecessaryNetworks(lbRule, parentRule);
        lbRule = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) childLb);
        copyNetworksFromTarget(lbRule, parentRule);


        LinkParentLbPoolsInChildLbCommand command = new LinkParentLbPoolsInChildLbCommand();

        Long sourceVipId = getVipIdApplyLbIfNeed(childLb);
        command.setChildLb(childLb.getUuid(), childLb.getName(), sourceVipId);

        Long targetVipId = getVipIdApplyLbIfNeed(parentLb);
        command.setParentLb(parentLb.getUuid(), parentLb.getName(), targetVipId);

        NetworkVO network = networkDao.findById(lbRule.getNetworkId());
        globoNetworkSvc.callCommand(command, network.getDataCenterId());

        registerLink(childLb, parentLb);


        copyPorts(parentRule, (LoadBalancerVO)childLb);

        return childLb;
    }

    private boolean isLbDsr(LoadBalancer lb) {
        List<GloboResourceConfigurationVO> configs = configDao.getConfiguration(GloboResourceType.LOAD_BALANCER, lb.getUuid(), GloboResourceKey.dsr);

        if (configs.size() > 0) {
            return configs.get(0).getBooleanValue();
        }
        return false;
    }

    private void copyPorts(LoadBalancingRule fromLb, LoadBalancerVO toLb) {
        List<LoadBalancerPortMapVO> loadBalancerPortMaps = _lbPortMapDao.listByLoadBalancerId(toLb.getId());
        for (LoadBalancerPortMapVO lBPortMap : loadBalancerPortMaps) {
            _lbPortMapDao.remove(lBPortMap.getId());
        }

        loadBalancerPortMaps = _lbPortMapDao.listByLoadBalancerId(fromLb.getId());
        for (LoadBalancerPortMapVO lBPortMap : loadBalancerPortMaps) {

            _lbPortMapDao.persist(new LoadBalancerPortMapVO(toLb.getId(), lBPortMap.getPublicPort(), lBPortMap.getPrivatePort()));
        }

        toLb.setDefaultPortEnd(fromLb.getDefaultPortEnd());
        toLb.setDefaultPortStart(fromLb.getDefaultPortStart());

        lbDao.update(toLb.getId(), toLb);

        FirewallRuleVO firewall = frDao.findById(toLb.getId());
        FirewallRuleVO fromFirewall = frDao.findById(fromLb.getId());
        firewall.setSourcePortEnd(fromFirewall.getSourcePortEnd());
        firewall.setSourcePortStart(fromFirewall.getSourcePortStart());

        frDao.update(firewall.getId(), firewall);

    }

    public LoadBalancer copyVmsAndNetworks(Long fromLbId, Long toLbid) {
        LoadBalancer lb = checkIfLBAlreadyIsLinked(toLbid);
        checkIfSourceLBHasVms(lb);

        LoadBalancer fromLb = checkIfLBAlreadyIsLinked(fromLbId);

        LoadBalancingRule lbRule = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) lb);
        LoadBalancingRule targetRule = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) fromLb);

        removeUnnecessaryNetworks(lbRule, targetRule);
        lbRule = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) lb);
        copyNetworksFromTarget(lbRule, targetRule);

        lbRule = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) lb);
        copyVmsFromTarget(lbRule, targetRule);

        return lb;
    }

    protected void copyVmsFromTarget(LoadBalancingRule lbRule, LoadBalancingRule targetLb) {

        List<Long> vmIds = new ArrayList<Long>();

        for (LoadBalancingRule.LbDestination lbDestination : targetLb.getDestinations()) {
            vmIds.add(lbDestination.getInstanceId());

        }

        AssignToLoadBalancerRuleCmd cmd = new AssignToLoadBalancerRuleCmd();
        cmd.setVirtualMachineIds(vmIds);
        Map<Long, List<String>> vmIdIpListMap = cmd.getVmIdIpListMap();

        lbService.assignToLoadBalancer(lbRule.getId(), vmIds, vmIdIpListMap);
    }

    private void removeUnnecessaryNetworks(LoadBalancingRule lbRule, LoadBalancingRule targetRule) {

        List<Long> lbRuleNetworks = lbRule.getAdditionalNetworks(); //ignore main network, it can not be removed
        List<Long> targetNetworks = targetRule.getAllNetworks();

        lbRuleNetworks.removeAll(targetNetworks);

        if (!lbRuleNetworks.isEmpty()) {
            lbService.removeNetworksFromLoadBalancer(lbRule.getId(), lbRuleNetworks);
        }

    }

    private void copyNetworksFromTarget(LoadBalancingRule lbRule, LoadBalancingRule targetRule) {
        List<Long> lbRuleNetworks = getNetworksToAddIntoSource(lbRule, targetRule);

        if (!lbRuleNetworks.isEmpty()){
            lbService.assignNetworksToLoadBalancer(lbRule.getId(), lbRuleNetworks);
        }
    }

    protected List<Long> getNetworksToAddIntoSource(LoadBalancingRule lbRule, LoadBalancingRule targetRule) {
        List<Long> lbRuleNetworks = lbRule.getAllNetworks();
        List<Long> targetNetworks = targetRule.getAllNetworks();

        targetNetworks.removeAll(lbRuleNetworks);
        return targetNetworks;
    }


    @Override
    public List<GloboResourceConfigurationVO> findLoadBalancerChilds(LoadBalancerVO lb) {
        return configDao.getConfigsByValue(GloboResourceType.LOAD_BALANCER, GloboResourceKey.linkedLoadBalancer, lb.getUuid());
    }

    @Override
    @DB
    public LoadBalancer unlinkLoadBalancer(Long lbid) {
        LoadBalancer lb = lbService.findById(lbid);

        Long vipId = getVipIdApplyLbIfNeed(lb);
        String region = GloboNetworkManager.GloboNetworkRegion.value();
        UnlinkPoolsFromLbCommand command = new UnlinkPoolsFromLbCommand(lbid, vipId, lb.getName(), region);

        NetworkVO byId = networkDao.findById(lb.getNetworkId());
        globoNetworkSvc.callCommand(command, byId.getDataCenterId());

        GloboResourceConfiguration linkedConfig = globoNetworkSvc.getGloboResourceConfiguration(lb.getUuid(), GloboResourceType.LOAD_BALANCER, GloboResourceKey.linkedLoadBalancer);
        resourceConfigDao.remove(linkedConfig.getId().toString());

        return lb;
    }

    private void checkIfLinkWillCreateARecursiveExecution(LoadBalancer lb, LoadBalancer targetLb) {
        GloboResourceConfiguration linkedConfig = globoNetworkSvc.getGloboResourceConfiguration(lb.getUuid(), GloboResourceType.LOAD_BALANCER, GloboResourceKey.linkedLoadBalancer);

    }


    private Long getVipIdApplyLbIfNeed(LoadBalancer lb) {
        GloboNetworkIpDetailVO vipIp = globoNetworkSvc.getNetworkApiVipIp(lb);
        Long vipId = vipIp.getGloboNetworkVipId();

        if (vipId == null) {
            try {
                s_logger.debug("LB " + lb.getName() + " is not applied, trying to apply in networkAPI...");
                NetworkVO network = networkDao.findById(lb.getNetworkId());
                LoadBalancingRule loadBalancerRuleToApply = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) lb);
                globoNetworkSvc.applyLbRuleInGloboNetwork(network, loadBalancerRuleToApply);

                vipIp = globoNetworkSvc.getNetworkApiVipIp(lb);
                vipId = vipIp.getGloboNetworkVipId();

                return vipId;
            } catch (Exception e) {
                throw new CloudRuntimeException("Load balancer '" + lb.getName() + "'  is not applied, unexpected error while trying to apply this load balancer in NetworkAPI. Please, contact your system administrator.", e);
            }
        }
        return vipId;
    }

    protected GloboResourceConfiguration registerLink(LoadBalancer lb, LoadBalancer targetLb) {
        GloboResourceConfigurationVO config = new GloboResourceConfigurationVO(GloboResourceType.LOAD_BALANCER, lb.getUuid(), GloboResourceKey.linkedLoadBalancer, targetLb.getUuid());

        config = resourceConfigDao.persist(config);

        return config;
    }

    protected void checkIfSourceLBHasVms(LoadBalancer lb) {
        List<LoadBalancerVMMapVO> loadBalancerVMMapVOS = _lb2VmMapDao.listByLoadBalancerId(lb.getId());

        if (loadBalancerVMMapVOS.size() > 0) {
            throw new CloudRuntimeException("Can not link load balancers. Please,remove all virtual machines(" + loadBalancerVMMapVOS.size() + ") from load balancer " + lb.getName() + " before link to another one.");
        }
    }

    /*
        throws an exception if lb already has link with another load balancer, else return source lb instance
     */
    protected LoadBalancer checkIfLBAlreadyIsLinked(Long sourceLbId) {
        LoadBalancer lb = lbService.findById(sourceLbId);

        GloboResourceConfiguration linkedConfig = globoNetworkSvc.getGloboResourceConfiguration(lb.getUuid(), GloboResourceType.LOAD_BALANCER, GloboResourceKey.linkedLoadBalancer);
        if (linkedConfig != null) {
            LoadBalancer target = lbService.findByUuid(linkedConfig.getValue());
            throw new CloudRuntimeException("Load balancer '" + lb.getName() + "'(" + lb.getUuid() + ") already has link with load balancer '"+ target.getName() + "'(" + target.getUuid() +").");
        }

        return lb;
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(ListGloboLinkableLoadBalancersCmd.class);
        cmdList.add(LinkGloboLoadBalancerCmd.class);
        cmdList.add(UnlinkGloboLoadBalancerCmd.class);
        return cmdList;
    }
}
