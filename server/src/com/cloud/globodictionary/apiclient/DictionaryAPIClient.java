package com.cloud.globodictionary.apiclient;

import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.globodictionary.exception.InvalidDictionaryAPIResponse;

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

    List<GloboDictionaryEntity> listBusinessServices() throws InvalidDictionaryAPIResponse;

    GloboDictionaryEntity getBusinessService(String id) throws InvalidDictionaryAPIResponse;

    List<GloboDictionaryEntity> listClients() throws InvalidDictionaryAPIResponse;

    GloboDictionaryEntity getClient(String id) throws InvalidDictionaryAPIResponse;

}