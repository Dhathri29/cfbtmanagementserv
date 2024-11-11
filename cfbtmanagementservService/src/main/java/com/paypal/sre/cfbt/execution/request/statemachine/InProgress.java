/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */

package com.paypal.sre.cfbt.execution.request.statemachine;

import com.ebay.kernel.cal.api.CalTransaction;
import com.ebay.kernel.cal.api.sync.CalTransactionFactory;
import com.paypal.infra.util.cal.CalType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import org.slf4j.LoggerFactory;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.Test;
import com.paypal.sre.cfbt.data.executor.TestExecutionContainer;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions.Message;
import com.paypal.sre.cfbt.executor.Executor;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.dal.TestDAO;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.request.Queue;
import com.paypal.sre.cfbt.request.QueueMonitor;
import com.paypal.sre.cfbt.management.timeout.Timeout;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;


/**
 * Implements messages appropriate for a Request in the IN_PROGRESS state.
 */
public class InProgress extends RequestState {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(InProgress.class);
    private List<ExecutionRequest> requests = new ArrayList<>();
    private final ExecutionRequestDAO requestDAO;
    private final TestDAO testDAO;
    private final Scheduler scheduler;
    private final DatabaseConfig dbConfig;
    private final Executor executorService = new Executor();
    private final ExecutorService threadExecutor = Executors.newSingleThreadExecutor();

    public InProgress(ExecutionRequest request, DatabaseConfig db, Scheduler scheduler) {
        this.requests.add(request);
        this.dbConfig = db;
        this.requestDAO = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
        this.testDAO = TestDAO.getInstance(db.getConnectionFactory());
        this.scheduler = scheduler;
    }

    public InProgress(List<ExecutionRequest> requests, DatabaseConfig db, Scheduler scheduler) {
        this.requests.addAll(requests);
        this.dbConfig = db;
        this.requestDAO = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
        this.testDAO = TestDAO.getInstance(db.getConnectionFactory());
        this.scheduler = scheduler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutionRequest deployComplete() throws Exception {
        //cancel the timeout schedule.
        for(ExecutionRequest request: requests) {
            if(request.isLegacyIntegrationModel()) {
                scheduler.cancel(request.getId(), Timeout.DEPLOYMENT_TIMEOUT.toString(), this.dbConfig.getConnectionFactory());
            }
        }
        return begin();
    }

    private void scheduleTests() {
        threadExecutor.submit(() -> {
            final CalTransaction calTransaction = CalTransactionFactory.create(CalType.URL.toString());
            calTransaction.setName("CFBT.InProgress State");
            calTransaction.addData("CFBT.InProgress: Scheduling the tests on the Kafka queue");
            CFBTLogger.logInfo(LOGGER, CFBTLogger.CalEventEnum.REQUEST_QUEUE,
                    "Enqueuing the nextRequests, ids = " + requests.stream().map(ExecutionRequest::getId).collect(Collectors.toList()));

            try {

                //Map containing request id & its associated test ids
                Map<String, List<String>> testIdsForRequest = new HashMap<>();
                for(ExecutionRequest executionRequest: requests) {
                    List<String> testIds = new ArrayList<>();
                    executionRequest.getTests().forEach((test) -> {
                        testIds.add(test.getId());
                    });
                    testIdsForRequest.put(executionRequest.getId(), testIds);
                }

                //Map containing request id & its associated tests which are configured & enabled.
                Map<String, List<Test>> testsForRequest = new HashMap<>();
                for(ExecutionRequest executionRequest: requests) {
                    List<Test> tests = new ArrayList<>();
                    tests.addAll(testDAO.loadEnabledConfiguredTests(testIdsForRequest.get(executionRequest.getId()), executionRequest.getDatacenter()));
                    if(tests != null && !tests.isEmpty()) {
                        testsForRequest.put(executionRequest.getId(), tests);
                    }
                }

                // If there are no tests (which are configured & enabled) for any of the request then transition all requests immediately to complete.
                if (testsForRequest == null && testsForRequest.isEmpty()) {
                    for(ExecutionRequest executionRequest: requests) {
                        QueueMonitor monitor = new QueueMonitor(Queue.releaseVettingQueue(dbConfig), new Scheduler(), dbConfig, executionRequest.getQueueName());
                        monitor.triggerStateEvent(executionRequest, Message.COMPLETE_TESTS);
                    }
                } else {
                    // If the number of configured enabled tests is less than the original request,
                    // update the number of tests to be run in the request.
                    for(ExecutionRequest executionRequest: requests) {
                        if(testsForRequest.containsKey(executionRequest.getId())) {
                            List<Test> tests = testsForRequest.get(executionRequest.getId());
                            if (executionRequest.getTests().size() != tests.size()) {
                                executionRequest = requestDAO.updateTests(executionRequest, tests);
                            } else {
                                executionRequest.setTests(tests);
                            }
                        } else {
                            //If there are no tests (which are configured & enabled) for this executionRequest then transition immediately to complete.
                            QueueMonitor monitor = new QueueMonitor(Queue.releaseVettingQueue(dbConfig), new Scheduler(), dbConfig, executionRequest.getQueueName());
                            monitor.triggerStateEvent(executionRequest, Message.COMPLETE_TESTS);
                        }
                    }

                    //Schedule the tests on Kafka queue.
                    List<TestExecutionContainer> requestList = executorService.scheduleTests(requests);
                    if(requestList == null || requestList.isEmpty()) {
                        for(ExecutionRequest executionRequest: requests) {
                            if (StringUtils.isBlank(executionRequest.getQueueName())) {
                                StateMachineFactory.getNextState(executionRequest, dbConfig, scheduler, Message.COMPLETE_TESTS).testsComplete();
                            } else {
                                QueueMonitor monitor = new QueueMonitor(Queue.releaseVettingQueue(dbConfig), new Scheduler(), dbConfig, executionRequest.getQueueName());
                                monitor.triggerStateEvent(executionRequest, Message.COMPLETE_TESTS);
                            }
                            executorService.emergencyStop(executionRequest.getId());
                        }
                    }
                }
                calTransaction.setStatus("0");
            } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, InProgress.class.getCanonicalName(), "Error creating the request List", ex);

                // If this fails, to be safe, let's complete the request.
                try {
                    for(ExecutionRequest executionRequest: requests) {
                        QueueMonitor monitor = new QueueMonitor(Queue.releaseVettingQueue(dbConfig), new Scheduler(), dbConfig, executionRequest.getQueueName());
                        monitor.triggerStateEvent(executionRequest, Message.ABORT);
                        // Just in case.
                        executorService.emergencyStop(executionRequest.getId());
                    }
                } catch (Exception nextEx) {
                    CFBTLogger.logError(LOGGER, InProgress.class.getCanonicalName(), "Trying to recover after an exception also failed", nextEx);
                }
                calTransaction.setStatus(ex);
            } finally {
                calTransaction.completed();
            }
        });
    }

    /**
     * Transition to in progress, then go ahead and try to schedule the tests.
     * @return {@link ExecutionRequest}
     * @throws java.lang.Exception - If transition to in progress fails.
     */
    @Override
    public ExecutionRequest begin() throws Exception {
        boolean updateDeploymentComplete = false;
        for(ExecutionRequest request: requests) {
            if (ExecutionRequest.Type.RELEASE.equals(request.getType()) &&
                    request.getReleaseTest() != null && request.getReleaseTest().getDeploymentComplete() == null) {
                updateDeploymentComplete = true;
                break;
            }
        }
        requests = requestDAO.transitionToInProgress(requests, updateDeploymentComplete);

        int maxReleaseExecutionTime = ConfigManager.getConfiguration().getInt("maxReleaseExecutionTime", 15);
        int stuckTestTimeout = ConfigManager.getConfiguration().getInt("stuckTestTimeout", 60);
        String timeoutAt = DateUtil.currentDateTimeUTC().plusMinutes(maxReleaseExecutionTime).toString();
        String stuckTestTime = DateUtil.currentDateTimeUTC().plusSeconds(stuckTestTimeout).toString();
        for(ExecutionRequest executionRequest: requests) {
            //Schedule a timeout
            scheduler.schedule(executionRequest.getId(), Timeout.TEST_EXECUTION_REQUEST_TIMEOUT.toString(), timeoutAt,
                    this.dbConfig.getConnectionFactory());
            //Schedule the stuck test timer
            scheduler.schedule(executionRequest.getId(), Timeout.STUCK_TEST_TIMEOUT.toString(), stuckTestTime,
                    this.dbConfig.getConnectionFactory());
        }
        scheduleTests();
        return requests.get((requests.size() - 1));
    }
}
