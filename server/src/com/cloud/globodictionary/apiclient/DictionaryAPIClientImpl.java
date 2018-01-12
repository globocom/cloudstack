package com.cloud.globodictionary.apiclient;

import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.globodictionary.apiclient.model.GloboDictionaryEntityVO;
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
    public List<GloboDictionaryEntity> listBusinessServices() {
        return this.listDictionaryEntity(GloboDictionaryEntityType.BUSINESS_SERVICE);
    }

    @Override
    public GloboDictionaryEntity getBusinessService(String id) {
        return this.getDictionaryEntity(GloboDictionaryEntityType.BUSINESS_SERVICE, id);
    }

    @Override
    public List<GloboDictionaryEntity> listClients() {
        return this.listDictionaryEntity(GloboDictionaryEntityType.CLIENT);
    }

    @Override
    public GloboDictionaryEntity getClient(String id) {
        return this.getDictionaryEntity(GloboDictionaryEntityType.CLIENT, id);
    }

    private List<GloboDictionaryEntity> listDictionaryEntity(GloboDictionaryEntityType entityType){
        String response = this.makeHttpRequest(entityType.getUri());
        return new Gson().fromJson(response, new TypeToken<ArrayList<GloboDictionaryEntityVO>>() { }.getType());
    }

    private GloboDictionaryEntity getDictionaryEntity(GloboDictionaryEntityType entityType, String id) {
        List<GloboDictionaryEntity> entities = this.listDictionaryEntity(entityType);
        for(GloboDictionaryEntity entity : entities){
            if(entity.getId().equals(id) && entity.isActive()){
                return entity;
            }
        }
        return null;
    }

    //TODO: logs
    private String makeHttpRequest(String uri){
        HttpClient httpclient = new HttpClient();
        GetMethod getMethod = new GetMethod(GloboDictionaryEndpoint.value() + uri);
        try {
            httpclient.executeMethod(getMethod);
            int status = httpclient.executeMethod(getMethod);
            if(status == HttpStatus.SC_OK) {
                return getMethod.getResponseBodyAsString();
            }else{
                //TODO: especificar exception
                throw new RuntimeException();
            }
            //TODO: melhorar tratamento
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            getMethod.releaseConnection();
        }
        return null;
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
