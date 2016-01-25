package com.cloud.api.query.vo;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "view_globo_vm")
public class GloboVmVO extends BaseViewVO{

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private long id;

    @Column(name = "uuid", updatable = false, nullable = false, length = 255)
    private String uuid = null;

    @Column(name = "display_name", updatable = false, nullable = false, length = 255)
    private String displayName = null;

    @Column(name = "instance_name", updatable = false, nullable = false, length = 255)
    private String instanceName = null;

    @Column(name = "state", updatable = false, nullable = false, length = 255)
    private String state = null;

    @Column(name = "ha_enabled", updatable = false, nullable = false)
    private boolean haEnabled;

    @Column(name = "account_id")
    private long accountId;

    @Column(name = "domain_id")
    private long domainId;


    @Column(name = "os_name", updatable = false, nullable = false, length = 255)
    private String osName;

    @Column(name = "dc_name", updatable = false, nullable = false, length = 255)
    private String dcName;


    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean isHaEnabled() {
        return haEnabled;
    }

    public void setHaEnabled(boolean haEnabled) {
        this.haEnabled = haEnabled;
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public long getDomainId() {
        return domainId;
    }

    public void setDomainId(long domainId) {
        this.domainId = domainId;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getDcName() {
        return dcName;
    }

    public void setDcName(String dcName) {
        this.dcName = dcName;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public long getId() {
        return id;
    }
}
