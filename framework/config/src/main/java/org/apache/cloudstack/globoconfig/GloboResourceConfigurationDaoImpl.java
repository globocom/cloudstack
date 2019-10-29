package org.apache.cloudstack.globoconfig;

import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.exception.CloudRuntimeException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Created by sinval.neto on 7/15/16.
 */
@Component
@DB()
public class GloboResourceConfigurationDaoImpl extends GenericDaoBase<GloboResourceConfigurationVO, String> implements GloboResourceConfigurationDao {
    private static final Logger s_logger = Logger.getLogger(GloboResourceConfigurationDaoImpl.class);

    private final SearchBuilder<GloboResourceConfigurationVO> ListByResourceId;



    public GloboResourceConfigurationDaoImpl() {
        ListByResourceId = createSearchBuilder();
        ListByResourceId.and("resourceUuid", ListByResourceId.entity().getResourceUuid(), SearchCriteria.Op.EQ);
        ListByResourceId.and("resourceType", ListByResourceId.entity().getResourceType(), SearchCriteria.Op.EQ);
        ListByResourceId.and("value", ListByResourceId.entity().getValue(), SearchCriteria.Op.EQ);
        ListByResourceId.and("key", ListByResourceId.entity().getKey(), SearchCriteria.Op.EQ);

        ListByResourceId.done();
    }

    @Override
    public Map<String, String> getConfiguration(long resourceId) {
        return null;
    }

    @Override
    public List<GloboResourceConfigurationVO> getConfiguration(GloboResourceType resourceType, String resourceUuid) {
        SearchCriteria<GloboResourceConfigurationVO> sc = ListByResourceId.create();
        sc.setParameters("resourceType", resourceType);
        sc.setParameters("resourceUuid", resourceUuid);
        return listBy(sc);
    }

    @Override
    public List<GloboResourceConfigurationVO> getConfiguration(GloboResourceType resourceType,
                                                               String resourceUuid, GloboResourceKey key) {
        SearchCriteria<GloboResourceConfigurationVO> sc = ListByResourceId.create();
        sc.setParameters("resourceType", resourceType);
        sc.setParameters("resourceUuid", resourceUuid);
        sc.setParameters("key", key);
        return listBy(sc);
    }

    @Override
    public GloboResourceConfigurationVO getFirst(GloboResourceType resourceType,
                                                               String resourceUuid, GloboResourceKey key) {
        SearchCriteria<GloboResourceConfigurationVO> sc = ListByResourceId.create();
        sc.setParameters("resourceType", resourceType);
        sc.setParameters("resourceUuid", resourceUuid);
        sc.setParameters("key", key);

        List<GloboResourceConfigurationVO> configs = listBy(sc);
        if (configs.size() > 0)
            return configs.get(0);
        return null;
    }

    @Override
    public void removeConfigurations(String uuid, GloboResourceType loadBalancer) {
        SearchCriteria<GloboResourceConfigurationVO> sc = ListByResourceId.create();
        sc.setParameters("resourceType", loadBalancer);
        sc.setParameters("resourceUuid", uuid);
        remove(sc);
    }

    @Override
    public List<GloboResourceConfigurationVO> getConfigsByValue(GloboResourceType type, GloboResourceKey key, String value) {
        SearchCriteria<GloboResourceConfigurationVO> sc = ListByResourceId.create();
        sc.setParameters("resourceType", type);
        sc.setParameters("value", value);
        sc.setParameters("key", key);

        List<GloboResourceConfigurationVO> configs = listBy(sc);
        return configs;
    }


    @Override
    @Deprecated
    public boolean updateValue(GloboResourceConfiguration config) {
        TransactionLegacy txn = TransactionLegacy.currentTxn();
        PreparedStatement stmt = null;
        try {
            stmt = txn.prepareStatement("UPDATE globo_resource_configuration SET value=? WHERE resource_uuid=?;");
            stmt.setString(1, config.getValue());
            stmt.setString(2, config.getResourceUuid());
            stmt.executeUpdate();
            return true;
        } catch (Exception e) {
            s_logger.warn("Unable to update GloboResourceConfiguration Value", e);
            throw new CloudRuntimeException("Error during pudate globo resource configuration value", e);
        } finally {
            try {
                if (stmt != null)
                    stmt.close();
            } catch (SQLException e) {
                s_logger.warn("error closing stmt", e);
            }
        }
    }


}