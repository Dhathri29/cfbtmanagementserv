/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.management.dal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.result.UpdateResult;
import com.paypal.sre.cfbt.data.execapi.CompleteRequest;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.OverrideDetails;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest.Action;
import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.dataaccess.DBOpLog;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.mongo.MongoDataMarshaller;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.DateUtil;

/**
 * Provides access to ReleaseTest DAO.
 */
public class ReleaseTestDAO extends AbstractDAO<ExecutionRequest> {
    private static ReleaseTestDAO INSTANCE = null;
    private MongoConnectionFactory db;
    private final Logger mLogger = LoggerFactory.getLogger(DBOpLog.class);   
    
    public static ReleaseTestDAO getInstance(MongoConnectionFactory db) {
        if (INSTANCE == null) {
            INSTANCE = new ReleaseTestDAO("ExecutionRequest", db);
        }
        return INSTANCE;
    }   

    private ReleaseTestDAO(String collectionName, MongoConnectionFactory db) {
        super(collectionName, ExecutionRequest.class);

        this.db = db;
    }
    
    private ReleaseTest constructReleaseTest(ExecutionRequest request) {
        ReleaseTest thisRelease = request.getReleaseTest();
        
        if (thisRelease != null) {
            request.setReleaseTest(null);
            thisRelease.setExecutionRequest(request);
            thisRelease.setId(request.getId());
        }
        
        return thisRelease;
    }
    
    public ReleaseTest readById(MongoConnection c, String id) {
        ExecutionRequest request = super.readOne(c, id);
        if (request == null) {
            return null;
        }
        return constructReleaseTest(request);
    }
    
    public ReleaseTest insert(MongoConnection c, ReleaseTest newReleaseTest) {
        Document update = new Document("releaseTest", newReleaseTest);
        Document filter = new Document("_id", new ObjectId(newReleaseTest.getId()));  
        
        super.update(c, filter, new Document("$set", update), false);
        
        ExecutionRequest request = super.readOne(c, filter);
        
        return constructReleaseTest(request);
    }

    /**
     * Query the Release
     * @param id {@link ReleaseTest} id.
     * @return {@link ReleaseTest}
     * @throws java.lang.Exception {@link Exception}
     */
    public ReleaseTest readByExecutionRequestId(String id) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            List<ExecutionRequest> list = super.read(c, new Document("_id", new ObjectId(id)));

            // We're flipping the relationship between ExecutionRequest and 
            // ReleaseTest here. Save the reference for the request's ReleaseTest, then null
            // it out of the ExecutionRequest. Finally set the ReleastTest's request to this request.
            if (list.size() > 0) {
                return constructReleaseTest(list.get(0));
            }

            return null;
        }
    }
    
    /**
     * Method to get the {@link ReleaseTest} based on the {@link ReleaseTest.CallStatus} specified with in the last
     * 30 minutes.
     * 
     * @param c
     *            the {@link MongoConnection}
     * @param callbackStatus
     *            the specified {@link ReleaseTest.CallStatus} to filter out the results
     * @return the {@link List} of {@link ReleaseTest}
     * @throws Exception
     */
    public List<ReleaseTest> readByTestingCompleteCallbackStatus(MongoConnection c,
            ReleaseTest.CallStatus callbackStatus) throws Exception {

        List<Document> filters = new ArrayList<>();
        filters.add(new Document("releaseTest.testingCompleteCallbackStatus", callbackStatus.toString()));

        DateTime dateTimeEnd = DateTime.parse(DateUtil.currentDateTimeISOFormat()).toDateTimeISO();
        DateTime dateTimeStart = DateTime.parse(DateUtil.currentDateTimeISOFormat()).toDateTimeISO().minusMinutes(30);
        
        //In case of 0 tests or isRollback is true, need to fetch the document with completionTime since there
        //is no execution
        Document timeFrame = new Document("$lte", dateTimeEnd.toString().replaceAll(".000Z", "")).append("$gte",
                        dateTimeStart.toString().replaceAll(".000Z", ""));
        
        filters.add(new Document("$or", Arrays.asList(
            new Document("executionStart", timeFrame),
            new Document("completionTime", timeFrame)
        )));

        filters.add(new Document("releaseTest.testingCompleteCallbackURL", new Document("$ne", null)));

        List<ExecutionRequest> list = super.read(c, new Document("$and", filters));

        if (list.size() > 0) {
            List<ReleaseTest> releaseTests = new ArrayList<>();
            for (ExecutionRequest eachRequest : list) {
                releaseTests.add(constructReleaseTest(eachRequest));
            }
            return releaseTests;
        }

        return null;
    }
    
    /**
     * Mark the release test completed by documenting the action.
     * @param request The {@link ExecutionRequest} request attached to this {@link ReleaseTest}
     * @param action {@link Action} The action performed
     * @return {@link ReleaseTest}
     * @throws Exception
     */
    public ReleaseTest completeAction(ExecutionRequest request, Action action) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            if (ReleaseTest.Action.CANCEL.equals(action)) {
                if (ExecutionRequest.Status.DEPLOYING.equals(request.getStatus())
                        || ExecutionRequest.Status.DEPLOY_WAITING.equals(request.getStatus())) {
                    action = ReleaseTest.Action.CANCEL_DEPLOYMENT;
                }
                else if (ExecutionRequest.Status.TESTING_COMPLETE.equals(request.getStatus())) {
                    action = ReleaseTest.Action.CANCEL_ROLLBACK;
                }
                else if (ExecutionRequest.Status.IN_PROGRESS.equals(request.getStatus())) {
                    action = ReleaseTest.Action.CANCEL_TESTS;
                }
            }
            if (request.getReleaseTest() != null) {
                request.getReleaseTest().setCompletionAction(action);
            }
            return completeRequest(c, request.getId(), new CompleteRequest(action, null));
        }
    }
    
    /**
     * Method to update the {@link OverrideDetails} in the {@link ReleaseTest} record corresponding to the id specified.
     * 
     * @param c
     *            the {@link MongoConnection} object
     * @param id
     *            the {@link ReleaseTest} id
     * @param overrideDetails
     *            the {@link OverrideDetails} to be updated
     * @return the updated {@link ReleaseTest}
     */
    public ReleaseTest updateOverrideDetails(MongoConnection c, String id, OverrideDetails overrideDetails) {
        Document filter = new Document("_id", new ObjectId(id));
        UpdateResult result = super.update(c, filter,
                new Document("$set", new Document("releaseTest.override", MongoDataMarshaller.encode(overrideDetails))),
                false);

        CFBTLogger.logInfo(mLogger, ReleaseTestDAO.class.getCanonicalName(),
                "Override details update for " + result.getMatchedCount() + " records.");

        return constructReleaseTest(super.readOne(c, filter));
    }

    /**
     * Mark the release test completed by documenting the action and override details.
     * @param c The MongoConnection
     * @param requestId The {@link ExecutionRequest} ID attached to this {@link ReleaseTest}
     * @param completeRequest The {@link CompleteRequest}
     * @return {@link ReleaseTest}
     */
    public ReleaseTest completeRequest(MongoConnection c, String requestId, CompleteRequest completeRequest) {
        String completionTime = DateUtil.currentDateTimeISOFormat();
        Document update = new Document("releaseTest.completionAction", completeRequest.getAction().toString());
        Document filter = new Document("_id", new ObjectId(requestId));

        if (ReleaseTest.Action.RELEASE.equals(completeRequest.getAction())) {
            update.append("releaseTest.rollbackStart", null);
            if(completeRequest.getOverrideDetails()!=null){
                update.append("releaseTest.override",  MongoDataMarshaller.encode(completeRequest.getOverrideDetails()));
            }
        } else if (ReleaseTest.Action.ROLLBACK.equals(completeRequest.getAction())) {
            update.append("releaseTest.rollbackComplete", completionTime);
        }
        update.append("completionTime", completionTime);
        
        UpdateResult result = super.update(c, filter, new Document("$set", update), false);
 
        CFBTLogger.logInfo(mLogger, ReleaseTestDAO.class.getCanonicalName(), "Complete update for " + result.getMatchedCount() + " records.");
        return constructReleaseTest(super.readOne(c, filter));
    }

    /**
     * Does everything completeRequest does but also moves the status to complete.
     *
     * To DO consolidate markComplete and completeRequest - completeRequest is being used by abort and that code can be cleaner.
     *
     * @param releaseTest {@link ReleaseTest}
     * @return {@link ReleaseTest}
     */
    public ReleaseTest markComplete(ReleaseTest releaseTest) throws Exception {
        if (releaseTest == null) {
            throw new IllegalArgumentException("This is not a releaseTest");
        }

        if (releaseTest.getCompletionAction() == null) {
            throw new IllegalArgumentException("Need to have an action to complete a release test.");            
        }

        String completionTime = DateUtil.currentDateTimeISOFormat();
        Document update = new Document("status", ExecutionRequest.Status.COMPLETED.toString());
        update.append("releaseTest.completionAction", releaseTest.getCompletionAction().toString());
        Document filter = new Document("_id", new ObjectId(releaseTest.getId()));

        if (ReleaseTest.Action.RELEASE.equals(releaseTest.getCompletionAction())) {
            update.append("releaseTest.rollbackStart", null);
            if(releaseTest.getOverride()!=null){
                update.append("releaseTest.override",  MongoDataMarshaller.encode(releaseTest.getOverride()));
            }
        } else if (ReleaseTest.Action.ROLLBACK.equals(releaseTest.getCompletionAction())) {
            update.append("releaseTest.rollbackComplete", completionTime);
        }
        update.append("completionTime", completionTime);

        UpdateResult result = super.update(db.newConnection(), filter, new Document("$set", update), false);

        CFBTLogger.logInfo(mLogger, ReleaseTestDAO.class.getCanonicalName(), "Complete update for " + result.getMatchedCount() + " records.");
        return constructReleaseTest(super.readOne(db.newConnection(), filter));
    }

    /**
     * Mark the deployment start
     * @param c {@link MongoConnection}
     * @param releaseTest {@link ReleaseTest}
     * @return {@link ReleaseTest}
     */
    public ReleaseTest markDeploymentStart(MongoConnection c, ReleaseTest releaseTest) {
        super.update(c, releaseTest.getId(), 
                new Document("$set", new Document("releaseTest.deploymentStart", releaseTest.getDeploymentStart())));

        return constructReleaseTest(super.readOne(c, releaseTest.getId()));
    }

    /**
     * Mark the rollback start
     * @param c {@link MongoConnection}
     * @param releaseTest {@link ReleaseTest}
     * @return {@link ReleaseTest}
     */
    public ReleaseTest markRollbackStart(ReleaseTest releaseTest) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            Document updateReleaseTest = new Document("releaseTest.rollbackStart", releaseTest.getRollbackStart());
            updateReleaseTest.append("releaseTest.deploymentEstimatedDuration", releaseTest.getDeploymentEstimatedDuration());
            super.update(c, releaseTest.getId(), new Document("$set", updateReleaseTest));
            return constructReleaseTest(super.readOne(c, releaseTest.getId()));
        }
    }

    public ReleaseTest readById(String id) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Save the deploymentComplete time as now for this release
     *
     * @param id {@link ExecutionRequest}
     * @throws java.lang.Exception
     */
    public void completeDeploy(String id) throws Exception {
        Document deploymentStatus = new Document("releaseTest.deploymentComplete", DateUtil.currentDateTimeISOFormat());

        try (MongoConnection c = db.newConnection()) {
            super.update(c, id, new Document("$set", deploymentStatus));
        }
    }
}
