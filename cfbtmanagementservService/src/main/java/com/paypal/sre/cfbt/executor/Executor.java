/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.executor;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.executor.ExecutorControlMessage;
import com.paypal.sre.cfbt.data.executor.ExecutorThreadControlMessage;
import com.paypal.sre.cfbt.data.executor.TestExecutionContainer;
import com.paypal.sre.cfbt.data.test.TestPackage;
import com.paypal.sre.cfbt.management.PartitionUtil;
import com.paypal.sre.cfbt.management.TestExecutionContainerFactory;
import com.paypal.sre.cfbt.management.kafka.ExecutorControlMessageProducer;
import com.paypal.sre.cfbt.management.kafka.ExecutorThreadControlMessageProducer;
import com.paypal.sre.cfbt.management.kafka.TestExecutionProducer;
import com.paypal.sre.cfbt.management.rest.api.PackageAction;
import com.paypal.sre.cfbt.shared.CFBTLogger;

/**
 * Meant to be a place holder to schedule tests on the queue.
 */
public class Executor {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Executor.class);

    public List<TestExecutionContainer> scheduleTests(List<ExecutionRequest> requests) {
        CFBTLogger.logInfo(LOGGER, Executor.class.getCanonicalName(), "scheduleTests");
        List<TestExecutionContainer> requestList = null;
        try {
            requestList = new TestExecutionContainerFactory().createRequestList(requests);
            if(!requestList.isEmpty()) {
                List<List<TestExecutionContainer>> partitionTests = PartitionUtil.partitionTestsBySharedData(requestList);
                PartitionUtil.getTestPartitions(partitionTests);
                PartitionUtil.sortPartitionsByPriority(partitionTests);
                PartitionUtil.printTestPartitions(partitionTests);
                for (List<TestExecutionContainer> testExecutionContainerList : partitionTests) {
                    TestExecutionProducer.scheduleTasks(stripParameters(testExecutionContainerList));
                }
            } 
        } catch (Exception e) {
            CFBTLogger.logError(LOGGER, Executor.class.getCanonicalName(), "Error creating the request List", e);
            
            //TODO: If this fails, to be safe, let's dequeue and finish the request.

        }
        return requestList;
    }

    /**
     * This method is to publish the emergency stop message to execserv nodes
     *
     * @param id - Execution Request ID
     */
    public void emergencyStop(String id) {
        CFBTLogger.logInfo(LOGGER, Executor.class.getCanonicalName(), "emergencyStop");
        ExecutorThreadControlMessage executorThreadControlMessage = new ExecutorThreadControlMessage();
        executorThreadControlMessage.setExecutionRequestId(id);
        executorThreadControlMessage.setMessageType(ExecutorThreadControlMessage.ControlMessageType.EMERGENCY_STOP);
        ExecutorThreadControlMessageProducer.publish(executorThreadControlMessage);
        CFBTLogger.logInfo(LOGGER, CFBTLogger.CalEventEnum.EMERGENCY_STOP, "CFBT - Published emergency stop message");
    }

    /**
     * This method publishes the test abort message to execserv nodes.
     * @param id test execution identifier.
     */
    public void abortTest(String id) {
        CFBTLogger.logInfo(LOGGER, Executor.class.getCanonicalName(), "Request received for stopping test : " + id);
        ExecutorThreadControlMessage executorThreadControlMessage = new ExecutorThreadControlMessage();
        executorThreadControlMessage.setExecutionId(id);
        executorThreadControlMessage.setMessageType(ExecutorThreadControlMessage.ControlMessageType.STOP_TEST);
        ExecutorThreadControlMessageProducer.publish(executorThreadControlMessage);
        CFBTLogger.logInfo(LOGGER, "CFBT_TEST_ABORT", "CFBT - Published stop message to abort the test.");
    }

    /**
     * Method to strip the parameters from the tests in {@link TestExecutionContainer}
     * 
     * @param testExecutionContainerList the list of {@link TestExecutionContainer}
     * @return the list of {@link TestExecutionContainer} with the parameters stripped.
     */
    private List<TestExecutionContainer> stripParameters(List<TestExecutionContainer> testExecutionContainerList) {
        for (TestExecutionContainer container : testExecutionContainerList) {
            container.getTest().setParameters(new ArrayList<>());
        }
        return testExecutionContainerList;
    }
    
    /**
     * This method publishes the package action message to other execserv nodes if
     * action is not intended for the local package. Otherwise, it performs action
     * on the local test package copy.
     *
     * @param ipAddress The ip address of the node
     * @param user The user info
     * @param packageAction The {@link PackageAction} object
     * @param testPackage The {@link TestPackage} object
     */
    public void notifyPackageAction(String user, String ipAddress, PackageAction packageAction, TestPackage testPackage) throws Exception {
        //publish message
        CFBTLogger.logInfo(LOGGER, Executor.class.getCanonicalName(), "notifyPackageAction : " + 
                "Publishing action message to kafka : "+ packageAction + testPackage.getFileName() + " for node : " + ipAddress);
        ExecutorControlMessage executorControlMessage = new ExecutorControlMessage(ExecutorControlMessage.MessageType.NOTIFY_PACKAGE_ACTION,
                ipAddress, packageAction.getAction(), testPackage, user);
        ExecutorControlMessageProducer.publish(executorControlMessage);
        CFBTLogger.logInfo(LOGGER, CFBTLogger.CalEventEnum.EXECUTOR_PUBLISH_MESSAGE, "CFBT - Published Notify Package Action message");
    }
    
    public void updateConfiguredThreads(String ipAddress, int numThreads) throws Exception {
        CFBTLogger.logInfo(LOGGER, Executor.class.getCanonicalName(), "updateConfiguredThreads");
        ExecutorControlMessage executorControlMessage = new ExecutorControlMessage(ExecutorControlMessage.MessageType.MODIFY_THREAD_COUNT_MESSAGE,
                ipAddress, numThreads);
        ExecutorControlMessageProducer.publish(executorControlMessage);
        CFBTLogger.logInfo(LOGGER, CFBTLogger.CalEventEnum.EXECUTOR_PUBLISH_MESSAGE, "CFBT - Published Modify Thread Count message");
        
    }
}
