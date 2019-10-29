/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.cloud.globodictionary;

import java.util.List;
import java.util.Map;

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

    List<GloboDictionaryEntity> listByExample(GloboDictionaryEntityType type, Map<String, String> example);

}
