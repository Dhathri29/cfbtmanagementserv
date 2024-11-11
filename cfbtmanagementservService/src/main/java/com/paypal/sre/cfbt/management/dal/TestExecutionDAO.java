/*
 * (C) 2016 PayPal, Internal software, do not distribute.
 */

package com.paypal.sre.cfbt.management.dal;

import com.mongodb.client.result.UpdateResult;
import com.paypal.sre.cfbt.data.execapi.Execution;
import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.NetworkUtil;

import java.util.ArrayList;
import java.util.List;
import org.bson.Document;

/**
 * This class is the singleton concrete implementation of the AbstractDAO class that handles {@link Execution} data
 * operations in the MongoDB "TestExecution" collection.
 * 
 */
public class TestExecutionDAO extends AbstractDAO<Execution> {

    private static TestExecutionDAO INSTANCE = null;
    private MongoConnectionFactory db;

    public static TestExecutionDAO getInstance(MongoConnectionFactory db) {
        if (INSTANCE == null) {
            INSTANCE = new TestExecutionDAO("TestExecution", db);
        }
        return INSTANCE;
    }
    
    /**
     * Constructor of singleton instance for this object.
     * @param aCollectionName containing collection name.
     * @param db The MongoConnectionFactory
     */
    public TestExecutionDAO(String aCollectionName, MongoConnectionFactory db) {
        super(aCollectionName,Execution.class);
        this.db = db;
    }

    /**
     * Loads the list executions associated by the execution request id.
     * @param id ExecutionRequest id.
     * @return {@link Execution}
     * @throws Exception 
     */
    public List<Execution> getExecutionsByRequestID(String id) throws Exception {
        List<String> searchIds = new ArrayList<>();
        searchIds.add(id);
        try (MongoConnection c = db.newConnection()) {
            return super.read(c, new Document("executionRequestIds", new Document("$elemMatch", new Document("$in", searchIds))));
        }
    }

    /**
     * Update the release vetting status for all executions associated with the test.
     * @param executionRequestId The ExecutionRequest id.
     * @param testId The test id.
     * @param executionReleaseVetting Execution release vetting status.
     * @throws Exception 
     */   
    public void updateReleaseVettingForExecutions(String executionRequestId, String testId, boolean executionReleaseVetting) throws Exception {
        Document testFilter = new Document("testId", testId);
        Document releaseVetting = new Document("releaseVetting", executionReleaseVetting);

        List<String> searchIds = new ArrayList<>();
        searchIds.add(executionRequestId);
        Document requestIdsQuery = new Document("$elemMatch", new Document("$in", searchIds));

        Document requestId = new Document("executionRequestIds", requestIdsQuery);
        List<Document> query = new ArrayList<>();
        query.add(requestId);
        query.add(testFilter);

        try (MongoConnection c = db.newConnection()) {
            super.update(c, new Document("$and", query), new Document("$set", releaseVetting), false);
        }    
    }

    /**
     * Move pending executions to skipped - relevant during a halt or stop.
     * @param executionRequestID {@link ExecutionRequst} id.
     * @return Number of updates made.
     * @throws Exception 
     */
    public long skipPendingExecutions(String executionRequestID) throws Exception {
        Document pendingStatus = new Document("status", Execution.Status.PENDING.name());		
        Document skipStatus = new Document("status", Execution.Status.SKIP.name());
        List<String> searchIds = new ArrayList<>();
        searchIds.add(executionRequestID);

        Document requestId = new Document("executionRequestIds", new Document("$elemMatch", new Document("$in", searchIds)));
        List<Document> andPendingList = new ArrayList<>();		
        andPendingList.add(requestId);		
        andPendingList.add(pendingStatus);		
        
        try (MongoConnection c = db.newConnection()) {		
            UpdateResult result = super.update(c, new Document("$and", andPendingList), new Document("$set", skipStatus), false);		
            
            return result.getMatchedCount();		
        }
    }

    /**
     * Retrieve all running executions for the execution request.
     * 
     * @param executionRequestID The Execution Request id.
     * @return The list of running executions.
     * @throws Exception 
     */
    public List<Execution> getRunningExecutions(String executionRequestID) throws Exception {
        List<String> searchIds = new ArrayList<>();
        searchIds.add(executionRequestID);

        List<Document> andInProgressList = new ArrayList<>();
        andInProgressList.add(new Document("executionRequestIds", new Document("$elemMatch", new Document("$in", searchIds))));
        andInProgressList.add(new Document("status", Execution.Status.IN_PROGRESS.name()));
        
        try (MongoConnection c = db.newConnection()) {
            return super.read(c, new Document("$and", andInProgressList));
        }        
    }

    /**
     * Transitions all running executions in this execution request to abort.
     *
     * @param executionRequestID The Execution Request} id.
     * @throws Exception Mongo connection errors.
     */
    public long abortRunningExecutions(String executionRequestID) throws Exception {
        Document inProgressStatus = new Document("status", Execution.Status.IN_PROGRESS.name());
        Document updateQuery = new Document();
        updateQuery.append("status", Execution.Status.ABORT.name());
        List<String> searchIds = new ArrayList<>();
        searchIds.add(executionRequestID);
        Document requestId = new Document("executionRequestIds", new Document("$elemMatch", new Document("$in", searchIds)));
        String ipAddress = NetworkUtil.getLocalInetAddress(ConfigManager.getConfiguration()).getHostAddress();
        updateQuery.append("abortNodeIPAddress", ipAddress);

        List<Document> andInProgressList = new ArrayList<>();
        andInProgressList.add(requestId);
        andInProgressList.add(inProgressStatus);

        try (MongoConnection c = db.newConnection()) {
            UpdateResult result = super.update(c, new Document("$and", andInProgressList),
                    new Document("$set", updateQuery), false);
            return result.getMatchedCount();
        }
    }

    /**
     * Inserts a new record containing {@code SerializableObject} data into the MongoDB collection.
     * 
     * @param c a {@code MongoConnection} object.
     * @param executions object of type stored in mClass member of the instance
     */
    public void insertExecutions(MongoConnection c, List<Execution> executions) {
        if (executions == null || executions.isEmpty()) {
            return;
        }
        for (Execution execution : executions) {
            execution.setTest(null);
        }
        super.insert(c, executions);
    }

    /**
     * Get Executions which are associated with given request ids.
     *
     * @param c a {@code MongoConnection} object.
     * @param  requestIds The list of request ids
     * @return The list of {@link Execution}
     */
    public List<Execution> getExecutionsByRequestIds(MongoConnection c, List<String> requestIds) throws Exception {
        Document query = new Document("$elemMatch", new Document("$in", requestIds));
        return super.read(c, new Document("executionRequestIds", query));
    }
}
