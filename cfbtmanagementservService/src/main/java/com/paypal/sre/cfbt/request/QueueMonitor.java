/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import com.ebay.kernel.cal.api.CalTransaction;
import com.ebay.kernel.cal.api.sync.CalTransactionFactory;
import com.paypal.infra.util.cal.CalType;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.dataaccess.DBOpLog;
import com.paypal.sre.cfbt.execution.request.statemachine.RequestState;
import com.paypal.sre.cfbt.execution.request.statemachine.StateMachineFactory;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions.Message;
import com.paypal.sre.cfbt.lock.DBLock;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.LoggerFactory;

/**
 * The primary entity pulling requests off the queue and performing state transitions.
 */
public class QueueMonitor implements QueueObserver {
    private final Queue queue;
    private final ExecutionRequestDAO requestDAO;
    private final Scheduler scheduler;
    private final DatabaseConfig db;
    private final List<MonitorObserver> observers = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String queueName;
    private final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DBOpLog.class);
    private final DBLock lock;

    public QueueMonitor(Queue queue, Scheduler scheduler, DatabaseConfig db, String queueName) throws Exception {
        this.queue = queue;
        this.requestDAO = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
        this.scheduler = scheduler;
        this.db = db;
        this.queueName = queueName;
        this.lock = new DBLock(db, queueName + "MonitorLock");
    }

    /**
     * Actually monitor the queue events.
     */
    public void monitorQueueEvents() {
        queue.subscribe(this);
    }

    /**
     * Allow clients to subscribe to monitor events. 
     * Because everything here is asynchronous, allow clients to receive the outcome when complete.
     * @param observer {@link MonitorObserver Anyone can listen to events if they implement the observer interface. 
     */
    public void subscribe(MonitorObserver observer) {
        observers.add(observer);     
    }

    private void checkQueue() {
        try {
            lock.lockAsync(10, (lockData) -> {
                final CalTransaction calTransaction = CalTransactionFactory.create(CalType.URL.toString());

                try {
                    calTransaction.setName("CFBT.Monitor");
                    calTransaction.addData("CFBT Monitor: Checking the queue");
                    CFBTLogger.logInfo(LOGGER, QueueMonitor.class.getCanonicalName(), "Checking the queue");

                    List<ExecutionRequest> runningRequests = new ArrayList<>();
                    runningRequests.addAll(requestDAO.getByStatusAndQueue(ExecutionRequest.Status.runningStatuses(), queueName));

                    // If there are no running requests, check the queue.
                    if (runningRequests.isEmpty()) {

                        List<ExecutionRequest> requestList = queue.dequeue();
                        List<ExecutionRequest> scheduledRequests = new ArrayList<>();
                        CFBTLogger.logInfo(LOGGER, QueueMonitor.class.getCanonicalName(), "Dequeued " + requestList.size() + " requests");

                        scheduledRequests.addAll(transitionPendedRequests(requestList));

                        if (requestList.isEmpty()) {
                            // Check to make sure there aren't lingering requests in position 0.
                            List<ExecutionRequest> pendedDequeuedRequests = requestDAO.getPendingPositionZero(queueName);

                            for (ExecutionRequest request : pendedDequeuedRequests) {
                                CFBTLogger.logInfo(LOGGER, QueueMonitor.class.getCanonicalName(), "Transitioning " + request.getId());

                                scheduledRequests.add(StateMachineFactory.getNextState(request, db, scheduler,Transitions.Message.BEGIN).begin());
                            }
                        }
                        if (!scheduledRequests.isEmpty()) {
                            observers.forEach((observer) -> {
                                observer.onEvent(scheduledRequests);
                            });
                        }
                    }
                    enqueuePositionlessRequests();
                    calTransaction.setStatus("0");
                } catch (Exception ex) {
                    calTransaction.setStatus(ex);
                    CFBTLogger.logError(LOGGER, QueueMonitor.class.getName(), ex.getMessage(), ex);
                } finally {
                    calTransaction.completed();
                    try {
                        lock.unlock(lockData.getLockKey());
                    } catch (Exception ex) {
                        CFBTLogger.logError(LOGGER, QueueMonitor.class.getCanonicalName(), ex.getMessage());
                    }
                }
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Error trying to monitor queue ", ex);
        }
    }

    private void enqueuePositionlessRequests() throws Exception {
         // Check if any positionless remain.
        List<ExecutionRequest> unqueuedRequests = new ArrayList<>();
        unqueuedRequests.addAll(requestDAO.getAllPositionless(queueName));

        if (!unqueuedRequests.isEmpty()) {
            unqueuedRequests.forEach((request) -> {
                CFBTLogger.logInfo(LOGGER, QueueMonitor.class.getCanonicalName(), "Enqueueing positionless request =  " + request.getId());
                try {
                    queue.enqueue(request, request.getPriority());
                } catch (Exception ex) {
                    CFBTLogger.logError(LOGGER, QueueMonitor.class.getCanonicalName(), ex.getMessage());
                }
            });
        }
    }

    private List<ExecutionRequest> transitionPendedRequests(List<ExecutionRequest> pendedRequests) {
        List<ExecutionRequest> scheduledRequests = new ArrayList<>();

        for (ExecutionRequest request : pendedRequests) {
            final CalTransaction calTransaction = CalTransactionFactory.create(CalType.URL.toString());
            calTransaction.setName("CFBT.Monitor");
            calTransaction.addData("CFBT Monitor: transitionPendedRequests");
            CFBTLogger.logInfo(LOGGER, QueueMonitor.class.getCanonicalName(), "Transitioning " + request.getId());

            try {
                scheduledRequests.add(StateMachineFactory.getNextState(request, db, scheduler,Transitions.Message.BEGIN).begin());
                calTransaction.setStatus("0");
            } catch (Exception ex) {
                CFBTLogger.logError(LOGGER, QueueMonitor.class.getName(), ex.getMessage(), ex);
                calTransaction.setStatus(ex);
            } finally {
                calTransaction.completed();
            }
        }

        return scheduledRequests;
    }

    private void signalSubscribers(List<ExecutionRequest> requestList) {
        observers.forEach((observer) -> {
             observer.onEvent(requestList);
        });
    }

    /**
     * The result of listening to queued events.
     * 
     * @param dequeuedRequests
     */
    @Override
    public void onDequeueEvent(List<ExecutionRequest> dequeuedRequests) {
        if (!dequeuedRequests.isEmpty()) {
            signalSubscribers(transitionPendedRequests(dequeuedRequests));
        }
    }

    @Override
    public void onEnqueueEvent(List<ExecutionRequest> runningRequests) {
        executor.submit(() -> {
            try {
                signalSubscribers(runningRequests);
            } catch (Exception ex) {
                CFBTLogger.logError(LOGGER, QueueMonitor.class.getName(), ex.getMessage(), ex);
            }
        });
    }

    /**
     * Trigger a state event.
     * @param request {@link ExecutionRequest}
     * @param message The message that triggers the transition.
     * @return
     * @throws Exception
     */
    public ExecutionRequest triggerStateEvent(ExecutionRequest request, Transitions.Message message) throws Exception {
       ExecutionRequest transitionedRequest = null;

        try {
            RequestState state = StateMachineFactory.getNextState(request, db, scheduler, message);

            switch (message) {
                case COMPLETE_TESTS: {
                    transitionedRequest = state.testsComplete();
                    break;
                }
                case COMPLETE: {
                    transitionedRequest = state.complete();
                    break;
                }
                case DEPLOY_COMPLETE: {
                    transitionedRequest = state.deployComplete();
                    break;
                }
                case ABORT: {
                    transitionedRequest = state.abort();
                    break;
                }
                case HALT: {
                    transitionedRequest = state.halt();
                    break;
                }
                case DEPLOY_WAITING: {
                    transitionedRequest = state.deployWaiting();
                    break;
                }
                default:
                    break;
            }
        } finally {
            checkQueue();
        }

        return transitionedRequest;
    }

    /**
     * Trigger a state event. (Note: Currently it supports only DEPLOY_COMPLETE for in-progress state)
     * @param requests {@link ExecutionRequest}
     * @param message The message that triggers the transition.
     * @return
     * @throws Exception
     */
    public ExecutionRequest triggerStateEvent(List<ExecutionRequest> requests, Transitions.Message message) throws Exception {
        ExecutionRequest transitionedRequest = null;

        try {
            RequestState state = StateMachineFactory.getNextState(requests, db, scheduler, message);

            switch (message) {
                case DEPLOY_COMPLETE: {
                    transitionedRequest = state.deployComplete();
                    break;
                }
                default:
                    break;
            }
        } finally {
            checkQueue();
        }

        return transitionedRequest;
    }

    /**
     * This method triggers timeout.
     * @param request Instance of {@link ExecutionRequest}.
     * @param action Action to that would result due to timeout.
     */
    public void triggerTimeout(ExecutionRequest request, ReleaseTest.Action action) {
        try {
            ExecutionRequest loadedRequest = requestDAO.getById(request.getId());

            if (loadedRequest == null) {
                throw new IllegalStateException("This request is not found");
            }
            RequestState state = StateMachineFactory.getNextState(request, db, scheduler, Message.TIMEOUT);
            state.timeout(action);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            CFBTLogger.logError(LOGGER, QueueMonitor.class.getCanonicalName(), ex.getMessage());
        }
        checkQueue();
    }
}
