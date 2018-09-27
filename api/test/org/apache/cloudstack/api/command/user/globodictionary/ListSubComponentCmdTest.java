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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class ListSubComponentCmdTest {

    @Mock
    GloboDictionaryService globoDictionaryService;
    ListSubComponentsCmd cmd;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testListWhitoutComponentId() throws Exception {
        GloboDictionaryEntity mockEntity = mock(GloboDictionaryEntity.class);
        when(globoDictionaryService.list(GloboDictionaryService.GloboDictionaryEntityType.SUB_COMPONENT)).thenReturn(Collections.singletonList(mockEntity));

        cmd = new DummySubComponentListCmd(globoDictionaryService, false, null);
        cmd.execute();

        ListResponse responses = (ListResponse) cmd.getResponseObject();
        GloboDictionaryResponse response = ((GloboDictionaryResponse) responses.getResponses().get(0));

        assertEquals(new Integer(1), responses.getCount());
        assertEquals("listdummyresponse", responses.getResponseName());
        assertEquals("response", response.getObjectName());

        verify(globoDictionaryService).list(GloboDictionaryService.GloboDictionaryEntityType.SUB_COMPONENT);
    }

    @Test
    public void testListWithComponentRequiredButWithoutComponentId() throws Exception {
        cmd = new DummySubComponentListCmd(globoDictionaryService, true, null);
        cmd.execute();

        ListResponse responses = (ListResponse) cmd.getResponseObject();

        assertEquals(new Integer(0), responses.getCount());
        assertEquals("listdummyresponse", responses.getResponseName());
    }

    @Test
    public void testListWithComponentId() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("componente_id", "11aabb");

        GloboDictionaryEntity mockEntity = mock(GloboDictionaryEntity.class);
        when(globoDictionaryService.listByExample(GloboDictionaryService.GloboDictionaryEntityType.SUB_COMPONENT, params)).thenReturn(Collections.singletonList(mockEntity));

        cmd = new DummySubComponentListCmd(globoDictionaryService, true, "11aabb");
        cmd.execute();

        ListResponse responses = (ListResponse) cmd.getResponseObject();
        GloboDictionaryResponse response = ((GloboDictionaryResponse) responses.getResponses().get(0));

        assertEquals(new Integer(1), responses.getCount());
        assertEquals("listdummyresponse", responses.getResponseName());
        assertEquals("response", response.getObjectName());

        verify(globoDictionaryService).listByExample(GloboDictionaryService.GloboDictionaryEntityType.SUB_COMPONENT, params);
    }

}

class DummySubComponentListCmd extends ListSubComponentsCmd {

    public DummySubComponentListCmd(GloboDictionaryService globoDictionaryService, boolean required, String componentId) {
        this.componentId = componentId;
        this.componentRequired = required;
        this.globoDictionaryService = globoDictionaryService;
    }

    @Override
    GloboDictionaryService.GloboDictionaryEntityType getEntity() {
        return GloboDictionaryService.GloboDictionaryEntityType.SUB_COMPONENT;
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

