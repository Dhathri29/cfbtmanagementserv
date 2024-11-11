/*
 * (C) 2020 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseRequest.ReleaseVehicle;
import com.paypal.sre.cfbt.data.test.Parameter;
import com.paypal.sre.cfbt.management.CFBTTestResourceClient;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.mockito.Matchers;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

/**
 * Test various paths through the state machine.
 */
public class QueueTest {
    static int subscribeCount1 = 0;
    static int subscribeCount2 = 0;

    // The state machine is asynchronous, so rely on futures to get feedback.
    DatabaseConfig db;
    DatabaseConfigFactory dbFactory = new DatabaseConfigFactory();

    @BeforeSuite
    public void init() throws Exception {
        Configuration config = mock(Configuration.class);
        when(config.getInt(Matchers.anyString(), Matchers.anyInt())).thenReturn(5);
        CFBTTestResourceClient testresourceservClient = mock(CFBTTestResourceClient.class);
        List<Parameter> parameters = new ArrayList<>();
        db = dbFactory.databaseConfig("Test");
        dbFactory.initSpecialMessaging();

        when(testresourceservClient.loadParameters(Matchers.anyString(), Matchers.anyString())).thenReturn(parameters);
        ConfigManager.setConfiguration(config, db, testresourceservClient);
    }

    private void delete(String id, ExecutionRequestDAO db, DatabaseConfig dbConfig) throws Exception {

        try (MongoConnection c = dbConfig.getConnectionFactory().newConnection()) {
            db.deleteById(c, id);
        }
    }

    @Test
    public void testQueue() {
        System.out.println("testQueue******");

        try {
            boolean addTests = true;

            ExecutionRequest request0 = dbFactory.createBasicReleaseTest(1, !addTests, "Running", ReleaseVehicle.ALTUS_ALM, "BasicQueue", false);
            Queue queue = new Queue(db, "BasicQueue");
            QueueMonitor monitor = new QueueMonitor(queue, new Scheduler(), db, "BasicQueue");
            monitor.monitorQueueEvents();
            queue.enqueue(request0, request0.getPriority());

            ExecutionRequest request1 = dbFactory.createBasicReleaseTest(2, !addTests, "A", ReleaseVehicle.ALTUS_ALM, "BasicQueue", false);
            ExecutionRequest request2 = dbFactory.createBasicReleaseTest(1, addTests, "B", ReleaseVehicle.ALTUS_ALM, "BasicQueue", false);
            ExecutionRequest request3 = dbFactory.createBasicReleaseTest(0, addTests, "C", ReleaseVehicle.ALTUS_ALM, "BasicQueue", false);
            ExecutionRequest request4 = dbFactory.createBasicReleaseTest(1, !addTests, "A", ReleaseVehicle.ALTUS_ALM, "BasicQueue", false);
            System.out.println("request1 = " + request1.getId());
            System.out.println("request2 = " + request2.getId());
            System.out.println("request3 = " + request3.getId());
            System.out.println("request4 = " + request4.getId());

            queue.enqueue(request1, request1.getPriority());
            queue.enqueue(request2, request2.getPriority());
            queue.enqueue(request3, request3.getPriority());
            queue.enqueue(request4, request4.getPriority());

            ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(db.getConnectionFactory());

            List<ExecutionRequest> requestList = dao.getPositionedPendedRequests("BasicQueue");
            System.out.println("Size = " + requestList.size());

            request0 = dao.getById(request0.getId());
            request1 = dao.getById(request1.getId());
            request2 = dao.getById(request2.getId());
            request3 = dao.getById(request3.getId());
            request4 = dao.getById(request4.getId());
            System.out.println("Current Time = " + DateUtil.currentDateTimeISOFormat());

            System.out.println("Checking request = " + request1.getId());
            DateTime requestTime1 = DateUtil.dateTimeUTC(request1.getEstimatedStartTime());
            DateTime time1 = DateUtil.dateTimeUTC(request3.getEstimatedStartTime()).plusSeconds(
                            request3.getReleaseTest().getDeploymentEstimatedDuration() + EstimatedTime.DEFAULT_TEST_EXECUTION_DURATION);

            System.out.println("Request 1, position = " + request1.getPosition());
            System.out.println("Request 1 estimatedStartTime = "+ request1.getEstimatedStartTime());
            System.out.println("Request 1 Expected Time = " + DateUtil.dateTimeISOFormat(time1));
            Assert.assertEquals(request1.getPosition(), 2);
            Assert.assertEquals(requestTime1.minuteOfDay(), time1.minuteOfDay());
            Assert.assertEquals(requestTime1.hourOfDay(), time1.hourOfDay());

            DateTime requestTime2 = DateUtil.dateTimeUTC(request2.getEstimatedStartTime());
            System.out.println("Found request 2, position = " + request2.getPosition());
            System.out.println("Found Time = "+ request2.getEstimatedStartTime());
            System.out.println("Expected Time = " + DateUtil.dateTimeISOFormat(time1));
            Assert.assertEquals(request2.getPosition(), 2);
            Assert.assertEquals(requestTime2.minuteOfDay(), time1.minuteOfDay());
            Assert.assertEquals(requestTime2.hourOfDay(), time1.hourOfDay());

            DateTime requestTime3 = DateUtil.dateTimeUTC(request3.getEstimatedStartTime());
            DateTime time3 = DateUtil.currentDateTimeUTC().plusSeconds(
                            request0.getReleaseTest().getDeploymentEstimatedDuration());
            System.out.println("Found request 3, position = " + request3.getPosition());
            System.out.println("Found Time = "+ request3.getEstimatedStartTime());
            System.out.println("Expected Time = " + DateUtil.dateTimeISOFormat(time3));
            Assert.assertEquals(request3.getPosition(), 1);
            Assert.assertEquals(requestTime3.minuteOfDay(), time3.minuteOfDay());
            Assert.assertEquals(requestTime3.hourOfDay(), time3.hourOfDay());

            DateTime requestTime4 = DateUtil.dateTimeUTC(request4.getEstimatedStartTime());
            DateTime time4 = requestTime1.plusSeconds(
                            request1.getReleaseTest().getDeploymentEstimatedDuration() +
                            EstimatedTime.DEFAULT_TEST_EXECUTION_DURATION);

            System.out.println("Found request 4, position = " + request4.getPosition());
            System.out.println("Found Time = "+ request4.getEstimatedStartTime());
            System.out.println("Expected Time = " + DateUtil.dateTimeISOFormat(time4));
            Assert.assertEquals(request4.getPosition(), 3);
            Assert.assertEquals(requestTime4.minuteOfDay(), time4.minuteOfDay());
            Assert.assertEquals(requestTime4.hourOfDay(), time4.hourOfDay());

            delete(request1.getId(), dao, db);
            delete(request2.getId(), dao, db);
            delete(request3.getId(), dao, db);
            delete(request4.getId(), dao, db);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.getMessage(), ex);
        }
    }

    @Test
    public void testChangePosition() {
        System.out.println("testChangePosition******");

        try {
            boolean addTests = true;
            boolean insertRequest = true;

            ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
            Queue queue = new Queue(db, "ChangePosition");
            QueueMonitor monitor = new QueueMonitor(queue, new Scheduler(), db, "ChangePosition");
            monitor.monitorQueueEvents();

            ExecutionRequest request1 = dbFactory.createBasicReleaseTest(2, addTests, "A", ReleaseVehicle.ALTUS_ALM, "ChangePosition", !insertRequest);
            QueueUtil.queueAndWait(request1, queue, monitor);

            ExecutionRequest request2 = dbFactory.createBasicReleaseTest(2, addTests, "B", ReleaseVehicle.ALTUS_ALM, "ChangePosition", !insertRequest);
            QueueUtil.queueAndWait(request2, queue, monitor);

            ExecutionRequest request3 = dbFactory.createBasicReleaseTest(2, addTests, "C", ReleaseVehicle.ALTUS_ALM, "ChangePosition", !insertRequest);
            QueueUtil.queueAndWait(request3, queue, monitor);

            ExecutionRequest request4 = dbFactory.createBasicReleaseTest(2, addTests, "D", ReleaseVehicle.ALTUS_ALM, "ChangePosition", !insertRequest);
            QueueUtil.queueAndWait(request4, queue, monitor);

            ExecutionRequest request5 = dbFactory.createBasicReleaseTest(2, !addTests, "E", ReleaseVehicle.ALTUS_ALM, "ChangePosition", !insertRequest);
            QueueUtil.queueAndWait(request5, queue, monitor);

            ExecutionRequest request6 = dbFactory.createBasicReleaseTest(2, !addTests, "B", ReleaseVehicle.ALTUS_ALM, "ChangePosition", !insertRequest);
            QueueUtil.queueAndWait(request6, queue, monitor);

            System.out.println("request1 = " + request1.getId());
            System.out.println("request2 = " + request2.getId());
            System.out.println("request3 = " + request3.getId());
            System.out.println("request4 = " + request4.getId());
            System.out.println("request5 = " + request5.getId());

            Assert.assertEquals(dao.getById(request1.getId()).getPosition(), 0);
            Assert.assertEquals(dao.getById(request2.getId()).getPosition(), 1);
            Assert.assertEquals(dao.getById(request3.getId()).getPosition(), 2);
            Assert.assertEquals(dao.getById(request4.getId()).getPosition(), 3);
            Assert.assertEquals(dao.getById(request5.getId()).getPosition(), 1);
            Assert.assertEquals(dao.getById(request6.getId()).getPosition(), 2);
            
            queue.changePosition(request4, -2);

            Assert.assertEquals(dao.getById(request1.getId()).getPosition(), 0);
            Assert.assertEquals(dao.getById(request2.getId()).getPosition(), 2);
            Assert.assertEquals(dao.getById(request3.getId()).getPosition(), 3);
            Assert.assertEquals(dao.getById(request4.getId()).getPosition(), 1);
            Assert.assertEquals(dao.getById(request5.getId()).getPosition(), 2);
            Assert.assertEquals(dao.getById(request6.getId()).getPosition(), 3);

            queue.changePosition(request4, 2);
            Assert.assertEquals(dao.getById(request1.getId()).getPosition(), 0);
            Assert.assertEquals(dao.getById(request2.getId()).getPosition(), 1);
            Assert.assertEquals(dao.getById(request3.getId()).getPosition(), 2);
            Assert.assertEquals(dao.getById(request4.getId()).getPosition(), 3);
            Assert.assertEquals(dao.getById(request5.getId()).getPosition(), 1);
            Assert.assertEquals(dao.getById(request6.getId()).getPosition(), 2);

            queue.changePosition(request6, -1);
            Assert.assertEquals(dao.getById(request1.getId()).getPosition(), 0);
            Assert.assertEquals(dao.getById(request2.getId()).getPosition(), 2);
            Assert.assertEquals(dao.getById(request3.getId()).getPosition(), 3);
            Assert.assertEquals(dao.getById(request4.getId()).getPosition(), 4);
            Assert.assertEquals(dao.getById(request5.getId()).getPosition(), 2);
            Assert.assertEquals(dao.getById(request6.getId()).getPosition(), 1);

            queue.changePosition(request6, 2);
            Assert.assertEquals(dao.getById(request1.getId()).getPosition(), 0);
            Assert.assertEquals(dao.getById(request2.getId()).getPosition(), 1);
            Assert.assertEquals(dao.getById(request3.getId()).getPosition(), 2);
            Assert.assertEquals(dao.getById(request4.getId()).getPosition(), 3);
            Assert.assertEquals(dao.getById(request5.getId()).getPosition(), 1);
            Assert.assertEquals(dao.getById(request6.getId()).getPosition(), 2);

            queue.changePosition(request5, 1);
            Assert.assertEquals(dao.getById(request1.getId()).getPosition(), 0);
            Assert.assertEquals(dao.getById(request2.getId()).getPosition(), 1);
            Assert.assertEquals(dao.getById(request3.getId()).getPosition(), 2);
            Assert.assertEquals(dao.getById(request4.getId()).getPosition(), 3);
            Assert.assertEquals(dao.getById(request5.getId()).getPosition(), 2);
            Assert.assertEquals(dao.getById(request6.getId()).getPosition(), 2);

            try {
                queue.changePosition(request1, 5);
                Assert.fail("Should have thrown an exception");
            } catch (Exception ex) {
                System.out.println("Caught an excpected exception = " + ex.getMessage());
                ex.printStackTrace();
            }

            queue.changePosition(request2, 0);
            Assert.assertEquals(dao.getById(request1.getId()).getPosition(), 0);
            Assert.assertEquals(dao.getById(request2.getId()).getPosition(), 1);
            Assert.assertEquals(dao.getById(request3.getId()).getPosition(), 2);
            Assert.assertEquals(dao.getById(request4.getId()).getPosition(), 3);
            Assert.assertEquals(dao.getById(request5.getId()).getPosition(), 2);
            Assert.assertEquals(dao.getById(request6.getId()).getPosition(), 2);

            delete(request1.getId(), dao, db);
            delete(request2.getId(), dao, db);
            delete(request3.getId(), dao, db);
            delete(request4.getId(), dao, db);
            delete(request5.getId(), dao, db);
            delete(request6.getId(), dao, db);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.getMessage(), ex);
        }
    }
}
