package com.paypal.sre.cfbt.management.timeout;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;

import com.paypal.sre.cfbt.dataaccess.FeatureDAO;
import com.paypal.sre.cfbt.management.rest.impl.DeploymentHandler;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.paypal.sre.cfbt.data.Event;
import com.paypal.sre.cfbt.data.Feature;
import com.paypal.sre.cfbt.data.execapi.Execution;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.data.execapi.Test;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.Status;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions.Message;
import com.paypal.sre.cfbt.executor.Executor;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRepository;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestRepository;
import com.paypal.sre.cfbt.management.dal.TestDAO;
import com.paypal.sre.cfbt.management.dal.TestExecutionDAO;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.management.rest.impl.RequestThreadHandler;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.DateUtil;
import com.paypal.sre.cfbt.shared.NetworkUtil;

/**
 * This class manages the timeout for cfbt executions.
 *
 */
@Component
@EnableScheduling
public class TimeoutManager {

    @Inject
    private DatabaseConfig dbConfig;
    private MongoConnectionFactory dbFactory;

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeoutManager.class);

    @PostConstruct
    public void init() {
        dbFactory = dbConfig.getConnectionFactory();
    }

    // Check for schedule every 10 seconds
    @Scheduled(cron = "*/10 * * * * *")
    public void run() {

        try {
            Scheduler scheduler = new Scheduler();
            TestExecutionDAO testExecutionDAO = TestExecutionDAO.getInstance(dbFactory);

            List<Event> events = new ArrayList<>();
            if(!ConfigManager.getConfiguration().getBoolean("processEvents", true)) {
                return;
            }
            events.addAll(scheduler.pollAndCommit(Timeout.DEPLOYMENT_TIMEOUT.toString(), dbFactory));
            events.addAll(scheduler.pollAndCommit(Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString(), dbFactory));
            events.addAll(scheduler.pollAndCommit(Timeout.REQUEST_COMPLETION_TIMEOUT.toString(), dbFactory));
            events.addAll(scheduler.pollAndCommit(Timeout.STUCK_TEST_TIMEOUT.toString(), dbFactory));

            // Run our perpetually scheduled executions watchdog.
            runExecutionWatchdog();

            // Process all of the scheduled events.
            if (!events.isEmpty()) {
                for (Event event : events) {
                    if (event.getTargetId() == null) {
                        CFBTLogger.logWarn(LOGGER, TimeoutManager.class.getCanonicalName(),
                                "Event found with null Target ID: " + event.getId()
                        );
                        continue;
                    }

                    ExecutionRequestDAO executionRequestDAO = ExecutionRequestDAO.getInstance(dbFactory);
                    ExecutionRequest request = executionRequestDAO.getById(event.getTargetId());
                    ExecutionRepository executionRepo = new ExecutionRepository();
                    ExecutionRequestRepository requestRepo = new ExecutionRequestRepository();
                    RequestThreadHandler threadHandler = new RequestThreadHandler(dbConfig, request.getQueueName());
                    TestExecutionDAO executionDAO = TestExecutionDAO.getInstance(dbConfig.getConnectionFactory());

                    if (Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString().equalsIgnoreCase(event.getName())) {
                        threadHandler.triggerTimeout(request, null);
                        scheduler.complete(request.getId(), Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString(),
                                dbConfig.getConnectionFactory()
                        );
                    } else if (Timeout.DEPLOYMENT_TIMEOUT.toString().equalsIgnoreCase(event.getName())) {
                        threadHandler.triggerTimeout(request, ReleaseTest.Action.DEPLOY_TIMEOUT);
                        DeploymentHandler.processDeployWaitingRequests(dbConfig, request.getQueueName());
                        scheduler.complete(request.getId(), Timeout.DEPLOYMENT_TIMEOUT.toString(),
                                dbConfig.getConnectionFactory()
                        );
                    } else if (Timeout.REQUEST_COMPLETION_TIMEOUT.toString().equalsIgnoreCase(event.getName())) {
                        threadHandler.triggerTimeout(request, ReleaseTest.Action.COMPLETE_TIMEOUT);
                        scheduler.complete(request.getId(), Timeout.REQUEST_COMPLETION_TIMEOUT.toString(),
                                dbConfig.getConnectionFactory()
                        );
                    } else if (Timeout.STUCK_TEST_TIMEOUT.toString().equalsIgnoreCase(event.getName())) {
                        // Tests are stuck in pending with no running tests; skip all pending tests.
                        List<String> requestIdsForSkippedExecutions = executionRepo.skipPendingExecutions(event.getTargetId(), executionDAO);
                        List<ExecutionRequest> updatedRequestList = executionRequestDAO.getByIds(requestIdsForSkippedExecutions);
                        if(updatedRequestList != null && !updatedRequestList.isEmpty()) {
                            for (ExecutionRequest updatedRequest : updatedRequestList) {
                                if (Status.TESTING_COMPLETE.equals(updatedRequest.getStatus())) {
                                    // Update the release recommendation and final disposition.
                                    threadHandler.triggerEvent(updatedRequest, Message.COMPLETE_TESTS);
                                    scheduler.complete(request.getId(), Timeout.STUCK_TEST_TIMEOUT.toString(),
                                            dbConfig.getConnectionFactory()
                                    );
                                } else {
                                    CFBTLogger.logWarn(LOGGER, TimeoutManager.class.getCanonicalName(),
                                            "Skipping stuck tests did not update status properly.");
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, TimeoutManager.class.getCanonicalName(), "Error while managing the timeouts.",
                    ex);
        }

    }

    /**
     * This method handles the actions to be taken for canceling events related to a completed {@link ExecutionRequest}
     * @param request {@link ExecutionRequest}
     */
    public void cancelTimeoutEvents(ExecutionRequest request) {
        try {
            Scheduler scheduler = new Scheduler();
            MongoConnectionFactory factory = ConfigManager.getDatabaseConfig().getConnectionFactory();
            if(request.isLegacyIntegrationModel()) {
                scheduler.cancel(request.getId(), Timeout.DEPLOYMENT_TIMEOUT.toString(), factory);
                scheduler.cancel(request.getId(), Timeout.REQUEST_COMPLETION_TIMEOUT.toString(), factory);
            }
            scheduler.cancel(request.getId(), Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString(), factory);
            scheduler.cancel(request.getId(), Timeout.STUCK_TEST_TIMEOUT.toString(), factory);
        } catch (UnknownHostException exception) {
            CFBTLogger.logError(LOGGER, TimeoutManager.class.getCanonicalName(), "marking events as canceled failed.",exception);
        }
    }

    /**
     * Method to run "scheduled" events that occur with every polling cycle.
     */
    private void runExecutionWatchdog() {
        ExecutionRequestDAO executionRequestDAO = ExecutionRequestDAO.getInstance(dbFactory);
        ExecutionRequestRepository executionRequestRepo = new ExecutionRequestRepository();
        TestExecutionDAO testExecutionDAO = TestExecutionDAO.getInstance(dbFactory);
        Scheduler scheduler = new Scheduler();
        List<String> statusList = new ArrayList<>();

        // Kick off the Out of SLA abort process
        abortOutOfSLATests();

        /*
         * Check currently running executions to see if anything meets the stuck test execution criteria. While running
         * under Kafka Parallel, every test that is in progress counts for every ExecutionRequest.
         */
        String stuckTestTime = DateUtil.currentDateTimeUTC().plusSeconds(
                ConfigManager.getConfiguration().getInt("stuckTestTimeout", 60))
                .toString();
        statusList.add(Status.IN_PROGRESS.toString());

        try {
            /*
             * This branch is for the Parallel Execution path.
             */
            boolean anyTestsRunning = false;
            for (ExecutionRequest request : executionRequestDAO.getByStatus(statusList)) {
                if(testExecutionDAO.getRunningExecutions(request.getId()).size() > 0 ) {
                    anyTestsRunning = true;
                    // We can short-circuit here to save a little time.
                    break;
                }
            }

            if (anyTestsRunning) {
                for (ExecutionRequest request : executionRequestRepo.getInProgressRequests(dbFactory)) {
                    scheduler.updateScheduleTime(request.getId(), Timeout.STUCK_TEST_TIMEOUT.toString(),
                            stuckTestTime, true, dbConfig.getConnectionFactory());
                }
            }
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, TimeoutManager.class.getCanonicalName(),
                    "Error while detecting stuck tests.", ex
            );
        }
    }


    /**
     * Method to abort any tests out of expected SLA.
     */
    private void abortOutOfSLATests() {
        try {
            List<String> statusList = new ArrayList<>();

            statusList.add(Status.IN_PROGRESS.toString());
            ExecutionRequestDAO executionRequestDAO = ExecutionRequestDAO.getInstance(dbFactory);
            TestExecutionDAO testExecutionDAO = TestExecutionDAO.getInstance(dbFactory);
            TestDAO testDAO = TestDAO.getInstance(dbFactory);
            List<ExecutionRequest> requestList = executionRequestDAO.getByStatus(statusList);

            Executor executor = new Executor();
            Configuration config = ConfigManager.getConfiguration();
            int defaultMaxTestExecutionTime = config.getInt("maxTestExecutionTimeInMinutes", 6) * 60;

            for (ExecutionRequest request : requestList) {
                List<Execution> executions = testExecutionDAO.getRunningExecutions(request.getId());
                for (Execution execution : executions) {
                    Test test = testDAO.readOne(dbConfig.getConnectionFactory().newConnection(), execution.getTestId(),
                            false, null);
                    long maxExecutionTime = (test.getMaxExecutionTime() == 0) ? defaultMaxTestExecutionTime
                            : test.getMaxExecutionTime();
                    int executionDuration = (int) DateUtil.getElapsedTimeInSeconds(execution.getExecutionTime());

                    if (executionDuration > maxExecutionTime) {
                        abortTest(execution, execution.getExecutionRequestIds(), executionDuration);
                        CFBTLogger.logInfo(LOGGER, TimeoutManager.class.getCanonicalName(),
                                NetworkUtil.getLocalInetAddress(config).getHostAddress()
                                        + " node aborted the test that is running too long"
                        );
                    }
                }
            }
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, TimeoutManager.class.getCanonicalName(), "Error while managing the timeouts.", ex);
        }
    }

    private void abortTest(Execution execution, List<String> requestIds, int executionDuration) {
        Configuration config = ConfigManager.getConfiguration();
        Executor executor = new Executor();
        ExecutionRequestDAO executionRequestDAO = ExecutionRequestDAO.getInstance(dbFactory);

        try {
            execution.setAbortNodeIPAddress(NetworkUtil.getLocalInetAddress(config).getHostAddress());
            execution.setDurationTime(executionDuration);
            recordTestAbort(execution);
            executor.abortTest(execution.getId());
            List<ExecutionRequest> updatedRequestList = executionRequestDAO.getByIds(requestIds);

            if(updatedRequestList != null && !updatedRequestList.isEmpty()) {
                for (ExecutionRequest updatedRequest : updatedRequestList) {
                    if (Status.TESTING_COMPLETE.equals(updatedRequest.getStatus())) {
                        // Update release recommendation and final status
                        RequestThreadHandler threadHandler = new RequestThreadHandler(dbConfig,
                                updatedRequest.getQueueName());
                        threadHandler.triggerEvent(updatedRequest, Message.COMPLETE_TESTS);
                    }
                }
            }
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, TimeoutManager.class.getCanonicalName(), "Error aborting test.", ex);
        }


    }

    /**
     * This method records the event of a test abort.
     * 
     * @param execution instance of {@link Execution}
     */
    private void recordTestAbort(Execution execution) {
        ExecutionRequestRepository requestRepo = new ExecutionRequestRepository();
        ExecutionRepository executionRepo = new ExecutionRepository();

        Execution.Status priorStatus = execution.getStatus();
        execution.setStatus(Execution.Status.ABORT);

        CFBTLogger.logInfo(LOGGER, "CFBT_TEST_ABORT_EVENT",
                "Updating Execution Status on an Abort: " + execution.getId() + ", " + execution.getTestName());

        try {
            MongoConnectionFactory db = ConfigManager.getDatabaseConfig().getConnectionFactory();

            boolean didExecutionUpdate = false;

            try {
                didExecutionUpdate = executionRepo.update(db, execution, priorStatus);
            } catch (Exception ex) {
                CFBTLogger.logError(LOGGER, "CFBT_TEST_ABORT_EVENT", ex.getMessage(), ex);
            }
            ExecutionRequestDAO executionRequestDAO = ExecutionRequestDAO.getInstance(db);
            executionRequestDAO.getById(execution.getExecutionRequestId());

            if (!didExecutionUpdate) {
                return;
            }

            CFBTLogger.logInfo(LOGGER, "CFBT_TEST_ABORT_EVENT",
                    "Updating ExecutionRequest Result: " + execution.getId() + ", " + execution.getTestName());
            try {
                requestRepo.updateFromExecution(db, execution, true);
            } catch (Exception ex) {
                CFBTLogger.logError(LOGGER, "CFBT_TEST_ABORT_EVENT", ex.getMessage(), ex);
            }

        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, "CFBT_TEST_ABORT_EVENT", ex.getMessage(), ex);
        }
    }

    /**
     * This method is responsible for finding out a {@link Feature} enabled or not
     * @param featureName - Name of the feature
     * @return true if feature is enabled all other conditions it is false
     */
    private boolean checkFeatureEnabled(String featureName) {
        Feature feature = null;
        try {
            feature = FeatureDAO.getInstance().readWithName(dbFactory.newConnection(), featureName);
        } catch (Exception ex) {
            //eat the exception and send the enabled as false
            CFBTLogger.logError(LOGGER, TimeoutManager.class.toString(), ex.getLocalizedMessage(), ex);
        }
        if(feature != null && feature.getEnabledFlag()) {
            return true;
        }
        return false;
    }
}
