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
package com.cloud.server;

import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.cloud.server.as.AutoScaleMonitor;
import com.cloud.server.as.AutoScaleCounterCollector;
import org.apache.cloudstack.utils.usage.UsageUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPoint;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPointSelector;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.utils.graphite.GraphiteClient;
import org.apache.cloudstack.utils.graphite.GraphiteException;

import org.apache.commons.lang.StringUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.GetStorageStatsCommand;
import com.cloud.agent.api.HostStatsEntry;
import com.cloud.agent.api.VgpuTypesInfo;
import com.cloud.agent.api.VmDiskStatsEntry;
import com.cloud.agent.api.VmNetworkStatsEntry;
import com.cloud.agent.api.VmStatsEntry;
import com.cloud.agent.api.VolumeStatsEntry;
import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.dc.Vlan.VlanType;
import com.cloud.dc.VlanVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.VlanDao;
import com.cloud.exception.StorageUnavailableException;
import com.cloud.gpu.dao.HostGpuGroupsDao;
import com.cloud.host.Host;
import com.cloud.host.HostStats;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;

import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceState;

import com.cloud.network.as.AutoScaleManager;
import com.cloud.network.as.dao.AutoScalePolicyConditionMapDao;
import com.cloud.network.as.dao.AutoScalePolicyDao;
import com.cloud.network.as.dao.AutoScaleVmGroupDao;
import com.cloud.network.as.dao.AutoScaleVmGroupPolicyMapDao;
import com.cloud.network.as.dao.AutoScaleVmGroupVmMapDao;
import com.cloud.network.as.dao.AutoScaleVmProfileDao;
import com.cloud.network.as.dao.ConditionDao;
import com.cloud.network.as.dao.CounterDao;
import com.cloud.org.Cluster;

import com.cloud.storage.ImageStoreDetailsUtil;
import com.cloud.storage.ScopeType;
import com.cloud.storage.Storage.ImageFormat;

import com.cloud.storage.StorageManager;
import com.cloud.storage.StorageStats;
import com.cloud.storage.VolumeStats;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.UserStatisticsVO;
import com.cloud.user.VmDiskStatisticsVO;
import com.cloud.user.dao.UserStatisticsDao;
import com.cloud.user.dao.VmDiskStatisticsDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.component.ComponentMethodInterceptable;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.net.MacAddress;
import com.cloud.vm.NicVO;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VmStats;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.UserVmDao;

/**
 * Provides real time stats for various agent resources up to x seconds
 *
 */
@Component
public class StatsCollector extends ManagerBase implements ComponentMethodInterceptable, Configurable {

    public static enum ExternalStatsProtocol {
        NONE("none"), GRAPHITE("graphite");
        String _type;

        ExternalStatsProtocol(String type) {
            _type = type;
        }

        @Override
        public String toString() {
            return _type;
        }
    }

    public static final Logger s_logger = Logger.getLogger(StatsCollector.class.getName());

    static final ConfigKey<Integer> vmDiskStatsInterval = new ConfigKey<Integer>("Advanced", Integer.class, "vm.disk.stats.interval", "0",
            "Interval (in seconds) to report vm disk statistics. Vm disk statistics will be disabled if this is set to 0 or less than 0.", false);
    static final ConfigKey<Integer> vmDiskStatsIntervalMin = new ConfigKey<Integer>("Advanced", Integer.class, "vm.disk.stats.interval.min", "300",
            "Minimal interval (in seconds) to report vm disk statistics. If vm.disk.stats.interval is smaller than this, use this to report vm disk statistics.", false);
    static final ConfigKey<Integer> vmNetworkStatsInterval = new ConfigKey<Integer>("Advanced", Integer.class, "vm.network.stats.interval", "0",
            "Interval (in seconds) to report vm network statistics (for Shared networks). Vm network statistics will be disabled if this is set to 0 or less than 0.", false);
    static final ConfigKey<Integer> vmNetworkStatsIntervalMin = new ConfigKey<Integer>("Advanced", Integer.class, "vm.network.stats.interval.min", "300",
            "Minimal Interval (in seconds) to report vm network statistics (for Shared networks). If vm.network.stats.interval is smaller than this, use this to report vm network statistics.", false);
    static final ConfigKey<Integer> StatsTimeout = new ConfigKey<Integer>("Advanced", Integer.class, "stats.timeout", "60000",
            "The timeout for stats call in milli seconds.", true, ConfigKey.Scope.Cluster);

    private static StatsCollector s_instance = null;

    private ScheduledExecutorService _executor = null;
    @Inject
    private AgentManager _agentMgr;
    @Inject
    private UserVmManager _userVmMgr;
    @Inject
    private HostDao _hostDao;
    @Inject
    private ClusterDao _clusterDao;
    @Inject
    private UserVmDao _userVmDao;
    @Inject
    private VolumeDao _volsDao;
    @Inject
    private PrimaryDataStoreDao _storagePoolDao;
    @Inject
    private StorageManager _storageManager;
    @Inject
    private DataStoreManager _dataStoreMgr;
    @Inject
    private ResourceManager _resourceMgr;
    @Inject
    private ConfigurationDao _configDao;
    @Inject
    private EndPointSelector _epSelector;
    @Inject
    private VmDiskStatisticsDao _vmDiskStatsDao;
    @Inject
    private ManagementServerHostDao _msHostDao;
    @Inject
    private UserStatisticsDao _userStatsDao;
    @Inject
    private NicDao _nicDao;
    @Inject
    private VlanDao _vlanDao;
    @Inject
    private AutoScaleVmGroupDao _asGroupDao;
    @Inject
    private AutoScaleVmGroupVmMapDao _asGroupVmDao;
    @Inject
    private AutoScaleManager _asManager;
    @Inject
    private VMInstanceDao _vmInstance;
    @Inject
    private ServiceOfferingDao _serviceOfferingDao;
    @Inject
    private AutoScaleVmGroupPolicyMapDao _asGroupPolicyDao;
    @Inject
    private AutoScalePolicyDao _asPolicyDao;
    @Inject
    private AutoScalePolicyConditionMapDao _asConditionMapDao;
    @Inject
    private ConditionDao _asConditionDao;
    @Inject
    private CounterDao _asCounterDao;
    @Inject
    private AutoScaleVmProfileDao _asProfileDao;

    @Inject
    private AutoScaleMonitor _autoScaleMonitor;
    @Inject
    private AutoScaleCounterCollector _autoScaleCounterCollector;
    @Inject
    private HostGpuGroupsDao _hostGpuGroupsDao;
    @Inject
    private ImageStoreDetailsUtil imageStoreDetailsUtil;


    private ConcurrentHashMap<Long, HostStats> _hostStats = new ConcurrentHashMap<Long, HostStats>();
    private final ConcurrentHashMap<Long, VmStats> _VmStats = new ConcurrentHashMap<Long, VmStats>();
    private final Map<String, VolumeStats> _volumeStats = new ConcurrentHashMap<String, VolumeStats>();
    private ConcurrentHashMap<Long, StorageStats> _storageStats = new ConcurrentHashMap<Long, StorageStats>();
    private ConcurrentHashMap<Long, StorageStats> _storagePoolStats = new ConcurrentHashMap<Long, StorageStats>();

    long hostStatsInterval = -1L;
    long hostAndVmStatsInterval = -1L;
    long storageStatsInterval = -1L;
    long volumeStatsInterval = -1L;
    long autoScaleStatsInterval = -1L;

    long autoScaleCounterCollectorInterval = -1L;

    List<Long> hostIds = null;
    private double _imageStoreCapacityThreshold = 0.90;

    String externalStatsPrefix = "";
    String externalStatsHost = null;
    int externalStatsPort = -1;
    boolean externalStatsEnabled = false;
    ExternalStatsProtocol externalStatsType = ExternalStatsProtocol.NONE;

    private ScheduledExecutorService _diskStatsUpdateExecutor;
    private int _usageAggregationRange = 1440;
    private String _usageTimeZone = "GMT";
    private final long mgmtSrvrId = MacAddress.getMacAddress().toLong();
    private static final int ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_COOPERATION = 5;    // 5 seconds
    private boolean _dailyOrHourly = false;

    //private final GlobalLock m_capacityCheckLock = GlobalLock.getInternLock("capacity.check");

    public static StatsCollector getInstance() {
        return s_instance;
    }

    public static StatsCollector getInstance(Map<String, String> configs) {
        s_instance.init(configs);
        return s_instance;
    }

    public StatsCollector() {
        s_instance = this;
    }

    @Override
    public boolean start() {
        init(_configDao.getConfiguration());
        return true;
    }

    private void init(Map<String, String> configs) {
        _executor = Executors.newScheduledThreadPool(6, new NamedThreadFactory("StatsCollector"));

        hostStatsInterval = NumbersUtil.parseLong(configs.get("host.stats.interval"), 60000L);
        hostAndVmStatsInterval = NumbersUtil.parseLong(configs.get("vm.stats.interval"), 60000L);
        storageStatsInterval = NumbersUtil.parseLong(configs.get("storage.stats.interval"), 60000L);
        volumeStatsInterval = NumbersUtil.parseLong(configs.get("volume.stats.interval"), 600000L);
        autoScaleStatsInterval = NumbersUtil.parseLong(configs.get("autoscale.stats.interval"), 60000L);
        autoScaleCounterCollectorInterval = NumbersUtil.parseLong(configs.get("autoscale.reading.interval"), 10000L);


        /* URI to send statistics to. Currently only Graphite is supported */
        String externalStatsUri = configs.get("stats.output.uri");
        if (externalStatsUri != null && !externalStatsUri.equals("")) {
            try {
                URI uri = new URI(externalStatsUri);
                String scheme = uri.getScheme();

                try {
                    externalStatsType = ExternalStatsProtocol.valueOf(scheme.toUpperCase());
                } catch (IllegalArgumentException e) {
                    s_logger.info(scheme + " is not a valid protocol for external statistics. No statistics will be send.");
                }

                if (!StringUtils.isEmpty(uri.getHost())) {
                    externalStatsHost = uri.getHost();
                }

                externalStatsPort = uri.getPort();

                if (!StringUtils.isEmpty(uri.getPath())) {
                    externalStatsPrefix = uri.getPath().substring(1);
                }

                /* Append a dot (.) to the prefix if it is set */
                if (!StringUtils.isEmpty(externalStatsPrefix)) {
                    externalStatsPrefix += ".";
                } else {
                    externalStatsPrefix = "";
                }

                externalStatsEnabled = true;
            } catch (URISyntaxException e) {
                s_logger.debug("Failed to parse external statistics URI: " + e.getMessage());
            }
        }

        if (hostStatsInterval > 0) {
            _executor.scheduleWithFixedDelay(new HostCollector(), 15000L, hostStatsInterval, TimeUnit.MILLISECONDS);
        }

        if (hostAndVmStatsInterval > 0) {
            _executor.scheduleWithFixedDelay(new VmStatsCollector(), 15000L, hostAndVmStatsInterval, TimeUnit.MILLISECONDS);
        }

        if (storageStatsInterval > 0) {
            _executor.scheduleWithFixedDelay(new StorageCollector(), 15000L, storageStatsInterval, TimeUnit.MILLISECONDS);
        }

        if (autoScaleStatsInterval > 0) {
            _executor.scheduleWithFixedDelay(_autoScaleMonitor, 15000L, autoScaleStatsInterval, TimeUnit.MILLISECONDS);
        }

        if (autoScaleCounterCollectorInterval > 0) {
            _executor.scheduleWithFixedDelay(_autoScaleCounterCollector, 15000L, autoScaleCounterCollectorInterval, TimeUnit.MILLISECONDS);
        }

        if (vmDiskStatsInterval.value() > 0) {
            if (vmDiskStatsInterval.value() < vmDiskStatsIntervalMin.value()) {
                s_logger.debug("vm.disk.stats.interval - " + vmDiskStatsInterval.value() + " is smaller than vm.disk.stats.interval.min - " + vmDiskStatsIntervalMin.value() + ", so use vm.disk.stats.interval.min");
                _executor.scheduleAtFixedRate(new VmDiskStatsTask(), vmDiskStatsIntervalMin.value(), vmDiskStatsIntervalMin.value(), TimeUnit.SECONDS);
            } else {
                _executor.scheduleAtFixedRate(new VmDiskStatsTask(), vmDiskStatsInterval.value(), vmDiskStatsInterval.value(), TimeUnit.SECONDS);
            }
        } else {
            s_logger.debug("vm.disk.stats.interval - " + vmDiskStatsInterval.value() + " is 0 or less than 0, so not scheduling the vm disk stats thread");
        }

        if (vmNetworkStatsInterval.value() > 0) {
            if (vmNetworkStatsInterval.value() < vmNetworkStatsIntervalMin.value()) {
                s_logger.debug("vm.network.stats.interval - " + vmNetworkStatsInterval.value() + " is smaller than vm.network.stats.interval.min - " + vmNetworkStatsIntervalMin.value() + ", so use vm.network.stats.interval.min");
                _executor.scheduleAtFixedRate(new VmNetworkStatsTask(), vmNetworkStatsIntervalMin.value(), vmNetworkStatsIntervalMin.value(), TimeUnit.SECONDS);
            } else {
                _executor.scheduleAtFixedRate(new VmNetworkStatsTask(), vmNetworkStatsInterval.value(), vmNetworkStatsInterval.value(), TimeUnit.SECONDS);
            }
        } else {
            s_logger.debug("vm.network.stats.interval - " + vmNetworkStatsInterval.value() + " is 0 or less than 0, so not scheduling the vm network stats thread");
        }

        if (volumeStatsInterval > 0) {
            _executor.scheduleAtFixedRate(new VolumeStatsTask(), 15000L, volumeStatsInterval, TimeUnit.MILLISECONDS);
        }

        //Schedule disk stats update task
        _diskStatsUpdateExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("DiskStatsUpdater"));

        String aggregationRange = configs.get("usage.stats.job.aggregation.range");
        _usageAggregationRange = NumbersUtil.parseInt(aggregationRange, 1440);
        _usageTimeZone = configs.get("usage.aggregation.timezone");
        if (_usageTimeZone == null) {
            _usageTimeZone = "GMT";
        }
        TimeZone usageTimezone = TimeZone.getTimeZone(_usageTimeZone);
        Calendar cal = Calendar.getInstance(usageTimezone);
        cal.setTime(new Date());
        long endDate = 0;
        int HOURLY_TIME = 60;
        final int DAILY_TIME = 60 * 24;
        if (_usageAggregationRange == DAILY_TIME) {
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.roll(Calendar.DAY_OF_YEAR, true);
            cal.add(Calendar.MILLISECOND, -1);
            endDate = cal.getTime().getTime();
            _dailyOrHourly = true;
        } else if (_usageAggregationRange == HOURLY_TIME) {
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.roll(Calendar.HOUR_OF_DAY, true);
            cal.add(Calendar.MILLISECOND, -1);
            endDate = cal.getTime().getTime();
            _dailyOrHourly = true;
        } else {
            endDate = cal.getTime().getTime();
            _dailyOrHourly = false;
        }
        if (_usageAggregationRange < UsageUtils.USAGE_AGGREGATION_RANGE_MIN) {
            s_logger.warn("Usage stats job aggregation range is to small, using the minimum value of " + UsageUtils.USAGE_AGGREGATION_RANGE_MIN);
            _usageAggregationRange = UsageUtils.USAGE_AGGREGATION_RANGE_MIN;
        }
        _diskStatsUpdateExecutor.scheduleAtFixedRate(new VmDiskStatsUpdaterTask(), (endDate - System.currentTimeMillis()), (_usageAggregationRange * 60 * 1000),
                TimeUnit.MILLISECONDS);

    }

    class HostCollector extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            try {
                s_logger.debug("HostStatsCollector is running...");

                SearchCriteria<HostVO> sc = _hostDao.createSearchCriteria();
                sc.addAnd("status", SearchCriteria.Op.EQ, Status.Up.toString());
                sc.addAnd("resourceState", SearchCriteria.Op.NIN, ResourceState.Maintenance, ResourceState.PrepareForMaintenance, ResourceState.ErrorInMaintenance);
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.Storage.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.ConsoleProxy.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.SecondaryStorage.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.LocalSecondaryStorage.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.TrafficMonitor.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.SecondaryStorageVM.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.ExternalFirewall.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.ExternalLoadBalancer.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.NetScalerControlCenter.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.L2Networking.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.BaremetalDhcp.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.BaremetalPxe.toString());
                ConcurrentHashMap<Long, HostStats> hostStats = new ConcurrentHashMap<Long, HostStats>();
                List<HostVO> hosts = _hostDao.search(sc, null);
                for (HostVO host : hosts) {
                    HostStatsEntry stats = (HostStatsEntry)_resourceMgr.getHostStatistics(host.getId());
                    if (stats != null) {
                        hostStats.put(host.getId(), stats);
                    } else {
                        s_logger.warn("Received invalid host stats for host: " + host.getId());
                    }
                }
                _hostStats = hostStats;
                // Get a subset of hosts with GPU support from the list of "hosts"
                List<HostVO> gpuEnabledHosts = new ArrayList<HostVO>();
                if (hostIds != null) {
                    for (HostVO host : hosts) {
                        if (hostIds.contains(host.getId())) {
                            gpuEnabledHosts.add(host);
                        }
                    }
                } else {
                    // Check for all the hosts managed by CloudStack.
                    gpuEnabledHosts = hosts;
                }
                for (HostVO host : gpuEnabledHosts) {
                    HashMap<String, HashMap<String, VgpuTypesInfo>> groupDetails = _resourceMgr.getGPUStatistics(host);
                    if (groupDetails != null) {
                        _resourceMgr.updateGPUDetails(host.getId(), groupDetails);
                    }
                }
                hostIds = _hostGpuGroupsDao.listHostIds();
            } catch (Throwable t) {
                s_logger.error("Error trying to retrieve host stats", t);
            }
        }
    }

    class VmStatsCollector extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            try {
                s_logger.trace("VmStatsCollector is running...");

                SearchCriteria<HostVO> sc = _hostDao.createSearchCriteria();
                sc.addAnd("status", SearchCriteria.Op.EQ, Status.Up.toString());
                sc.addAnd("resourceState", SearchCriteria.Op.NIN, ResourceState.Maintenance, ResourceState.PrepareForMaintenance, ResourceState.ErrorInMaintenance);
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.Storage.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.ConsoleProxy.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.SecondaryStorage.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.LocalSecondaryStorage.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.TrafficMonitor.toString());
                sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.SecondaryStorageVM.toString());
                List<HostVO> hosts = _hostDao.search(sc, null);

                /* HashMap for metrics to be send to Graphite */
                HashMap metrics = new HashMap<String, Integer>();

                for (HostVO host : hosts) {
                    List<UserVmVO> vms = _userVmDao.listRunningByHostId(host.getId());
                    List<Long> vmIds = new ArrayList<Long>();

                    for (UserVmVO vm : vms) {
                        vmIds.add(vm.getId());
                    }

                    try {
                        HashMap<Long, VmStatsEntry> vmStatsById = _userVmMgr.getVirtualMachineStatistics(host.getId(), host.getName(), vmIds);

                        if (vmStatsById != null) {
                            VmStatsEntry statsInMemory = null;

                            Set<Long> vmIdSet = vmStatsById.keySet();
                            for (Long vmId : vmIdSet) {
                                VmStatsEntry statsForCurrentIteration = vmStatsById.get(vmId);
                                statsInMemory = (VmStatsEntry)_VmStats.get(vmId);

                                if (statsInMemory == null) {
                                    //no stats exist for this vm, directly persist
                                    _VmStats.put(vmId, statsForCurrentIteration);
                                } else {
                                    //update each field
                                    statsInMemory.setCPUUtilization(statsForCurrentIteration.getCPUUtilization());
                                    statsInMemory.setNumCPUs(statsForCurrentIteration.getNumCPUs());
                                    statsInMemory.setNetworkReadKBs(statsInMemory.getNetworkReadKBs() + statsForCurrentIteration.getNetworkReadKBs());
                                    statsInMemory.setNetworkWriteKBs(statsInMemory.getNetworkWriteKBs() + statsForCurrentIteration.getNetworkWriteKBs());
                                    statsInMemory.setDiskWriteKBs(statsInMemory.getDiskWriteKBs() + statsForCurrentIteration.getDiskWriteKBs());
                                    statsInMemory.setDiskReadIOs(statsInMemory.getDiskReadIOs() + statsForCurrentIteration.getDiskReadIOs());
                                    statsInMemory.setDiskWriteIOs(statsInMemory.getDiskWriteIOs() + statsForCurrentIteration.getDiskWriteIOs());
                                    statsInMemory.setDiskReadKBs(statsInMemory.getDiskReadKBs() + statsForCurrentIteration.getDiskReadKBs());
                                    statsInMemory.setMemoryKBs(statsForCurrentIteration.getMemoryKBs());
                                    statsInMemory.setIntFreeMemoryKBs(statsForCurrentIteration.getIntFreeMemoryKBs());
                                    statsInMemory.setTargetMemoryKBs(statsForCurrentIteration.getTargetMemoryKBs());

                                    _VmStats.put(vmId, statsInMemory);
                                }

                                /**
                                 * Add statistics to HashMap only when they should be send to a external stats collector
                                 * Performance wise it seems best to only append to the HashMap when needed
                                 */
                                if (externalStatsEnabled) {
                                    VMInstanceVO vmVO = _vmInstance.findById(vmId);
                                    String vmName = vmVO.getUuid();

                                    metrics.put(externalStatsPrefix + "cloudstack.stats.instances." + vmName + ".cpu.num", statsForCurrentIteration.getNumCPUs());
                                    metrics.put(externalStatsPrefix + "cloudstack.stats.instances." + vmName + ".cpu.utilization", statsForCurrentIteration.getCPUUtilization());
                                    metrics.put(externalStatsPrefix + "cloudstack.stats.instances." + vmName + ".network.read_kbs", statsForCurrentIteration.getNetworkReadKBs());
                                    metrics.put(externalStatsPrefix + "cloudstack.stats.instances." + vmName + ".network.write_kbs", statsForCurrentIteration.getNetworkWriteKBs());
                                    metrics.put(externalStatsPrefix + "cloudstack.stats.instances." + vmName + ".disk.write_kbs", statsForCurrentIteration.getDiskWriteKBs());
                                    metrics.put(externalStatsPrefix + "cloudstack.stats.instances." + vmName + ".disk.read_kbs", statsForCurrentIteration.getDiskReadKBs());
                                    metrics.put(externalStatsPrefix + "cloudstack.stats.instances." + vmName + ".disk.write_iops", statsForCurrentIteration.getDiskWriteIOs());
                                    metrics.put(externalStatsPrefix + "cloudstack.stats.instances." + vmName + ".disk.read_iops", statsForCurrentIteration.getDiskReadIOs());
                                    metrics.put(externalStatsPrefix + "cloudstack.stats.instances." + vmName + ".memory.total_kbs", statsForCurrentIteration.getMemoryKBs());
                                    metrics.put(externalStatsPrefix + "cloudstack.stats.instances." + vmName + ".memory.internalfree_kbs", statsForCurrentIteration.getIntFreeMemoryKBs());
                                    metrics.put(externalStatsPrefix + "cloudstack.stats.instances." + vmName + ".memory.target_kbs", statsForCurrentIteration.getTargetMemoryKBs());

                                }

                            }

                            /**
                             * Send the metrics to a external stats collector
                             * We send it on a per-host basis to prevent that we flood the host
                             * Currently only Graphite is supported
                             */
                            if (!metrics.isEmpty()) {
                                if (externalStatsType != null && externalStatsType == ExternalStatsProtocol.GRAPHITE) {

                                    if (externalStatsPort == -1) {
                                        externalStatsPort = 2003;
                                    }

                                    s_logger.debug("Sending VmStats of host " + host.getId() + " to Graphite host " + externalStatsHost + ":" + externalStatsPort);

                                    try {
                                        GraphiteClient g = new GraphiteClient(externalStatsHost, externalStatsPort);
                                        g.sendMetrics(metrics);
                                    } catch (GraphiteException e) {
                                        s_logger.debug("Failed sending VmStats to Graphite host " + externalStatsHost + ":" + externalStatsPort + ": " + e.getMessage());
                                    }

                                    metrics.clear();
                                }
                            }
                        }

                    } catch (Exception e) {
                        s_logger.debug("Failed to get VM stats for host with ID: " + host.getId());
                        continue;
                    }
                }

            } catch (Throwable t) {
                s_logger.error("Error trying to retrieve VM stats", t);
            }
        }
    }

    public VmStats getVmStats(long id) {
        return _VmStats.get(id);
    }

    class VmDiskStatsUpdaterTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            GlobalLock scanLock = GlobalLock.getInternLock("vm.disk.stats");
            try {
                if (scanLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_COOPERATION)) {
                    //Check for ownership
                    //msHost in UP state with min id should run the job
                    ManagementServerHostVO msHost = _msHostDao.findOneInUpState(new Filter(ManagementServerHostVO.class, "id", true, 0L, 1L));
                    if (msHost == null || (msHost.getMsid() != mgmtSrvrId)) {
                        s_logger.debug("Skipping aggregate disk stats update");
                        scanLock.unlock();
                        return;
                    }
                    try {
                        Transaction.execute(new TransactionCallbackNoReturn() {
                            @Override
                            public void doInTransactionWithoutResult(TransactionStatus status) {
                                //get all stats with delta > 0
                                List<VmDiskStatisticsVO> updatedVmNetStats = _vmDiskStatsDao.listUpdatedStats();
                                for (VmDiskStatisticsVO stat : updatedVmNetStats) {
                                    if (_dailyOrHourly) {
                                        //update agg bytes
                                        stat.setAggBytesRead(stat.getCurrentBytesRead() + stat.getNetBytesRead());
                                        stat.setAggBytesWrite(stat.getCurrentBytesWrite() + stat.getNetBytesWrite());
                                        stat.setAggIORead(stat.getCurrentIORead() + stat.getNetIORead());
                                        stat.setAggIOWrite(stat.getCurrentIOWrite() + stat.getNetIOWrite());
                                        _vmDiskStatsDao.update(stat.getId(), stat);
                                    }
                                }
                                s_logger.debug("Successfully updated aggregate vm disk stats");
                            }
                        });
                    } catch (Exception e) {
                        s_logger.debug("Failed to update aggregate disk stats", e);
                    } finally {
                        scanLock.unlock();
                    }
                }
            } catch (Exception e) {
                s_logger.debug("Exception while trying to acquire disk stats lock", e);
            } finally {
                scanLock.releaseRef();
            }
        }
    }

    class VmDiskStatsTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            //Check for ownership
            //msHost in UP state with min id should run the job
            ManagementServerHostVO msHost = _msHostDao.findOneInUpState(new Filter(ManagementServerHostVO.class, "id", true, 0L, 1L));
            if(msHost == null || (msHost.getMsid() != mgmtSrvrId)){
                s_logger.debug("Skipping collect vm disk stats from hosts");
                return;
            }
            // collect the vm disk statistics(total) from hypervisor. added by weizhou, 2013.03.
            s_logger.trace("Running VM disk stats ...");
            try {
                Transaction.execute(new TransactionCallbackNoReturn() {
                    @Override
                    public void doInTransactionWithoutResult(TransactionStatus status) {
                        s_logger.debug("VmDiskStatsTask is running...");

                        SearchCriteria<HostVO> sc = _hostDao.createSearchCriteria();
                        sc.addAnd("status", SearchCriteria.Op.EQ, Status.Up.toString());
                        sc.addAnd("resourceState", SearchCriteria.Op.NIN, ResourceState.Maintenance, ResourceState.PrepareForMaintenance,
                                ResourceState.ErrorInMaintenance);
                        sc.addAnd("type", SearchCriteria.Op.EQ, Host.Type.Routing.toString());
                        sc.addAnd("hypervisorType", SearchCriteria.Op.EQ, HypervisorType.KVM); // support KVM only util 2013.06.25
                        List<HostVO> hosts = _hostDao.search(sc, null);

                        for (HostVO host : hosts) {
                            List<UserVmVO> vms = _userVmDao.listRunningByHostId(host.getId());
                            List<Long> vmIds = new ArrayList<Long>();

                            for (UserVmVO vm : vms) {
                                if (vm.getType() == VirtualMachine.Type.User) // user vm
                                    vmIds.add(vm.getId());
                            }

                            HashMap<Long, List<VmDiskStatsEntry>> vmDiskStatsById = _userVmMgr.getVmDiskStatistics(host.getId(), host.getName(), vmIds);
                            if (vmDiskStatsById == null)
                                continue;

                            Set<Long> vmIdSet = vmDiskStatsById.keySet();
                            for (Long vmId : vmIdSet) {
                                List<VmDiskStatsEntry> vmDiskStats = vmDiskStatsById.get(vmId);
                                if (vmDiskStats == null)
                                    continue;
                                UserVmVO userVm = _userVmDao.findById(vmId);
                                for (VmDiskStatsEntry vmDiskStat : vmDiskStats) {
                                    SearchCriteria<VolumeVO> sc_volume = _volsDao.createSearchCriteria();
                                    sc_volume.addAnd("path", SearchCriteria.Op.EQ, vmDiskStat.getPath());
                                    List<VolumeVO> volumes = _volsDao.search(sc_volume, null);
                                    if ((volumes == null) || (volumes.size() == 0))
                                        break;
                                    VolumeVO volume = volumes.get(0);
                                    VmDiskStatisticsVO previousVmDiskStats =
                                            _vmDiskStatsDao.findBy(userVm.getAccountId(), userVm.getDataCenterId(), vmId, volume.getId());
                                    VmDiskStatisticsVO vmDiskStat_lock = _vmDiskStatsDao.lock(userVm.getAccountId(), userVm.getDataCenterId(), vmId, volume.getId());

                                    if ((vmDiskStat.getBytesRead() == 0) && (vmDiskStat.getBytesWrite() == 0) && (vmDiskStat.getIORead() == 0) &&
                                            (vmDiskStat.getIOWrite() == 0)) {
                                        s_logger.debug("IO/bytes read and write are all 0. Not updating vm_disk_statistics");
                                        continue;
                                    }

                                    if (vmDiskStat_lock == null) {
                                        s_logger.warn("unable to find vm disk stats from host for account: " + userVm.getAccountId() + " with vmId: " + userVm.getId() +
                                                " and volumeId:" + volume.getId());
                                        continue;
                                    }

                                    if (previousVmDiskStats != null &&
                                            ((previousVmDiskStats.getCurrentBytesRead() != vmDiskStat_lock.getCurrentBytesRead()) ||
                                                    (previousVmDiskStats.getCurrentBytesWrite() != vmDiskStat_lock.getCurrentBytesWrite()) ||
                                                    (previousVmDiskStats.getCurrentIORead() != vmDiskStat_lock.getCurrentIORead()) || (previousVmDiskStats.getCurrentIOWrite() != vmDiskStat_lock.getCurrentIOWrite()))) {
                                        s_logger.debug("vm disk stats changed from the time GetVmDiskStatsCommand was sent. " + "Ignoring current answer. Host: " +
                                                host.getName() + " . VM: " + vmDiskStat.getVmName() + " Read(Bytes): " + vmDiskStat.getBytesRead() + " write(Bytes): " +
                                                vmDiskStat.getBytesWrite() + " Read(IO): " + vmDiskStat.getIORead() + " write(IO): " + vmDiskStat.getIOWrite());
                                        continue;
                                    }

                                    if (vmDiskStat_lock.getCurrentBytesRead() > vmDiskStat.getBytesRead()) {
                                        if (s_logger.isDebugEnabled()) {
                                            s_logger.debug("Read # of bytes that's less than the last one.  " +
                                                    "Assuming something went wrong and persisting it. Host: " + host.getName() + " . VM: " + vmDiskStat.getVmName() +
                                                    " Reported: " + vmDiskStat.getBytesRead() + " Stored: " + vmDiskStat_lock.getCurrentBytesRead());
                                        }
                                        vmDiskStat_lock.setNetBytesRead(vmDiskStat_lock.getNetBytesRead() + vmDiskStat_lock.getCurrentBytesRead());
                                    }
                                    vmDiskStat_lock.setCurrentBytesRead(vmDiskStat.getBytesRead());
                                    if (vmDiskStat_lock.getCurrentBytesWrite() > vmDiskStat.getBytesWrite()) {
                                        if (s_logger.isDebugEnabled()) {
                                            s_logger.debug("Write # of bytes that's less than the last one.  " +
                                                    "Assuming something went wrong and persisting it. Host: " + host.getName() + " . VM: " + vmDiskStat.getVmName() +
                                                    " Reported: " + vmDiskStat.getBytesWrite() + " Stored: " + vmDiskStat_lock.getCurrentBytesWrite());
                                        }
                                        vmDiskStat_lock.setNetBytesWrite(vmDiskStat_lock.getNetBytesWrite() + vmDiskStat_lock.getCurrentBytesWrite());
                                    }
                                    vmDiskStat_lock.setCurrentBytesWrite(vmDiskStat.getBytesWrite());
                                    if (vmDiskStat_lock.getCurrentIORead() > vmDiskStat.getIORead()) {
                                        if (s_logger.isDebugEnabled()) {
                                            s_logger.debug("Read # of IO that's less than the last one.  " + "Assuming something went wrong and persisting it. Host: " +
                                                    host.getName() + " . VM: " + vmDiskStat.getVmName() + " Reported: " + vmDiskStat.getIORead() + " Stored: " +
                                                    vmDiskStat_lock.getCurrentIORead());
                                        }
                                        vmDiskStat_lock.setNetIORead(vmDiskStat_lock.getNetIORead() + vmDiskStat_lock.getCurrentIORead());
                                    }
                                    vmDiskStat_lock.setCurrentIORead(vmDiskStat.getIORead());
                                    if (vmDiskStat_lock.getCurrentIOWrite() > vmDiskStat.getIOWrite()) {
                                        if (s_logger.isDebugEnabled()) {
                                            s_logger.debug("Write # of IO that's less than the last one.  " + "Assuming something went wrong and persisting it. Host: " +
                                                    host.getName() + " . VM: " + vmDiskStat.getVmName() + " Reported: " + vmDiskStat.getIOWrite() + " Stored: " +
                                                    vmDiskStat_lock.getCurrentIOWrite());
                                        }
                                        vmDiskStat_lock.setNetIOWrite(vmDiskStat_lock.getNetIOWrite() + vmDiskStat_lock.getCurrentIOWrite());
                                    }
                                    vmDiskStat_lock.setCurrentIOWrite(vmDiskStat.getIOWrite());

                                    if (!_dailyOrHourly) {
                                        //update agg bytes
                                        vmDiskStat_lock.setAggBytesWrite(vmDiskStat_lock.getNetBytesWrite() + vmDiskStat_lock.getCurrentBytesWrite());
                                        vmDiskStat_lock.setAggBytesRead(vmDiskStat_lock.getNetBytesRead() + vmDiskStat_lock.getCurrentBytesRead());
                                        vmDiskStat_lock.setAggIOWrite(vmDiskStat_lock.getNetIOWrite() + vmDiskStat_lock.getCurrentIOWrite());
                                        vmDiskStat_lock.setAggIORead(vmDiskStat_lock.getNetIORead() + vmDiskStat_lock.getCurrentIORead());
                                    }

                                    _vmDiskStatsDao.update(vmDiskStat_lock.getId(), vmDiskStat_lock);
                                }
                            }
                        }
                    }
                });
            } catch (Exception e) {
                s_logger.warn("Error while collecting vm disk stats from hosts", e);
            }
        }
    }

    class VmNetworkStatsTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            //Check for ownership
            //msHost in UP state with min id should run the job
            ManagementServerHostVO msHost = _msHostDao.findOneInUpState(new Filter(ManagementServerHostVO.class, "id", true, 0L, 1L));
            if(msHost == null || (msHost.getMsid() != mgmtSrvrId)){
                s_logger.debug("Skipping collect vm network stats from hosts");
                return;
            }
            // collect the vm network statistics(total) from hypervisor
            try {
                Transaction.execute(new TransactionCallbackNoReturn() {
                    @Override
                    public void doInTransactionWithoutResult(TransactionStatus status) {
                        s_logger.debug("VmNetworkStatsTask is running...");

                        SearchCriteria<HostVO> sc = _hostDao.createSearchCriteria();
                        sc.addAnd("status", SearchCriteria.Op.EQ, Status.Up.toString());
                        sc.addAnd("resourceState", SearchCriteria.Op.NIN, ResourceState.Maintenance, ResourceState.PrepareForMaintenance, ResourceState.ErrorInMaintenance);
                        sc.addAnd("type", SearchCriteria.Op.EQ, Host.Type.Routing.toString());
                        List<HostVO> hosts = _hostDao.search(sc, null);

                        for (HostVO host : hosts)
                        {
                            List<UserVmVO> vms = _userVmDao.listRunningByHostId(host.getId());
                            List<Long> vmIds = new ArrayList<Long>();

                            for (UserVmVO vm : vms) {
                                if (vm.getType() == VirtualMachine.Type.User) // user vm
                                    vmIds.add(vm.getId());
                            }

                            HashMap<Long, List<VmNetworkStatsEntry>> vmNetworkStatsById = _userVmMgr.getVmNetworkStatistics(host.getId(), host.getName(), vmIds);
                            if (vmNetworkStatsById == null)
                                continue;

                            Set<Long> vmIdSet = vmNetworkStatsById.keySet();
                            for(Long vmId : vmIdSet)
                            {
                                List<VmNetworkStatsEntry> vmNetworkStats = vmNetworkStatsById.get(vmId);
                                if (vmNetworkStats == null)
                                    continue;
                                UserVmVO userVm = _userVmDao.findById(vmId);
                                if (userVm == null) {
                                    s_logger.debug("Cannot find uservm with id: " + vmId + " , continue");
                                    continue;
                                }
                                s_logger.debug("Now we are updating the user_statistics table for VM: " + userVm.getInstanceName() + " after collecting vm network statistics from host: " + host.getName());
                                for (VmNetworkStatsEntry vmNetworkStat:vmNetworkStats) {
                                    SearchCriteria<NicVO> sc_nic = _nicDao.createSearchCriteria();
                                    sc_nic.addAnd("macAddress", SearchCriteria.Op.EQ, vmNetworkStat.getMacAddress());
                                    NicVO nic = _nicDao.search(sc_nic, null).get(0);
                                    List<VlanVO> vlan = _vlanDao.listVlansByNetworkId(nic.getNetworkId());
                                    if (vlan == null || vlan.size() == 0 || vlan.get(0).getVlanType() != VlanType.DirectAttached)
                                        continue; // only get network statistics for DirectAttached network (shared networks in Basic zone and Advanced zone with/without SG)
                                    UserStatisticsVO previousvmNetworkStats = _userStatsDao.findBy(userVm.getAccountId(), userVm.getDataCenterId(), nic.getNetworkId(), nic.getIPv4Address(), vmId, "UserVm");
                                    if (previousvmNetworkStats == null) {
                                        previousvmNetworkStats = new UserStatisticsVO(userVm.getAccountId(), userVm.getDataCenterId(),nic.getIPv4Address(), vmId, "UserVm", nic.getNetworkId());
                                        _userStatsDao.persist(previousvmNetworkStats);
                                    }
                                    UserStatisticsVO vmNetworkStat_lock = _userStatsDao.lock(userVm.getAccountId(), userVm.getDataCenterId(), nic.getNetworkId(), nic.getIPv4Address(), vmId, "UserVm");

                                    if ((vmNetworkStat.getBytesSent() == 0) && (vmNetworkStat.getBytesReceived() == 0)) {
                                        s_logger.debug("bytes sent and received are all 0. Not updating user_statistics");
                                        continue;
                                    }

                                    if (vmNetworkStat_lock == null) {
                                        s_logger.warn("unable to find vm network stats from host for account: " + userVm.getAccountId() + " with vmId: " + userVm.getId()+ " and nicId:" + nic.getId());
                                        continue;
                                    }

                                    if (previousvmNetworkStats != null
                                            && ((previousvmNetworkStats.getCurrentBytesSent() != vmNetworkStat_lock.getCurrentBytesSent())
                                            || (previousvmNetworkStats.getCurrentBytesReceived() != vmNetworkStat_lock.getCurrentBytesReceived()))) {
                                        s_logger.debug("vm network stats changed from the time GetNmNetworkStatsCommand was sent. " +
                                                "Ignoring current answer. Host: " + host.getName()  + " . VM: " + vmNetworkStat.getVmName() +
                                                " Sent(Bytes): " + vmNetworkStat.getBytesSent() + " Received(Bytes): " + vmNetworkStat.getBytesReceived());
                                        continue;
                                    }

                                    if (vmNetworkStat_lock.getCurrentBytesSent() > vmNetworkStat.getBytesSent()) {
                                        if (s_logger.isDebugEnabled()) {
                                            s_logger.debug("Sent # of bytes that's less than the last one.  " +
                                                    "Assuming something went wrong and persisting it. Host: " + host.getName() + " . VM: " + vmNetworkStat.getVmName() +
                                                    " Reported: " + vmNetworkStat.getBytesSent() + " Stored: " + vmNetworkStat_lock.getCurrentBytesSent());
                                        }
                                        vmNetworkStat_lock.setNetBytesSent(vmNetworkStat_lock.getNetBytesSent() + vmNetworkStat_lock.getCurrentBytesSent());
                                    }
                                    vmNetworkStat_lock.setCurrentBytesSent(vmNetworkStat.getBytesSent());

                                    if (vmNetworkStat_lock.getCurrentBytesReceived() > vmNetworkStat.getBytesReceived()) {
                                        if (s_logger.isDebugEnabled()) {
                                            s_logger.debug("Received # of bytes that's less than the last one.  " +
                                                    "Assuming something went wrong and persisting it. Host: " + host.getName() + " . VM: " + vmNetworkStat.getVmName() +
                                                    " Reported: " + vmNetworkStat.getBytesReceived() + " Stored: " + vmNetworkStat_lock.getCurrentBytesReceived());
                                        }
                                        vmNetworkStat_lock.setNetBytesReceived(vmNetworkStat_lock.getNetBytesReceived() + vmNetworkStat_lock.getCurrentBytesReceived());
                                    }
                                    vmNetworkStat_lock.setCurrentBytesReceived(vmNetworkStat.getBytesReceived());

                                    if (! _dailyOrHourly) {
                                        //update agg bytes
                                        vmNetworkStat_lock.setAggBytesReceived(vmNetworkStat_lock.getNetBytesReceived() + vmNetworkStat_lock.getCurrentBytesReceived());
                                        vmNetworkStat_lock.setAggBytesSent(vmNetworkStat_lock.getNetBytesSent() + vmNetworkStat_lock.getCurrentBytesSent());
                                    }

                                    _userStatsDao.update(vmNetworkStat_lock.getId(), vmNetworkStat_lock);
                                }
                            }
                        }
                    }
                });
            } catch (Exception e) {
                s_logger.warn("Error while collecting vm network stats from hosts", e);
            }
        }
    }


    class VolumeStatsTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            try {
                List<StoragePoolVO> pools = _storagePoolDao.listAll();

                for (StoragePoolVO pool : pools) {
                    List<VolumeVO> volumes = _volsDao.findByPoolId(pool.getId(), null);
                    List<String> volumeLocators = new ArrayList<String>();
                    for (VolumeVO volume: volumes){
                        if (volume.getFormat() == ImageFormat.QCOW2) {
                            volumeLocators.add(volume.getUuid());
                        }
                        else if (volume.getFormat() == ImageFormat.VHD){
                            volumeLocators.add(volume.getPath());
                        }
                        else if (volume.getFormat() == ImageFormat.OVA){
                            volumeLocators.add(volume.getChainInfo());
                        }
                        else {
                            s_logger.warn("Volume stats not implemented for this format type " + volume.getFormat() );
                            break;
                        }
                    }
                    try {
                        Map<String, VolumeStatsEntry> volumeStatsByUuid;
                        if (pool.getScope() == ScopeType.ZONE) {
                            volumeStatsByUuid = new HashMap<>();
                            for (final Cluster cluster: _clusterDao.listByZoneId(pool.getDataCenterId())) {
                                final Map<String, VolumeStatsEntry> volumeStatsForCluster = _userVmMgr.getVolumeStatistics(cluster.getId(), pool.getUuid(), pool.getPoolType(), volumeLocators, StatsTimeout.value());
                                if (volumeStatsForCluster != null) {
                                    volumeStatsByUuid.putAll(volumeStatsForCluster);
                                }
                            }
                        } else {
                            volumeStatsByUuid = _userVmMgr.getVolumeStatistics(pool.getClusterId(), pool.getUuid(), pool.getPoolType(), volumeLocators, StatsTimeout.value());
                        }
                        if (volumeStatsByUuid != null){
                            for (final Map.Entry<String, VolumeStatsEntry> entry : volumeStatsByUuid.entrySet()) {
                                if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                                    continue;
                                }
                                _volumeStats.put(entry.getKey(), entry.getValue());
                            }
                        }
                    } catch (Exception e) {
                        s_logger.warn("Failed to get volume stats for cluster with ID: " + pool.getClusterId(), e);
                        continue;
                    }
                }
            } catch (Throwable t) {
                s_logger.error("Error trying to retrieve volume stats", t);
            }
        }
    }

    public VolumeStats getVolumeStats(String volumeLocator) {
        if (volumeLocator != null && _volumeStats.containsKey(volumeLocator)) {
            return _volumeStats.get(volumeLocator);
        }
        return null;
    }

    class StorageCollector extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            try {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("StorageCollector is running...");
                }

                List<DataStore> stores = _dataStoreMgr.listImageStores();
                ConcurrentHashMap<Long, StorageStats> storageStats = new ConcurrentHashMap<Long, StorageStats>();
                for (DataStore store : stores) {
                    if (store.getUri() == null) {
                        continue;
                    }

                    Integer nfsVersion = imageStoreDetailsUtil.getNfsVersion(store.getId());
                    GetStorageStatsCommand command = new GetStorageStatsCommand(store.getTO(), nfsVersion);
                    EndPoint ssAhost = _epSelector.select(store);
                    if (ssAhost == null) {
                        s_logger.debug("There is no secondary storage VM for secondary storage host " + store.getName());
                        continue;
                    }
                    long storeId = store.getId();
                    Answer answer = ssAhost.sendMessage(command);
                    if (answer != null && answer.getResult()) {
                        storageStats.put(storeId, (StorageStats)answer);
                        s_logger.trace("HostId: " + storeId + " Used: " + ((StorageStats)answer).getByteUsed() + " Total Available: " +
                                ((StorageStats)answer).getCapacityBytes());
                    }
                }
                _storageStats = storageStats;
                ConcurrentHashMap<Long, StorageStats> storagePoolStats = new ConcurrentHashMap<Long, StorageStats>();

                List<StoragePoolVO> storagePools = _storagePoolDao.listAll();
                for (StoragePoolVO pool : storagePools) {
                    // check if the pool has enabled hosts
                    List<Long> hostIds = _storageManager.getUpHostsInPool(pool.getId());
                    if (hostIds == null || hostIds.isEmpty())
                        continue;
                    GetStorageStatsCommand command = new GetStorageStatsCommand(pool.getUuid(), pool.getPoolType(), pool.getPath());
                    long poolId = pool.getId();
                    try {
                        Answer answer = _storageManager.sendToPool(pool, command);
                        if (answer != null && answer.getResult()) {
                            storagePoolStats.put(pool.getId(), (StorageStats)answer);

                            // Seems like we have dynamically updated the pool size since the prev. size and the current do not match
                            if (_storagePoolStats.get(poolId) != null && _storagePoolStats.get(poolId).getCapacityBytes() != ((StorageStats)answer).getCapacityBytes()) {
                                pool.setCapacityBytes(((StorageStats)answer).getCapacityBytes());
                                _storagePoolDao.update(pool.getId(), pool);
                            }
                        }
                    } catch (StorageUnavailableException e) {
                        s_logger.info("Unable to reach " + pool, e);
                    } catch (Exception e) {
                        s_logger.warn("Unable to get stats for " + pool, e);
                    }
                }
                _storagePoolStats = storagePoolStats;
            } catch (Throwable t) {
                s_logger.error("Error trying to retrieve storage stats", t);
            }
        }

    }

    public boolean imageStoreHasEnoughCapacity(DataStore imageStore) {
        StorageStats imageStoreStats = _storageStats.get(imageStore.getId());
        if (imageStoreStats != null && (imageStoreStats.getByteUsed()/(imageStoreStats.getCapacityBytes()*1.0)) <= _imageStoreCapacityThreshold) {
            return true;
        }
        return false;
    }

    public StorageStats getStorageStats(long id) {
        return _storageStats.get(id);
    }

    public HostStats getHostStats(long hostId) {
        return _hostStats.get(hostId);
    }

    public StorageStats getStoragePoolStats(long id) {
        return _storagePoolStats.get(id);
    }

    @Override
    public String getConfigComponentName() {
        return StatsCollector.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] { vmDiskStatsInterval, vmDiskStatsIntervalMin, vmNetworkStatsInterval, vmNetworkStatsIntervalMin, StatsTimeout };
    }
}
