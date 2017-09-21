package com.globo.globonetwork.cloudstack.manager;

import com.cloud.network.dao.LoadBalancerVMMapDao;
import com.cloud.network.dao.LoadBalancerVMMapVO;
import com.cloud.network.lb.LoadBalancingRulesService;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globonetwork.cloudstack.api.loadbalancer.LinkGloboLoadBalancerCmd;
import com.globo.globonetwork.cloudstack.api.loadbalancer.ListGloboLinkableLoadBalancersCmd;
import org.apache.cloudstack.globoconfig.GloboResourceConfiguration;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationDao;
import org.apache.cloudstack.globoconfig.GloboResourceConfigurationVO;
import org.apache.cloudstack.globoconfig.GloboResourceKey;
import org.apache.cloudstack.globoconfig.GloboResourceType;
import org.springframework.stereotype.Component;

import javax.ejb.Local;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Component
@Local({GloboLoadBalancerService.class, PluggableService.class})
public class GloboLoadBalancerManager implements GloboLoadBalancerService, PluggableService {

    @Inject
    protected GloboNetworkService globoNetworkSvc;

    @Inject
    protected LoadBalancingRulesService lbService;

    @Inject
    protected GloboResourceConfigurationDao resourceConfigDao;

    @Inject
    protected LoadBalancerVMMapDao _lb2VmMapDao;

    @Override
    public LoadBalancer linkLoadBalancer(Long sourceLbid, Long targetLbid) {
        LoadBalancer targetLb = lbService.findById(targetLbid);


        LoadBalancer lb = checkIfLBAlreadyIsLinked(sourceLbid);
        checkIfSourceLBHasVms(lb);

        GloboResourceConfiguration config = registerLink(lb, targetLb);

        return lb;
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
        return cmdList;
    }
}
