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
import javax.ejb.Local;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Component
@Local({GloboDictionaryService.class, PluggableService.class})
public class GloboDictionaryManager implements GloboDictionaryService, PluggableService {

    @Inject
    private GloboDictionaryAPIClient dictionaryAPIClient;

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
