package com.cloud.globodictionary;

import java.util.List;

public interface GloboDictionaryService {

    enum GloboDictionaryEntityType {
        CLIENT("/clientes", "Clients"), BUSINESS_SERVICE("/servicos-de-negocio", "Business Services"),
        COMPONENT("/componentes", "Components"), SUB_COMPONENT("/sub-componentes", "Sub-components"),
        PRODUCT("/produtos", "Products");

        private final String uri;
        private final String friendlyName;

        GloboDictionaryEntityType(String uri, String friendlyName) {
            this.uri = uri;
            this.friendlyName = friendlyName;
        }

        public String getUri() {
            return uri;
        }

        public String getFriendlyName() {
            return friendlyName;
        }
    }

    List<GloboDictionaryEntity> list(GloboDictionaryEntityType type);

    GloboDictionaryEntity get(GloboDictionaryEntityType type, String id);
}
