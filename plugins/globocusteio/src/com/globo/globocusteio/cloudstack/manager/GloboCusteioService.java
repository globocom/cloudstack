package com.globo.globocusteio.cloudstack.manager;

import com.globo.globocusteio.cloudstack.client.model.BusinessService;
import com.globo.globocusteio.cloudstack.client.model.Client;

import java.util.List;

public interface GloboCusteioService {

    List<BusinessService> listBusinessServices();

    BusinessService getBusinessService(String id);

    List<Client> listClients();

    Client getClient(String id);
}
