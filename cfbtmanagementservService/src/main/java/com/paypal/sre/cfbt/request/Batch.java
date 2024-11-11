/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.util.ArrayList;
import java.util.List;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Holds logic to manage batched {@link ExecutionRequest} 
 * 
 */
public final class Batch {
    private final List<ExecutionRequest> batchedRequests = new ArrayList<>();
    private int position = ExecutionRequest.DEQUEUED_POSITION;
    private int priority = ExecutionRequest.UNSET_PRIORITY;
    private ExecutionRequestDAO dao;
    private EstimatedTime estimate;
    private ExecutionRequest longestRequest;
    private String estimatedStartTime;
    private Batch prevBatch;
    private final DatabaseConfig db;

    static List<Batch> createListOfBatches(List<ExecutionRequest> pendingQueue, DatabaseConfig db, Batch prevBatch,
                                           boolean calculateEstimate, EstimatedTime estimate) throws Exception {
        return Batch.createListOfBatches(pendingQueue, db, prevBatch, calculateEstimate, false, estimate);
    }

    static List<Batch> createListOfBatches(List<ExecutionRequest> pendingQueue, DatabaseConfig db, Batch prevBatch,
                                           boolean calculateEstimate, boolean storePosition, EstimatedTime estimate) throws Exception {
        List<Batch> batchedList = new ArrayList<>();
        Batch batch = null;
        Batch listPrev = null;
        for (ExecutionRequest request : pendingQueue) {
            if (batch != null && request.getPosition() == batch.getPosition()) {
                batch.addRequest(request, false);
            } else {
                if (listPrev == null) {
                    batch = new Batch(request, db, prevBatch, storePosition, calculateEstimate, estimate);
                } else {
                    batch = new Batch(request, db, listPrev, storePosition, calculateEstimate, estimate);
                }
                listPrev = batch;
                batchedList.add(batch);
            }
        }
        return batchedList;
    }

    private void init(Batch prevBatch, EstimatedTime estimate) {
        this.dao = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
        this.estimate = estimate;
        this.prevBatch = prevBatch;
    }

    public Batch(List<ExecutionRequest> requestList, DatabaseConfig db, Batch prevBatch, EstimatedTime estimate) throws Exception {
        if (requestList == null || requestList.isEmpty()) {
            throw new IllegalArgumentException("requestList is empty");
        }
        this.db = db;
        init(prevBatch, estimate);

        for (ExecutionRequest request : requestList) {
           addRequest(request, false);
        }

        if (prevBatch != null) {
            this.estimatedStartTime = estimate.estimatedStartTime(prevBatch.longestRequest);
        } else {
            this.estimatedStartTime = DateUtil.currentDateTimeISOFormat();
        }
    }

    public Batch(ExecutionRequest request, DatabaseConfig db, Batch prevBatch, boolean storePosition, boolean calculateEstimate, EstimatedTime estimate) throws Exception {
        this.db = db;
        init(prevBatch, estimate);
        this.position = request.getPosition();

        if (!request.checkFastPass()) {
            this.priority = request.getPriority();
        }
        this.longestRequest = request;

        if (!calculateEstimate) {
            this.estimatedStartTime = request.getEstimatedStartTime();
        } else {
            if (prevBatch != null) {
                this.estimatedStartTime = estimate.estimatedStartTime(prevBatch.getLongestRequest());
                request.setEstimatedStartTime(estimatedStartTime);
            } else {
                this.estimatedStartTime = DateUtil.currentDateTimeISOFormat();
            }
        }
 
        if (storePosition) {
            dao.updatePosition(request);
        }

        batchedRequests.add(request);      
    }

    /**
     * Add a request to the batch.
     * @param request {@link ExecutionReqeust} 
     * @param update Update the request to storage.
     * @throws Exception 
     */
    public void addRequest(ExecutionRequest request, boolean update) throws Exception {
        batchedRequests.add(request);
        if (!request.checkFastPass() && request.getPriority() < priority) {
            this.priority = request.getPriority();
        }

        request.setPosition(position);
        request.setEstimatedStartTime(estimatedStartTime);

        if (longestRequest == null) {
            longestRequest = request;
        } else if (estimate.estimatedRequestTime(request) > estimate.estimatedRequestTime(longestRequest)) {
            longestRequest = request;
        }

        if (update) {
            dao.updatePosition(request);
        }
    }

    public int getPosition() {
        return position;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Returns true if none of the requests in the list has tests.
     * @return true if noTests
     */
    private boolean allFastPass() {
        return batchedRequests.stream().allMatch((request) -> request.checkFastPass());
    }

    public void updatePrevBatch(Batch prevBatch) throws Exception {
        this.prevBatch = prevBatch;
        estimatedStartTime = estimate.estimatedStartTime(prevBatch.getLongestRequest());
        for (ExecutionRequest request : batchedRequests) {
            request.setEstimatedStartTime(estimatedStartTime);
            dao.updatePosition(request);
        }
    }

    /**
     * Increment current position and update position and estimatedStartTime with db.
     * @throws java.lang.Exception
     */
    public void incrementPosition() throws Exception {
        position++;
        estimatedStartTime = estimate.estimatedStartTime(prevBatch.getLongestRequest());
        for (ExecutionRequest request : batchedRequests) {
            request.setEstimatedStartTime(estimatedStartTime);
            dao.incrementPosition(request);
        }
    }

    /**
     * Will check if the requests in the inputted batch should actually be batched here.
     * 
     * @param batch {@link Batch} 
     * @throws Exception Throws exception querying mongo.
     */
    public void checkForRebatch(Batch batch) throws Exception {
        List<ExecutionRequest> movedRequests = new ArrayList<>();

        if (this.position < batch.getPosition()) {
            for (ExecutionRequest request : batch.batchedRequests) {
                if (shouldBatch(request)) {
                    request.setPosition(position);
                    request.setEstimatedStartTime(estimatedStartTime);
                    batchedRequests.add(request);
                    dao.updatePosition(request);
                    movedRequests.add(request);
                }
            }

            for (ExecutionRequest request : movedRequests) {
                batch.batchedRequests.remove(request);
            }

            if (batch.batchedRequests.isEmpty()) {
                batch.priority = ExecutionRequest.UNSET_PRIORITY;
                batch.position = ExecutionRequest.DEQUEUED_POSITION;
            }
        }
        
    }

    public ExecutionRequest getLongestRequest() {
        return longestRequest;
    }

    public boolean shouldBatch(ExecutionRequest newRequest) {
        BatchRules rules = new BatchRules(db, batchedRequests, newRequest, priority);
        return rules.shouldBatch();
    }

    public String getEstimatedStartTime() {
        return estimatedStartTime;
    }

    private boolean isAdhoc() {
        if (batchedRequests.stream().anyMatch((request) -> (request.thisIsAdhoc()))) {
            return true;
        }

        return false;
    }

    public boolean isHigherPriority(ExecutionRequest newRequest) {
        // First come first serve if they are both fast pass.
        if (newRequest.checkFastPass()) {
            return true;
        }

        if (allFastPass()) {
            return false;
        }

        return (priority <= newRequest.getPriority());
    }

    public List<ExecutionRequest> batchedRequests() {
        return batchedRequests;
    }

    Batch prevBatch() {
        return prevBatch;
    }

    void setPrevBatch(Batch prevBatch) {
        this.prevBatch = prevBatch;
    }

    /**
     * Set the batch to position 0 and current estimatedStartTime.
     *
     * @return
     * @throws Exception
     */
    List<ExecutionRequest> dequeue() throws Exception {
        for (ExecutionRequest request: batchedRequests) {
            request.setPosition(0);
            request.setEstimatedStartTime(DateUtil.currentDateTimeISOFormat());
            dao.updatePosition(request);
        }

        return batchedRequests;
    }

    /**
     * Decrement the position and recalculate estimatedStartTime.
     */
    void decrementPosition() throws Exception {
        // Cannot decrement off the queue.
        if (position <= ExecutionRequest.TOP_POSITION) {
            return;
        }

        position--;

        if (prevBatch != null) {
            estimatedStartTime = estimate.estimatedStartTime(prevBatch.getLongestRequest());
        }

        for (ExecutionRequest request : batchedRequests) {
            request.setEstimatedStartTime(estimatedStartTime);
            dao.decrementPosition(request);
        }
    }

    /**
     * Return the deployment estimate for the longest request.
     * @return deployment time.
     */
    public int longestDeployment() {
        if (!longestRequest.thisIsAdhoc()) {
            return longestRequest.getReleaseTest().getDeploymentEstimatedDuration();
        } else {
            return 0;
        }
    }

    /**
     * Return the requestTime for the request that is dictating the position.
     * @return Request Time.
     */
    public String getRequestTime() {
        ExecutionRequest highestPriorityRequest = null;

        for (ExecutionRequest request : batchedRequests) {
            if (highestPriorityRequest == null) {
                highestPriorityRequest = request;
            } else if (!request.checkFastPass() && getPriority() > highestPriorityRequest.getPriority()) {
                highestPriorityRequest = request;
            }
        }

        if (highestPriorityRequest == null) {
            return null;
        }
        return highestPriorityRequest.getRequestTime();
    }

    /**
     * Check if the supplied request is in the batch.
     *
     * @param request {@link ExecutionRequest} The request being sought.
     * @return true if its in the batch.
     */
    public boolean isInBatch(ExecutionRequest request) {
        for (ExecutionRequest candidateRequest : batchedRequests()) {
            if (candidateRequest.getId().equals(request.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove the request from the batch.
     * 
     * @param request {@link ExecutionRequest} to remove.
     */
    void remove(ExecutionRequest request) {
        ExecutionRequest localRequest = null;

        for (ExecutionRequest thisRequest: batchedRequests) {
            if (thisRequest.getId().equals(request.getId())) {
                localRequest = thisRequest;
            }
        }
        batchedRequests.remove(localRequest);

        if (localRequest.getId().equals(longestRequest.getId())) {
            longestRequest = null;
            for (ExecutionRequest thisRequest : batchedRequests) {
                if (longestRequest == null) {
                    longestRequest = thisRequest;
                } else if (estimate.estimatedRequestTime(thisRequest) > estimate.estimatedRequestTime(longestRequest)) {
                    longestRequest = request;
                }
            }
        }
    }
}
