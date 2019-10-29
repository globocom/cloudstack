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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GloboDictionaryAPIClientImpl implements GloboDictionaryAPIClient, Configurable {

    public static final Logger s_logger = Logger.getLogger(GloboDictionaryAPIClientImpl.class);

    private static final ConfigKey<String> GloboDictionaryEndpoint = new ConfigKey<>("Globo Dictionary", String.class, "globodictionary.api.endpoint", "",
            "Globo Dictionary API Endpoint", true, ConfigKey.Scope.Global);

    private final HttpClient httpClient;
    private static final String API_ID_QUERY_PARAMETER = "id_service_now";

    @Inject
    private GloboDictionaryApiUnmarshaller globoDictionaryApiUnmarshaller;

    public GloboDictionaryAPIClientImpl() {
        this.httpClient = new HttpClient(new MultiThreadedHttpConnectionManager());
    }

    public GloboDictionaryAPIClientImpl(HttpClient httpClient){
        this.httpClient = httpClient;
    }

    @Override
    public GloboDictionaryEntity get(GloboDictionaryService.GloboDictionaryEntityType type, String id) {
        return this.getDictionaryEntity(type, id);
    }

    @Override
    public List<GloboDictionaryEntity> list(GloboDictionaryService.GloboDictionaryEntityType type) {
        return this.listDictionaryEntity(type);
    }

    @Override
    public List<GloboDictionaryEntity> listByExample(GloboDictionaryService.GloboDictionaryEntityType type, Map<String, String> example) {
        String queryString = "";

        String querySymbol = "";
        for (Map.Entry<String, String> entry : example.entrySet()) {
                queryString += String.format("%s%s=%s", querySymbol, entry.getKey(), entry.getValue());
                querySymbol = "&";
        }

        String response = this.makeHttpRequest(type, queryString);
        return globoDictionaryApiUnmarshaller.unmarshal(response);
    }

    private List<GloboDictionaryEntity> listDictionaryEntity(GloboDictionaryService.GloboDictionaryEntityType entityType) {
        String response = this.makeHttpRequest(entityType);
        return globoDictionaryApiUnmarshaller.unmarshal(response);
    }

    private GloboDictionaryEntity getDictionaryEntity(GloboDictionaryService.GloboDictionaryEntityType entityType, String id) {
        String response = this.makeHttpRequest(entityType, API_ID_QUERY_PARAMETER + "=" + id);
        List<GloboDictionaryEntity> entities = new Gson().fromJson(response, new TypeToken<ArrayList<GloboDictionaryEntityVO>>() { }.getType());
        if(entities != null && entities.size() > 0){
            return entities.get(0);
        }
        return null;
    }

    private String makeHttpRequest(GloboDictionaryService.GloboDictionaryEntityType entityType) {
        return this.makeHttpRequest(entityType, "");
    }

    private String makeHttpRequest(GloboDictionaryService.GloboDictionaryEntityType entityType, String queryString) {
        GetMethod getMethod = this.createRequest(entityType, queryString);
        try {
            int status = this.httpClient.executeMethod(getMethod);
            if(status == HttpStatus.SC_OK) {
                return getMethod.getResponseBodyAsString();
            }else{
                s_logger.error("[DictionaryAPI] Invalid API status code " + status);
                s_logger.error("[DictionaryAPI] Invalid API response body " + getMethod.getResponseBodyAsString());
                throw new CloudRuntimeException("Error listing " + entityType.getFriendlyName());
            }
        } catch (IOException e) {
            s_logger.error("[DictionaryAPI] Communication failure", e);
            throw new CloudRuntimeException("Error listing " + entityType.getFriendlyName(), e);
        } finally {
            getMethod.releaseConnection();
        }
    }

    protected GetMethod createRequest(GloboDictionaryService.GloboDictionaryEntityType entityType, String queryString) {
        return new GetMethod(GloboDictionaryEndpoint.value() + entityType.getUri() + "?" + queryString);
    }

    @Override
    public String getConfigComponentName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {GloboDictionaryEndpoint};
    }

    public void setGloboDictionaryApiUnmarshaller(GloboDictionaryApiUnmarshaller globoDictionaryApiUnmarshaller) {
        this.globoDictionaryApiUnmarshaller = globoDictionaryApiUnmarshaller;
    }

}
