/*
 * (C) 2020 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import java.util.List;
import java.util.ArrayList;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.management.appproperty.ApplicationProperty;
import com.paypal.sre.cfbt.management.dal.ApplicationPropertyDAO;
import com.paypal.sre.cfbt.shared.DateUtil;
import com.paypal.sre.cfbt.data.execapi.Alert;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import java.util.ListIterator;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;

/**
 * Class to set the special messaging on demand.
 */
public class SpecialMessage {
    private final ApplicationProperty property;
    private final DatabaseConfig db;
    private final String queueName;
    public static int DEFAULT_DEPLOYMENT_TIME = 600;   
    public static String LARGE_ESTIMATE_WAIT_RELEASE = "A release with a long estimated wait was already in the queue.";
    public static String HIGH_PRIORITY_CUT_IN = "Another higher priority release entered the queue.";
    public static String LARGE_NUMBER_RELEASES_AHEAD = "A large number of releases ahead.";
    private List<ExecutionRequest> pendingQueue = null;
    private final EstimatedTime estimate;

    /**
     * Default public constructor
     * @param db Connection to mongo.
     * @param queueName Name of the queue.
     * @throws java.lang.Exception Mongo connection issues.
     */
    public SpecialMessage(DatabaseConfig db, String queueName) throws Exception {
        this.db = db;
        ApplicationPropertyDAO appDAO = ApplicationPropertyDAO.getInstance();
        property = appDAO.getApplicationProperty(db.getConnectionFactory().newConnection());
        this.queueName = queueName;
        this.estimate = new EstimatedTime(db.getConnectionFactory());
    }

    /**
     * Validates the request to ensure special messaging can be calculated and stored.
     *
     * The request has to be a Release, Has to be in the Pending queue and has to have
     * an estimatedStartTime.
     * 
     * @param request request to apply special messaging
     * @return true if request has the info necessary.
     */
    private boolean validateRequestForSpecialMessage(ExecutionRequest request) {
        return !((request == null) || 
                (request.thisIsAdhoc()) ||
                (!ExecutionRequest.Status.PENDING.equals(request.getStatus())) ||
                (request.getEstimatedStartTime() == null));
    }

    /**
     * Return the type of alert based on the estimatedStartTime
     * @param estimatedStartTime The time this request is expected to begin.
     * @return {@link Alert.Type}
     */
    private Alert.Type alertType(String estimatedStartTime) {
        long elapsedTime = -DateUtil.getElapsedTimeInSeconds(estimatedStartTime);
        long mediumWaitThreshold = 0;
        if(property.getMediumWaitTimeThreshold()!=null){
            mediumWaitThreshold = property.getMediumWaitTimeThreshold() * 60;
        }
        long largeWaitThreshold = 0;
        if(property.getLargeWaitTimeThreshold()!=null){
             largeWaitThreshold = property.getLargeWaitTimeThreshold() * 60;
        }
        if (elapsedTime < mediumWaitThreshold) {
            return Alert.Type.SUCCESS;
        } else if (elapsedTime >= mediumWaitThreshold && elapsedTime < largeWaitThreshold) {
            return Alert.Type.WARNING;
        } else {
            return Alert.Type.DANGER;
        }
    }

    /**
     * Add the list of reasons that are driving the message.
     * @param request {@link ExecutionRequest} The queued request
     * @param alertType The type of alert to return reasons for.
     * @throws Exception On mongo connection errors.
     */
    private List<String> reasons(ExecutionRequest request, Alert.Type alertType) throws Exception {
        if (Alert.Type.SUCCESS.equals(alertType) || Alert.Type.INFO.equals(alertType)) {
            return null;
        }

        if (pendingQueue == null) {
            pendingQueue = ExecutionRequestDAO.getInstance(db.getConnectionFactory()).getPositionedPendedRequests(queueName);
        }

        List<String> reasons = new ArrayList<>();
        List<Batch> batchedQueue = Batch.createListOfBatches(pendingQueue, db, null, false, estimate);
        ListIterator<Batch> listIter = batchedQueue.listIterator(batchedQueue.size());
        Batch requestBatch = null;
        boolean largeEstimateReasonAdded = false;
        boolean highPrioirtyCutInAdded = false;
        
        while (listIter.hasPrevious()) {
            Batch currentBatch = listIter.previous();
            if (requestBatch == null && currentBatch.isInBatch(request)) {
                requestBatch = currentBatch;
            } else if (requestBatch != null) {
                if (isLargeEstimateWait(currentBatch) && !largeEstimateReasonAdded) {
                    largeEstimateReasonAdded = true;
                    reasons.add(LARGE_ESTIMATE_WAIT_RELEASE);
                }
                if (!highPrioirtyCutInAdded && highPriorityReleaseCutIn(currentBatch, request)) {
                    highPrioirtyCutInAdded = true;
                    reasons.add(HIGH_PRIORITY_CUT_IN);                    
                }
            }
        }

        // If a long release is not ahead of me yet I still need a reason, it must be because there are a large number of releases.
        if (!largeEstimateReasonAdded) {
            reasons.add(LARGE_NUMBER_RELEASES_AHEAD);
        }

        return reasons;
    }
 
    /**
     * Return the special messages for the supplied request.
     * @param alertType The type of alert driving the messages
     * @param request The request to get the special message
     * @return The list of messages
     * @throws Exception 
     */
    private String message(Alert.Type alertType, ExecutionRequest request) throws Exception {
        DateTime dateTime = DateUtil.dateTimeUTC(request.getEstimatedStartTime());

        if (DateUtil.currentDateTimeUTC().isAfter(dateTime)) {
            return "Request should start any time now.";
        } else {
            org.joda.time.format.DateTimeFormatter fmt = DateTimeFormat.forPattern("MM/dd/yyyy hh:mm a z");
            String estimatedStartTimeMessage = "Estimated Time to Start: " + dateTime.withZone(DateTimeZone.forID("America/Los_Angeles")).toString(fmt);

            switch (alertType) {
                case SUCCESS: return property.getNormalWaitTimeMessage() +  " " + estimatedStartTimeMessage;
                case WARNING: return property.getMediumWaitTimeMessage() + " " + estimatedStartTimeMessage;
                case DANGER:  return property.getLargeWaitTimeMessage() + " " + estimatedStartTimeMessage;
            }
        }
 
        throw new IllegalArgumentException("Invalid alertType = " + alertType);
    }

    /**
     * Add the special messages to the request.
     * @param request The request to add messages to.
     * @throws Exception Throw an exception when a mongo connection occurs.
     */
    public void addSpecialMessage(ExecutionRequest request) throws Exception {
        if (validateRequestForSpecialMessage(request)) {
            Alert.Type alertType = alertType(request.getEstimatedStartTime());
            Alert alert = new Alert();
            alert.setType(alertType.toString());
            alert.setMessage(message(alertType, request));
            if (Alert.Type.DANGER.equals(alertType) || Alert.Type.WARNING.equals(alertType)) {
                alert.setReasons(reasons(request, alertType));
            }
            request.getReleaseTest().setAlert(alert);
        }
    }

    /**
     * Add special messages to the list of requests.
     * @param releaseTestList The list of requests.
     * @throws Exception 
     */
    public void addSpecialMessage(List<ReleaseTest> releaseTestList) throws Exception {
        for (ReleaseTest releaseTest : releaseTestList) {
            ExecutionRequest request = ExecutionRequest.createFromReleaseTest(releaseTest);
            if (validateRequestForSpecialMessage(request)) {
                Alert.Type alertType = alertType(request.getEstimatedStartTime());
                Alert alert = new Alert();
                alert.setType(alertType.toString());
                alert.setMessage(message(alertType, request));
                if (Alert.Type.DANGER.equals(alertType) || Alert.Type.WARNING.equals(alertType)) {
                    alert.setReasons(reasons(request, alertType));
                }
                releaseTest.setAlert(alert);
            }
        }
    }

    /**
     * If the reason for the wait is that there's a long estimate to wait, return true.
     * @param currentBatch the inspected batch.
     * @return true if there's a long wait time.
     */
    private boolean isLargeEstimateWait(Batch currentBatch) {
        return currentBatch.longestDeployment() > DEFAULT_DEPLOYMENT_TIME;
    }

    /**
     * If the long wait time is because there's a high prioirty request that cut in.
     * @param candidateBatch The batch being checked.
     * @param request The request needing special messages.
     * @return true if there's a high priority request.
     */
    private boolean highPriorityReleaseCutIn(Batch candidateBatch, ExecutionRequest request) {
        DateTime candidateRequestTime = DateUtil.dateTimeUTC(candidateBatch.getRequestTime());
        DateTime requestTime = DateUtil.dateTimeUTC(request.getRequestTime());

        if (candidateBatch.getPosition() < request.getPosition() && candidateRequestTime.isAfter(requestTime)) {
            return true;
        }

        return false;
    }
}
