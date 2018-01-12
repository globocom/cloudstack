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
        return this.getStatus().equals("Ativo");
    }
}
