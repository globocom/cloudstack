package com.globo.globocusteio.cloudstack.manager;

import com.cloud.utils.component.PluggableService;
import com.globo.globocusteio.cloudstack.api.ListBusinessServicesCmd;
import com.globo.globocusteio.cloudstack.api.ListClientsCmd;
import com.globo.globocusteio.cloudstack.client.CusteioClient;
import com.globo.globocusteio.cloudstack.client.model.BusinessService;
import com.globo.globocusteio.cloudstack.client.model.Client;
import org.springframework.stereotype.Component;

import javax.ejb.Local;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Component
@Local({GloboCusteioService.class, PluggableService.class})
public class GloboCusteioManager implements GloboCusteioService, PluggableService {

    @Inject
    private CusteioClient custeioClient;

    public List<BusinessService> listBusinessServices() {
        return custeioClient.listBusinessServices();
    }

    public BusinessService getBusinessService(String id) {
        return custeioClient.getBusinessService(id);
    }

    public List<Client> listClients() {
        return custeioClient.listClients();
    }

    public Client getClient(String id) {
        return custeioClient.getClient(id);
    }

    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(ListBusinessServicesCmd.class);
        cmdList.add(ListClientsCmd.class);
        return cmdList;
    }
}
