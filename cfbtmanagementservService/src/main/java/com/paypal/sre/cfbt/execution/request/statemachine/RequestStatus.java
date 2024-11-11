package com.paypal.sre.cfbt.execution.request.statemachine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.management.dal.*;
import com.paypal.sre.cfbt.management.rest.impl.RequestThreadHandler;
import com.paypal.sre.cfbt.management.timeout.Timeout;
import com.paypal.sre.cfbt.management.timeout.TimeoutManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.paypal.sre.cfbt.data.execapi.Execution;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.ReleaseRecommendation;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.ResultStatus;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.Status;
import com.paypal.sre.cfbt.data.execapi.Test;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO.Statistics;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.CFBTLogger;


/**
 * Manages the persistence of Request Status state.
 */
public class RequestStatus {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestStatus.class);
    private final ExecutionRequest request;
    private final ExecutionRequestDAO requestDAO;
    private final TestExecutionDAO executionDAO;
    private ResultStatus resultStatus;
    private ReleaseRecommendation releaseRecommendation;
    private Status status;
    private boolean requestRetry;

    public RequestStatus(ExecutionRequest request, ExecutionRequestDAO requestDAO, TestExecutionDAO executionDAO) {
        this.request = request;
        this.requestDAO = requestDAO;
        this.executionDAO = executionDAO;
    }

    public ResultStatus resultStatus() {
        return resultStatus;
    }

    public ReleaseRecommendation releaseRecommendation() {
        return releaseRecommendation;
    }

    public Status status() {
        return status;
    }

    public boolean requestRetry() { return requestRetry;}
 
    /**
     * Halt the request.
     * @param status
     * @throws java.lang.Exception 
     */
    public void halt(Status status) throws Exception {
        // If we've already adjudicated...
        if (!ResultStatus.IN_PROGRESS.equals(request.getResultStatus())) {
            resultStatus = request.getResultStatus();
            releaseRecommendation = request.getReleaseRecommendation();
            if(request.getReleaseTest() != null) {
                requestRetry = request.getReleaseTest().getRequestRetry();
            }
            this.status = status;

            return;
        }

        ExecutionRepository executionRepo = new ExecutionRepository();
        List<String> requestIdsForSkippedExecutions = executionRepo.skipPendingExecutions(request.getId(), executionDAO);

        List<Execution> runningExecutions = executionDAO.getRunningExecutions(request.getId());
            
        if (runningExecutions.isEmpty()) {
            processResult(); 
            this.status = status;
        }
        else {
            this.status = Status.HALT_IN_PROGRESS;
        }
    }

    /**
     * Abort the request.
     *
     * @param request
     *            {@link ExecutionRequest}
     * @param status
     *            {@link Status}
     * @throws IllegalStateException
     *             Covers errors trying to abort.
     */
    public void abort(ExecutionRequest request, Status status) throws IllegalStateException, Exception {
        if (request == null || request.getId() == null) {
            return;
        }

        // If we've already adjudicated...
        if (!ResultStatus.IN_PROGRESS.equals(request.getResultStatus())) {
            try {
                // But have not yet completed the request, just complete and return.
                if (!Status.COMPLETED.equals(request.getStatus())) {
                    requestDAO.updateStatus(request.getId(), Status.COMPLETED);
                    request.setStatus(Status.COMPLETED);
                }
                return;
            } catch (Exception ex) {
                CFBTLogger.logError(LOGGER, RequestStatus.class.getCanonicalName(),
                        "Failed to determine a final status", ex);
                throw new IllegalStateException("Failed to determine a final status", ex);
            }
        }

        try {
            // Transition request to the status up front before migrating the statistics.
            requestDAO.markTestComplete(request.getId(), status);
            request.setStatus(status);
        } catch (Exception ex) {
            // catch and log, but continue... The status will be incomplete.
            CFBTLogger.logError(LOGGER, RequestStatus.class.getCanonicalName(), "Error transitioning status.", ex);
        }

        try {
            // Transition the skipped executions first so they don't turn into running executions while we're aborting
            // the other running executions.
            ExecutionRepository executionRepo = new ExecutionRepository();
            List<String> requestIdsForSkippedExecutions = executionRepo.skipPendingExecutions(request.getId(), executionDAO);
            List<String> requestIdsForAbortedExecutions = executionRepo.abortRunningExecutions(request.getId(), executionDAO);

        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, RequestStatus.class.getCanonicalName(), "Error aborting executions", ex);
            throw new IllegalStateException(
                    "Failed to abort the execution, something is wrong with ability to persist this transtition", ex);
        }

        if (ExecutionRequest.Status.TESTING_COMPLETE.equals(status)) {
            processResult();

            if (ReleaseRecommendation.CANNOT_BE_DETERMINED.equals(releaseRecommendation) || 
                ReleaseRecommendation.RELEASE.equals(releaseRecommendation)) {
                this.status = Status.COMPLETED;
            } else {
                this.status = Status.TESTING_COMPLETE;
            }
        }
    }

   /**
     * This method communicates the state (complete) so that we can set the status appropriately.
     *
     * @throws Exception 
     */
    public void complete() throws Exception {
        processResult();
        status = Status.COMPLETED;
    }

    /**
     * This method communicates the state (testsComplete) so that we can set the status appropriately.
     * @throws Exception 
     */
    public void testsComplete() throws Exception {
        processResult();

        if (ReleaseRecommendation.CANNOT_BE_DETERMINED.equals(releaseRecommendation) || 
            ReleaseRecommendation.RELEASE.equals(releaseRecommendation)) {
            status = Status.COMPLETED;
        } else {
            status = Status.TESTING_COMPLETE;
        }
    }
    
    public void processResult() throws Exception {
        ExecutionRequest loadedRequest = requestDAO.getById(request.getId());
        MongoConnectionFactory db = ConfigManager.getDatabaseConfig().getConnectionFactory();
        TestRepository testRepo = new TestRepository();

        // Pull out all tests in the request that are used for release vetting.
        for (Test thisTest : loadedRequest.getTests()) {
            thisTest = testRepo.getTest(db, thisTest.getId());
            if (thisTest != null) {
                boolean executionReleaseVetting = thisTest.getUseForReleaseVetting();

                if (executionReleaseVetting) {
                    //If Test_Configuration_Support feature is enabled then check the release vetting flag specific for datacenter
                    executionReleaseVetting = thisTest.isReleaseVettingForDatacenter(loadedRequest.getDatacenter());
                }
                executionDAO.updateReleaseVettingForExecutions(loadedRequest.getId(), thisTest.getId(), executionReleaseVetting);
            }
        }
        requestRetry = getRequestRetry(loadedRequest);
        releaseRecommendation = getReleaseRecommendation(loadedRequest);
        resultStatus = getResultStatus(loadedRequest);
    }

    /**
     * This function calculates the release recommendation for the given execution request.
     * Note: This should not be called while the execution request is still in progress.
     * @param executionRequest {@link ExecutionRequest}
     * @return {@link ReleaseRecommendation}
     */
    private ReleaseRecommendation getReleaseRecommendation(ExecutionRequest executionRequest) throws Exception {
        //When release test is completed due to no RVE pool then release recommendation is 'Release'.
        if (request.getReleaseTest() != null && ReleaseTest.Action.COMPLETE_NOPOOL.equals(request.getReleaseTest().getCompletionAction())) {
            return ReleaseRecommendation.RELEASE;
        }
        if (executionRequest.checkFastPass()) {
            return ReleaseRecommendation.RELEASE;
        }
        List<Execution> executionList = executionDAO.getExecutionsByRequestID(executionRequest.getId());
        for (Execution execution: executionList) {
            if (execution.getReleaseVetting() != null && execution.getReleaseVetting() && Execution.Status.FAIL.equals(execution.getStatus())) {
                return ReleaseRecommendation.DO_NOT_RELEASE;
            }
        }
        if (executionRequest.getNumberTests() > 0 && executionRequest.getPercentComplete() == 0
            && executionRequest.getAbortedTests() == 0 && executionRequest.getSkippedTests() == 0) {
            return ReleaseRecommendation.CANNOT_BE_DETERMINED;
        } 
        return ReleaseRecommendation.RELEASE;
    }

    private boolean getRequestRetry(ExecutionRequest executionRequest) throws Exception {
        if (executionRequest.checkFastPass() || ExecutionRequest.Type.ADHOC.equals(executionRequest.getType())) {
            return false;
        }
        List<Execution> executionList = executionDAO.getExecutionsByRequestID(executionRequest.getId());
        for (Execution execution: executionList) {
            //When more than one manifest is mapped to the test failure then the request must be marked as retry request
            if (Boolean.TRUE.equals(execution.getReleaseVetting()) && Execution.Status.FAIL.equals(execution.getStatus()) && execution.getExecutionRequestIds().size() > 1)  {
                return true;
            }
        }
        return false;
    }

    /**
     * This function calculates the result status for the given execution request.
     * Note: This should not be called while the execution request is still in progress.
     * @param executionRequest {@link ExecutionRequest}
     * @return {@link ResultStatus}
     */
    private ResultStatus getResultStatus(ExecutionRequest executionRequest) { 
        if (ReleaseRecommendation.RELEASE.equals(releaseRecommendation)) {
            if (executionRequest.getNumWarnings() > 0) {
                return ResultStatus.PASS_WITH_WARNING;
            } else {
                return ResultStatus.PASS;
            }
        }
        if (executionRequest.getFailedTests() > 0) {
            
            return ResultStatus.FAILURE;
        }
        if (executionRequest.getTestsInError() > 0) {
            return ResultStatus.ERROR;
        }
        if (executionRequest.getAbortedTests() > 0 || executionRequest.getSkippedTests() > 0) {
            return ResultStatus.ABORTED;
        }
        if (executionRequest.getNumWarnings() > 0) {
            return ResultStatus.PASS_WITH_WARNING;
        }
        return ResultStatus.PASS;
    }
}
