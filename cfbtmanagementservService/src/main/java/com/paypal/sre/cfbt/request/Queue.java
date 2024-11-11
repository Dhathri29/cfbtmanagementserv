/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.dataaccess.DBOpLog;
import com.paypal.sre.cfbt.lock.DBLock;
import com.paypal.sre.cfbt.lock.LockData;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

/**
 * Our Execution Request queue to manage one execution request at a time.
 */
public class Queue {

    private final ExecutionRequestDAO dao;
    private final List<QueueObserver> observers = new ArrayList<>();
    private final DatabaseConfig db;
    private final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DBOpLog.class);
    private final DBLock lock;
    private final String queueName;
    private final EstimatedTime estimate;
    public static final String RELEASE_VETTING_QUEUE_NAME = "ReleaseVetting";
    private boolean isDuplicateExecutionRequest = false;

    public Queue(DatabaseConfig db, String queueName) throws Exception {
        this.dao = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
        this.db = db;
        this.lock = new DBLock(db, queueName + "Lock");
        this.queueName = queueName;
        this.estimate = new EstimatedTime(db.getConnectionFactory());
    }

    public static Queue releaseVettingQueue(DatabaseConfig dbConfig) throws Exception {
        return new Queue(dbConfig, RELEASE_VETTING_QUEUE_NAME);
    }

    /**
     * Enqueue the newRequest at the requested priority.
     * @param newRequest {@link ExecutionRequest}
     * @param priority The priority of the enqueued newRequest.
     * @return Enqueued {@link ExecutionRequest}
     * @throws java.lang.Exception
     */
    public ExecutionRequest enqueue(ExecutionRequest newRequest, int priority) throws Exception {
        newRequest.setPriority(priority);
        CFBTLogger.logInfo(LOGGER, Queue.class.getCanonicalName(), "Getting a lock to enqueue " + newRequest.getId() + ", priority = " + newRequest.getPriority());

        LockData lockData = lock.lock(10);
            try {
                List<ExecutionRequest> pendingRequests = new ArrayList<>();

                CFBTLogger.logInfo(LOGGER, Queue.class.getCanonicalName(), "Trying to enqueue " + newRequest.getId() + ", priority = " + newRequest.getPriority());

                pendingRequests.addAll(dao.getPositionedPendedRequests(queueName));

                List<ExecutionRequest> runningRequests = new ArrayList<>();
                runningRequests.addAll(dao.getByStatusAndQueue(ExecutionRequest.Status.runningStatuses(), queueName));

                List<ExecutionRequest> allRequestsinQueue = new ArrayList<>();

                allRequestsinQueue.addAll(pendingRequests);
                allRequestsinQueue.addAll(runningRequests);

                pendingRequests.sort((r1, r2) -> r1.getPosition() - r2.getPosition());

                int lastPosition = 0;
                Batch newRequestBatch = null;
                boolean movePosition = false;
                Batch runningBatch  = null;

                if (!runningRequests.isEmpty()) {
                    runningBatch = new Batch(runningRequests, db, null, estimate);
                }

                // insert the request into the DB
                dao.insert(newRequest, queueName);

                List<Batch> batchedList = Batch.createListOfBatches(pendingRequests, db, runningBatch, false, estimate);
                Batch prevBatch = runningBatch;

                for (Batch thisBatch : batchedList) {
                    if (newRequestBatch != null) {
                        newRequestBatch.checkForRebatch(thisBatch);
                    }
                    if (movePosition) {
                        if (prevBatch != null && prevBatch.batchedRequests().isEmpty()) {
                            thisBatch.updatePrevBatch(prevBatch.prevBatch());
                        } else {
                            thisBatch.incrementPosition();
                        }
                    }
                    if (newRequest.checkIsPositionless() && thisBatch.shouldBatch(newRequest)) {
                        thisBatch.addRequest(newRequest, true);
                        newRequestBatch = thisBatch;
                    } else if (newRequest.checkIsPositionless() && !thisBatch.isHigherPriority(newRequest)) {
                        newRequest.setPosition(thisBatch.getPosition());
                        newRequest.setEstimatedStartTime(thisBatch.getEstimatedStartTime());
                        newRequestBatch = new Batch(newRequest, db, prevBatch, true, false, estimate);
                        movePosition = true;
                        thisBatch.updatePrevBatch(newRequestBatch);
                        thisBatch.incrementPosition();
                    }
                    lastPosition = thisBatch.getPosition();
                    prevBatch = thisBatch;
                }

                if (newRequest.checkIsPositionless()) {
                    newRequest.setPosition(lastPosition + 1);

                    if (prevBatch == null) {
                        newRequest.setEstimatedStartTime(DateUtil.currentDateTimeISOFormat());
                    } else {
                        newRequest.setEstimatedStartTime(estimate.estimatedStartTime(prevBatch.getLongestRequest()));
                    }
                    dao.setPositionAndPriority(newRequest, queueName);
                }

                SpecialMessage special = new SpecialMessage(db, queueName);
                special.addSpecialMessage(newRequest);

                processQueue(runningRequests);
            } finally {
                try {
                    lock.unlock(lockData.getLockKey());
                } catch (Exception ex) {
                    CFBTLogger.logError(LOGGER, Queue.class.getCanonicalName(), ex.getMessage(), ex);
                }
            }
        return newRequest;
    }

    /**
     * Check if the request came for enqueue is duplicate or not.
     */
    public boolean isDuplicateRequest() {
        return isDuplicateExecutionRequest;
    }

    /**
     * Process the queue
     * @param runningRequests {list of {@link ExecutionRequest}
     */
    private void processQueue(List<ExecutionRequest> runningRequests) {
        List<ExecutionRequest> dequeuedRequests = new ArrayList<>();
        try {
            if (runningRequests.isEmpty()) {
               dequeuedRequests.addAll(localDequeue());
            }
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, Queue.class.getCanonicalName(), ex.getMessage(), ex);
        }

        if (!dequeuedRequests.isEmpty()) {
            observers.forEach((observer) -> {
                observer.onDequeueEvent(dequeuedRequests);
            });
        } else {
            observers.forEach((observer) -> {
                observer.onEnqueueEvent(runningRequests);
            });
        }
    }

    /**
     * For interested observers to get notified on queue events.
     * @param observer {@link QueueObserver}
     */
    public void subscribe(QueueObserver observer) {
        observers.add(observer);
    }

    private List<ExecutionRequest> localDequeue() throws Exception {
        List<ExecutionRequest> queuedRequests = dao.getPositionedPendedRequests(queueName);
        List<ExecutionRequest> dequeuedRequests = new ArrayList<>();

        queuedRequests.sort((r1, r2) ->  r1.getPosition() - r2.getPosition());
        List<Batch> batchedList = Batch.createListOfBatches(queuedRequests, db, null, false, estimate);
        boolean topOfList = true;
 
        for (Batch thisBatch : batchedList) {
            if (topOfList) {
                dequeuedRequests = thisBatch.dequeue();
                topOfList = false;
            } else {
                thisBatch.decrementPosition();
            }
        }

        if (!dequeuedRequests.isEmpty()) {
            queuedRequests = dao.getPositionedPendedRequests(queueName);
            queuedRequests.sort((r1, r2) ->  r1.getPosition() - r2.getPosition());
        }

        return dequeuedRequests;
    }


    private List<ExecutionRequest> localDequeueRequest(ExecutionRequest executionRequest) throws Exception {

        List<ExecutionRequest> executionRequestsList = new ArrayList<>();
        List<ExecutionRequest> pendingRequestsList = dao.getPositionedPendedRequests(queueName);
        List<ExecutionRequest> runningRequestList = dao.getByStatusAndQueue(ExecutionRequest.Status.runningStatuses(), queueName);

        List<ExecutionRequest> dequeudRequests = new ArrayList<>();
        dequeudRequests.add(executionRequest);

        boolean isRunningRequest = false;
        boolean isPendingRequest = false;
        for(ExecutionRequest ex: runningRequestList) {
            if(ex.getId().equals(executionRequest.getId())) {
                runningRequestList.remove(ex);
                isRunningRequest = true;
                break;
            }
        }

        if(!isRunningRequest) {
            for (ExecutionRequest ex : pendingRequestsList) {
                if (ex.getId().equals(executionRequest.getId())) {
                    pendingRequestsList.remove(ex);
                    isPendingRequest = true;
                    break;
                }
            }
        }

        executionRequestsList.addAll(pendingRequestsList);
        executionRequestsList.addAll(runningRequestList);
        executionRequestsList.sort((r1, r2) -> r1.getPosition() - r2.getPosition());

        if(isPendingRequest || isRunningRequest) {

            List<Batch> batchedList = Batch.createListOfBatches(executionRequestsList, db, null, true, true, estimate);

            if (isPendingRequest && batchedList != null && !batchedList.isEmpty()) {
                for (int i = 0; i < batchedList.size(); i++) {
                    if (i != batchedList.get(i).getPosition()) {
                        batchedList.get(i).decrementPosition();
                    }
                }
            }
        }
        return dequeudRequests;
    }

    /**
     * Dequeue the requests in the queue.
     * @param executionRequest
     * @return {@link ExecutionRequest} List.
     */
    public List<ExecutionRequest> dequeueRequest(ExecutionRequest executionRequest) throws Exception {
        List<ExecutionRequest> specificRequests = new ArrayList<>();

        LockData lockData = lock.lock(10);
            try {
                specificRequests.addAll(localDequeueRequest(executionRequest));
            } finally {
                try {
                    lock.unlock(lockData.getLockKey());
                } catch (Exception ex) {
                    CFBTLogger.logError(LOGGER, Queue.class.getCanonicalName(), ex.getMessage(), ex);
                }
            }
    
        return specificRequests;
    }

    /**
     * This method triggers deque request in a batch.
     * @param dbConfig The {@link DatabaseConfig} object
     * @param request The {@link ExecutionRequest} object
     * @throws Exception
     */
    public static void triggerDequeueRequest(DatabaseConfig dbConfig, ExecutionRequest request) throws Exception {
        if(StringUtils.isNotBlank(request.getQueueName())) {
            Queue queue = new Queue(dbConfig, request.getQueueName());
            queue.dequeueRequest(request);
        }
    }

    /**
     * Dequeue the requests in the top position.
     * @return {@link ExecutionRequest} List.
     */
    List<ExecutionRequest> dequeue() throws Exception {
        List<ExecutionRequest> topRequests = new ArrayList<>();

        LockData lockData = lock.lock(10);
            try {
                topRequests.addAll(localDequeue());
            } finally {
                try {
                    lock.unlock(lockData.getLockKey());
                } catch (Exception ex) {
                    CFBTLogger.logError(LOGGER, Queue.class.getCanonicalName(), ex.getMessage(), ex);
                }
            }

        return topRequests;
    }

    private void setEstimatedStartTime(ExecutionRequest request, ExecutionRequest prevRequest) throws Exception {
        request.setEstimatedStartTime(estimate.estimatedStartTime(prevRequest));
        dao.updatePosition(request);
    }

    /**
     * Move the request up or down from current position.
     *
     * Positive offset moves it down, negative moves it up.
     * This function is kind of complicated:
     *
     * currentPosition is the position where the request begins.
     * newPosition is the position where the request will end up.
     * startPosition will be the currentPosition if the request is moving down the queue.
     * startPosition will be the newPosition if the request is moving up the queue.
     * endPosition will be the newPosition if the request is moving down the queue.
     * endPosition will be the currentPosition if the request is moving down the queue.
     *
     * If the startPosition is the newPosition, we'll set the request to this position and increment
     * all requests between startPosition and newPosition.
     *
     * If the endPosition is the newPosition, we'll set the request to this position and decrement
     * all requests between the startPosition and newPosition.
     *
     * Batches complicate this a bit:
     *
     * 1) If a batch resides in the newPosition and the newPosition is the startPosition (the request is moving up the queue),
     *  then, we'll either try to batch the newPosition into the batch, or push the batch down and check if any
     *  manifests in the old batch can be batched with the new manifest that's now in front of it in the queue.
     *
     * 2) If a batch resides in the newPosition and the newPosition is the endPosition (the request is moving down the queue),
     * then, we'll try to batch the request if possible, if not, it will be placed after the batch.
     *
     * @param request {@link ExecutionRequest} to change position.
     * @param offset Moves down the queue if positive, up the queue if negative.
     * @return The modified ExecutionRequest.
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws TimeoutException
     */
    public ExecutionRequest changePosition(ExecutionRequest request, int offset) throws Exception {
        if (offset == 0) {
            return request;
        }

        LockData lockData = lock.lock(10);
            try {
                List<ExecutionRequest> queuedRequests = dao.getPositionedPendedRequests(queueName);
                List<ExecutionRequest> runningReqests = dao.getByStatus(ExecutionRequest.Status.runningStatuses());
                Batch runningBatch = new Batch(runningReqests, db, null, estimate);

                queuedRequests.sort((r1, r2) ->  r1.getPosition() - r2.getPosition());
                List<Batch> batchedList = Batch.createListOfBatches(queuedRequests, db, runningBatch, false, estimate);

                if (queuedRequests.isEmpty()) {
                    throw new IllegalStateException("The queue is empty");
                }

                int currentPosition = request.getPosition();
                int newPosition = currentPosition + offset;

                if (newPosition < 1 || newPosition > batchedList.get(batchedList.size() - 1).getPosition()) {
                    throw new IllegalArgumentException("Offset pushes the queue off the queue.");
                }

                // The startPosition becomes the newPosition if the request is moving up in the queue.
                int startPosition = currentPosition > newPosition ? newPosition : currentPosition;
                int endPosition = currentPosition < newPosition ? newPosition : currentPosition;

                boolean inRange = true;
                int index = 0;
                boolean goingIntoBatch = false;
                boolean leavingBatch = false;
                int currentIndex = 0;
                int newIndex = 0;
                Batch newBatch = null;

                while(inRange && index < batchedList.size()) {
                    Batch thisBatch = batchedList.get(index);
                    if (startPosition == thisBatch.getPosition()) {
                        if (newPosition == startPosition) {
                            if (thisBatch.shouldBatch(request)) {
                                goingIntoBatch = true;
                                thisBatch.addRequest(request, true);
                                newBatch = thisBatch;
                            } else {
                                // We can't batch into the new position, so just take it.
                                request.setPosition(thisBatch.getPosition());
                                newBatch = new Batch(request, db, thisBatch.prevBatch(), false, false, estimate);
                            }
                            newIndex = index;
                        } else if (currentPosition == startPosition) {
                            if (thisBatch.batchedRequests().size() > 1) {
                                leavingBatch = true;
                            }

                            thisBatch.remove(request);
                            currentIndex = index;
                        }
                    } else if (endPosition == thisBatch.getPosition()) {
                        inRange = false;
                        if (newPosition == endPosition) {
                            if (thisBatch.shouldBatch(request)) {
                                goingIntoBatch = true;
                                thisBatch.addRequest(request, true);
                                newBatch = thisBatch;
                            } else {
                                // We can't batch into the new position, so just take it.
                                request.setPosition(thisBatch.getPosition());
                                if (leavingBatch) {
                                    newBatch = new Batch(request, db, thisBatch.prevBatch(), false, false, estimate);
                                } else {
                                    newBatch = new Batch(request, db, thisBatch, false, false, estimate);
                                }
                            }
                            newIndex = index;
                        } else if (currentPosition == endPosition) {
                            if (thisBatch.batchedRequests().size() > 1) {
                                leavingBatch = true;
                            }

                            thisBatch.remove(request);
                            currentIndex = index;
                        }
                    }
                    index++;
                }

                int startIndex = 0;
                int endIndex = 0;
                boolean increment = false;
                boolean calcLast = false;
                if (goingIntoBatch && !leavingBatch) {
                    startIndex = currentIndex;

                    // Only need to do this if the startIndex is not on the end of the list.
                    if (currentIndex < batchedList.size() - 1 ) {
                        batchedList.get(currentIndex + 1).setPrevBatch(batchedList.get(currentIndex).prevBatch());
                    }
                    endIndex = batchedList.size() - 1;
                } else if (!goingIntoBatch && leavingBatch) {
                    startIndex = newIndex;
                    endIndex = batchedList.size() - 1;
                    increment = true;

                    if (currentIndex < newIndex) {
                        newBatch.setPrevBatch(batchedList.get(newIndex).prevBatch());
                        batchedList.get(newIndex).setPrevBatch(newBatch);
                    } else {
                        newBatch.setPrevBatch(batchedList.get(newIndex).prevBatch());
                        batchedList.get(newIndex).setPrevBatch(newBatch);
                    }
                    setEstimatedStartTime(request, newBatch.prevBatch().getLongestRequest());
                } else if (!goingIntoBatch & !leavingBatch) {
                    startIndex = (currentIndex < newIndex) ? currentIndex : newIndex;
                    endIndex = (currentIndex > newIndex) ? currentIndex : newIndex;
                    increment = (newIndex < currentIndex);

                    // Going down.
                    if (currentIndex < newIndex) {
                        batchedList.get(currentIndex + 1).setPrevBatch(batchedList.get(currentIndex).prevBatch());
                        newBatch.setPrevBatch(batchedList.get(newIndex));

                        if (newIndex < batchedList.size() - 1) {
                            batchedList.get(newIndex  + 1).setPrevBatch(newBatch);
                        }
                        calcLast = true;
                    // Going up.
                    } else {
                        newBatch.setPrevBatch(batchedList.get(newIndex).prevBatch());
                        batchedList.get(newIndex).setPrevBatch(newBatch);

                        if (currentIndex < batchedList.size() - 1) {
                            batchedList.get(currentIndex + 1).setPrevBatch(batchedList.get(currentIndex).prevBatch());
                        }
                        setEstimatedStartTime(request, newBatch.prevBatch().getLongestRequest());
                    }
                }

                for (index = startIndex; index <= endIndex; index++) {
                    if (increment) {
                        batchedList.get(index).incrementPosition();
                    } else {
                        batchedList.get(index).decrementPosition();
                    }
                }

                if (calcLast) {
                    setEstimatedStartTime(request, newBatch.prevBatch().getLongestRequest());
                }

                SpecialMessage special = new SpecialMessage(db, queueName);
                special.addSpecialMessage(request);

            } finally {
                try {
                    lock.unlock(lockData.getLockKey());
                } catch (Exception ex) {
                    CFBTLogger.logError(LOGGER, Queue.class.getCanonicalName(), ex.getMessage(), ex);
                }
            }

        return request;
    }

    /**
     * Provides a method to adjust estimatedStartTime based on something happening to the running request
     * @throws Exception 
     */
    public void adjustEstimatedStartTime() throws Exception {
        lock.lockAsync(10, (lockData) -> {
            try {
                Batch runningBatch = null;
                List<ExecutionRequest> runningRequests = dao.getByStatusAndQueue(ExecutionRequest.Status.runningStatuses(), queueName);

                if (!runningRequests.isEmpty()) {
                    runningBatch = new Batch(runningRequests, db, null, estimate);
                }
                List<ExecutionRequest> queuedRequests = dao.getPositionedPendedRequests(queueName);

                queuedRequests.sort((r1, r2) ->  r1.getPosition() - r2.getPosition());
                List<Batch> batchedList = Batch.createListOfBatches(queuedRequests, db, runningBatch, true, estimate);
                 
                for (Batch thisBatch : batchedList) {
                    dao.updateEstimatedStartTime(thisBatch.batchedRequests());
                }
            } catch (Exception ex) {
                CFBTLogger.logError(LOGGER, Queue.class.getCanonicalName(), ex.getMessage(), ex);
            } finally {
                try {
                    lock.unlock(lockData.getLockKey());
                } catch (Exception ex) {
                    CFBTLogger.logError(LOGGER, Queue.class.getCanonicalName(), ex.getMessage());
                }
            }
        });
    }
}
