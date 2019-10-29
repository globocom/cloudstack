package org.apache.cloudstack.globoconfig;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import java.util.Objects;


/**
 * Created by sinval.neto on 7/15/16.
 */
@Entity
@Table(name = "globo_resource_configuration")
public class GloboResourceConfigurationVO implements GloboResourceConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "resource_type")
    private GloboResourceType resourceType;

    @Column(name = "resource_uuid")
    private String resourceUuid;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "key")
    private GloboResourceKey key;

    @Column(name = "value", length = 255)
    private String value;

    public GloboResourceConfigurationVO(){}

    public GloboResourceConfigurationVO(GloboResourceType resourceType, String resourceUuid, GloboResourceKey key, String value){
        this.resourceType = resourceType;
        this.resourceUuid = resourceUuid;
        this.key = key;
        this.value = value;
    }

    @Override
    public String getInstance() {
        return null;
    }

    @Override
    public Long getId() {
        return this.id;
    }

    @Override
    public GloboResourceType getResourceType() {
        return this.resourceType;
    }

    @Override
    public String getResourceUuid() {
        return this.resourceUuid;
    }

    @Override
    public GloboResourceKey getKey() {
        return this.key;
    }

    @Override
    public String getValue() {
        return this.value;
    }

    @Override
    public void setValue(String value) {this.value = value; }

    public boolean getBooleanValue() {
        return Boolean.valueOf(this.getValue());
    }

    public void setBoolValue(boolean value) {
        this.setValue(Boolean.toString(value));
    }

    public void setId(Long id) {
        this.id = id;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GloboResourceConfigurationVO that = (GloboResourceConfigurationVO) o;
        return Objects.equals(id, that.id) &&
                resourceType == that.resourceType &&
                Objects.equals(resourceUuid, that.resourceUuid) &&
                key == that.key &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, resourceType, resourceUuid, key, value);
    }
}
