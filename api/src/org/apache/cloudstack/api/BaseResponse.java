// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.api;

import com.google.gson.annotations.SerializedName;

import com.cloud.serializer.Param;
import org.apache.cloudstack.context.CallContext;

public abstract class BaseResponse implements ResponseObject {
    private transient String responseName;
    private transient String objectName;

    @SerializedName(ApiConstants.JOB_ID)
    @Param(description = "the UUID of the latest async job acting on this object")
    protected String jobId;

    @SerializedName(ApiConstants.JOB_STATUS)
    @Param(description = "the current status of the latest async job acting on this object")
    private Integer jobStatus;

    @SerializedName(ApiConstants.CONTEXT)
    @Param(description = "context error")
    private String context;

    public BaseResponse() {
    }

    public BaseResponse(final String objectName) {
        this.objectName = objectName;
    }

    @Override
    public final String getResponseName() {
        return responseName;
    }

    @Override
    public final void setResponseName(String responseName) {
        this.responseName = responseName;
    }

    @Override
    public final String getObjectName() {
        return objectName;
    }

    @Override
    public final void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    @Override
    public String getObjectId() {
        return null;
    }

    @Override
    public String getJobId() {
        return jobId;
    }

    @Override
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    @Override
    public Integer getJobStatus() {
        return jobStatus;
    }

    @Override
    public void setJobStatus(Integer jobStatus) {
        this.jobStatus = jobStatus;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }


    public void buildCurrentContext() {
        CallContext context = CallContext.current();
        if (context != null) {
            setContext(context.getNdcContext());
        }
    }
}
