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

package com.globo.globonetwork.cloudstack.api;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;

public class GloboNetworkPoolOptionExternalResponse extends BaseResponse {

    @SerializedName(ApiConstants.ID)
    @Param(description = "ID of the Pool option")
    private Long id;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "Name of the Pool option")
    private String name;

    public GloboNetworkPoolOptionExternalResponse(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
