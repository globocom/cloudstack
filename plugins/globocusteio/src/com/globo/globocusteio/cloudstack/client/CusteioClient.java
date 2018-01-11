package com.globo.globocusteio.cloudstack.client;

import com.globo.globocusteio.cloudstack.client.model.BusinessService;
import com.globo.globocusteio.cloudstack.client.model.Client;
import java.util.List;

public interface CusteioClient{

    public List<BusinessService> listBusinessServices();
    public BusinessService getBusinessService(String id);
    public List<Client> listClients();
    public Client getClient(String id);

}