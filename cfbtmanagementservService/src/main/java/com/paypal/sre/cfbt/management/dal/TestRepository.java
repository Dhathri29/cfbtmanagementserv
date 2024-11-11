package com.paypal.sre.cfbt.management.dal;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.sre.cfbt.data.Activity;
import com.paypal.sre.cfbt.dataaccess.ActivityDAO;
import com.paypal.sre.cfbt.request.User;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.joda.time.DateTime;
import com.mongodb.BasicDBObject;
import com.mongodb.client.AggregateIterable;
import com.paypal.sre.cfbt.data.TestConfiguration;
import com.paypal.sre.cfbt.data.execapi.ComponentList;
import com.paypal.sre.cfbt.data.execapi.Datacenter;
import com.paypal.sre.cfbt.data.execapi.Execution;
import com.paypal.sre.cfbt.data.execapi.Test;
import com.paypal.sre.cfbt.data.test.Component;
import com.paypal.sre.cfbt.data.test.TestStatistics;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.mongo.MongoDataMarshaller;
import com.paypal.sre.cfbt.shared.DateUtil;
import com.rits.cloning.Cloner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRepository {
    private static final Logger logger = LoggerFactory.getLogger(TestRepository.class);
    /**
     * Correct the selected browser if it's set incorrectly.
     * @param aDB a {@link MongoConnectionFactory}
     * @param c {@link MongoConnection}
     * @param test {@link Test}
     */
    private void correctTestData(MongoConnectionFactory aDB, MongoConnection c, Test test) {
        // It's possible needToBeConfigured has been affected, so update this as well, just in case.
        if (test != null) {
            Document correctedData = new Document();

            if (test.correctSelectedBrowser()) {
                correctedData.append("selectedBrowser", test.getSelectedBrowser())
                             .append("needToBeConfigured", test.getNeedToBeConfigured());
            }

            if (test.getTypeSerializable()== null) {
                test.setType(Test.Type.JAVA);

                correctedData.append("type", Test.Type.JAVA.name());
            }

            if (!correctedData.isEmpty()) {
                TestDAO.getInstance(aDB).update(c, test.getId(), new Document("$set", correctedData));
            }
        }
    }

    /**
     * Update the statistics of the supplied {@code Test} object using the latest {@code Execution} result. TODO:
     * Vulnerable to contention, since I'm doing a read, then an update. I may blow away another's updates.
     *
     * @param aDB
     *            a {@link MongoConnectionFactory}
     * @param test
     *            the {@link Test} object to update
     * @param result
     *            the latest {@link Execution} result
     * @return the updated test
     * @throws UnknownHostException
     *             Indicates that the IP address of the host could not be determined.
     * @throws Exception
     *             Indicates a general exception.
     */
    public Test updateLatestResult(MongoConnectionFactory aDB, Test test, Execution result)
            throws UnknownHostException, Exception {
        Test aTest = null;
        final String statuses = "PASS:FAIL:ERROR:IN_PROGRESS";

        try (MongoConnection c = aDB.newConnection()) {
            aTest = TestDAO.getInstance(aDB).readOne(c, test.getId(), false, null);

            if (aTest != null) {
                TestStatistics stats = aTest.getTestStatistics();

                if (result.getExecutionTime() != null) {
                    stats.setLastRunTime(result.getExecutionTime());
                }

                if (result.getDurationTime() != null && statuses.contains(result.getStatus().toString())) {
                    int numExecutions = stats.getNumberExecutions();

                    Double prevAvg = stats.getAverageExecutionTime();
                    // Calculate new total time.
                    Double totalTime = prevAvg * numExecutions + result.getDurationTime();
                    // Increment number of executions.
                    numExecutions++;

                    stats.setNumberExecutions(numExecutions);
                    stats.setAverageExecutionTime(totalTime / numExecutions);

                    if (result.getDurationTime() < stats.getMinExecutionTime() || stats.getMinExecutionTime() == 0) {
                        stats.setMinExecutionTime(result.getDurationTime());
                    }

                    if (result.getDurationTime() > stats.getMaxExecutionTime() || stats.getMaxExecutionTime() == 0) {
                        stats.setMaxExecutionTime(result.getDurationTime());
                    }
                }

                if (Execution.Status.PASS.equals(result.getStatus())) {
                    stats.setNumberSuccesses(stats.getNumberSuccesses() + 1);
                }
                else if (Execution.Status.FAIL.equals(result.getStatus())) {
                    stats.setNumberFailures(stats.getNumberFailures() + 1);
                }
                else if (Execution.Status.ERROR.equals(result.getStatus())) {
                    stats.setNumberErrors(stats.getNumberErrors() + 1);
                }
                BasicDBObject updateSpec = new BasicDBObject();
                BasicDBObject updateFields = new BasicDBObject();

                updateFields.put("testStatistics", MongoDataMarshaller.encode(stats));
                if (!Execution.Status.IN_PROGRESS.equals(result.getStatus())) { //do not calc realtime stats for In Progress
                    //add statistics for any executions within 7 days prior to this execution
                    TestStatistics stats7 = this.getStatisticsForTest(aDB, aTest.getId(), 1,
                            result.getStatus(), result.getDurationTime(), stats.getLastRunTime());
                    aTest.setTestStatistics7(stats7);
                    updateFields.put("testStatistics7", MongoDataMarshaller.encode(stats7));
                    //add statistics for any executions within 30 days prior to this execution
                    TestStatistics stats30 = this.getStatisticsForTest(aDB, aTest.getId(), 3,
                            result.getStatus(), result.getDurationTime(), stats.getLastRunTime());
                    aTest.setTestStatistics30(stats30);
                    updateFields.put("testStatistics30", MongoDataMarshaller.encode(stats30));
                }
                updateFields.put("time_updated", DateUtil.currentDateTimeISOFormat());
                updateSpec.put("$set", updateFields);
                TestDAO.getInstance(aDB).update(c, test.getId(), updateSpec);
            }
        }

        return aTest;
    }
    
    public TestStatistics getStatisticsForTest(MongoConnectionFactory db, String testId, Integer days,
            Execution.Status lastStatus, Integer lastDuration, String lastRunTime) throws Exception {
        TestStatistics returnStats = new TestStatistics();
        AggregateIterable<Document> output = null;

        // initialize returnStats;
        returnStats.setAverageExecutionTime((Double) 0.0);
        returnStats.setMinExecutionTime(0);
        returnStats.setMaxExecutionTime(0);
        returnStats.setNumberSuccesses(0);
        returnStats.setNumberFailures(0);
        returnStats.setNumberFalseNegatives(0);
        returnStats.setNumberFalsePositives(0);
        returnStats.setNumberErrors(0);

        // only pull records for these statuses - existing runs and current (in progress) run.
        final String statuses = "PASS:FAIL:ERROR:IN_PROGRESS";
        List<Document> dateOr = new ArrayList<>();

        if (lastRunTime != null) {
            DateTime dateTimeStart = DateTime.parse(lastRunTime).toDateTimeISO().minusDays(days);
            DateTime dateTimeEnd = DateTime.parse(lastRunTime).toDateTimeISO().plusMinutes(1);

            dateOr.add(new Document("executionTime", new Document("$lte", dateTimeEnd.toString().replaceAll(".000", ""))
                .append("$gte", dateTimeStart.toString().replaceAll(".000", ""))));
        }

        dateOr.add(new Document("executionTime", new Document("$exists", false)));
        Document matchBy = new Document("$or", dateOr);
        matchBy.append("testId", testId);
        matchBy.append("status", new Document("$in", Arrays.asList(statuses.split(":"))));
        Document groupStatusBy = new Document("_id",
                new Document("status", "$status").append("correctStatus", "$correctStatus")).append("total",
                        new Document("$sum", 1));
        Document groupDurationBy = new Document("_id", null).append("count", new Document("$sum", 1))
                .append("average", new Document("$avg", "$durationTime"))
                .append("min", new Document("$min", "$durationTime"))
                .append("max", new Document("$max", "$durationTime"));

        TestExecutionDAO testExecutionDAO = TestExecutionDAO.getInstance(db);

        output = testExecutionDAO.aggregate(db.newConnection(),
                Arrays.asList(new Document("$match", matchBy), new Document("$group", groupStatusBy)));

        // first tally the fail/pass/error statistics for previous executions.
        for (Document dbObject : output) {
            Document theStatDoc = dbObject.get("_id", Document.class);
            boolean isItCorrect = theStatDoc.getBoolean("correctStatus");
            int theValue = dbObject.getInteger("total");
            switch (theStatDoc.getString("status")) {
            case "FAIL":
                if (isItCorrect) {
                    returnStats.setNumberFailures(theValue);
                } else {
                    returnStats.setNumberFalseNegatives(theValue);
                }
                break;
            case "PASS":
                if (isItCorrect) {
                    returnStats.setNumberSuccesses(theValue);
                } else {
                    returnStats.setNumberFalsePositives(theValue);
                }
                break;
            case "ERROR":
                returnStats.setNumberErrors(theValue);
                break;
            default:
                break;
            }
        }

        output = testExecutionDAO.aggregate(db.newConnection(),
                Arrays.asList(new Document("$match", matchBy), new Document("$group", groupDurationBy)));
        Document theStatDoc = output.first();
        // if we have stats returned from aggregate, and we have a duration passed in for
        // current execution, collect min/max/average/count stats for existing executions
        if (theStatDoc != null && lastDuration != null) {
            Integer minObj = (minObj = theStatDoc.getInteger("min")) != null ? minObj : 0;
            Integer maxObj = (maxObj = theStatDoc.getInteger("max")) != null ? maxObj : 0;
            Double avgObj = (avgObj = theStatDoc.getDouble("average")) != null ? avgObj : 0;
            Integer countObj = theStatDoc.getInteger("count");
            if ((minObj > lastDuration || minObj == 0) && statuses.contains(lastStatus.toString())) {
                returnStats.setMinExecutionTime(lastDuration);
            } else {
                returnStats.setMinExecutionTime(minObj);
            }
            if (maxObj < lastDuration && statuses.contains(lastStatus.toString())) {
                returnStats.setMaxExecutionTime(lastDuration);
            } else {
                returnStats.setMaxExecutionTime(maxObj);
            }

            // then adjust min/max/average with the lastDuration from current execution
            // but only if not aborted or skipped
            Double totalTime = avgObj * (countObj - 1);
            if (statuses.contains(lastStatus.toString())) {
                totalTime = totalTime + lastDuration;
            } else {
                countObj = countObj - 1;
            }
            returnStats.setNumberExecutions(countObj);
            returnStats.setAverageExecutionTime(totalTime / countObj);
        }

        return returnStats;
    }

    /**
     * Method to get all the tests in DB.
     *
     * @param aDB
     *            - the {@link MongoConnectionFactory} getConfiguration which should be used to retrieve the data
     * @return the list of {@link Test}, based on the search keyword.
     * @throws java.net.UnknownHostException
     *             If it has trouble connecting with Mongo.
     */
    public List<Test> getTests(MongoConnectionFactory aDB) throws UnknownHostException, Exception {

        List<Test> results;

        try (MongoConnection c = aDB.newConnection()) {
            Document queryDoc = new Document("installed", true);
            queryDoc.append("needToBeConfigured", false);

            results = TestDAO.getInstance(aDB).read(c, queryDoc, null, null);
            int defaultExecutionTime = ConfigManager.getConfiguration().getInt("maxTestExecutionTimeInMinutes", 6) * 60;
            for (Test aTest : results) {
                if (aTest.getMaxExecutionTime() == 0) {
                    aTest.setMaxExecutionTime(defaultExecutionTime);
                }

                correctTestData(aDB, c, aTest);
            }
        }

        return results;
    }

    /**
     * Return all tests that are affected by a list of components
     *
     * @param aDB
     *            a {@code MongoConnectionFactory}
     * @param components
     *            a {@code ComponentList} used to search the {@code Test} database.
     * @param datacenter The datacenter name
     * @return a list of {@code Test} objects.
     * @throws java.lang.Exception
     *             If there's an exception loading tests.
     */
    public List<Test> searchTest(MongoConnectionFactory aDB, ComponentList components,
            String datacenter)
            throws Exception {

        List<Test> allTests = getTests(aDB); // we only want configured tests
        List<Test> outTests = new ArrayList<>();

        if (components == null) {
            throw new IllegalArgumentException("compnents is null");
        }

        Boolean compFound;
        for (Test aTest : allTests) {
            boolean dcSpecificEnabled = aTest.isEnabledForDatacenter(datacenter);

            if (aTest.getEnabled() && !aTest.getNeedToBeConfigured() && dcSpecificEnabled) {
                compFound = false;
                for (Component component : components.getComponents()) {
                    component.setName(component.getName().toLowerCase());
                    List<String> compNames = new ArrayList<>();
                    for (Component aComp : aTest.getComponents()) {
                        compNames.add(aComp.getName());
                    }
                    if (compNames.contains(component.getName())) {
                        compFound = true;
                    }
                }
                if (compFound) {
                    outTests.add(aTest);
                }
            }
        }

        return outTests;
    }

    /**
     * Return all tests that are mapped to users
     *
     * @param userDetails
     *            contains the user details
     * @param releaseEnabledTests
     *            a {@code ComponentList} used to search the {@code Test} database.
     * @param user User name
     * @return a list of {@code Test} objects.
     */
    public List<Test> filterEnabledTestForUser(List<Test> releaseEnabledTests, User userDetails, String user) {
        CFBTLogger.logInfo(logger, "filterEnabledTestForUser", "Updating the test list for the user :"+user);
        String filePath = "testConfig/CFBTAccountsMapper.json";
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<String>> mappedUserTests = new HashMap<>();
        List<Test> filteredTest = new ArrayList<>();
        try {

            InputStream locatorFile = getClass().getClassLoader().getResourceAsStream(filePath);
            mappedUserTests = mapper.readValue(locatorFile, new TypeReference<Map<String, List<String>>>() {
            });
            if(mappedUserTests.get(userDetails.getUserId())!=null){
                for(Test test : releaseEnabledTests){
                    if(mappedUserTests.get(userDetails.getUserId()).contains(test.getMethodName())){
                        filteredTest.add(test);
                    }
                }
            }
            else if(mappedUserTests.get(user)!=null){
                for(Test test : releaseEnabledTests){
                    if(mappedUserTests.get(user).contains(test.getMethodName())){
                        filteredTest.add(test);
                    }
                }
            }
            else{
                return releaseEnabledTests;
            }

        } catch (IOException e) {
            CFBTLogger.logError(CFBTLogger.CalEventEnum.TEST_UPDATE, "CFBT API: Error while updating test list for the given user.", e);
        }
        return filteredTest;
    }

    /**
     * Method to get a single test in the DB.
     *
     * @param mDB
     *            - the {@link MongoConnectionFactory} getConfiguration which should be used to retrieve the data
     * @param aId
     *            - the id of the test as {@link String}.
     * @return the {@link Test}, based on the id.
     * @throws java.net.UnknownHostException
     *             Throws when unable to connect to host.
     */
    public Test getTest(MongoConnectionFactory mDB, String aId)
            throws UnknownHostException, Exception {
        Test test;
        try (MongoConnection c = mDB.newConnection()) {
            test = TestDAO.getInstance(mDB).readOne(c, aId);
            correctTestData(mDB, c, test);

            if (test != null && test.getMaxExecutionTime() == 0) {
                test.setMaxExecutionTime(ConfigManager.getConfiguration().getInt("maxTestExecutionTimeInMinutes", 6) * 60);
            }
        }

        return test;
    }
    
    /**
     * This method is responsible for updating the testConfiguration whenever dataCenter get updated
     * @param mDB - The Mongo connection factory
     * @param dataCenters - List of DataCenter
     * @throws Exception 
     */
    public void updateTestConfigurations(MongoConnectionFactory mDB, List<Datacenter> dataCenters) throws Exception{
        Runnable runnableThread = new Runnable() {
        @Override
        public void run(){
        try (MongoConnection c = mDB.newConnection()) {
            List<Test> tests =  TestDAO.getInstance(mDB).read(c, new Document());
            for(Test test : tests){
                boolean changeDetected = false;
                Map<String, TestConfiguration> testConfigurations = test.getTestConfigurations();
                if(testConfigurations == null || testConfigurations.size() == 0 ||
                        testConfigurations.size() != dataCenters.size()){
                    changeDetected = true;
                } else {
                    for(Datacenter dataCenter : dataCenters) {
                        if(!StringUtils.isBlank(dataCenter.getName()) && !testConfigurations.containsKey(dataCenter.getName())) {
                            changeDetected = true;
                        }
                    }
                }
                if(changeDetected) {
                    Map<String, TestConfiguration> testConfigurationsUpdated = new HashMap<String, TestConfiguration>();
                    List<String> dataCenterToBeUpdated = new ArrayList<>();
                    for (Datacenter dataCenter : dataCenters) {
                        if (!StringUtils.isBlank(dataCenter.getName())) {
                            if(testConfigurations != null && testConfigurations.containsKey(dataCenter.getName())) {
                                //Incase testConfigurations of existing dataCenter exists then keep the value
                                testConfigurationsUpdated.put(dataCenter.getName(), testConfigurations.get(dataCenter.getName()));
                            } else{
                                TestConfiguration testConfiguration = new TestConfiguration();
                                testConfiguration.setEnabled(test.getEnabled());
                                testConfiguration.setReleaseVetting(test.getUseForReleaseVetting());
                                testConfigurationsUpdated.put(dataCenter.getName(), testConfiguration);
                                dataCenterToBeUpdated.add(dataCenter.getName());
                            }
                        }
                    }
                    Cloner cloner = new Cloner();
                    Test testUpdated = cloner.deepClone(test);
                    testUpdated.setId(null);
                    testUpdated.setTestConfigurations(testConfigurationsUpdated);
                    testUpdated.setTimeUpdated(DateUtil.currentDateTimeISOFormat());
                    TestDAO.getInstance(mDB).update(c, test, new Document("$set", MongoDataMarshaller.encode(testUpdated)));
                    createActivitiesForNewTestConfig(mDB, test.getId(), test.getEnabled(), test.getUseForReleaseVetting(),dataCenterToBeUpdated);
                }
            }
            CFBTLogger.logInfo(logger, "updateTestConfigurations", "Successfully updated the data centers in the test configurations." );
        }catch(Exception e){
            CFBTLogger.logError(CFBTLogger.CalEventEnum.UPDATE_DATACENTERS, "CFBT API: Error while updating data centers in the test configurations.", e);
        }
        }
        };
        new Thread(runnableThread).start();
    }

    /**
     * This method is responsible for updating the testConfiguration whenever dataCenter gets updated
     * @param mDB - The Mongo connection factory
     * @param testId - Test ID
     * @param enabledFlag - Enabled flag
     * @param releaseVettingFlag - Release Vetting flag
     * @throws Exception
     */
    private void createActivitiesForNewTestConfig(MongoConnectionFactory mDB,String testId , boolean enabledFlag , boolean releaseVettingFlag , List<String> dataCenterToBeUpdated) {
        for(String dataCenter : dataCenterToBeUpdated) {
            createActivity(mDB, testId, "TestDataCenterEnabled", "/testConfigurations/" + dataCenter + "/enabled",enabledFlag);
            createActivity(mDB, testId, "TestDataCenterReleaseVetting", "/testConfigurations/" + dataCenter + "/releaseVetting",releaseVettingFlag);
        }
    }

    /**
     * This method is responsible for creating an activity
     * @param mDB - The Mongo connection factory
     * @param testId - Test ID
     * @param eventName - Event name
     * @param fieldName -Field name
     * @param currentValue - value of enabled / releaseVetting flag
     * @throws Exception
     */
    private void createActivity(MongoConnectionFactory mDB,String testId , String eventName , String fieldName, boolean currentValue) {
        try {
            Activity activity = new Activity();
            activity.setActor("cfbt_system");
            activity.setTargetId(testId);

            String previousValue = null;
            String latestValue = null;
            if (Boolean.TRUE.equals(currentValue)) {
                previousValue = Boolean.FALSE.toString();
                latestValue = Boolean.TRUE.toString();
            } else {
                previousValue = Boolean.TRUE.toString();
                latestValue = Boolean.FALSE.toString();
            }
            activity.setPreviousValue(previousValue);
            activity.setLatestValue(latestValue);
            activity.setTimeModified(DateUtil.currentDateTimeISOFormat());
            activity.setEventName(eventName);
            activity.setFieldName(fieldName);
            activity.setAction(Activity.Action.ADD);
            List<String> groups = new ArrayList<>();
            groups.add("Test");
            activity.setGroups(groups);
            activity.setId(null);
            ActivityDAO activityDAO = ActivityDAO.getInstance();
            Activity activityOut = activityDAO.createActivity(mDB.newConnection(),activity);
            if(activityOut != null){
                CFBTLogger.logInfo(logger, "updateTestConfigDCActivities", "Successfully updated the activities for testId :"+ testId );
            }
        }catch(Exception e){
            CFBTLogger.logError(CFBTLogger.CalEventEnum.UPDATE_DATACENTERS, "Error while adding new Activity", e);
        }
    }
}
