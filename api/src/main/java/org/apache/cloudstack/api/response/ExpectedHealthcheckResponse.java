package org.apache.cloudstack.api.response;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;

public class ExpectedHealthcheckResponse extends BaseResponse implements Comparable<ExpectedHealthcheckResponse> {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the Pool ID")
    private Long id;

    @SerializedName("expected")
    @Param(description = "Expected healthcheck")
    private String expected;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    @Override
    public int compareTo(ExpectedHealthcheckResponse o) {
        return this.getExpected().compareToIgnoreCase(o.getExpected());
    }
}
