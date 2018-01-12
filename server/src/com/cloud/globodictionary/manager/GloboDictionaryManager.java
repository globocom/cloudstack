package com.cloud.globodictionary.manager;

import com.cloud.globodictionary.GloboDictionaryService;
import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.utils.component.PluggableService;
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
        return dictionaryAPIClient.listBusinessServices();
    }

    public GloboDictionaryEntity getBusinessService(String id) {
        return dictionaryAPIClient.getBusinessService(id);
    }

    public List<GloboDictionaryEntity> listClients() {
        return dictionaryAPIClient.listClients();
    }

    public GloboDictionaryEntity getClient(String id) {
        return dictionaryAPIClient.getClient(id);
    }

    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(ListBusinessServicesCmd.class);
        cmdList.add(ListClientsCmd.class);
        return cmdList;
    }
}
