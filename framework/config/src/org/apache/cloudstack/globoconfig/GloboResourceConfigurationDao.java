package org.apache.cloudstack.globoconfig;

import com.cloud.utils.db.GenericDao;

import java.util.List;
import java.util.Map;



/**
 * Created by sinval.neto on 7/15/16.
 */
public interface GloboResourceConfigurationDao extends GenericDao<GloboResourceConfigurationVO, String> {
    Map<String, String> getConfiguration(long resourceId);

    List<GloboResourceConfigurationVO> getConfiguration(GloboResourceType resourceType, String resourceUuid);

    List<GloboResourceConfigurationVO> getConfiguration(GloboResourceType resourceType, String resourceUuid, GloboResourceKey key);

    public GloboResourceConfigurationVO getFirst(GloboResourceType resourceType,
                                                 String resourceUuid, GloboResourceKey key);

    public void removeConfigurations(String uuid, GloboResourceType loadBalancer);

    public boolean updateValue(GloboResourceConfiguration config);


    public List<GloboResourceConfigurationVO> getConfigsByValue(GloboResourceType type, GloboResourceKey key, String value);
}
