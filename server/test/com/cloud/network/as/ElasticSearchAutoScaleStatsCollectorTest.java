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

import com.cloud.network.as.dao.AutoScaleVmGroupVmMapDao;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.config.impl.ConfigDepotImpl;
import org.apache.cloudstack.framework.config.impl.ConfigurationVO;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import org.mockito.Mockito;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ElasticSearchAutoScaleStatsCollectorTest extends AutoScaleStatsCollectorTest{

    @Before
    public void setUp() {
        super.setUp();
        autoScaleStatsCollector = new ElasticSearchAutoScaleStatsCollector();
    }

    @Test
    public void testReadVmStatsWithCpuCounter(){
        mockElasticSearchClient("cpu", 0.10, "elastic.com", 8080);
        super.testReadVmStatsWithCpuCounter();
    }

    @Test
    public void testReadVmStatsWithMemoryCounter(){
        mockElasticSearchClient("memory", 0.25, "elastic.com", 8080);
        super.testReadVmStatsWithMemoryCounter();
    }

    @Test
    @Override
    public void testReadVmStatsWithError(){
        AutoScaleVmGroupVmMapDao _asGroupVmDao = mock(AutoScaleVmGroupVmMapDao.class);
        when(_asGroupVmDao.listByGroup(anyLong())).thenThrow(new RuntimeException());
        autoScaleStatsCollector._asGroupVmDao = _asGroupVmDao;

        Map<String, Double> countersSummary = autoScaleStatsCollector.retrieveMetrics(asGroup, vmList);

        assert countersSummary == null;
    }
    @Test
    public void testReadVmStatsWithNullAverage(){
        mockElasticSearchClient("cpu", null, "elastic.com", 8080);
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




    private void mockElasticSearchClient(String counter, Double average, String host, Integer port){

        ConfigDepotImpl mock = mock(ConfigDepotImpl.class);
        ConfigurationDao mockDAo = mock(ConfigurationDao.class);
        when(mock.global()).thenReturn(mockDAo);
        when(mockDAo.findById("autoscale.elasticsearch.host")).thenReturn(new ConfigurationVO("Advanced", "String", null, "autoscale.elasticsearch.host", host, null));
        when(mockDAo.findById("autoscale.elasticsearch.port")).thenReturn(new ConfigurationVO("Advanced", "String", null, "autoscale.elasticsearch.port", port.toString(), null));
        when(mockDAo.findById("autoscale.elasticsearch.index")).thenReturn(new ConfigurationVO("Advanced", "String", null, "autoscale.elasticsearch.index", "cloudstack", null));
        ConfigKey.init(mock);

        autoScaleStatsCollector = new ElasticSearchAutoScaleStatsCollector();

        ElasticSearchAutoScaleStatsCollector elasticCollector = ((ElasticSearchAutoScaleStatsCollector)autoScaleStatsCollector);

        SimpleHttp simpleHttp = Mockito.mock(SimpleHttp.class);


        String result = "{\n" +
                "  \"took\" : 73,\n" +
                "  \"timed_out\" : false,\n" +
                "  \"_shards\" : {\n" +
                "    \"total\" : 4,\n" +
                "    \"successful\" : 4,\n" +
                "    \"failed\" : 0\n" +
                "  },\n" +
                "  \"hits\" : {\n" +
                "    \"total\" : 0,\n" +
                "    \"max_score\" : 0.0,\n" +
                "    \"hits\" : [ ]\n" +
                "  },\n" +
                "  \"aggregations\" : {\n" +
                "    \"counter_average\" : {\n" +
                "      \"value\" : @valueMocked\n" +
                "    }\n" +
                "  }\n" +
                "}";

        result = result.replace("@valueMocked", average != null ? average.toString() : "null");
        String url = "http://" + host + ":" + port + "/cloudstack/" + counter + "/_search";
        // http://elastic.com:8080/cloudstack/cpu/_search
        when(simpleHttp.post(eq(url) , anyString())).thenReturn(result);
        elasticCollector.setSimpleHttp(simpleHttp);

    }

}
