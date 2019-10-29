package com.globo.globodns.cloudstack.element;


public class GloboDnsTO {
    private Long zoneId;
    private String resourceId;
    private String domain;
    private String record;
    private String ipAddress;

    public GloboDnsTO(Long zoneId, String resourceId, String record, String domain, String ipAddress) {
        this.zoneId = zoneId;
        this.resourceId = resourceId;
        this.domain = domain;
        this.record = record;
        this.ipAddress = ipAddress;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getRecord() {
        return record;
    }

    public void setRecord(String record) {
        this.record = record;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
