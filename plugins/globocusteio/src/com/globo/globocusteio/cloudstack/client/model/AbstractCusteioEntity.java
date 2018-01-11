package com.globo.globocusteio.cloudstack.client.model;


import com.google.gson.annotations.SerializedName;

public abstract class AbstractCusteioEntity {

    @SerializedName("id_service_now")
    protected String id;

    @SerializedName("nome")
    protected String name;

    @SerializedName("status")
    protected String status;

    public AbstractCusteioEntity() {
    }

    public AbstractCusteioEntity(String id, String name, String status) {
        this.id = id;
        this.name = name;
        this.status = status;
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
