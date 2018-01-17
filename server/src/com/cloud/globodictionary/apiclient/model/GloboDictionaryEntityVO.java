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
package com.cloud.globodictionary.apiclient.model;

import com.cloud.globodictionary.GloboDictionaryEntity;
import com.google.gson.annotations.SerializedName;

public class GloboDictionaryEntityVO implements GloboDictionaryEntity {

    @SerializedName("id_service_now")
    protected String id;

    @SerializedName("nome")
    protected String name;

    @SerializedName("status")
    protected String status;

    public GloboDictionaryEntityVO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return this.getStatus().equals("Ativo") || this.getStatus().equals("Em uso");
    }

    public int compareTo(GloboDictionaryEntity entity) {
        return this.name.compareTo(entity.getName());
    }
}
