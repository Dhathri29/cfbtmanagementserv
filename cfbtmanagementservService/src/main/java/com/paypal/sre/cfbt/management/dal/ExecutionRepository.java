/*
 * (C) 2016 PayPal, Internal software, do not distribute.
 */

package com.paypal.sre.cfbt.management.dal;

import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.paypal.sre.cfbt.shared.NetworkUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.result.UpdateResult;
import com.paypal.sre.cfbt.data.execapi.Execution;
import com.paypal.sre.cfbt.data.execapi.SystemUnderTest;
import com.paypal.sre.cfbt.data.execapi.Test;
import com.paypal.sre.cfbt.data.test.Attachment;
import com.paypal.sre.cfbt.data.test.Parameter;
import com.paypal.sre.cfbt.data.test.SpecialRequest;
import com.paypal.sre.cfbt.data.test.Step;
import com.paypal.sre.cfbt.management.CFBTTestResourceClient;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.mongo.MongoDataMarshaller;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.util.ArrayList;

/**
 * This class contains the higher level database operations, primarily, but not restricted to, {@link Execution} data.
 */
public class ExecutionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionRepository.class);
    /**
     * This method initializes test execution. 
     * @param mDB instance of mongo connection factory.
     * @param test instance of test.
     * @param executionReqID id for the test execution.
     * @param dcg data center in which the test will be executed.
     * @param numRetries number of times to retry.
     * @param systemUnderTest value indicates system under test.
     * @return instance of execution.
     * @throws UnknownHostException instance of UnknownHostException.
     * @throws Exception instance of Exception.
     */
    public Execution initializeExecution(MongoConnectionFactory mDB, Test test, String executionReqID, String dcg, int numRetries, SystemUnderTest systemUnderTest, String syntheticLocation) throws Exception {
        Execution execution = new Execution();
        Map<String, String> otherInfo = new HashMap<>();
        for (SpecialRequest requestType : test.getSpecialRequests()) {
            if (SpecialRequest.NEW_ACCOUNT.equals(requestType)) {
                CFBTTestResourceClient accountManager = ConfigManager.getTestResourceServClient();
                otherInfo.put("emailAddress", accountManager.getNewAccount(test).getEmailAddress());
            }
            if(SpecialRequest.KEYSTORE.equals(requestType)) {
                Test testDetails = TestDAO.getInstance(mDB).readOne(mDB.newConnection(), test.getId(), false, null);
                for(Parameter testParameter: testDetails.getParameters()) {
                    if("keystore".equals(testParameter.getSpecialRequestFieldType())) {
                        String paramName = testParameter.getName();

                        for (Parameter occParam : test.getParameters()) {
                            if (paramName.equals(occParam.getName())) {
                                otherInfo.put(paramName+"-keystore", occParam.getValue().toString());
                            }
                        }
                    }
                }
            }
        }
        
        if(!testConfigured(test)) {
            execution.setStatus(Execution.Status.SKIP);
            List<Step> steps = new ArrayList<>();
            steps.add(createSkippedStep("Skipped while initializing the test due to accounts not configured", 1));
            execution.setSteps(steps);
        } else {
            execution.setStatus(Execution.Status.PENDING);
        }
        execution.setTestId(test.getId());
        execution.setTest(test);
        
        if (StringUtils.isNotBlank(test.getMethodName())) {
            execution.setTestName(test.getMethodName());
        }
        else {
            execution.setTestName(test.getName());           
        }
        execution.setExecutionRequestId(executionReqID);
        List<String> executionRequestIds = new ArrayList<>();
        executionRequestIds.add(executionReqID);
        execution.setExecutionRequestIds(executionRequestIds);
        execution.setCorrectStatus(true);
        execution.setTestRetries(numRetries);
        execution.setRetryAttempt(0);
        execution.setDatacenter(dcg);
        execution.setBrowser(test.getSelectedBrowser());
        execution.setDurationTime(0);
        execution.setNodeIpaddress("0");
        execution.setAbortNodeIPAddress("0");
        execution.setNumWarnings(0);
        execution.setSystemUnderTest(systemUnderTest);
        execution.setReleaseVetting(test.getUseForReleaseVetting() && test.isReleaseVettingForDatacenter(dcg));
        execution.setOtherInfo(otherInfo);
        execution.setSyntheticLocation(syntheticLocation);
        if(StringUtils.isNotBlank(test.getSelectedPlatform()) )
            execution.setSelectedPlatform(test.getSelectedPlatform());
        /*
         In the future, we may configure number of mobile devices for the same test, then different execution rows will be added.
         For now, it is just one device and the first device entry of List will be taken by default
         */
        if(CollectionUtils.isNotEmpty(test.getDevices()) && test.getDevices().get(0) != null &&
                StringUtils.isNotBlank(test.getDevices().get(0).getDevice()) ) {
            execution.setDeviceName(test.getDevices().get(0).getDevice());

            if(StringUtils.isNotBlank(test.getDevices().get(0).getOs()))
                execution.setOperatingSystem(test.getDevices().get(0).getOs());
        }
        return execution;
    }
    
    /**
     * This method creates the execution in the event of a system error.
     * 
     * @param mDB
     *            instance of {@link MongoConnectionFactory}
     * @param test
     *            instance of {@link Test}
     * @param executionReqID
     *            execution request id.
     * @param dcg
     *            data center in which test would be run.
     * @param numRetries
     *            number of time to retry.
     * @param systemUnderTest
     *            system under test.
     * @param errorSteps
     *            a list of {@link Step}
     * @return instance of {@link Execution}
     */
    public Execution initializeExecutionAsError(MongoConnectionFactory mDB, Test test, String executionReqID, String dcg, int numRetries,
            SystemUnderTest systemUnderTest, List<Step> errorSteps) {
        Execution execution = new Execution();
        execution.setStatus(Execution.Status.ERROR);
        execution.setSteps(errorSteps);
        execution.setTestId(test.getId());
        execution.setTest(test);

        if (StringUtils.isNotBlank(test.getMethodName())) {
            execution.setTestName(test.getMethodName());
        } else {
            execution.setTestName(test.getName());
        }
        execution.setExecutionRequestId(executionReqID);
        List<String> executionRequestIds = new ArrayList<>();
        executionRequestIds.add(executionReqID);
        execution.setExecutionRequestIds(executionRequestIds);
        execution.setCorrectStatus(true);
        execution.setTestRetries(numRetries);
        execution.setRetryAttempt(0);
        execution.setDatacenter(dcg);
        execution.setBrowser(test.getSelectedBrowser());
        execution.setDurationTime(0);
        execution.setExecutionTime(DateUtil.currentDateTimeISOFormat());
        execution.setNodeIpaddress("0");
        execution.setAbortNodeIPAddress("0");
        execution.setNumWarnings(0);
        execution.setSystemUnderTest(systemUnderTest);
        if(StringUtils.isNotBlank(test.getSelectedPlatform()) )
            execution.setSelectedPlatform(test.getSelectedPlatform());
        /*
         In the future, we may configure number of mobile devices for the same test, then different execution rows will be added.
         For now, it is just one device and the first device entry of List will be taken by default
         */
        if(CollectionUtils.isNotEmpty(test.getDevices()) && test.getDevices().get(0) != null &&
                StringUtils.isNotBlank(test.getDevices().get(0).getDevice()) ) {
            execution.setDeviceName(test.getDevices().get(0).getDevice());

            if(StringUtils.isNotBlank(test.getDevices().get(0).getOs()))
                execution.setOperatingSystem(test.getDevices().get(0).getOs());
        }
        return execution;
    }

    /**
     * This method is responsible for finding out whether any of parameter of a test is not configured
     * @param test
     * @return true iff all the parameters are configured
     */
    public boolean testConfigured(Test test) {
        // if there are no parameters for this test, then it is clearly not configured
        if (test.getParameters().isEmpty()) {
            return false;
        }
        for(Parameter param : test.getParameters()) {
            if(param.getValue() == null || param.getValue().length == 0) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * @param skippedReason
     * @param number
     * @return
     */
    static public Step createSkippedStep(String skippedReason, int number) {
        Step step = new Step();
        step.setName("Skipped Step");
        step.setResult(Step.Result.SKIPPED);
        step.setReason(skippedReason);
        step.setStepNum(number);
        return step;
    }
    /**
     * This method updates the test {@link Execution} data.
     * @param mDB       instance of {@link MongoConnectionFactory}
     * @param execution instance of {@link Execution}
     * @param priorStatus instance of {@link Execution.Status} for query purposes
     * @return indicates whether there was an update to the record.
     * @throws UnknownHostException
     * @throws Exception
     */
    public boolean update(MongoConnectionFactory mDB, Execution execution, Execution.Status priorStatus)
            throws UnknownHostException, Exception {
        boolean updateComplete = false;
        TestExecutionDAO testExecutionDAO = TestExecutionDAO
                .getInstance(ConfigManager.getDatabaseConfig().getConnectionFactory());
        try (MongoConnection c = mDB.newConnection()) {
            List<Document> statusQuery = new ArrayList<>();
            Document idQuery = new Document("_id", new ObjectId(execution.getId()));

            statusQuery.add(idQuery);

            if (null == execution.getStatus()) {
                execution.setStatus(Execution.Status.ERROR);
            }

            statusQuery.add(new Document("status", priorStatus.name()));

            Document andQuery = new Document("$and", statusQuery);

            Document statusUpdate = new Document("status", execution.getStatus().name());

            if (execution.getDurationTime() != null) {
                statusUpdate.append("durationTime", execution.getDurationTime());
            }
            if (execution.getExecutionTime() != null) {
                statusUpdate.append("executionTime", execution.getExecutionTime());
            }
            if (execution.getNodeIpaddress() != null) {
                statusUpdate.append("node_ipaddress", execution.getNodeIpaddress());
            }
            if (execution.getAbortNodeIPAddress() != null) {
                statusUpdate.append("abortNodeIPAddress", execution.getAbortNodeIPAddress());
            }
            if (execution.getRetryAttempt() != null) {
                statusUpdate.append("retryAttempt", execution.getRetryAttempt());
            }

            Document update = new Document("$set", statusUpdate);

            // If the execution is aborted, there will be no steps not already saved.
            if (!Execution.Status.ABORT.equals(execution.getStatus())) {

                for (Step eachStep : execution.getSteps()) {
                    List<Attachment> attachments = addAttachments(mDB, eachStep, execution.getId());
                    if (attachments != null && attachments.size() == eachStep.getAttachments().size()) {
                        eachStep.setAttachments(attachments);
                    }
                }
                update.append("$push",
                        new Document("steps", new Document("$each", MongoDataMarshaller.encode(execution.getSteps()))));
            }

            UpdateResult mongoUpdate = testExecutionDAO.update(c, andQuery, update, false);

            if (mongoUpdate.getMatchedCount() > 0) {
                updateComplete = true;
                CFBTLogger.logInfo(LOGGER, CFBTLogger.CalEventEnum.MONGO_EXECUTION_COLLECTION,
                        "Test result updated for test execution id = " + execution.getId());
            } else {
                // Did not transitionToInProgress the execution, transition status to whatever
                // was saved.
                Execution savedResult = testExecutionDAO.readOne(c, execution.getId());
                execution.setStatus(savedResult.getStatus());
                CFBTLogger.logInfo(LOGGER, CFBTLogger.CalEventEnum.MONGO_EXECUTION_COLLECTION,
                        "Test result failed to update for test execution id = " + execution.getId());
            }
        }

        return updateComplete;
    }

    /**
     * This method records a list of {@link Attachment} associated with a execution.
     * @param mDB         instance of {@link MongoConnectionFactory}
     * @param step        instance of {@link Step}
     * @param executionId id of test {@link Execution}
     * @return list of {@link Attachment}
     * @throws UnknownHostException
     */
    private List<Attachment> addAttachments(MongoConnectionFactory mDB, Step step, String executionId)
            throws UnknownHostException {
        List<Attachment> attachments = new ArrayList<Attachment>();
        for (Attachment eachAttachment : step.getAttachments()) {
            String id = addAttachment(mDB, eachAttachment, executionId);
            if (id != null) {
                String type = eachAttachment.getType();
                String name = eachAttachment.getName();
                eachAttachment = new Attachment();
                eachAttachment.setId(id);
                eachAttachment.setName(name);
                eachAttachment.setType(type);
                attachments.add(eachAttachment);
            }
        }
        return attachments;
    }

    /**
     * This method records an {@link Attachment}
     * @param mDB             instance of {@link MongoConnectionFactory}
     * @param attachmentToAdd instance of {@link Attachment}
     * @param executionId     id of test {@link Execution}
     * @return id of {@link Attachment}
     * @throws UnknownHostException
     */
    private String addAttachment(MongoConnectionFactory mDB, Attachment attachmentToAdd, String executionId)
            throws UnknownHostException {
        String id = null;
        AttachmentDAO attachmentDAO = AttachmentDAO.getInstance();
        MongoConnection c = mDB.newConnection();
        attachmentToAdd.setExecutionId(executionId);
        String size;
        try {
            size = String.valueOf(attachmentToAdd.getContent().getBytes("UTF-8").length);
            attachmentToAdd.setSize(size);
        } catch (UnsupportedEncodingException ex) {
            CFBTLogger.logError(LOGGER, ExecutionRepository.class.toString(),
                    "The Character Encoding - UTF-8 is not supported", ex);
            throw new IllegalStateException("The Character Encoding - UTF-8 is not supported", ex);
        }
        id = attachmentDAO.insert(c, attachmentToAdd);

        return id;
    }

    /**
     * Move pending executions to skipped - relevant during a halt or stop.
     * @param executionRequestID The execution request id.
     * @return The list of updated request ids associated with skipped executions.
     * @throws Exception
     */
    public List<String> skipPendingExecutions(String executionRequestID, TestExecutionDAO executionDAO) throws Exception {

        List<String> updatedequestIds = new ArrayList<>();
        MongoConnectionFactory factory = ConfigManager.getDatabaseConfig().getConnectionFactory();
        List<Execution> executions = executionDAO.getExecutionsByRequestID(executionRequestID);
        ExecutionRequestRepository requestRepo = new ExecutionRequestRepository();

        for (Execution execution : executions) {
            if (execution.getStatus().toString().equalsIgnoreCase(Execution.Status.PENDING.toString())){
                execution.setStatus(Execution.Status.SKIP);
                /*
                 * Only update the ExecutionRequest if the execution Status has changed, otherwise
                 * the counters in the ExecutionRequest collection will increment incorrectly.
                 */
                if (this.update(factory, execution, Execution.Status.PENDING)) {
                    requestRepo.updateFromExecution(factory, execution, false);
                    updatedequestIds.addAll(execution.getExecutionRequestIds());
                }
            }
        }
        return updatedequestIds;
    }

    /**
     * Transitions all running executions in this execution request to abort.
     *
     * @param executionRequestID The execution request id.
     * @return The list of updated request ids associated with aborted executions.
     * @throws Exception
     *             Mongo connection errors.
     */
    public List<String> abortRunningExecutions(String executionRequestID, TestExecutionDAO executionDAO) throws Exception {
        List<String> updatedRequestIds = new ArrayList<>();
        List<Execution> executions = executionDAO.getExecutionsByRequestID(executionRequestID);
        MongoConnectionFactory factory = ConfigManager.getDatabaseConfig().getConnectionFactory();
        ExecutionRequestRepository requestRepo = new ExecutionRequestRepository();

        for (Execution execution : executions) {
            if (execution.getStatus().toString().equalsIgnoreCase(Execution.Status.IN_PROGRESS.toString())) {
                execution.setStatus(Execution.Status.ABORT);
                String ipAddress = NetworkUtil.getLocalInetAddress(ConfigManager.getConfiguration()).getHostAddress();
                execution.setAbortNodeIPAddress(ipAddress);
                /*
                 * Only update the ExecutionRequest if the execution Status has changed, otherwise
                 * the counters in the ExecutionRequest collection will increment incorrectly.
                 */
                if (this.update(factory, execution, Execution.Status.IN_PROGRESS)) {
                    requestRepo.updateFromExecution(factory, execution, true);
                    updatedRequestIds.addAll(execution.getExecutionRequestIds());
                }
            }
        }
        return updatedRequestIds;
    }
}
