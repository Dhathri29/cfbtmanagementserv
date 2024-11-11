/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */

package com.paypal.sre.cfbt.execution.request.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.ReleaseRecommendation;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.Status;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.executor.Executor;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.dal.ReleaseTestDAO;
import com.paypal.sre.cfbt.management.dal.TestExecutionDAO;
import com.paypal.sre.cfbt.management.timeout.Timeout;
import com.paypal.sre.cfbt.request.Queue;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.util.ArrayList;

/**
 * All tests are complete, but we need to pause for a possible rollback.
 */
public class CompleteTests extends RequestState {
    private final ExecutionRequest request;
    private final ExecutionRequestDAO requestDAO;
    private final TestExecutionDAO executionDAO;
    private final ReleaseTestDAO releaseDAO;
    private final Scheduler scheduler;
    private final DatabaseConfig db;
    private final Executor executorService = new Executor();
    private final Logger LOGGER = LoggerFactory.getLogger(CompleteTests.class);

    public CompleteTests(ExecutionRequest request, DatabaseConfig db, Scheduler scheduler) {
        this.request = request;
        requestDAO = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
        executionDAO = TestExecutionDAO.getInstance(db.getConnectionFactory());
        releaseDAO = ReleaseTestDAO.getInstance(db.getConnectionFactory());
        this.scheduler = scheduler;
        this.db = db;
    }

    private void markRollbackStart() throws Exception {
        CFBTLogger.logInfo(LOGGER, CompleteTests.class.getCanonicalName(), "markRollbackStart : "+ this.request.getId());

        ReleaseTest releaseTest = releaseDAO.readByExecutionRequestId(request.getId());

        releaseTest.setRollbackStart(DateUtil.currentDateTimeISOFormat());
        // Reset the deployment estimated duration to initial before rollback start.
        releaseTest.setDeploymentEstimatedDuration(releaseTest.getInitialDeploymentEstimatedDuration());
        releaseDAO.markRollbackStart(releaseTest);
        if(this.request.isLegacyIntegrationModel()) {
            int estimatedDeployDuration = this.request.getReleaseTest().getDeploymentEstimatedDuration();
            String timeoutAt = DateUtil.currentDateTimeUTC().plusSeconds(estimatedDeployDuration).toString();

            // Schedule a timeout for rollback
            scheduler.schedule(this.request.getId(), Timeout.REQUEST_COMPLETION_TIMEOUT.toString(), timeoutAt,
                    this.db.getConnectionFactory());
        }
        // Need to update the queue.
        Queue queue = Queue.releaseVettingQueue(db);
        queue.adjustEstimatedStartTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutionRequest testsComplete() throws Exception {
        CFBTLogger.logInfo(LOGGER, CompleteTests.class.getCanonicalName(), "ID = " + request.getId());

        //Cancel the timeout schedule for test execution request.
        scheduler.cancel(this.request.getId(), Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString(), this.db.getConnectionFactory());
        //Cancel the timeout schedule for stuck tests
        scheduler.cancel(this.request.getId(), Timeout.STUCK_TEST_TIMEOUT.toString(), this.db.getConnectionFactory());

        RequestStatus requestStatus = new RequestStatus(request, requestDAO, executionDAO);

        requestStatus.testsComplete();

        requestDAO.updateResult(request, requestStatus.releaseRecommendation(), requestStatus.resultStatus(),
                requestStatus.status(), requestStatus.requestRetry());

        if (ExecutionRequest.ReleaseRecommendation.DO_NOT_RELEASE.equals(requestStatus.releaseRecommendation())) {
            markRollbackStart();
        }

        return request;
    }

    /**
     * Halt and abort are doing the same thing at this stage. Kill any current timers, start another, waiting for
     * rollback.
     * 
     * @return {@link ExecutionRequest}
     * @throws java.lang.Exception
     */
    @Override
    public ExecutionRequest abort() throws Exception {
        scheduler.cancel(this.request.getId(), Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString(), db.getConnectionFactory());

        RequestStatus requestStatus = new RequestStatus(request, requestDAO, executionDAO);

        // Before we make the transition, mark the action which requires the from state
        // information to record properly.
        if (request.getReleaseTest() != null) {
            releaseDAO.completeAction(request, ReleaseTest.Action.CANCEL);
        }

        requestStatus.abort(request, ExecutionRequest.Status.TESTING_COMPLETE);
        requestDAO.updateResult(request, requestStatus.releaseRecommendation(), requestStatus.resultStatus(), requestStatus.status(), requestStatus.requestRetry());

        executorService.emergencyStop(request.getId());

        // If it's a Release Recommendation, need to transition immediately to complete.
        if (ReleaseRecommendation.DO_NOT_RELEASE.equals(requestStatus.releaseRecommendation())) {
            markRollbackStart();
        } else {
            requestDAO.updateResult(request, requestStatus.releaseRecommendation(), requestStatus.resultStatus(),
                    Status.COMPLETED, requestStatus.requestRetry());
        }

        return request;
    }

    /**
     * Halt and abort are doing the same thing at this stage. Kill any current timers, start another, waiting for
     * rollback.
     *
     * @return {@link ExecutionRequest}
     * @throws java.lang.Exception
     *             on problems halting.
     */
    @Override
    public ExecutionRequest halt() throws Exception {
        RequestStatus requestStatus = new RequestStatus(request, requestDAO, executionDAO);
        requestStatus.halt(ExecutionRequest.Status.TESTING_COMPLETE);

        // If tests are still running, let the test finished, then transition.
        if (ExecutionRequest.Status.HALT_IN_PROGRESS.equals(requestStatus.status())) {
             requestDAO.updateStatus(request.getId(), requestStatus.status());
             request.setStatus(requestStatus.status());
            return request;
        }
        scheduler.cancel(this.request.getId(), Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString(), db.getConnectionFactory());

        // If it's a Release Recommendation, need to transition immediately to complete.
        if (ReleaseRecommendation.RELEASE.equals(request.getReleaseRecommendation())) {
            updateStatus(ExecutionRequest.Status.COMPLETED);
        } else {
            markRollbackStart();;
        }

        return request;
    }

    /**
     * This method is invoked when test execution request is Timed out.
     * 
     * @return instance of {@link ExecutionRequest}
     * @throws Exception instance of {@link Exception}
     */
    @Override
    public ExecutionRequest complete() throws Exception {
        scheduler.cancel(this.request.getId(), Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString(), db.getConnectionFactory());

        RequestStatus requestStatus = new RequestStatus(request, requestDAO, executionDAO);
        // Before we make the transition, mark the action which requires the from state
        // information to record properly.
        if (this.request.getReleaseTest() != null) {
            requestStatus.testsComplete();
            if (ReleaseRecommendation.DO_NOT_RELEASE.equals(this.request.getReleaseRecommendation())) {
                markRollbackStart();
            }
        } else {
            requestStatus.complete();
        }
        return requestDAO.updateResult(request, requestStatus.releaseRecommendation(), requestStatus.resultStatus(),
                requestStatus.status(), requestStatus.requestRetry());
    }

    /**
     * This method acts upon timeout.
     * @param action
     * @return {@link ExecutionRequest}
     */
    @Override
    public ExecutionRequest timeout(ReleaseTest.Action action) throws Exception {
        CFBTLogger.logInfo(LOGGER, "TEST_EXECUTION_REQUEST_TIMEOUT", "Test exeution request time out for : "+ this.request.getId());
        abort();
        return request;
    }

    /**
     * Update the complete request status based on an evaluation of the list of execution requests.
     *
     * @param status
     *            Indicates whether the request was aborted or not - should be in a final state.
     * @throws Exception
     *             when errors received from the database
     */
    private void updateStatus(ExecutionRequest.Status status) throws Exception {
        requestDAO.updateStatus(request.getId(), status);
        request.setStatus(status);
    }
}
