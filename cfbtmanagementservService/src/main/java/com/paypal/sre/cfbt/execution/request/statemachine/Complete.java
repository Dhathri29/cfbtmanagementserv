/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.execution.request.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.Status;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.executor.Executor;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.dal.ReleaseTestDAO;
import com.paypal.sre.cfbt.management.dal.TestExecutionDAO;
import com.paypal.sre.cfbt.management.timeout.Timeout;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import com.paypal.sre.cfbt.shared.CFBTLogger;

/**
 * The final state in the state machine where the status is fully decided.
 */
public class Complete extends RequestState {
    private final Logger LOGGER = LoggerFactory.getLogger(Complete.class);
    private final ExecutionRequest request;
    private final ExecutionRequestDAO requestDAO;
    private final TestExecutionDAO executionDAO;
    private final ReleaseTestDAO releaseDAO;
    private final Scheduler scheduler;
    private final DatabaseConfig db;

    public Complete(ExecutionRequest request, DatabaseConfig db, Scheduler scheduler) {
        this.request = request;
        this.requestDAO = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
        this.executionDAO = TestExecutionDAO.getInstance(db.getConnectionFactory());
        this.releaseDAO = ReleaseTestDAO.getInstance(db.getConnectionFactory());
        this.scheduler = scheduler;
        this.db = db;
    }

    /**
     * This is to cover a few different transitions. If we've gotten here by skipping test executions (if result status
     * is still in progress), we need to adjudicate. Otherwise, we just need to complete the status.
     * 
     * @throws Exception
     */
    private ExecutionRequest completeResult() throws Exception {
        CFBTLogger.logInfo(LOGGER, Complete.class.getCanonicalName(), "completeResult");
        RequestStatus requestStatus = new RequestStatus(request, requestDAO, executionDAO);
        requestStatus.complete();
        return requestDAO.updateResult(request, requestStatus.releaseRecommendation(), requestStatus.resultStatus(),
                requestStatus.status(), requestStatus.requestRetry());
    }

    /**
     * This call will update the status and remove the request from the queue.
     * 
     * @return {@link ExecutionRequest}
     * @throws java.lang.Exception
     *             On transition
     */
    @Override
    public ExecutionRequest complete() throws Exception {
        if(request.isLegacyIntegrationModel()) {
            scheduler.cancel(this.request.getId(), Timeout.REQUEST_COMPLETION_TIMEOUT.toString(), this.db.getConnectionFactory());
        }
        if (ExecutionRequest.ResultStatus.IN_PROGRESS.equals(request.getResultStatus())) {
            return completeResult();
        } else {
            ReleaseTest releaseTest = releaseDAO.markComplete(ReleaseTest.createFromExecutionRequest(request));

            return ExecutionRequest.createFromReleaseTest(releaseTest);
        }
    }

    /**
     * This method acts upon timeout.
     * @param action {@link ReleaseTest.Action}
     * @return {@link ExecutionRequest}
     * @throws java.lang.Exception
     */
    @Override
    public ExecutionRequest timeout(ReleaseTest.Action action) throws Exception {
        CFBTLogger.logInfo(LOGGER, "REQUEST_TIMEOUT", "Request timeout for "+ this.request.getId());

        // Need to record the action before a transition occurs because we need the state context of where
        // we came from for cancel to signal appropriately to customers.
        if (request.getReleaseTest() != null && action != null) {
            releaseDAO.completeAction(request, action);
        }

        if (ExecutionRequest.ResultStatus.IN_PROGRESS.equals(request.getResultStatus())) {
            RequestStatus requestStatus = new RequestStatus(request, requestDAO, executionDAO);
            requestStatus.complete();

            return requestDAO.updateResult(request, requestStatus.releaseRecommendation(), requestStatus.resultStatus(),
                    requestStatus.status(), requestStatus.requestRetry());
        } else {
            requestDAO.transitionToComplete(request);
            return request;
        }
    }

    /**
     * This call will update the status and remove the request from the queue.
     * 
     * @return {@link ExecutionRequest}
     * @throws java.lang.Exception
     *             On transition
     */
    @Override
    public ExecutionRequest testsComplete() throws Exception {
        scheduler.cancel(this.request.getId(), Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString(),
                this.db.getConnectionFactory());
        return completeResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutionRequest deployComplete() throws Exception {
        if(this.request.isLegacyIntegrationModel()) {
            // Cancel the deployment timeout scheduler
            scheduler.cancel(this.request.getId(), Timeout.DEPLOYMENT_TIMEOUT.toString(), this.db.getConnectionFactory());
        }
        // mark the deployment complete time as well
        releaseDAO.completeDeploy(this.request.getId());
        return completeResult();
    }

    /**
     * A transition into Complete on an abort signal.
     *
     * @return {@link ExecutionRequest}
     */
    @Override
    public ExecutionRequest abort() throws Exception {
        RequestStatus requestStatus = new RequestStatus(request, requestDAO, executionDAO);
        try {

            // Need to record the action before a transition occurs because we need the state context of where
            // we came from for cancel to signal appropriately to customers.
            if (request.getReleaseTest() != null && !request.checkCompleted()) {
                releaseDAO.completeAction(request, ReleaseTest.Action.CANCEL);
            }

            scheduler.cancel(request.getId(), Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString(), this.db.getConnectionFactory());

            // Only need to send the signal if tests are running.
            if (ExecutionRequest.Status.IN_PROGRESS.equals(request.getStatus())) {
                Executor executor = new Executor();
                executor.emergencyStop(request.getId());
            }

            requestStatus.abort(request, Status.COMPLETED);

        } catch (Exception ex) {
            throw new IllegalStateException("Error trying to halt a completed request", ex);
        }

        return completeResult();
    }

    /**
     * Assume a transition into Complete, the execution is already halted, we just need to cancel timers and pull it off
     * the queue.
     *
     * @return {@link ExecutionRequest}
     */
    @Override
    public ExecutionRequest halt() {
        try {
            if (request.getReleaseTest() != null && !request.checkCompleted()) {
                releaseDAO.completeAction(request, ReleaseTest.Action.CANCEL);
            }
            RequestStatus requestStatus = new RequestStatus(request, requestDAO, executionDAO);
            requestStatus.halt(ExecutionRequest.Status.COMPLETED);

            if (ExecutionRequest.Status.COMPLETED.equals(requestStatus.status())) {
                scheduler.cancel(this.request.getId(), Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString(),
                    this.db.getConnectionFactory());
                return requestDAO.updateResult(request, requestStatus.releaseRecommendation(), requestStatus.resultStatus(),
                    requestStatus.status(), requestStatus.requestRetry());
            } else {
                return requestDAO.updateState(request, requestStatus.status());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Error trying to halt a completed request", ex);
        }
    }
}
