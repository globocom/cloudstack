package com.globo.globocusteio.cloudstack.client;

import com.globo.globocusteio.cloudstack.client.model.BusinessService;
import com.globo.globocusteio.cloudstack.client.model.Client;
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
@Local(value = CusteioClient.class)
public class CusteioClientImpl implements CusteioClient, Configurable {

    public static final Logger s_logger = Logger.getLogger(CusteioClientImpl.class);

    private static final String BUSINESS_SERVICE_URI = "/servicos-de-negocio";
    private static final String CLIENT_URI = "/clientes";

    private static final ConfigKey<String> GloboCusteioEndpoint = new ConfigKey<>("Custeio", String.class, "globocusteio.api.endpoint", "",
            "Custeio Dictionary API Endpoint", true, ConfigKey.Scope.Global);

    public List<BusinessService> listBusinessServices() {
        String response = this.makeHttpRequest(BUSINESS_SERVICE_URI);
        return new Gson().fromJson(response, new TypeToken<ArrayList<BusinessService>>() { }.getType());
    }

    public BusinessService getBusinessService(String id) {
        for(BusinessService businessService : this.listBusinessServices()){
            if(businessService.getId().equals(id) && businessService.isActive()){
                return businessService;
            }
        }
        return null;
    }

    public List<Client> listClients() {
        String response = this.makeHttpRequest(CLIENT_URI);
        return new Gson().fromJson(response, new TypeToken<ArrayList<Client>>(){ }.getType());
    }

    public Client getClient(String id) {
        for(Client client : this.listClients()){
            if(client.getId().equals(id) && client.isActive()){
                return client;
            }
        }
        return null;
    }

    //TODO: logs
    private String makeHttpRequest(String uri){
        HttpClient httpclient = new HttpClient();
        GetMethod getMethod = new GetMethod(GloboCusteioEndpoint.value() + uri);
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
        return new ConfigKey<?>[] { GloboCusteioEndpoint };
    }
}
