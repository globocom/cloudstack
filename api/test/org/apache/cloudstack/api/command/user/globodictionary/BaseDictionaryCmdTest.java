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
package org.apache.cloudstack.api.command.user.globodictionary;

import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.globodictionary.GloboDictionaryService;
import org.apache.cloudstack.api.response.GloboDictionaryResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class BaseDictionaryCmdTest {

    public static final String BUSINESS_SERVICE_ID = "31cf5f75db9f43409fc15458dc96198b";
    public static final String BUSINESS_SERVICE_NAME = "Assinatura - Vendas";

    @Mock
    GloboDictionaryService globoDictionaryService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testList() throws Exception {
        GloboDictionaryEntity mockEntity = mock(GloboDictionaryEntity.class);
        when(mockEntity.getId()).thenReturn(BUSINESS_SERVICE_ID);
        when(mockEntity.getName()).thenReturn(BUSINESS_SERVICE_NAME);
        when(globoDictionaryService.list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE)).thenReturn(Collections.singletonList(mockEntity));

        DummyBaseDictionaryCmd cmd = new DummyBaseDictionaryCmd(globoDictionaryService);
        cmd.execute();
        ListResponse responses = (ListResponse) cmd.getResponseObject();
        GloboDictionaryResponse response = ((GloboDictionaryResponse) responses.getResponses().get(0));

        assertEquals(new Integer(1), responses.getCount());
        assertEquals("listdummyresponse", responses.getResponseName());
        assertEquals(BUSINESS_SERVICE_ID, response.getId());
        assertEquals(BUSINESS_SERVICE_NAME, response.getName());
        assertEquals("response", response.getObjectName());

        verify(globoDictionaryService).list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE);
    }

    @Test
    public void testListGivenEmptyResponse() throws Exception {
        when(globoDictionaryService.list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE)).thenReturn(new ArrayList<GloboDictionaryEntity>());

        DummyBaseDictionaryCmd cmd = new DummyBaseDictionaryCmd(globoDictionaryService);
        cmd.execute();
        ListResponse responses = (ListResponse) cmd.getResponseObject();

        assertEquals(new Integer(0), responses.getCount());
        assertEquals("listdummyresponse", responses.getResponseName());

        verify(globoDictionaryService).list(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE);
    }

    @Test
    public void testGet() throws Exception {
        GloboDictionaryEntity mockEntity = mock(GloboDictionaryEntity.class);
        when(mockEntity.getId()).thenReturn(BUSINESS_SERVICE_ID);
        when(mockEntity.getName()).thenReturn(BUSINESS_SERVICE_NAME);
        when(globoDictionaryService.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID)).thenReturn(mockEntity);

        DummyBaseDictionaryCmd cmd = new DummyBaseDictionaryCmd(globoDictionaryService);
        cmd.setId(BUSINESS_SERVICE_ID);
        cmd.execute();
        ListResponse responses = (ListResponse) cmd.getResponseObject();
        GloboDictionaryResponse response = ((GloboDictionaryResponse) responses.getResponses().get(0));

        assertEquals(new Integer(1), responses.getCount());
        assertEquals("listdummyresponse", responses.getResponseName());
        assertEquals(BUSINESS_SERVICE_ID, response.getId());
        assertEquals(BUSINESS_SERVICE_NAME, response.getName());
        assertEquals("response", response.getObjectName());

        verify(globoDictionaryService).get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);
    }

    @Test
    public void testGetGivenObjectNotFound() throws Exception {
        when(globoDictionaryService.get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID)).thenReturn(null);

        DummyBaseDictionaryCmd cmd = new DummyBaseDictionaryCmd(globoDictionaryService);
        cmd.setId(BUSINESS_SERVICE_ID);
        cmd.execute();
        ListResponse responses = (ListResponse) cmd.getResponseObject();
        assertEquals(new Integer(0), responses.getCount());
        assertEquals("listdummyresponse", responses.getResponseName());

        verify(globoDictionaryService).get(GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE, BUSINESS_SERVICE_ID);
    }
}

class DummyBaseDictionaryCmd extends BaseDictionaryCmd {

    public DummyBaseDictionaryCmd(GloboDictionaryService globoDictionaryService) {
        this.globoDictionaryService = globoDictionaryService;
    }

    @Override
    GloboDictionaryService.GloboDictionaryEntityType getEntity() {
        return GloboDictionaryService.GloboDictionaryEntityType.BUSINESS_SERVICE;
    }

    @Override
    public String getCommandName() {
        return "listdummyresponse";
    }

    @Override
    String getResponseName() {
        return "response";
    }
}