/*
 * (C) 2020 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.Alert;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseRequest;
import com.paypal.sre.cfbt.management.appproperty.ApplicationProperty;
import com.paypal.sre.cfbt.management.dal.ApplicationPropertyDAO;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.configuration.Configuration;
import org.bson.Document;
import org.junit.Assert;
import org.mockito.Matchers;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

/**
 *
 */
public class SpecialMessageTest {
    DatabaseConfig db;
    DatabaseConfigFactory dbFactory = new DatabaseConfigFactory();
    String queueName = "Test";
    String normalWaitTimeMessage;
    String mediumWaitTimeMessage;
    String largeWaitTimeMessage;

    @BeforeSuite
    public void init() throws Exception {
        db = dbFactory.databaseConfig(queueName);
        Configuration config = mock(Configuration.class);
        when(config.getInt(Matchers.anyString(), Matchers.anyInt())).thenReturn(5);
        
        ApplicationPropertyDAO appDAO = ApplicationPropertyDAO.getInstance();
        List<ApplicationProperty> appProperties = new ArrayList<>();
        ApplicationProperty appProperty = new ApplicationProperty();
        appProperty.setLargeWaitTimeThreshold(120);
        appProperty.setMediumWaitTimeThreshold(60);
        appProperty.setLargeWaitTimeMessage("Expect a long wait.");
        appProperty.setMediumWaitTimeMessage("Expect a longer than normal wait.");
        appProperty.setNormalWaitTimeMessage("Deployments are proceeding normally. Expect a normal wait.");
        appProperty.setNumberOfThreads(2);
        appProperty.setNodeIsDeadInMinutes(10);
        appProperty.setThreadIsDownInMinutes(10);
        appProperty.setNodeClusterActiveInMinutes(10);
        
        appProperties.add(appProperty);
        appDAO.insert(db.getConnectionFactory().newConnection(), appProperties);
        ApplicationProperty property = appDAO.getApplicationProperty(db.getConnectionFactory().newConnection());
        
        normalWaitTimeMessage = property.getNormalWaitTimeMessage();
        mediumWaitTimeMessage = property.getMediumWaitTimeMessage();
        largeWaitTimeMessage = property.getLargeWaitTimeMessage();
    }

    @Test
    public void normalTest() {
        try {
            ExecutionRequest request1 = dbFactory.createBasicReleaseTest(1, true, "A", ReleaseRequest.ReleaseVehicle.CRS, queueName, Boolean.TRUE);
            request1.setEstimatedStartTime(DateUtil.dateTimeISOFormat(DateUtil.currentDateTimeUTC().plusMinutes(10)));

            SpecialMessage special = new SpecialMessage(db, queueName);
            special.addSpecialMessage(request1);

            Assert.assertEquals(Alert.Type.SUCCESS.toString(), request1.getReleaseTest().getAlert().getType());
            Assert.assertTrue(request1.getReleaseTest().getAlert().getMessage().contains(normalWaitTimeMessage));
        } catch (Exception ex) {
            Assert.fail(ex.getMessage());
        }
    }

    @Test
    public void MediumTest() {
        try {
            ExecutionRequest request1 = dbFactory.createBasicReleaseTest(0, true, "A", ReleaseRequest.ReleaseVehicle.CRS, queueName, Boolean.TRUE);
            ExecutionRequest request2 = dbFactory.createBasicReleaseTest(1, true, "B", ReleaseRequest.ReleaseVehicle.CRS, queueName, Boolean.TRUE);
            request1.setRequestTime(DateUtil.dateTimeISOFormat(DateUtil.currentDateTimeUTC().plusMinutes(5)));
            request1.setEstimatedStartTime(DateUtil.dateTimeISOFormat(DateUtil.currentDateTimeUTC().plusMinutes(10)));
            request1.getReleaseTest().setDeploymentEstimatedDuration(70*60);
            request1.setPosition(1);
            request2.setPosition(2);            
            request2.setEstimatedStartTime(DateUtil.dateTimeISOFormat(DateUtil.currentDateTimeUTC().plusMinutes(80)));
            ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
            dao.update(db.getConnectionFactory().newConnection(), request1, 
                    new Document("$set", new Document("position", request1.getPosition())
                            .append("requestTime", request1.getRequestTime())
                            .append("estimatedStartTime", request1.getEstimatedStartTime())
                            .append("releaseTest.deploymentEstimatedDuration", request1.getReleaseTest().getDeploymentEstimatedDuration())));
            
            dao.update(db.getConnectionFactory().newConnection(), request2, new Document("$set", new Document("position", request2.getPosition()).append("estimatedStartTime", request2.getEstimatedStartTime())));

            SpecialMessage special = new SpecialMessage(db, queueName);
            special.addSpecialMessage(request2);

            Assert.assertEquals(Alert.Type.WARNING.toString(), request2.getReleaseTest().getAlert().getType());
            Assert.assertTrue(request2.getReleaseTest().getAlert().getMessage().contains(mediumWaitTimeMessage));

            boolean foundCutInMessage = false;
            boolean foundLongRelease = false;
            for (String reason : request2.getReleaseTest().getAlert().getReasons()) {
                if (reason.equals(SpecialMessage.HIGH_PRIORITY_CUT_IN)) {
                    foundCutInMessage = true;
                } else if (reason.equals(SpecialMessage.LARGE_ESTIMATE_WAIT_RELEASE)) {
                    foundLongRelease = true;
                }
            }

            Assert.assertTrue(foundLongRelease);
            Assert.assertTrue(foundCutInMessage);
                
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.getMessage());
        }
    }

    @Test
    public void LargeTest() {
        try {
            ExecutionRequest request1 = dbFactory.createBasicReleaseTest(1, true, "A", ReleaseRequest.ReleaseVehicle.CRS, queueName, Boolean.TRUE);
            ExecutionRequest request2 = dbFactory.createBasicReleaseTest(1, true, "B", ReleaseRequest.ReleaseVehicle.CRS, queueName, Boolean.TRUE);
            ExecutionRequest request3 = dbFactory.createBasicReleaseTest(1, true, "C", ReleaseRequest.ReleaseVehicle.CRS, queueName, Boolean.TRUE);
            ExecutionRequest request4 = dbFactory.createBasicReleaseTest(1, true, "D", ReleaseRequest.ReleaseVehicle.CRS, queueName, Boolean.TRUE);
            ExecutionRequest request5 = dbFactory.createBasicReleaseTest(1, true, "E", ReleaseRequest.ReleaseVehicle.CRS, queueName, Boolean.TRUE);

            request1.setEstimatedStartTime(DateUtil.dateTimeISOFormat(DateUtil.currentDateTimeUTC().plusMinutes(10)));
            request2.setEstimatedStartTime(DateUtil.dateTimeISOFormat(DateUtil.currentDateTimeUTC().plusMinutes(10)));
            request3.setEstimatedStartTime(DateUtil.dateTimeISOFormat(DateUtil.currentDateTimeUTC().plusMinutes(10)));
            request4.setEstimatedStartTime(DateUtil.dateTimeISOFormat(DateUtil.currentDateTimeUTC().plusMinutes(10)));
            request5.setEstimatedStartTime(DateUtil.dateTimeISOFormat(DateUtil.currentDateTimeUTC().plusMinutes(130)));


            SpecialMessage special = new SpecialMessage(db, "testQueue");
            special.addSpecialMessage(request5);

            Assert.assertEquals(Alert.Type.DANGER.toString(), request5.getReleaseTest().getAlert().getType());
            Assert.assertTrue(request5.getReleaseTest().getAlert().getMessage().contains(largeWaitTimeMessage));
            Assert.assertEquals(SpecialMessage.LARGE_NUMBER_RELEASES_AHEAD, request5.getReleaseTest().getAlert().getReasons().get(0));

        } catch (Exception ex) {
            Assert.fail(ex.getMessage());
        }
    }    
}
