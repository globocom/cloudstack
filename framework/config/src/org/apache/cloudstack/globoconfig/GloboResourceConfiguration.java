package org.apache.cloudstack.globoconfig;

/**
 * Created by sinval.neto on 7/13/16.
 */
public interface GloboResourceConfiguration {

    String getInstance();

    Long getId();

    GloboResourceType getResourceType();

    Long getResourceId();

    GloboResourceKey getKey();

    String getValue();

    void setValue(String value);

}
