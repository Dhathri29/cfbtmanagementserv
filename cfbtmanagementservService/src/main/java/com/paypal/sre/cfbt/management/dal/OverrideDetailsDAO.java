package com.paypal.sre.cfbt.management.dal;

import java.util.List;

import org.bson.Document;

import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.data.execapi.OverrideDetails;
import com.paypal.sre.cfbt.mongo.MongoConnection;

public class OverrideDetailsDAO extends AbstractDAO<OverrideDetails> {

    private static final OverrideDetailsDAO instance = new OverrideDetailsDAO("OverrideDetails");

    /**
     * @param collectionName
     *            - the collection name
     */
    public OverrideDetailsDAO(String collectionName) {
        super(collectionName, OverrideDetails.class);
    }

    /**
     * Accessor for singleton instance of this object.
     *
     * @return the instance.
     */
    public static OverrideDetailsDAO getInstance() {
        return instance;
    }

    /**
     * Returns the latest {@link OverrideDetails} document associated with the specified release id.
     * 
     * @param c
     *            - the {@link MongoConnection}
     * @param releaseId
     *            - the release id
     * @return the corresponding {@link OverrideDetails}
     * @throws Exception
     */
    public OverrideDetails read(MongoConnection c, String releaseId) throws Exception {
        Document sort = new Document("_id", -1);
        Document query = new Document("releaseId", releaseId);
        List<OverrideDetails> records = read(c, query, null, sort, 1);
        if (records.size() > 0) {
            return records.get(0);
        }
        return null;
    }
    
    /**
     * Returns the latest {@link OverrideDetails} document associated with the specified component version.
     * 
     * @param c
     *            - the {@link MongoConnection}
     * @param componentVersion
     *            - the component version to be used for querying
     * @return the corresponding {@link OverrideDetails}
     * @throws Exception
     */
    public OverrideDetails readByComponentVersion(MongoConnection c, String componentVersion) throws Exception {
        Document sort = new Document("_id", -1);
        Document query = new Document("componentVersions.currentVersion", componentVersion);
        List<OverrideDetails> records = read(c, query, null, sort, 1);
        if (records.size() > 0) {
            return records.get(0);
        }
        return null;
    }

}
