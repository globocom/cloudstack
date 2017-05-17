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

import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.google.api.client.json.GenericJson;
import com.google.api.client.util.ArrayMap;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.log4j.Logger;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONObject;

public class ElasticSearchAutoScaleStatsCollector extends AutoScaleStatsCollector implements Configurable{

    private static final ConfigKey<String> ElasticSearchHost = new ConfigKey<>("Advanced", String.class, "autoscale.elasticsearch.host", null,
            "Elastic search server host name", true, ConfigKey.Scope.Global);

    private static final ConfigKey<String> ElasticSearchProtocol = new ConfigKey<>("Advanced", String.class, "autoscale.elasticsearch.protocol", "http",
            "Elastic search server protocol(https/http)", true, ConfigKey.Scope.Global);

    private static final ConfigKey<Integer> ElasticSearchPort = new ConfigKey<>("Advanced", Integer.class, "autoscale.elasticsearch.port", null,
            "Elastic search server transport module port", true, ConfigKey.Scope.Global);

    private static final ConfigKey<String> ElasticSearchClusterName = new ConfigKey<>("Advanced", String.class, "autoscale.elasticsearch.cluster", null,
            "Elastic search server cluster name", true, ConfigKey.Scope.Global);

    private static final ConfigKey<String> ElasticSearchIndexName = new ConfigKey<>("Advanced", String.class, "autoscale.elasticsearch.index", null,
            "Elastic search index name", true, ConfigKey.Scope.Global);

    private static final Logger s_logger = Logger.getLogger(ElasticSearchAutoScaleStatsCollector.class.getName());


    private SimpleHttp simpleHttp;


    public ElasticSearchAutoScaleStatsCollector(){
        this.simpleHttp = new SimpleHttp();
    }

    @Override
    public Map<String, Double> retrieveMetrics(AutoScaleVmGroup asGroup, List<VMInstanceVO> vmList) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("[AutoScale] Collecting ElasticSearch data.");
        }

        if(vmList == null || vmList.size() == 0){
            return null;
        }

        if(!connectionIsSet()) {
            s_logger.debug("[AutoScale] connection not setted.");
            return null;
        }

        Map<String, Double> avgSummary = new HashMap<>();
        List<Pair<String, Integer>> counterNameAndDuration = this.getPairOfCounterNameAndDuration(asGroup);

        try {
            for (Pair<String, Integer> counter : counterNameAndDuration) {
                String counterName = counter.first().split(",")[0];
                Integer duration = counter.second();
                Double value = this.queryForStats(asGroup.getUuid(), counterName, duration);
                avgSummary.put(counterName, value);
            }
        }catch (RuntimeException ex){
            s_logger.error("[AutoScale] Error while reading AutoScale group " + asGroup.getId() + " stats", ex);
        }
        return avgSummary;
    }

    private Double getStatAverage(SearchResponse response) {
        Double average = null;
        Aggregations aggregations = response.getAggregations();
        if(aggregations != null){
            average = ((InternalAvg) aggregations.asMap().get("counter_average")).getValue();
            average = (average >= 0.0 ? average : null);
        }
        return average;
    }

    protected Double queryForStats(String autoScaleGroupUUID, String counterName, Integer duration) {

        try {
            String query = buildQuery(autoScaleGroupUUID, duration);

            String url = ElasticSearchProtocol.value() + "://" +
                    ElasticSearchHost.value() + ":" +
                    ElasticSearchPort.value().toString() + "/" +
                    ElasticSearchIndexName.value() + "/" +
                    counterName + "/_search";

            String result = simpleHttp.post(url, query);
            GenericJson metric = SimpleHttp.parse(result, GenericJson.class);
            ArrayMap aggregations = (ArrayMap)metric.get("aggregations");
            ArrayMap counterAverage = (ArrayMap)aggregations.get("counter_average");
            return ((BigDecimal)counterAverage.get("value")).doubleValue();

        }catch (Exception e) {
            throw new CloudRuntimeException("Error while searching autoscale metrics. autoscaleGroup: " + autoScaleGroupUUID, e);
        }
    }



    protected String buildQuery(String autoScaleGroupUUID, Integer duration){

        JSONObject request = new JSONObject();
        request.put("from", 0);
        request.put("size", 0);


        JSONObject query = new JSONObject();
        request.put("query", query);
        JSONObject filtered = new JSONObject();
        JSONObject queryFiltered = new JSONObject();


        JSONObject filter = new JSONObject();
        JSONObject andFilter = new JSONObject();
        List<JSONObject> filtersAnd = new ArrayList<JSONObject>();
        JSONObject rangeFilters = new JSONObject();
        JSONObject timestampRange = new JSONObject();
        JSONObject simpleTerm = new JSONObject();
        JSONObject term = new JSONObject();



        query.put("filtered", filtered);
        filtered.put("query", queryFiltered);
        queryFiltered.put("match_all", new JSONObject());
        filtered.put("filter", filter);
        filter.put("and", andFilter);
        andFilter.put("filters", filtersAnd);

        filtersAnd.add(rangeFilters);
        JSONObject range = new JSONObject();
        rangeFilters.put("range",range );
        range.put("@timestamp", timestampRange);


        timestampRange.put("from", "now-" + duration + "s/s");
        timestampRange.put("to", "now");
        timestampRange.put("include_lower", true);
        timestampRange.put("include_upper", true);

        filtersAnd.add(simpleTerm);
        simpleTerm.put("term", term);
        term.put("autoScaleGroupUuid.raw", autoScaleGroupUUID);


        JSONObject agg = new JSONObject();
        request.put("aggregations", agg);
        JSONObject counterAv = new JSONObject();
        agg.put("counter_average", counterAv);

        JSONObject field = new JSONObject();
        counterAv.put("avg", field);
        field.put("field", "value");


        return request.toJSONString();

    }

    private boolean connectionIsSet() {
        return ElasticSearchHost.value() != null && ElasticSearchIndexName.value() != null && ElasticSearchPort.value().toString() != null;
    }



    @Override
    public String getConfigComponentName() {
        return ElasticSearchAutoScaleStatsCollector.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{ ElasticSearchPort, ElasticSearchHost, ElasticSearchClusterName, ElasticSearchIndexName };
    }

    public SimpleHttp getSimpleHttp() {
        return simpleHttp;
    }

    public void setSimpleHttp(SimpleHttp simpleHttp) {
        this.simpleHttp = simpleHttp;
    }


}
