package com.cloud.globodictionary;

import java.util.List;

public interface GloboDictionaryService {

    List<GloboDictionaryEntity> listBusinessServices();

    GloboDictionaryEntity getBusinessService(String id);

    List<GloboDictionaryEntity> listClients();

    GloboDictionaryEntity getClient(String id);
}
