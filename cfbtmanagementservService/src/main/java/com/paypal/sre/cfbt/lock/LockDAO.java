/*
 * (C) 2016 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.lock;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.lock.LockData.LockStatus;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.util.ArrayList;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.bson.Document;
import org.joda.time.DateTime;

/**
 * This class is the singleton concrete implementation of the AbstractDAO
 * class that handles {@link Test} data operations in the MongoDB 
 * "Test" collection.
 */
public class LockDAO extends AbstractDAO<LockData> {
    private static LockDAO INSTANCE = null;
    private MongoConnectionFactory db;

    public static LockDAO getInstance(MongoConnectionFactory db) {
        if (INSTANCE == null) {
            INSTANCE = new LockDAO("LockData", db);
        }
        return INSTANCE;
    }
    
    /**
     * Constructor of singleton instance for this object.
     * @param aCollectionName containing collection name.
     * @param db The MongoConnectionFactory
     */
    private LockDAO(String aCollectionName, MongoConnectionFactory db) {
        super(aCollectionName,LockData.class);
        this.db = db;
    } 

    /**
     * If the query successfully updates the collection, the lock is obtained otherwise it fails.
     * @param lockName The name of the lock to create.
     * @param lockHoldTime At what point to automatically release the lock.
     * @return true if the lock is granted, false otherwise.
     * @throws Exception Thrown on error interacting with the db.
     */
    public boolean lock(String lockName, DateTime lockHoldTime) throws Exception {
        if (StringUtils.isBlank(lockName)) {
            throw new IllegalArgumentException("lockName cannot be null");
        }
        
        if (lockHoldTime == null || lockHoldTime.isBeforeNow()) {
            throw new IllegalArgumentException("lockHoldTime is invalid");
        }

        try (MongoConnection c = db.newConnection()) {
            List<Document> andFilter = new ArrayList<>();
            List<Document> orFilter = new ArrayList<>();

            orFilter.add(new Document("lockStatus", LockStatus.UNLOCKED.toString()));
            orFilter.add(new Document("timeLocked", new Document("$lte", DateUtil.currentDateTimeISOFormat())));
            andFilter.add(new Document("lockName", lockName));
            andFilter.add(new Document("$or", orFilter));
            Document updateDoc = new Document("lockStatus", LockStatus.LOCKED.toString())
                    .append("lockKey", UUID.randomUUID().toString())
                    .append("timeUpdated", DateUtil.currentDateTimeISOFormat())
                    .append("timeLocked", DateUtil.dateTimeISOFormat(lockHoldTime));
            UpdateResult updateResult = super.update(c, new Document("$and", andFilter), new Document("$set", updateDoc), false);

            if (updateResult.getMatchedCount() > 0) {
                return true;
            }

             return false;
        }
    }

    /**
     * Move the state to unlock with the key that only the client who retrieved the lock should have.
     * 
     * @param lockKey The UUID created when the lock was granted.
     * @throws Exception 
     */
    public void unlock(String lockKey) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            Document update = new Document("lockStatus", LockStatus.UNLOCKED.toString())
                    .append("timeUpdated", DateUtil.currentDateTimeISOFormat());
            UpdateResult result = super.update(c, new Document("lockKey", lockKey), new Document("$set", update), false);

            if (result.getMatchedCount() <= 0) {
                throw new IllegalArgumentException("lockKey is not found in the database.");
            }
        }
    }

    /**
     * Get the lock contents.
     * @param  lockName 
     * @return The lock contents.
     * @throws Exception 
     */
    public LockData read(String lockName) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            LockData data = super.readOne(c, new Document("lockName", lockName));
            return data;
        }
    }

    /**
     * Generates the lock at initialization time.
     * 
     * @param lockName Name of the lock to initialize.
     */
    public void insertIfNotFound(String lockName) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            Document update = new Document("lockStatus", LockStatus.UNLOCKED.toString())
                    .append("timeUpdated", DateUtil.currentDateTimeISOFormat());
            super.update(c, new Document("lockName", lockName), new Document("$setOnInsert", update), true);  
        }
    }
}
