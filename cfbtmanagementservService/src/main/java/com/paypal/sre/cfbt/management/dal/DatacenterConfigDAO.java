/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.paypal.sre.cfbt.management.dal;

import com.paypal.sre.cfbt.data.execapi.Datacenter;
import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoDataMarshaller;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

/**
 * This class is the singleton concrete implementation of the AbstractDAO
 * class that handles {@link DatacenterConfigDAO} data operations in the MongoDB
 * "DatacenterConfig" collection.
 */
public class DatacenterConfigDAO extends AbstractDAO<Datacenter> {
    
    private static final DatacenterConfigDAO mInstance = new DatacenterConfigDAO("DatacenterConfig");

    /**
     * Constructor of singleton instance for this object.
     * 
     * @param aCollectionName the name of the collection
     */
    public DatacenterConfigDAO(String aCollectionName) {
        super(aCollectionName,Datacenter.class);
    }

    /**
     * Accessor for singleton instance of this object.
     * 
     * @return the instance.
     */
    public static DatacenterConfigDAO getInstance() {
        return mInstance;
    }
    
    public String updateDatacenter(MongoConnection mongocon, Datacenter dc) {
        String id = null;
        Document update = new Document("$set", MongoDataMarshaller.encode(dc));
        id = findOrInsert(mongocon, new Document("name", dc.getName()), update);
        return id;
    }
    
    public void deleteExcludingIds(MongoConnection mongocon, List<ObjectId> excludeIds)
    {
        delete(mongocon, new Document("_id", new Document("$nin", excludeIds)));
    }

    /**
     * Get the release vetting datacenter.
     *
     * @param mongoConnection The {@link MongoConnection} object
     * @return The default {@link Datacenter}.
     * @throws Exception On error while retrieving the default datacenter from the DB.
     */
    public Datacenter getReleaseVettingDatacenter(MongoConnection mongoConnection) throws Exception {
        return super.readOne(mongoConnection, new Document("releaseVetting", true));

    }
}
