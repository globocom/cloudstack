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

import com.cloud.globodictionary.GloboDictionaryService;
import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.utils.component.PluggableService;
import com.cloud.globodictionary.apiclient.GloboDictionaryAPIClient;
import org.apache.cloudstack.api.command.user.globodictionary.ListBusinessServicesCmd;
import org.apache.cloudstack.api.command.user.globodictionary.ListClientsCmd;
import org.apache.cloudstack.api.command.user.globodictionary.ListComponentsCmd;
import org.apache.cloudstack.api.command.user.globodictionary.ListSubComponentsCmd;
import org.apache.cloudstack.api.command.user.globodictionary.ListProductsCmd;

import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GloboDictionaryManager implements GloboDictionaryService, PluggableService {

    @Inject
    private GloboDictionaryAPIClient dictionaryAPIClient;


    public GloboDictionaryManager() {
    }

    public GloboDictionaryManager(GloboDictionaryAPIClient dictionaryAPIClient) {
        this.dictionaryAPIClient = dictionaryAPIClient;
    }

    @Override
    public List<GloboDictionaryEntity> list(GloboDictionaryEntityType type) {
        return this.filterActives(dictionaryAPIClient.list(type));
    }

    @Override
    public GloboDictionaryEntity get(GloboDictionaryEntityType type, String id) {
        GloboDictionaryEntity businessService = dictionaryAPIClient.get(type, id);
        if (businessService != null && businessService.isActive()) {
            return businessService;
        }
        return null;
    }

    @Override
    public List<GloboDictionaryEntity> listByExample(GloboDictionaryEntityType type, Map<String, String> example) {
        return dictionaryAPIClient.listByExample(type, example);
    }

    private List<GloboDictionaryEntity> filterActives(List<GloboDictionaryEntity> entities){
        List<GloboDictionaryEntity> actives = new ArrayList<>();
        for(GloboDictionaryEntity entity : entities){
            if(entity.isActive()){
                actives.add(entity);
            }
        }
        return actives;
    }

    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(ListBusinessServicesCmd.class);
        cmdList.add(ListClientsCmd.class);
        cmdList.add(ListComponentsCmd.class);
        cmdList.add(ListSubComponentsCmd.class);
        cmdList.add(ListProductsCmd.class);
        return cmdList;
    }
}
