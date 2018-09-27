package com.cloud.globodictionary.apiclient;

import com.cloud.globodictionary.GloboDictionaryEntity;

import java.util.List;

public interface GloboDictionaryApiUnmarshaller {

    public List<GloboDictionaryEntity> unmarshal(String response);

}
