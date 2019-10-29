package com.cloud.network.as;

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

import javax.inject.Inject;

public class AutoScaleStatsCollectorFactoryImpl implements AutoScaleStatsCollectorFactory, Configurable {

    @Inject
    RRDAutoScaleStatsCollector rrdAutoScaleStatsCollector;
    @Inject
    ElasticSearchAutoScaleStatsCollector elasticSearchAutoScaleStatsCollector;

    private static final String RRD = "rrd";
    private static final String ELASTIC_SEARCH = "elasticsearch";

    private static final ConfigKey<String> StatsDataSource = new ConfigKey<String>("Advanced", String.class, "autoscale.stats.datasource", "rrd",
            "Auto scale VM stats data source (rrd/elasticsearch)", true, ConfigKey.Scope.Global);

    @Override
    public AutoScaleStatsCollector getStatsCollector() {
        if(RRD.equals(StatsDataSource.value())){
            return rrdAutoScaleStatsCollector;
        }else if(ELASTIC_SEARCH.equals(StatsDataSource.value())){
            return elasticSearchAutoScaleStatsCollector;
        }
        return rrdAutoScaleStatsCollector; //rrd as default option
    }

    @Override
    public String getConfigComponentName() {
        return AutoScaleStatsCollectorFactoryImpl.class.getName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{ StatsDataSource };
    }
}
