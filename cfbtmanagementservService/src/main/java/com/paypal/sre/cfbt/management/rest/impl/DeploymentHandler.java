package com.paypal.sre.cfbt.management.rest.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.slf4j.LoggerFactory;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.Status;
import com.paypal.sre.cfbt.data.execapi.ExtendDeployment;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions.Message;
import com.paypal.sre.cfbt.lock.DBLock;
import com.paypal.sre.cfbt.lock.LockData;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.dal.ReleaseTestRepo;
import com.paypal.sre.cfbt.management.timeout.Timeout;
import com.paypal.sre.cfbt.request.Queue;
import com.paypal.sre.cfbt.request.QueueMonitor;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.DateUtil;

/**
 * Handles the deployment update from the client.
 *
 */
public class DeploymentHandler {

    private final DBLock lock;
    private final DatabaseConfig dbConfig;
    private final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DeploymentHandler.class);
    private final String queueName;
    private final String DeploymentLock = "Deployment_Update_Lock";

    /**
     * Method to initialize the {@link DBLock} and {@link DatabaseConfig}.
     * @param db
     * @param queueName
     * @throws java.lang.Exception
     */
    public DeploymentHandler(DatabaseConfig db, String queueName) throws Exception {
        this.lock = new DBLock(db, DeploymentLock);
        this.dbConfig = db;
        this.queueName = queueName;
    }

    private ExecutionRequest triggerStateTransition(ExecutionRequest request, Message message) throws Exception {
        RequestThreadHandler requestThreadHandler = new RequestThreadHandler(dbConfig, queueName);
        return (requestThreadHandler.triggerEvent(request, message));
    }

    private ExecutionRequest triggerStateTransition(List<ExecutionRequest> requests, Message message) throws Exception {
        Queue queue = new Queue(dbConfig, requests.get(0).getQueueName());
        Scheduler scheduler = new Scheduler();
        QueueMonitor monitor = new QueueMonitor(queue, scheduler, dbConfig, queueName);
        return monitor.triggerStateEvent(requests, message);
    }

    /**
     * This is internal implementation to handle a deploy complete call for a
     * release test.
     * 
     * @param releaseTest instance of {@link ReleaseTest}
     * @return {@link ReleaseTest}
     * @throws Exception instance of {@link Exception}
     */
    public ReleaseTest deployComplete(ReleaseTest releaseTest) throws Exception {
        ReleaseTest transitionedRelease = null;

        // Throws an exception if we can't get the lock within the alloted time.
        LockData lockData = lock.lock(10);
            try {
                ExecutionRequest currentRequest = ExecutionRequest.createFromReleaseTest(releaseTest);
                if (currentRequest == null) {
                    throw new IllegalStateException(
                            "This request is not in the queue/ not active, id = " + releaseTest.getExecutionRequest().getId());
                }
                ExecutionRequest transitionedRequest = null;
                if (currentRequest.thisIsRunNow()) {
                    transitionedRequest = triggerStateTransition(currentRequest, Message.DEPLOY_COMPLETE);
                } else {
                    transitionedRequest = deployCompleteQueueRequest(currentRequest);
                }
                transitionedRelease = ReleaseTest.createFromExecutionRequest(transitionedRequest);
            } finally {
                try {
                    lock.unlock(lockData.getLockKey());
                } catch (Exception ex) {
                    CFBTLogger.logError(LOGGER, DeploymentHandler.class.getCanonicalName(), ex.getMessage());
                }
            }

        return transitionedRelease;
    }

    /**
     * This is internal implementation to handle a deploy complete call for a
     * release test in the Queue.
     *
     * @param currentRequest The instance of {@link ExecutionRequest}
     * @return The {@link ExecutionRequest} object
     * @throws Exception instance of {@link Exception}
     */
    private ExecutionRequest deployCompleteQueueRequest(ExecutionRequest currentRequest) throws Exception {
        ExecutionRequest transitionedRequest = null;
        List<ExecutionRequest> deployWaitingRequestsInQueue = new ArrayList<>();
        List<ExecutionRequest> deployCompleteRequests = new ArrayList<>();
        List<String> deployingRequestIds = new ArrayList<>();
        ExecutionRequestDAO executionRequestDAO = ExecutionRequestDAO.getInstance(this.dbConfig.getConnectionFactory());
        // Load all requests currently in-progress.
        List<String> statusList = new ArrayList<>();
        statusList.add(Status.DEPLOY_WAITING.toString());
        statusList.add(Status.DEPLOYING.toString());
        List<ExecutionRequest> requestList = executionRequestDAO.getByStatusAndQueue(statusList, queueName);
        // Used to adjust estimated start time.

        boolean hasFastPassInQueue = false;
        // Check the current requests is a deploy only request
        boolean isCurrentFastPass = currentRequest.checkFastPass();
        for (ExecutionRequest executionRequest : requestList) {

            if (ExecutionRequest.Status.DEPLOY_WAITING.equals(executionRequest.getStatus())) {
                deployWaitingRequestsInQueue.add(executionRequest);
            } else if (ExecutionRequest.Status.DEPLOYING.equals(executionRequest.getStatus())) {
                if (!hasFastPassInQueue) {
                    hasFastPassInQueue = executionRequest.checkFastPass();
                }
                deployingRequestIds.add(executionRequest.getId());
            }
        }
        boolean lastRequestFound = false;
        if (deployingRequestIds.size() == 1 && deployingRequestIds.get(0).equals(currentRequest.getId())) {
            lastRequestFound = true;
        }
        // Check for fast pass requests in queue and current request has tests
        if (!isCurrentFastPass && !lastRequestFound) {
            // Transition to deploy waiting state
            transitionedRequest = triggerStateTransition(currentRequest, Message.DEPLOY_WAITING);
        } else {
            // Is last request found in the queue?
            if (deployingRequestIds.size() == 1 && !deployWaitingRequestsInQueue.isEmpty()) {
                // Last request found, add the deploy waiting request to the list
                deployCompleteRequests.addAll(deployWaitingRequestsInQueue);
            }

            if (!isCurrentFastPass) {
                //If current request is not fast pass then add it to deploy complete request list and then transition all requests to in-progress.
                deployCompleteRequests.add(currentRequest);
                if (deployCompleteRequests.size() == 1) {
                    //This last single request is in Deploying state & there is no other deploy waiting request
                    transitionedRequest = triggerStateTransition(deployCompleteRequests.get(0), Message.DEPLOY_COMPLETE);
                } else {
                    transitionedRequest = triggerStateTransition(deployCompleteRequests, Message.DEPLOY_COMPLETE);
                }
            } else {
                //If current request is fast pass then transition all other deploy waiting tests to deploy complete (to start test execution) and then transition single current fast pass request
                if (lastRequestFound && !deployCompleteRequests.isEmpty()) {
                    triggerStateTransition(deployCompleteRequests, Message.DEPLOY_COMPLETE);
                }
                transitionedRequest = triggerStateTransition(currentRequest, Message.DEPLOY_COMPLETE);
            }
        }
        Queue queue = new Queue(dbConfig, queueName);
        queue.adjustEstimatedStartTime();
        return transitionedRequest;
    }

    /**
     * Method used to transition any isolated deploy waiting request in the queue.
     * There is a possibility in case if the last request in a batch deployment is
     * timed out, then if there is any deploy waiting request will be isolated.
     * This method helps in finding the isolated deploy waiting request and transition
     * to deploy complete.
     */
    private void findAndTransitionDeployWaitingInBatch() throws Exception {
        this.lock.lockAsync(10, (lockData) -> {
            try {
                int deployingCount = 0;
                List<ExecutionRequest> deployWaitingRequestList = new ArrayList<>();
                ExecutionRequestDAO executionRequestDAO = ExecutionRequestDAO
                        .getInstance(this.dbConfig.getConnectionFactory());
                List<String> statusList = new ArrayList<>();
                statusList.add(Status.DEPLOY_WAITING.toString());
                statusList.add(Status.DEPLOYING.toString());
                List<ExecutionRequest> requestList = executionRequestDAO.getByStatusAndQueue(statusList, queueName);
                for (ExecutionRequest executionRequest : requestList) {
                    if (ExecutionRequest.Status.DEPLOYING.equals(executionRequest.getStatus())) {
                        deployingCount++;
                    } else if (ExecutionRequest.Status.DEPLOY_WAITING.equals(executionRequest.getStatus())) {
                        deployWaitingRequestList.add(executionRequest);
                    }
                }
                // In a batch, when we have one deploying request which is about to be timed-out or cancelled
                // and other release test with test is in deploy waiting state, then transition the deploy waiting
                // request to deploy complete.
                if (deployingCount == 0 && !deployWaitingRequestList.isEmpty()) {
                    // Transition all requests in batch to deploy complete state
                    triggerStateTransition(deployWaitingRequestList, Message.DEPLOY_COMPLETE);
                }
            } catch (Exception ex) {
                CFBTLogger.logError(LOGGER, DeploymentHandler.class.getCanonicalName(), ex.getMessage());
            } finally {
                try {                    
                    lock.unlock(lockData.getLockKey());
                } catch (Exception ex) {
                    CFBTLogger.logError(LOGGER, DeploymentHandler.class.getCanonicalName(), ex.getMessage());
                }
            }
        });
    }

    /**
     * This method processes deploy waiting requests in a batch.
     * @param dbConfig The {@link DatabaseConfig} object
     * @param queueName The queue-name of a request.
     * @throws Exception
     */
    public static void processDeployWaitingRequests(DatabaseConfig dbConfig, String queueName) throws Exception {
        if (StringUtils.isNotBlank(queueName)) {
            DeploymentHandler deploymentHandler = new DeploymentHandler(dbConfig, queueName);
            deploymentHandler.findAndTransitionDeployWaitingInBatch();
        }
    }

    /**
     * This method triggers action during a extend deploy request.
     *
     * @param releaseTest             instance of {@link ReleaseTest}
     * @param extendDeploymentRequest instance of {@link ExtendDeployment}
     * @throws Exception
     */
    public void extendDeploy(ReleaseTest releaseTest, ExtendDeployment extendDeploymentRequest) throws Exception {
        int extendDeployDuration = extendDeploymentRequest.getExtendedTimeInSeconds();
        String startTime;
        Timeout timeoutType;

        if (!(ReleaseTest.IntegrationModel.FULL_DEPLOYMENT.equals(releaseTest.getIntegrationModel()))) {
            if (null == releaseTest.getExecutionRequest().getStatus()) {
                throw new IllegalStateException("Cannot extend deploy if we're not in a state where we're waiting for it");
            } else switch (releaseTest.getExecutionRequest().getStatus()) {
                case DEPLOYING:
                    startTime = releaseTest.getDeploymentStart();
                    timeoutType = Timeout.DEPLOYMENT_TIMEOUT;
                    break;
                case TESTING_COMPLETE:
                    startTime = releaseTest.getRollbackStart();
                    timeoutType = Timeout.REQUEST_COMPLETION_TIMEOUT;
                    break;
                default:
                    throw new IllegalStateException("Cannot extend deploy if we're not in a state where we're waiting for it");
            }

            if (extendDeployDuration < 10) {
                // Add some buffer since our timeout scheduler runs every 10 seconds.
                extendDeployDuration = extendDeployDuration + 10;
            }

            int newSLA = getTimeToDeadline(startTime, releaseTest.getDeploymentEstimatedDuration()) + extendDeployDuration;
            Scheduler scheduler = new Scheduler();
            scheduler.updateScheduleTime(releaseTest.getExecutionRequest().getId(), timeoutType.toString(),
                    DateUtil.currentDateTimeUTC().plusSeconds(newSLA).toString(), false, this.dbConfig.getConnectionFactory());
        }
        releaseTest = new ReleaseTestRepo(this.dbConfig.getConnectionFactory()).updateDeploymentEstimatedDuration(
                this.dbConfig.getConnectionFactory(), releaseTest.getId(),
                extendDeploymentRequest.getExtendedTimeInSeconds());

        if(releaseTest != null && releaseTest.getExecutionRequest() != null && StringUtils.isNotBlank(releaseTest.getExecutionRequest().getQueueName())) {
            Queue queue = new Queue(dbConfig, queueName);
            queue.adjustEstimatedStartTime();
        }
    }

    /**
     * Get the time (in seconds) to the specified deadline from the current time.
     *
     * @param startTime                  - the start time from which to calculate
     *                                   the deadline
     * @param maxTimeToDeadlineInSeconds - the maximum time to deadline in seconds
     *                                  from the start time.
     * @return the time (in seconds) to the deadline.
     */
    private static int getTimeToDeadline(String startTime, int maxTimeToDeadlineInSeconds) {
        int timeToDeadline = maxTimeToDeadlineInSeconds - getElapsedTimeInSeconds(startTime);

        if (timeToDeadline > 0) {
            return timeToDeadline;
        }

        return 0;
    }

    /**
     * The calculated duration (in seconds) between the specified start time and the
     * current time.
     *
     * @param startTime - The start time from which to calculate the elapsed time.
     * @return The elapsed duration in seconds.
     */
    private static int getElapsedTimeInSeconds(String startTime) {
        DateTime requestStartTime = DateUtil.dateTimeUTC(startTime);
        DateTime currentTime = DateUtil.currentDateTimeUTC();

        Period elapsedTime = new Period(requestStartTime, currentTime);

        return elapsedTime.getHours() * 3600 + elapsedTime.getMinutes() * 60 + elapsedTime.getSeconds();
    }
}
