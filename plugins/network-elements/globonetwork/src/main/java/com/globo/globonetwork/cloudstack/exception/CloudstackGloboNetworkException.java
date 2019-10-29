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
package com.globo.globonetwork.cloudstack.exception;

import com.cloud.utils.exception.CloudRuntimeException;

public class CloudstackGloboNetworkException extends CloudRuntimeException {

    private static final long serialVersionUID = 678159764759471937L;

    private int napiCode;
    private String napiDescription;
    private String context;

    public CloudstackGloboNetworkException(int napiCode, String napiDescription) {
        super(napiCode + " - " + napiDescription);
        this.napiCode = napiCode;
        this.napiDescription = napiDescription;
    }
    public CloudstackGloboNetworkException(int napiCode, String napiDescription, String context) {
        super(napiCode + " - " + napiDescription);
        this.napiCode = napiCode;
        this.napiDescription = napiDescription;
        this.context = context;
    }

    public CloudstackGloboNetworkException(String message) {
        super(message);
    }

    public int getNapiCode() {
        return napiCode;
    }

    public String getNapiDescription() {
        return napiDescription;
    }

    public String getContext() { return this.context; }
}
