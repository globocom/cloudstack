package org.apache.cloudstack.api.response;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;

public class BusinessServiceResponse extends BaseResponse {

    @SerializedName(ApiConstants.ID)
    @Param(description = "the business service id")
    private String id;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "the business service name")
    private String name;

    public BusinessServiceResponse(String id, String name) {
        this.id = id;
        this.name = name;
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
}
