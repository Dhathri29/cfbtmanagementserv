package com.paypal.sre.cfbt.management;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.paypal.sre.cfbt.data.execapi.Execution;
import com.paypal.sre.cfbt.data.execapi.Execution.Status;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.Test;
import com.paypal.sre.cfbt.data.executor.TestExecutionContainer;
import com.paypal.sre.cfbt.data.test.Parameter;
import com.paypal.sre.cfbt.data.test.Step;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions;
import com.paypal.sre.cfbt.management.dal.ExecutionRepository;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestRepository;
import com.paypal.sre.cfbt.management.dal.TestExecutionDAO;
import com.paypal.sre.cfbt.management.dal.TestDAO;
import com.paypal.sre.cfbt.management.dal.TestRepository;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.request.Queue;
import com.paypal.sre.cfbt.request.QueueMonitor;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Factory class for {@link TestExecutionContainer}
 *
 */
public class TestExecutionContainerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestExecutionContainerFactory.class);

    private final CFBTTestResourceClient testresourceservClient;
    private final MongoConnectionFactory db;
    private int numRetries;

    /**
     * @param numRetries             Number of retries for this execution.
     */
    public TestExecutionContainerFactory(int numRetries) {
        this.db = ConfigManager.getDatabaseConfig().getConnectionFactory();
        this.testresourceservClient = ConfigManager.getTestResourceServClient();
        this.numRetries = numRetries;
    }

    /**
     * default constructor
     */
    public TestExecutionContainerFactory() {
        this(0);
    }

    /***
     * This function is responsible for creating a list of executions in containers
     *
     * @param executionRequests List of {@link ExecutionRequest} objects containing valid tests
     * @return {@link List<TestExecutionContainer>> } returns the initialized executions.
     * @throws java.lang.Exception throws errors trying to connect TestResourceServ.
     */
    public List<TestExecutionContainer> createRequestList(List<ExecutionRequest> executionRequests) throws Exception {

        List<TestExecutionContainer> thisRequest = new ArrayList<>();
        Map<String, Test> testMap = new HashMap<>();
        Execution testExecution = null;

        //Map containing test id & associated execution
        Map<String, Execution> testExecutionMap = new HashMap<>();

        //If multiple requests have the same tests, create & run the test execution only once.
        for(ExecutionRequest exRequest: executionRequests) {
            for (Test thisTest : exRequest.getTests()) {
                if(thisTest != null && (testExecutionMap.isEmpty() || !testExecutionMap.containsKey(thisTest.getId())))
                {
                    try {
                        List<Parameter> parameters = testresourceservClient.loadParameters(thisTest.getId(), exRequest.getParameterGroup());
                        TestDAO.getInstance(db).mergeTestParameterValues(thisTest,parameters);
                        testMap.put(thisTest.getId(), thisTest);

                        testExecution = new ExecutionRepository().initializeExecution(db, thisTest, exRequest.getId(),
                                    exRequest.getDatacenter(), numRetries, exRequest.getSystemUnderTest(), exRequest.getSyntheticLocation());
                    } catch (Exception ex) {
                        CFBTLogger.logError(LOGGER, TestExecutionContainer.class.getCanonicalName(),
                                    "Error while initializing the test : " + thisTest.getId(), ex);
                        // Mark the test as error.
                        List<Step> steps = new ArrayList<>();

                        steps.add(createErrorStep("Error while initializing the test", 1));
                        testExecution = new ExecutionRepository().initializeExecutionAsError(db, thisTest,
                                    exRequest.getId(), exRequest.getDatacenter(), numRetries,
                                    exRequest.getSystemUnderTest(), steps);
                    }

                    testExecutionMap.put(thisTest.getId(), testExecution);
                } else if(thisTest != null && testExecutionMap.containsKey(thisTest.getId()))
                {
                    //associate existing test execution to multiple requests.
                    Execution existingExecution = testExecutionMap.get(thisTest.getId());
                    existingExecution.getExecutionRequestIds().add(exRequest.getId());
                }
            }
        }

        TestExecutionDAO testExecutionDAO = TestExecutionDAO.getInstance(db);
        List<Execution> storedExecutions = null;

        List<String> requestIds = executionRequests.stream().map(ExecutionRequest::getId).collect(Collectors.toList());

        try (MongoConnection c = db.newConnection()) {
            testExecutionDAO.insertExecutions(db.newConnection(), new ArrayList<>(testExecutionMap.values()));

            storedExecutions = testExecutionDAO.getExecutionsByRequestIds(c, requestIds);
        }

        Map<String, String> executionParamGroupMap = new HashMap<>();
        for(Execution execution: storedExecutions) {
            for(ExecutionRequest executionRequest: executionRequests) {
                if(execution.getExecutionRequestIds().contains(executionRequest.getId())) {
                    executionParamGroupMap.put(execution.getId(), executionRequest.getParameterGroup());
                    break;
                }
            }
        }

        for (Execution execution : storedExecutions) {
            TestExecutionContainer container = new TestExecutionContainer(testMap.get(execution.getTestId()), execution,
                    new ArrayList<>(), executionParamGroupMap.get(execution.getId()));
            // Do not add the test to the request if the status is error.
            if (!execution.getStatus().equals(Status.ERROR) && !execution.getStatus().equals(Status.SKIP)) {
                thisRequest.add(container);
            } else {
                updateErroredResult(container, db);
            }
        }
        return thisRequest;
    }

    /**
     * Method to create a error step
     * 
     * @param errorReason the reason for the error
     * @param number      the step number
     * @return the error {@link Step}
     */
    private static Step createErrorStep(String errorReason, int number) {
        Step step = new Step();
        step.setName("Error Step");
        step.setResult(Step.Result.ERROR);
        step.setReason("Error while initializing the test, reason = " + errorReason);
        step.setStepNum(number);
        return step;
    }
    
    /**
     * Method to update the result of an {@link Execution} due to an error while initializing the
     * {@link TestExecutionContainer}
     * 
     * @param testExecutionContainer the {@link TestExecutionContainer}
     * @param db                     the {@link MongoConnectionFactory}
     */
    private static void updateErroredResult(TestExecutionContainer testExecutionContainer, MongoConnectionFactory db) {

        Execution execution = testExecutionContainer.getExecution();
        Test test = testExecutionContainer.getTest();

        CFBTLogger.logInfo(LOGGER, CFBTLogger.CalEventEnum.HANDLE_EXECUTE_RESPONSE,
                "Handling Test Response: " + execution.getExecutionRequestId() + ", " + execution.getTestName());

        // Create repositories.
        ExecutionRequestRepository requestRepo = new ExecutionRequestRepository();
        TestRepository testRepo = new TestRepository();

        try {
            testRepo.updateLatestResult(db, test, execution);
            requestRepo.updateFromExecution(db, execution, false);
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, CFBTLogger.CalEventEnum.HANDLE_EXECUTE_RESPONSE, ex.getMessage(), ex);
        }

        try {
            List<ExecutionRequest> executionRequests = ExecutionRequestDAO.getInstance(db).getByIds(execution.getExecutionRequestIds());
            if(executionRequests != null && !executionRequests.isEmpty()) {
                for(ExecutionRequest executionRequest: executionRequests) {
                    if (ExecutionRequest.Status.TESTING_COMPLETE.equals(executionRequest.getStatus())) {
                        QueueMonitor monitor = new QueueMonitor(
                                Queue.releaseVettingQueue(ConfigManager.getDatabaseConfig()), new Scheduler(), ConfigManager.getDatabaseConfig(), executionRequest.getQueueName());
                        monitor.triggerStateEvent(executionRequest, Transitions.Message.COMPLETE_TESTS);
                    }
                }
            }
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, CFBTLogger.CalEventEnum.HANDLE_EXECUTE_RESPONSE, ex.getMessage(), ex);
        }
    }

}
