package org.apache.cloudstack.api.response;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.BaseResponse;

/**
 * Created by sinval.neto on 7/20/16.
 */
public class RegisterDnsForLoadBalancerResponse extends BaseResponse {

    @SerializedName("result")
    @Param(description = "the result of the operation")
    private String result;

    @SerializedName("id")
    @Param(description = "the id of the resource")
    private String id;

    @SerializedName("resourcetype")
    @Param(description = "the resourcetype of the resource")
    private String resourceType;

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getResourceType() {
        return this.resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
}
