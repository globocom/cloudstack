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
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;

//import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Key;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
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


    static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");
    private volatile HttpRequestFactory requestFactory;

    public ElasticSearchAutoScaleStatsCollector(){
        this.requestFactory = getRequestFactory();
    }

    @Override
    public Map<String, Double> retrieveMetrics(AutoScaleVmGroup asGroup, List<VMInstanceVO> vmList) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("[AutoScale] Collecting ElasticSearch data.");
        }

        if(!connectionIsSet())
            return null;

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
        try{
            String query = buildQuery(autoScaleGroupUUID, duration);
            HttpRequest request;

            final byte [] contentBytes = query.getBytes();
            HttpContent body = new ByteArrayContent("application/json", contentBytes );

            HttpRequestFactory requestFactory = this.getRequestFactory();

            GenericUrl url = getUrl(counterName);
            request = requestFactory.buildRequest("POST", url, body);

            request.setLoggingEnabled(true);

            s_logger.debug("[AutoScale] searching autoscalegroup: " + autoScaleGroupUUID + ", url:" + request.getUrl().toString() + ", result: " + query);
            HttpResponse response = request.execute();

            String responseContent = response.parseAsString();
            Integer statusCode = response.getStatusCode();

            s_logger.debug("[AutoScale] results metrics autoscalegroup: " + autoScaleGroupUUID + ", statusCode:" + statusCode + ", result: " + responseContent);

            MetricResult result = parse(responseContent, MetricResult.class);

            return result.getValue();
        } catch (HttpResponseException httpException){
            s_logger.error("[AutoScale] error while get metrics. StatusCode: " + httpException.getStatusCode() + ", Content: " + httpException.getContent() + ", msg:" + httpException.getMessage(), httpException);


            throw new CloudRuntimeException("error searching autoscale metrics", httpException);
        } catch (IOException e) {
            s_logger.error("IOError " , e);
            throw new CloudRuntimeException("error searching autoscale metrics", e);
        }

    }

    private GenericUrl getUrl(String counterName) {
        try {
            String url = ElasticSearchProtocol.value() + "://" +
                    ElasticSearchHost.value() + ":" +
                    ElasticSearchPort.value().toString() + "/" +
                    ElasticSearchIndexName.value() + "/" +
                    counterName + "/_search";

            return new GenericUrl(url);
        } catch (Exception e) {
            throw new CloudRuntimeException("Error build elasticsearch url. ", e);
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
        return true;
    }

    protected HttpRequestFactory getRequestFactory() {
        if (this.requestFactory == null) {
            synchronized (this) {
                loadConfig();
                this.requestFactory = HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
                    public void initialize(HttpRequest request) throws IOException {
                        request.setParser(new JsonObjectParser(JSON_FACTORY));
//                        request.setReadTimeout(ElasticSearchAutoScaleStatsCollector.this.readTimeout);
//                        request.setConnectTimeout(ElasticSearchAutoScaleStatsCollector.this.connectTimeout);
//                        request.setNumberOfRetries(ElasticSearchAutoScaleStatsCollector.this.numberOfRetries);
                    }
                });
            }
        }
        return this.requestFactory;
    }

    @Override
    public String getConfigComponentName() {
        return ElasticSearchAutoScaleStatsCollector.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{ ElasticSearchPort, ElasticSearchHost, ElasticSearchClusterName, ElasticSearchIndexName };
    }

    public static <T> T parse(String output, Class<T> dataType) throws CloudRuntimeException {
        try {
            InputStream stream = new ByteArrayInputStream(output.getBytes(DEFAULT_CHARSET));

            com.google.api.client.json.JsonFactory jsonFactory = new JacksonFactory();
            return new JsonObjectParser(jsonFactory).parseAndClose(stream, DEFAULT_CHARSET, dataType);

        } catch (IOException e) {
            throw new CloudRuntimeException("IOError trying to parse : " + output + " to " + dataType + e, e);
        }
    }


    public static class MetricResult extends GenericJson {
        @Key("aggregations")
        private Aggregations aggregations;


        public static class Aggregations  extends GenericJson{
            @Key("counter_average")
            private CounterAverage counterAverage;


            public static class CounterAverage extends GenericJson{
                @Key("value")
                private Double value;
            }
        }
        public Double getValue() {
            if ( aggregations != null && aggregations.counterAverage != null ) {
                Double value = aggregations.counterAverage.value;
                return aggregations.counterAverage.value >= 0.0 ? value : null;
            }
            return null;
        }
    }

    private static void loadConfig() {
        System.out.println("Init");

//
//        URL url = MainTester.class.getClassLoader().getResource("config.properties");
//        System.setProperty("java.util.logging.config.file", url.getFile());
//            System.setProperty("java.util.logging.config.file", "/Users/lucas.castro/projects/globoNetworkAPI/globoNetworkAPI-tester/src/main/resources/config.properties");

        ConsoleHandler logHandler = new ConsoleHandler();
        logHandler.setLevel(Level.ALL);
        java.util.logging.Logger httpLogger = java.util.logging.Logger.getLogger("com.google.api.client.http");
        httpLogger.setLevel(Level.ALL);
        httpLogger.addHandler(logHandler);

        httpLogger = java.util.logging.Logger.getLogger("com.globocom.globoNetwork.client");
        httpLogger.setLevel(Level.CONFIG);
        httpLogger.addHandler(logHandler);
    }
}
