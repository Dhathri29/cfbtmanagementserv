/*
 * (C) 2020 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.Execution;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.OverrideDetails;
import com.paypal.sre.cfbt.data.execapi.ReleaseRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.data.execapi.ExtendDeployment;
import com.paypal.sre.cfbt.data.executor.TestExecutionSetRequest;
import com.paypal.sre.cfbt.data.test.Parameter;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions;
import com.paypal.sre.cfbt.management.CFBTTestResourceClient;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.dal.TestExecutionDAO;
import com.paypal.sre.cfbt.management.kafka.ExecutorThreadControlMessageProducer;
import com.paypal.sre.cfbt.management.kafka.TestExecutionProducer;
import com.paypal.sre.cfbt.management.kafka.Topic;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.management.rest.impl.DeploymentHandler;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.configuration.Configuration;
import org.bson.Document;
import org.mockito.Matchers;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.apache.kafka.clients.producer.Producer;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

/**
 * Unit tests that cover various state transitions.
 */
public class StateMachineTest implements MonitorObserver {
    static int subscribeCount1 = 0;
    static int subscribeCount2 = 0;

    // The state machine is asynchronous, so rely on futures to get feedback.
    private CompletableFuture<List<ExecutionRequest>> result;
    DatabaseConfig db;
    DatabaseConfigFactory dbFactory = new DatabaseConfigFactory();

    @BeforeSuite
    public void init() throws Exception {
        Configuration config = mock(Configuration.class);
        when(config.getInt(Matchers.anyString(), Matchers.anyInt())).thenReturn(5);
        when(config.getString(Topic.Executor_Thread_Control_Events.getProperty())).thenReturn(Topic.Executor_Thread_Control_Events.getName());
        CFBTTestResourceClient testresourceservClient = mock(CFBTTestResourceClient.class);
        List<Parameter> parameters = new ArrayList<>();
        Parameter p = new Parameter();
        p.setName("email");
        p.setValue("email@paypal.com".toCharArray());
        parameters.add(p);
        db = dbFactory.databaseConfig("Test");
        dbFactory.initSpecialMessaging();

        TestExecutionProducer.setTopic(Topic.Executor_Thread_Control_Events.getName());
        ExecutorThreadControlMessageProducer.setTopic(Topic.Executor_Thread_Control_Events.getName());
        ExecutorThreadControlMessageProducer.setProducer(mock(Producer.class));
        when(testresourceservClient.loadParameters(Matchers.anyString(), Matchers.anyString())).thenReturn(parameters);

        ConfigManager.setConfiguration(config, db, testresourceservClient);

        Producer<String, TestExecutionSetRequest> producer = mock(Producer.class);
        TestExecutionProducer.setProducer(producer);
    }

    /**
     * Test basic flow through the Execution Request state machine.
     */
    @Test
    public void testBasicExecutionRequest() {
        System.out.println("testBasicExecutionRequest******");

        try {
            ExecutionRequest request = dbFactory.createBasicExecutionRequest("ExecutionRequestQueue", false);
            System.out.println("request = " + request.getId());

            Queue queue = new Queue(db, "ExecutionRequestQueue");
            Scheduler scheduler = new Scheduler();
            QueueMonitor monitor = new QueueMonitor(queue, scheduler, db, "ExecutionRequestQueue");
            monitor.monitorQueueEvents();
            queue.enqueue(request, 0);
            ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(db.getConnectionFactory());

            request = dao.getById(request.getId());

            ExecutionRequest transitionedRequest = monitor.triggerStateEvent(request, Transitions.Message.COMPLETE_TESTS);

            Assert.assertEquals(transitionedRequest.getStatus(), ExecutionRequest.Status.COMPLETED);
            Assert.assertEquals(transitionedRequest.getId(), request.getId());

            delete(request.getId(), dao, db);

        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.getMessage(), ex);
        }
    }

    private void validateReleaseTest(QueueMonitor monitor, ExecutionRequest request) throws Exception {
        ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(db.getConnectionFactory());

        request = dao.getById(request.getId());
        
        ExecutionRequest transitionedRequest = monitor.triggerStateEvent(request, Transitions.Message.DEPLOY_COMPLETE);

        System.out.println("running request, in progress" + transitionedRequest.getId());

        Assert.assertEquals(transitionedRequest.getStatus(), ExecutionRequest.Status.IN_PROGRESS);
        Assert.assertEquals(transitionedRequest.getId(), request.getId());

        transitionedRequest = monitor.triggerStateEvent(transitionedRequest, Transitions.Message.COMPLETE_TESTS);

        System.out.println("running request, complete" + transitionedRequest.getId());

        Assert.assertEquals(transitionedRequest.getStatus(), ExecutionRequest.Status.COMPLETED);
        Assert.assertEquals(transitionedRequest.getId(), request.getId());
    }

    /**
     * Test basic flow through the Release Test state machine.
     */
    @Test
    public void testBasicReleaseTestFlow() {
        System.out.println("testBasicReleaseTestFlow******");

        try {
            boolean hasTests = true;
            ExecutionRequest request1 = dbFactory.createBasicReleaseTest(0, hasTests, "A", ReleaseRequest.ReleaseVehicle.ALTUS_ALM, "RollbackQueue",false);
            ExecutionRequest request2 = dbFactory.createBasicReleaseTest(0, hasTests, "B", ReleaseRequest.ReleaseVehicle.ALTUS_ALM, "RollbackQueue",false);
            System.out.println("Request 1 = " + request1.getId());
            System.out.println("Request 2 = " + request2.getId());
            Queue queue = new Queue(db, "ReleaseTest");
            Scheduler scheduler = new Scheduler();
            QueueMonitor monitor = new QueueMonitor(queue, scheduler, db, "ReleaseTest");
            monitor.monitorQueueEvents();
            queue.enqueue(request1, 1);
            validateReleaseTest(monitor, request1);

            queue.enqueue(request2, 1);
            validateReleaseTest(monitor, request2);
            ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(db.getConnectionFactory());

            delete(request1.getId(), dao, db);
            delete(request2.getId(), dao, db);

        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.getMessage(), ex);
        }
    }


    /**
     * Test basic flow through the Release Test state machine.
     */
    @Test
    public void testRollbackStateResult() {
        System.out.println("testRollbackStateResult******");

        try {
            boolean addTests = true;

            ExecutionRequest request1 = dbFactory.createBasicReleaseTest(0, addTests, "A", ReleaseRequest.ReleaseVehicle.ALTUS_ALM, "RollbackQueue",false);
            Queue queue = new Queue(db, "RollbackQueue");
            Scheduler scheduler = new Scheduler();
            QueueMonitor monitor = new QueueMonitor(queue, scheduler, db, "RollbackQueue");
            monitor.monitorQueueEvents();
            // The monitor has a subscribe feature mostly to make it testable.
            queue.enqueue(request1, request1.getPriority());
            ExecutionRequestDAO requestDAO = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
            request1 = requestDAO.getById(request1.getId());
            
            ExecutionRequest transitionedRequest = monitor.triggerStateEvent(request1, Transitions.Message.DEPLOY_COMPLETE);

            Assert.assertEquals(transitionedRequest.getStatus(), ExecutionRequest.Status.IN_PROGRESS);
            Assert.assertEquals(transitionedRequest.getId(), request1.getId());
            Thread.sleep(1000);
            TestExecutionDAO executionDAO = TestExecutionDAO.getInstance(db.getConnectionFactory());
            List<Execution> executionList = executionDAO.getExecutionsByRequestID(request1.getId());

            for (Execution thisExecution : executionList) {
                try (MongoConnection c = db.getConnectionFactory().newConnection()) {
                    executionDAO.updateReleaseVettingForExecutions(request1.getId(), request1.getTests().get(0).getId(), true);
                    executionDAO.update(c, thisExecution, new Document("$set", new Document("status", Execution.Status.FAIL.toString())));
                    requestDAO.update(c, request1.getId(), new Document("$set", new Document("failedTests", 1)));
                } catch (Exception ex) {
                    Assert.fail(ex.getMessage());
                }
            }

            transitionedRequest.setFailedTests(1);

            transitionedRequest = monitor.triggerStateEvent(transitionedRequest, Transitions.Message.COMPLETE_TESTS);

            System.out.println("running request, complete, id = " + transitionedRequest.getId());
            System.out.println("running request, complete" + transitionedRequest.getId());
            System.out.println("Status = " + transitionedRequest.getStatus());
            System.out.println("Result Status = " + transitionedRequest.getResultStatus());
            System.out.println("Release Recommendation = " + transitionedRequest.getReleaseRecommendation());

            Assert.assertEquals(transitionedRequest.getStatus(), ExecutionRequest.Status.TESTING_COMPLETE);
            Assert.assertEquals(transitionedRequest.getResultStatus(), ExecutionRequest.ResultStatus.FAILURE);
            Assert.assertEquals(transitionedRequest.getReleaseRecommendation(), ExecutionRequest.ReleaseRecommendation.DO_NOT_RELEASE);
            Assert.assertEquals(transitionedRequest.getId(), request1.getId());

            transitionedRequest.getReleaseTest().setCompletionAction(ReleaseTest.Action.RELEASE);
            OverrideDetails override = new OverrideDetails();
            override.setChangeId("1111");
            override.setOverrideApprover("approver");
            override.setStatus(OverrideDetails.Status.OVERRIDDEN);
            transitionedRequest.getReleaseTest().setOverride(override);
            
            transitionedRequest = monitor.triggerStateEvent(transitionedRequest, Transitions.Message.COMPLETE);

            Assert.assertEquals(transitionedRequest.getStatus(), ExecutionRequest.Status.COMPLETED);
            Assert.assertEquals(transitionedRequest.getResultStatus(), ExecutionRequest.ResultStatus.FAILURE);
            Assert.assertEquals(transitionedRequest.getReleaseRecommendation(), ExecutionRequest.ReleaseRecommendation.DO_NOT_RELEASE);
            Assert.assertEquals(transitionedRequest.getId(), request1.getId());

            delete(request1.getId(), requestDAO, db);

        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.getMessage(), ex);
        }
    }

    @Test
    public void testBatch() {
        System.out.println("testBatch******");

        try {
            Queue queue = new Queue(db, "BatchTest");
            Scheduler scheduler = new Scheduler();
            QueueMonitor monitor = new QueueMonitor(queue, scheduler, db, "BatchTest");
            monitor.monitorQueueEvents();

            boolean addTests = true;

            ExecutionRequest request1 = dbFactory.createBasicReleaseTest(0, addTests, "A", ReleaseRequest.ReleaseVehicle.ALTUS_ALM, "BatchTest",false);
            ExecutionRequest request2 = dbFactory.createBasicReleaseTest(1, addTests, "B", ReleaseRequest.ReleaseVehicle.ALTUS_ALM, "BatchTest",false);
            ExecutionRequest request3 = dbFactory.createBasicReleaseTest(1, !addTests,"C", ReleaseRequest.ReleaseVehicle.ALTUS_ALM, "BatchTest",false);

            queue.enqueue(request1, request1.getPriority());
            queue.enqueue(request2, request2.getPriority());
            queue.enqueue(request3, request3.getPriority());

            System.out.println("request1 = " + request1.getId());
            System.out.println("request2 = " + request2.getId());
            System.out.println("request3 = " + request3.getId());

            ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
            monitor.subscribe(this);
            result = new CompletableFuture<>();

            ExecutionRequest transitionedRequest1 = dao.getById(request1.getId());
            transitionedRequest1 = monitor.triggerStateEvent(transitionedRequest1, Transitions.Message.ABORT);
            Assert.assertEquals(transitionedRequest1.getStatus(), ExecutionRequest.Status.COMPLETED);

            List<ExecutionRequest> runningRequests = result.get(100, TimeUnit.SECONDS);
            Assert.assertEquals(runningRequests.size(), 2);        
            ExecutionRequest transitionedRequest2 = dao.getById(request2.getId());
            ExecutionRequest transitionedRequest3 = dao.getById(request3.getId());
            Assert.assertEquals(transitionedRequest2.getStatus(), ExecutionRequest.Status.DEPLOYING);
            Assert.assertEquals(transitionedRequest3.getStatus(), ExecutionRequest.Status.DEPLOYING);

            int deploymentTime = transitionedRequest2.getReleaseTest().getDeploymentEstimatedDuration();

            DeploymentHandler deploymentHandler = new DeploymentHandler(db,"BatchTest");

            deploymentHandler.extendDeploy(ReleaseTest.createFromExecutionRequest(transitionedRequest2), new ExtendDeployment(300));
            transitionedRequest2 = dao.getById(request2.getId());

            Assert.assertEquals(transitionedRequest2.getReleaseTest().getDeploymentEstimatedDuration(), deploymentTime + 300);

            ReleaseTest releaseTest2 = deploymentHandler.deployComplete(ReleaseTest.createFromExecutionRequest(transitionedRequest2));
            Assert.assertEquals(releaseTest2.getExecutionRequest().getStatus(), ExecutionRequest.Status.DEPLOY_WAITING);

            ReleaseTest releaseTest3 = deploymentHandler.deployComplete(ReleaseTest.createFromExecutionRequest(transitionedRequest3));

            transitionedRequest2 = dao.getById(request2.getId());
            
            Assert.assertEquals(releaseTest3.getExecutionRequest().getStatus(), ExecutionRequest.Status.COMPLETED);
            Assert.assertTrue(transitionedRequest2.getStatus() == ExecutionRequest.Status.IN_PROGRESS ||
                              transitionedRequest2.getStatus() == ExecutionRequest.Status.TESTING_COMPLETE);
            
            transitionedRequest2 = monitor.triggerStateEvent(transitionedRequest2, Transitions.Message.ABORT);
            Assert.assertEquals(transitionedRequest2.getStatus(), ExecutionRequest.Status.COMPLETED);

        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.getMessage(), ex);
        }
    }

    @Override
    public void onEvent(List<ExecutionRequest> requestList) {
        requestList.forEach((request) -> {
            System.out.println("On Event = " + request.getId() + ", state = " + request.getStatus());
        });
        result.complete(requestList);
    }

    private void delete(String id, ExecutionRequestDAO db, DatabaseConfig dbConfig) throws Exception {
        try (MongoConnection c = dbConfig.getConnectionFactory().newConnection()) {
            db.deleteById(c, id);
        }
    }
}
