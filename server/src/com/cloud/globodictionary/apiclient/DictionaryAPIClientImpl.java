package com.cloud.globodictionary.apiclient;

import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.globodictionary.apiclient.model.GloboDictionaryEntityVO;
import com.cloud.globodictionary.exception.InvalidDictionaryAPIResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.ejb.Local;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Component
@Local(value = DictionaryAPIClient.class)
public class DictionaryAPIClientImpl implements DictionaryAPIClient, Configurable {

    public static final Logger s_logger = Logger.getLogger(DictionaryAPIClientImpl.class);

    private static final ConfigKey<String> GloboDictionaryEndpoint = new ConfigKey<>("Globo Dictionary", String.class, "globodictionary.api.endpoint", "",
            "Globo Dictionary API Endpoint", true, ConfigKey.Scope.Global);

    @Override
    public List<GloboDictionaryEntity> listBusinessServices() throws InvalidDictionaryAPIResponse {
        return this.listDictionaryEntity(GloboDictionaryEntityType.BUSINESS_SERVICE);
    }

    @Override
    public GloboDictionaryEntity getBusinessService(String id) throws InvalidDictionaryAPIResponse {
        return this.getDictionaryEntity(GloboDictionaryEntityType.BUSINESS_SERVICE, id);
    }

    @Override
    public List<GloboDictionaryEntity> listClients() throws InvalidDictionaryAPIResponse {
        return this.listDictionaryEntity(GloboDictionaryEntityType.CLIENT);
    }

    @Override
    public GloboDictionaryEntity getClient(String id) throws InvalidDictionaryAPIResponse {
        return this.getDictionaryEntity(GloboDictionaryEntityType.CLIENT, id);
    }

    private List<GloboDictionaryEntity> listDictionaryEntity(GloboDictionaryEntityType entityType) throws InvalidDictionaryAPIResponse {
        String response = this.makeHttpRequest(entityType.getUri());
        return new Gson().fromJson(response, new TypeToken<ArrayList<GloboDictionaryEntityVO>>() { }.getType());
    }

    private GloboDictionaryEntity getDictionaryEntity(GloboDictionaryEntityType entityType, String id) throws InvalidDictionaryAPIResponse {
        String response = this.makeHttpRequest(entityType.getUri(), "id_service_now=" + id);
        List<GloboDictionaryEntity> entities = new Gson().fromJson(response, new TypeToken<ArrayList<GloboDictionaryEntityVO>>() { }.getType());
        if(entities != null && entities.size() > 0){
            return entities.get(0);
        }
        return null;
    }

    private String makeHttpRequest(String uri) throws InvalidDictionaryAPIResponse {
        return this.makeHttpRequest(uri, "");
    }

    private String makeHttpRequest(String uri, String queryString) throws InvalidDictionaryAPIResponse {
        HttpClient httpclient = new HttpClient();
        GetMethod getMethod = new GetMethod(GloboDictionaryEndpoint.value() + uri + "?" + queryString);
        try {
            httpclient.executeMethod(getMethod);
            int status = httpclient.executeMethod(getMethod);
            if(status == HttpStatus.SC_OK) {
                return getMethod.getResponseBodyAsString();
            }else{
                s_logger.error("[DictionaryAPI] Invalid API status code " + status);
                s_logger.error("[DictionaryAPI] Invalid API response body " + getMethod.getResponseBodyAsString());
                throw new InvalidDictionaryAPIResponse();
            }
        } catch (IOException e) {
            s_logger.error("[DictionaryAPI] Communication failure", e);
            throw new InvalidDictionaryAPIResponse(e);
        } finally {
            getMethod.releaseConnection();
        }
    }

    @Override
    public String getConfigComponentName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {GloboDictionaryEndpoint};
    }
}
