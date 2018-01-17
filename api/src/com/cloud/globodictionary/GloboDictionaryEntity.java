package com.cloud.globodictionary;

public interface GloboDictionaryEntity extends Comparable<GloboDictionaryEntity> {

    String getId();

    void setId(String id);

    String getName();

    void setName(String name);

    String getStatus();

    void setStatus(String status);

    boolean isActive();
}
