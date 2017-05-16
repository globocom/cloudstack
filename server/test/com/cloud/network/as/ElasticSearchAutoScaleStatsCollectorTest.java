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

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class ElasticSearchAutoScaleStatsCollectorTest extends AutoScaleStatsCollectorTest{

    @Before
    public void setUp() {
        super.setUp();
        autoScaleStatsCollector = new ElasticSearchAutoScaleStatsCollector();
    }

    @Test
    public void testReadVmStatsWithCpuCounter(){
        mockElasticSearchClient(0.10);
        super.testReadVmStatsWithCpuCounter();
    }

    @Test
    public void testReadVmStatsWithMemoryCounter(){
        mockElasticSearchClient(0.25);
        super.testReadVmStatsWithMemoryCounter();
    }

    @Test
    public void testReadVmStatsWithNullAverage(){
        mockElasticSearchClient(null);
        mockAutoScaleVmGroupVmMapDao();
        mockAutoScaleGroupPolicyMapDao();
        mockAutoScalePolicyDao();
        mockAutoScalePolicyConditionMapDao();
        mockConditionDao();
        mockCounterDao("cpu");

        Map<String, Double> countersSummary = autoScaleStatsCollector.retrieveMetrics(asGroup, vmList);

        assert countersSummary.get("cpu") == null;
    }


    @Test
    public void testBuildQuery() {

        ElasticSearchAutoScaleStatsCollector elasticCollector =  new ElasticSearchAutoScaleStatsCollector();

        String query = elasticCollector.buildQuery("abc", 10);

        assertNotNull(query);

        System.out.println(query);

        assertTrue(query.contains("from"));
        assertTrue(query.contains("to"));


        assertTrue(query.startsWith("{"));
        assertTrue(query.endsWith("}"));

        assertTrue(query.contains("\"autoScaleGroupUuid.raw\":\"abc\""));
    }

    private void mockElasticSearchClient(Double average){
//        TransportClient client = mock(TransportClient.class);
//        SearchRequestBuilder searchRequestBuilder = mock(SearchRequestBuilder.class);
//        ListenableActionFuture listenableActionFuture = mock(ListenableActionFuture.class);
//        SearchResponse searchResponse = mock(SearchResponse.class);
//        Aggregations aggregations = mock(Aggregations.class);
//        Map<String, Aggregation> aggregationResponse = new HashMap<>();
//        aggregationResponse.put("counter_average", new InternalAvg("counter_average", average != null ? average : Double.NaN, 1));
//
//        when(client.prepareSearch(anyString())).thenReturn(searchRequestBuilder);
//        when(searchRequestBuilder.setTypes(anyString())).thenReturn(searchRequestBuilder);
//        when(searchRequestBuilder.setFrom(anyInt())).thenReturn(searchRequestBuilder);
//        when(searchRequestBuilder.setSize(anyInt())).thenReturn(searchRequestBuilder);
//        when(searchRequestBuilder.setQuery(any(QueryBuilder.class))).thenReturn(searchRequestBuilder);
//        when(searchRequestBuilder.addAggregation(any(AbstractAggregationBuilder.class))).thenReturn(searchRequestBuilder);
//        when(searchRequestBuilder.execute()).thenReturn(listenableActionFuture);
//        when(listenableActionFuture.actionGet()).thenReturn(searchResponse);
//        when(searchResponse.getAggregations()).thenReturn(aggregations);
//        when(aggregations.asMap()).thenReturn(aggregationResponse);
//
//        ((ElasticSearchAutoScaleStatsCollector)autoScaleStatsCollector).elasticSearchClient = client;
    }
}
