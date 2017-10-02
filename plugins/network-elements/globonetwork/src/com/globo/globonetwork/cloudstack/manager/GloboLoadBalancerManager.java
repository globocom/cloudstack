package com.globo.globonetwork.cloudstack.manager;

import com.cloud.network.dao.LoadBalancerVMMapDao;
import com.cloud.network.dao.LoadBalancerVMMapVO;
import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.network.lb.LoadBalancingRulesService;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.region.ha.GlobalLoadBalancingRulesService;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.GloboNetworkIpDetailVO;
import com.globo.globonetwork.cloudstack.api.loadbalancer.LinkGloboLoadBalancerCmd;
import com.globo.globonetwork.cloudstack.api.loadbalancer.ListGloboLinkableLoadBalancersCmd;
import com.globo.globonetwork.cloudstack.api.loadbalancer.UnlinkGloboLoadBalancerCmd;
import org.apache.cloudstack.api.command.user.loadbalancer.AssignToLoadBalancerRuleCmd;
import org.apache.cloudstack.globoconfig.GloboResourceConfiguration;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationDao;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.ejb.Local;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Local({GloboLoadBalancerService.class, PluggableService.class})
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
    NetworkDao networkDao;

    @Override
    public LoadBalancer linkLoadBalancer(Long sourceLbid, Long targetLbid) {
        LoadBalancer lb = checkIfLBAlreadyIsLinked(sourceLbid);
        checkIfSourceLBHasVms(lb);

        LoadBalancer targetLb = checkIfLBAlreadyIsLinked(targetLbid);

        LoadBalancingRule lbRule = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) lb);
        LoadBalancingRule targetRule = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) targetLb);

        removeUnnecessaryNetworks(lbRule, targetRule);
        lbRule = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) lb);
        copyNetworksFromTarget(lbRule, targetRule);

        lbRule = _lbMgr.getLoadBalancerRuleToApply((LoadBalancerVO) lb);
        copyVmsFromTarget(lbRule, targetRule);

        registerLink(lb, targetLb);

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
    public LoadBalancer unlinkLoadBalancer(Long lbid) {
        LoadBalancer lb = lbService.findById(lbid);

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
            throw new CloudRuntimeException("Please, remove all virtual machines(" + loadBalancerVMMapVOS.size() + ") from load balancer " + lb.getName() + " before link to another one.");
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
