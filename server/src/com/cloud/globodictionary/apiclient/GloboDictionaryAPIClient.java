package com.cloud.globodictionary.apiclient;

import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.globodictionary.GloboDictionaryService;

import java.util.List;

public interface GloboDictionaryAPIClient {

    GloboDictionaryEntity get(GloboDictionaryService.GloboDictionaryEntityType type, String id);

    List<GloboDictionaryEntity> list(GloboDictionaryService.GloboDictionaryEntityType type);
}