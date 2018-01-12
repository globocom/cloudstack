package com.cloud.globodictionary.apiclient;

import com.cloud.globodictionary.GloboDictionaryEntity;
import java.util.List;

public interface DictionaryAPIClient {

    enum GloboDictionaryEntityType {
        CLIENT("/clientes"), BUSINESS_SERVICE("/servicos-de-negocio");

        private final String uri;

        GloboDictionaryEntityType(String uri) {
            this.uri = uri;
        }

        public String getUri() {
            return uri;
        }
    }

    List<GloboDictionaryEntity> listBusinessServices();

    GloboDictionaryEntity getBusinessService(String id);

    List<GloboDictionaryEntity> listClients();

    GloboDictionaryEntity getClient(String id);

}