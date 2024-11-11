/*
 * (C) 2020 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.TestConfiguration;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseRequest.ReleaseVehicle;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.data.execapi.SystemUnderTest;
import com.paypal.sre.cfbt.data.test.Component;
import com.paypal.sre.cfbt.data.test.Parameter;
import com.paypal.sre.cfbt.management.appproperty.ApplicationProperty;
import com.paypal.sre.cfbt.management.dal.ApplicationPropertyDAO;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.dal.TestDAO;
import com.paypal.sre.cfbt.mongo.MongoConnectionFakeFactory;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

/**
 * Create the fongo test db and a set of factories to insert data into said facgtory.
 */
public class DatabaseConfigFactory {
    private MongoConnectionFakeFactory db;
    @Mock private DatabaseConfig dbConfig;

    public DatabaseConfig databaseConfig(String dbName) {
        db = new MongoConnectionFakeFactory("", "", dbName, "", "");
        db.setMock(true);
        MockitoAnnotations.initMocks(this);        
        when(dbConfig.getConnectionFactory()).thenReturn(db);  
        
        return dbConfig;
    }

    public ExecutionRequest createBasicReleaseTest(int priority, boolean addTests, String componentName, ReleaseVehicle vehicle, String queueName, Boolean insert) throws Exception {
        Map<String, TestConfiguration> msMasterConfig = new HashMap<>();
        TestConfiguration testConfig = new TestConfiguration();
        testConfig.setEnabled(Boolean.TRUE);
        testConfig.setReleaseVetting(Boolean.TRUE);

        msMasterConfig.put("msmaster", testConfig);
        List<com.paypal.sre.cfbt.data.execapi.Test> tests = new ArrayList<>();

        if (addTests) {       
            Parameter param = new Parameter();
            param.setName("name1");
            param.setValue("val".toCharArray());
            com.paypal.sre.cfbt.data.execapi.Test test1 = new com.paypal.sre.cfbt.data.execapi.Test.TestBuilder("testName", "packageName", com.paypal.sre.cfbt.data.execapi.Test.Type.JAVA)
                    .className("className")
                    .jarName("jarName")
                    .methodName("methodName")
                    .enabled(Boolean.TRUE)
                    .installed(Boolean.TRUE)
                    .useForReleaseVetting(Boolean.TRUE)
                    .testConfigurations(msMasterConfig)
                    .parameters(Arrays.asList(param))
                    .build();
            test1.calculateNeedToBeConfigured();
            TestDAO.getInstance(db).insert(test1);
            tests.add(test1);
        }
 
        SystemUnderTest system = new SystemUnderTest();
        system.setDataCenter("msmaster");

        ExecutionRequest req = ExecutionRequest.builder()
                                    .numberTests(tests.size())
                                    .tests(tests)
                                    .status(ExecutionRequest.Status.PENDING)
                                    .resultStatus(ExecutionRequest.ResultStatus.IN_PROGRESS)
                                    .releaseRecommendation(ExecutionRequest.ReleaseRecommendation.PENDING)
                                    .datacenter("msmaster")
                                    .systemUnderTest(system)
                                    .type(ExecutionRequest.Type.RELEASE)
                                    .priority(priority)
                                    .queueName(queueName)
                                    .requestTime(DateUtil.currentDateTimeISOFormat())
                                    .build();

        List<Component> components = new ArrayList<>();
        Component component = new Component();
        component.setName(componentName);
        component.setCurrentVersion("1");
        components.add(component);

        ReleaseTest release = ReleaseTest.builder()
                                    .defaults()
                                    .components(components)
                                    .releaseVehicle(vehicle.toString())
                                    .deploymentEstimatedDuration(600)
                                    .changeId("1111")
                                    .build();
 
        req.setReleaseTest(release);

        if (insert) {
            ExecutionRequestDAO.getInstance(db).insert(req, queueName);
        }

        return req;
    }

    public ExecutionRequest createBasicExecutionRequest(String queueName, boolean insert) throws Exception {
        Map<String, TestConfiguration> msMasterConfig = new HashMap<>();
        TestConfiguration testConfig = new TestConfiguration();
        testConfig.setEnabled(Boolean.TRUE);
        testConfig.setReleaseVetting(Boolean.TRUE);
        SystemUnderTest system = new SystemUnderTest();
        system.setDataCenter("msmaster");

        msMasterConfig.put("msmaster", testConfig);
        com.paypal.sre.cfbt.data.execapi.Test test1 = new com.paypal.sre.cfbt.data.execapi.Test.TestBuilder("testName", "packageName", com.paypal.sre.cfbt.data.execapi.Test.Type.JAVA)
                    .className("className")
                    .jarName("jarName")
                    .enabled(Boolean.TRUE)
                    .installed(Boolean.TRUE)
                    .useForReleaseVetting(Boolean.TRUE)
                    .testConfigurations(msMasterConfig)
                    .build();
        test1.calculateNeedToBeConfigured();

        TestDAO.getInstance(db).insert(test1);

        List<com.paypal.sre.cfbt.data.execapi.Test> tests = new ArrayList<>();
        tests.add(test1);
        ExecutionRequest req = ExecutionRequest.builder()
                                    .numberTests(1)
                                    .tests(tests)
                                    .status(ExecutionRequest.Status.PENDING)
                                    .resultStatus(ExecutionRequest.ResultStatus.IN_PROGRESS)
                                    .releaseRecommendation(ExecutionRequest.ReleaseRecommendation.PENDING)
                                    .datacenter("msmaster")
                                    .systemUnderTest(system)
                                    .priority(1)
                                    .type(ExecutionRequest.Type.ADHOC).build();

        if (insert) {
            ExecutionRequestDAO.getInstance(db).insert(req, queueName);
        }

        return req;
    }

    public void initSpecialMessaging() throws Exception {
        ApplicationPropertyDAO appDAO = ApplicationPropertyDAO.getInstance();
        ApplicationProperty property = new ApplicationProperty();
        property.setLargeWaitTimeMessage("Expect a long wait.");
        property.setLargeWaitTimeThreshold(120);
        property.setMediumWaitTimeMessage("Expect a longer than normal wait.");
        property.setMediumWaitTimeThreshold(60);
        property.setNormalWaitTimeMessage("Deployments are proceeding normally. Expect a normal wait.");

        appDAO.insert(dbConfig.getConnectionFactory().newConnection(), property);
    }
}
