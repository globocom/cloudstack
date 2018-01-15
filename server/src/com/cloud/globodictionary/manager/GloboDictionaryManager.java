package com.cloud.globodictionary.manager;

import com.cloud.globodictionary.GloboDictionaryService;
import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.globodictionary.exception.InvalidDictionaryAPIResponse;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.command.user.globodictionary.ListBusinessServicesCmd;
import org.apache.cloudstack.api.command.user.globodictionary.ListClientsCmd;
import com.cloud.globodictionary.apiclient.DictionaryAPIClient;
import org.springframework.stereotype.Component;

import javax.ejb.Local;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Component
@Local({GloboDictionaryService.class, PluggableService.class})
public class GloboDictionaryManager implements GloboDictionaryService, PluggableService {

    @Inject
    private DictionaryAPIClient dictionaryAPIClient;

    public List<GloboDictionaryEntity> listBusinessServices() {
        List<GloboDictionaryEntity> businessServices = null;
        try {
            return filterActives(dictionaryAPIClient.listBusinessServices());
        } catch (InvalidDictionaryAPIResponse e) {
            throw new CloudRuntimeException("Error listing business services", e);
        }
    }

    public GloboDictionaryEntity getBusinessService(String id) {
        try {
            GloboDictionaryEntity businessService = dictionaryAPIClient.getBusinessService(id);
            if (businessService != null && businessService.isActive()) {
                return businessService;
            }
        } catch (InvalidDictionaryAPIResponse e) {
            throw new CloudRuntimeException("Error listing business services", e);
        }
        return null;
    }

    public List<GloboDictionaryEntity> listClients() {
        try {
            return filterActives(dictionaryAPIClient.listClients());
        } catch (InvalidDictionaryAPIResponse e) {
            throw new CloudRuntimeException("Error listing clients", e);
        }
    }

    public GloboDictionaryEntity getClient(String id) {
        try {
            GloboDictionaryEntity client = dictionaryAPIClient.getClient(id);
            if(client != null && client.isActive()){
                return client;
            }
        } catch (InvalidDictionaryAPIResponse e) {
            throw new CloudRuntimeException("Error listing clients", e);
        }
        return null;
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
        return cmdList;
    }
}
