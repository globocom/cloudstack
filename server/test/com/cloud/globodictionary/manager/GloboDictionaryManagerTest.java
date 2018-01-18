/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.cloud.globodictionary.manager;

import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.globodictionary.GloboDictionaryService;
import com.cloud.globodictionary.apiclient.GloboDictionaryAPIClient;
import com.cloud.globodictionary.apiclient.model.GloboDictionaryEntityVO;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GloboDictionaryManagerTest {

    public static final String BUSINESS_SERVICE_ID = "31cf5f75db9f43409fc15458dc96198b";
    public static final String BUSINESS_SERVICE_NAME = "Assinatura - Vendas";

    @Mock
    private GloboDictionaryAPIClient apiClient;

    private GloboDictionaryManager manager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        manager = new GloboDictionaryManager(apiClient);
    }

    @Test
    public void testGet(){
        GloboDictionaryEntityVO apiResponse = createEntity(BUSINESS_SERVICE_ID, BUSINESS_SERVICE_NAME, "Em uso");
        when(apiClient.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID)).thenReturn(apiResponse);
        GloboDictionaryEntity entity = manager.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);

        assertNotNull(entity);
        assertEquals(apiResponse, entity);
        verify(apiClient).get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);
    }

    @Test
    public void testGetGivenObjectIsNotActive(){
        GloboDictionaryEntityVO apiResponse = createEntity(BUSINESS_SERVICE_ID, BUSINESS_SERVICE_NAME, "Desativado");
        when(apiClient.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID)).thenReturn(apiResponse);
        GloboDictionaryEntity entity = manager.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);

        assertNull(entity);
        verify(apiClient).get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);
    }

    @Test
    public void testGetGivenObjectNotFound(){
        when(apiClient.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID)).thenReturn(null);
        GloboDictionaryEntity entity = manager.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);

        assertNull(entity);
        verify(apiClient).get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);
    }

    @Test
    public void testList(){
        GloboDictionaryEntity apiResponse = createEntity(BUSINESS_SERVICE_ID, BUSINESS_SERVICE_NAME, "Em uso");
        List<GloboDictionaryEntity> responseList = Collections.singletonList(apiResponse);
        when(apiClient.list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE)).thenReturn(responseList);
        List<GloboDictionaryEntity> entities = manager.list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE);

        assertEquals(1, entities.size());
        verify(apiClient).list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE);
    }

    @Test
    public void testListGivenObjectIsNotActive(){
        GloboDictionaryEntity apiResponse = createEntity(BUSINESS_SERVICE_ID, BUSINESS_SERVICE_NAME, "Desativado");
        List<GloboDictionaryEntity> responseList = Collections.singletonList(apiResponse);
        when(apiClient.list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE)).thenReturn(responseList);
        List<GloboDictionaryEntity> entities = manager.list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE);

        assertEquals(0, entities.size());
        verify(apiClient).list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE);
    }


    private GloboDictionaryEntityVO createEntity(String businessServiceId, String businessServiceName, String status) {
        return new GloboDictionaryEntityVO(businessServiceId, businessServiceName, status);
    }
}