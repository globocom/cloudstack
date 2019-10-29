package com.cloud.globodictionary.apiclient;

import com.cloud.globodictionary.GloboDictionaryEntity;
import com.cloud.globodictionary.apiclient.model.GloboDictionaryEntityVO;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class GloboDictionaryApiUnmarshallerImpl implements GloboDictionaryApiUnmarshaller {

    public List<GloboDictionaryEntity> unmarshal(String response) {
        List<GloboDictionaryEntity> globoDictionaryEntities = new Gson().fromJson(
            response, new TypeToken<ArrayList<GloboDictionaryEntityVO>>() { }.getType()
        );
        Collections.sort(globoDictionaryEntities);
        return globoDictionaryEntities;
    }

}
