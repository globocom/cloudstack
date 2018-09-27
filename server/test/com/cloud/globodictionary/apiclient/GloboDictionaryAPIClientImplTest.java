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
package com.cloud.globodictionary.apiclient;

import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.globodictionary.GloboDictionaryService;
import com.cloud.globodictionary.apiclient.model.GloboDictionaryEntityVO;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GloboDictionaryAPIClientImplTest {

    public static final String BUSINESS_SERVICE_ID = "31cf5f75db9f43409fc15458dc96198b";
    public static final String BUSINESS_SERVICE_NAME = "Assinatura - Vendas";
    @Mock
    private HttpClient httpClient;

    @Spy
    private GloboDictionaryAPIClientImpl apiClient;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        apiClient = spy(new GloboDictionaryAPIClientImpl(httpClient));
    }

    @Test
    public void testGet() throws Exception {
        GetMethod getMethodMock = mock(GetMethod.class);
        when(getMethodMock.getResponseBodyAsString()).thenReturn(getBusinessServiceJSON());
        mockCreateRequest(getMethodMock);
        mockExecuteMethod(200);

        GloboDictionaryEntity entity = apiClient.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);

        assertEquals(BUSINESS_SERVICE_NAME, entity.getName());
        assertEquals(BUSINESS_SERVICE_ID, entity.getId());
        verify(httpClient).executeMethod(getMethodMock);
    }

    @Test
    public void testGetGivenObjectNotFound() throws Exception {
        GetMethod getMethodMock = mock(GetMethod.class);
        when(getMethodMock.getResponseBodyAsString()).thenReturn("[]");
        mockCreateRequest(getMethodMock);
        mockExecuteMethod(200);

        GloboDictionaryEntity entity = apiClient.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);

        assertNull(entity);
        verify(httpClient).executeMethod(getMethodMock);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testGetGivenStatusCodeIsNot200() throws Exception {
        GetMethod getMethodMock = mock(GetMethod.class);
        mockCreateRequest(getMethodMock);
        mockExecuteMethod(500);

        apiClient.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testGetGivenIOError() throws Exception {
        GetMethod getMethodMock = mock(GetMethod.class);
        mockCreateRequest(getMethodMock);
        doThrow(new IOException()).when(httpClient).executeMethod(any(GetMethod.class));

        apiClient.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);
    }

    @Test
    public void testList() throws Exception {
        GetMethod getMethodMock = mock(GetMethod.class);
        when(getMethodMock.getResponseBodyAsString()).thenReturn(getBusinessServiceJSON());
        mockCreateRequest(getMethodMock);
        mockExecuteMethod(200);

        GloboDictionaryEntityVO vo1 = new GloboDictionaryEntityVO();
        vo1.setId(BUSINESS_SERVICE_ID);
        vo1.setName(BUSINESS_SERVICE_NAME);
        vo1.setStatus("Ativo");

        ArrayList<GloboDictionaryEntity> vos = new ArrayList<>();
        vos.add(vo1);

        GloboDictionaryApiUnmarshaller unmarshallerMock = mock(GloboDictionaryApiUnmarshaller.class);
        apiClient.setGloboDictionaryApiUnmarshaller(unmarshallerMock);
        when(unmarshallerMock.unmarshal(getBusinessServiceJSON())).thenReturn(vos);

        List<GloboDictionaryEntity> entities = apiClient.list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE);

        GloboDictionaryEntity entity = entities.get(0);
        assertEquals(1, entities.size());
        assertEquals(BUSINESS_SERVICE_NAME, entity.getName());
        assertEquals(BUSINESS_SERVICE_ID, entity.getId());
        verify(httpClient).executeMethod(getMethodMock);
        verify(unmarshallerMock).unmarshal(getBusinessServiceJSON());
    }

    @Test
    public void testListByExample() throws IOException {
        GetMethod getMethodMock = mock(GetMethod.class);
        when(getMethodMock.getResponseBodyAsString()).thenReturn(getBusinessServiceJSON());
        mockCreateRequest(getMethodMock);
        mockExecuteMethod(200);

        GloboDictionaryEntityVO vo1 = new GloboDictionaryEntityVO();
        vo1.setId("11aa");
        vo1.setName("Teste");
        vo1.setStatus("Ativo");

        GloboDictionaryEntityVO vo2 = new GloboDictionaryEntityVO();
        vo2.setId("22bb");
        vo2.setName("Teste2");
        vo2.setStatus("Ativo");

        ArrayList<GloboDictionaryEntity> vos = new ArrayList<>();
        vos.add(vo1);
        vos.add(vo2);

        GloboDictionaryApiUnmarshaller unmarshallerMock = mock(GloboDictionaryApiUnmarshaller.class);
        apiClient.setGloboDictionaryApiUnmarshaller(unmarshallerMock);
        when(unmarshallerMock.unmarshal(getBusinessServiceJSON())).thenReturn(vos);

        Map<String, String> params = new HashMap<>();
        params.put("component_id", "1199aaff");
        List<GloboDictionaryEntity> globoDictionaryEntities = apiClient.listByExample(GloboDictionaryService.GloboDictionaryEntityType.SUB_COMPONENT, params);
        assertEquals(2, globoDictionaryEntities.size());
        assertEquals("22bb", globoDictionaryEntities.get(1).getId());

        verify(unmarshallerMock).unmarshal(getBusinessServiceJSON());
    }

    @Test
    public void testListGivenObjectNotFound() throws Exception {
        GetMethod getMethodMock = mock(GetMethod.class);
        when(getMethodMock.getResponseBodyAsString()).thenReturn("[]");
        mockCreateRequest(getMethodMock);
        mockExecuteMethod(200);

        GloboDictionaryApiUnmarshaller unmarshallerMock = mock(GloboDictionaryApiUnmarshaller.class);
        when(unmarshallerMock.unmarshal("[]")).thenReturn(new ArrayList<>());
        apiClient.setGloboDictionaryApiUnmarshaller(unmarshallerMock);

        List<GloboDictionaryEntity> entities = apiClient.list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE);

        assertEquals(0, entities.size());
        verify(httpClient).executeMethod(getMethodMock);
        verify(unmarshallerMock).unmarshal("[]");
    }

    @Test(expected = CloudRuntimeException.class)
    public void testListGivenStatusCodeIsNot200() throws Exception {
        GetMethod getMethodMock = mock(GetMethod.class);
        mockCreateRequest(getMethodMock);
        mockExecuteMethod(500);

        apiClient.list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testListGivenIOError() throws Exception {
        GetMethod getMethodMock = mock(GetMethod.class);
        mockCreateRequest(getMethodMock);
        doThrow(new IOException()).when(httpClient).executeMethod(any(GetMethod.class));

        apiClient.list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE);
    }

    private void mockExecuteMethod(int status) throws IOException {
        when(httpClient.executeMethod(any(GetMethod.class))).thenReturn(status);
    }

    private void mockCreateRequest(GetMethod getMethodMock) {
        doReturn(getMethodMock).when(apiClient).createRequest(any(GloboDictionaryService.GloboDictionaryEntityType.class), anyString());
    }

    private String getBusinessServiceJSON(){
        return "[{\"macro_servico\": \"Assinatura\", \"detalhe_servico\": \"Vendas\", \"nome\": \"Assinatura - Vendas\", \"descricao\": \"infraestrutura para o sistema de vendas\", \"driver_custeio\": \"N\\u00famero de Assinantes\", \"status\": \"Ativo\", \"coorporativo\": true, \"servico_negocio_armazenamento_id\": \"31cf5f75db9f43409fc15458dc96198b\", \"id_service_now\": \"31cf5f75db9f43409fc15458dc96198b\"}]";
    }
}