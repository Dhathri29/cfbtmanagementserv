package com.paypal.sre.cfbt.management.rest.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import com.paypal.sre.cfbt.management.appproperty.ApplicationPropertiesInfo;
import com.paypal.sre.cfbt.management.appproperty.ApplicationProperty;
import com.paypal.sre.cfbt.management.appproperty.ApplicationPropertyJsonPatchProcessor;
import com.paypal.sre.cfbt.request.RequestHandler;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import com.ebayinc.platform.service.jsonpatch.JsonPatchProcessorImpl;
import com.ebayinc.platform.services.jsonpatch.JSonPatchProcessor;
import com.ebayinc.platform.services.jsonpatch.JsonPatch;
import com.ebayinc.platform.services.jsonpatch.JsonPatchException;
import com.paypal.platform.error.api.BusinessException;
import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.data.Feature;
import com.paypal.sre.cfbt.shared.Constants;
import com.paypal.sre.cfbt.data.execapi.ChangePositionRequest;
import com.paypal.sre.cfbt.data.execapi.ClusterDetails;
import com.paypal.sre.cfbt.data.execapi.ClusterUpdateRequest;
import com.paypal.sre.cfbt.data.execapi.CompleteRequest;
import com.paypal.sre.cfbt.data.execapi.ComponentList;
import com.paypal.sre.cfbt.data.execapi.Datacenters;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.Status;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.Type;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequestDetails;
import com.paypal.sre.cfbt.data.execapi.ExtendDeployment;
import com.paypal.sre.cfbt.data.execapi.FeatureList;
import com.paypal.sre.cfbt.data.execapi.ApplicationProperties;
import com.paypal.sre.cfbt.data.execapi.ComponentsStatus;
import com.paypal.sre.cfbt.data.execapi.ComponentsStatusRequest;
import com.paypal.sre.cfbt.data.execapi.NodeUpdateProperties;
import com.paypal.sre.cfbt.data.execapi.NotifyPackageActionRequest;
import com.paypal.sre.cfbt.data.execapi.OverrideDetails;
import com.paypal.sre.cfbt.data.execapi.ReleaseRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest.Action;
import com.paypal.sre.cfbt.data.execapi.RunRequest;
import com.paypal.sre.cfbt.data.execapi.SystemStatusRequest;
import com.paypal.sre.cfbt.data.execapi.SystemUnderTest;
import com.paypal.sre.cfbt.data.execapi.Test;
import com.paypal.sre.cfbt.data.notification.SlackNotification;
import com.paypal.sre.cfbt.data.test.TestPackage;
import com.paypal.sre.cfbt.dataaccess.FeatureDAO;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions.Message;
import com.paypal.sre.cfbt.executor.Executor;
import com.paypal.sre.cfbt.management.CFBTTestResourceClient;
import com.paypal.sre.cfbt.management.cluster.ClusterInfo;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.DatacenterConfigRepository;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.dal.FeatureRepository;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestRepository;
import com.paypal.sre.cfbt.management.dal.OverrideDetailsRepo;
import com.paypal.sre.cfbt.management.dal.ReleaseTestDAO;
import com.paypal.sre.cfbt.management.dal.ReleaseTestRepo;
import com.paypal.sre.cfbt.management.dal.SlackNotificationDAO;
import com.paypal.sre.cfbt.management.dal.TestDAO;
import com.paypal.sre.cfbt.management.dal.TestRepository;
import com.paypal.sre.cfbt.management.features.FeatureChange;
import com.paypal.sre.cfbt.management.features.FeatureManager;
import com.paypal.sre.cfbt.management.rest.api.CFBTMANAGEMENTAPI;
import com.paypal.sre.cfbt.management.rest.api.PackageAction;
import com.paypal.sre.cfbt.management.web.DatacenterProxyManager;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.request.CFBTExceptionList;
import com.paypal.sre.cfbt.request.Queue;
import com.paypal.sre.cfbt.request.User;
import com.paypal.sre.cfbt.request.SpecialMessage;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.CFBTLogger.CalEventEnum;
import com.paypal.sre.cfbt.shared.DateUtil;
import com.paypal.sre.cfbt.shared.Page;
import com.paypal.sre.cfbt.shared.PagedResource;
import javax.ws.rs.ForbiddenException;

@Component
@Scope("singleton")
public class CFBTManagementService implements CFBTMANAGEMENTAPI {

    private static final Logger logger = LoggerFactory.getLogger(CFBTManagementService.class);
    private static MongoConnectionFactory dbConnectionFactory;
    private final Configuration config;
    private final DatabaseConfig dbConfig;
    private final long startTime;
    private final CFBTTestResourceClient testresourceservClient;
    private final RBAC rbac = new RBAC();

    @PostConstruct
    public void init() {
        setDBConnectionFactory(dbConfig.getConnectionFactory());
        ConfigManager.setConfiguration(config, dbConfig, testresourceservClient);
        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Initialization completed in " + elapsed + " ms");
    }

    @Inject
    CFBTManagementService(Configuration config, DatabaseConfig dbConfig, 
            CFBTTestResourceClient testresourceservClient) {
        this.config = config;
        this.dbConfig = dbConfig;
        this.testresourceservClient = testresourceservClient;
        startTime = System.currentTimeMillis();
    }

    @Override
    public Response status() {
        return Response.ok().entity("Hello CFBT User! I am ready to serve you.").build();
    }
    

    public static void setDBConnectionFactory(MongoConnectionFactory dbConnectionFactory) {
        CFBTManagementService.dbConnectionFactory = dbConnectionFactory;
    }

    /**
     * This converts passed in test ids to Test objects with nothing set but the test id.
     * @param testIds The list of test id's to insert into the test.
     * @return {@link List<Test>}
     */
    private List<Test> stringIdsToTests(List<String> testIds) {
        List<Test> tests =  new ArrayList<>();
        for (String id : testIds) {
            Test test = new Test();
            test.setId(id); 
            test.clearAllButIds();
            tests.add(test);
        }

        return tests;
    }

    @Override
    public Response testExecutionBatchEmergencyStop(ExecutionRequestDetails executionRequestDetails) {
        List<ExecutionRequest> executionRequestList = new ArrayList<>();
        try {
            ExecutionRequestDAO requestDAO = ExecutionRequestDAO.getInstance(dbConnectionFactory);
            if (executionRequestDetails != null && !StringUtils.isBlank(executionRequestDetails.getExecutionRequestId())) {
                CFBTLogger.logInfo(logger, CalEventEnum.EMERGENCY_STOP, "CFBT API: Emergency Stop = " + executionRequestDetails.getExecutionRequestId());
                ExecutionRequest request = requestDAO.getById(executionRequestDetails.getExecutionRequestId());

                if (request == null) {
                    throw new IllegalArgumentException("Request ID not found");
                }

                //Remove the request from Queue
                Queue.triggerDequeueRequest(dbConfig, request);

                //Abort the request
                RequestThreadHandler threadHandler = new RequestThreadHandler(dbConfig, request.getQueueName());
                executionRequestList.add(threadHandler.triggerEvent(request, Transitions.Message.ABORT));

                DeploymentHandler.processDeployWaitingRequests(dbConfig, request.getQueueName());
            } else {
                List<ExecutionRequest> executionRequests = new ArrayList<>();
                executionRequests.addAll(requestDAO.getByStatus(Status.queuedStatuses()));
                RequestThreadHandler threadHandler = new RequestThreadHandler(dbConfig, Queue.RELEASE_VETTING_QUEUE_NAME);

                threadHandler.asyncHandler(executionRequests, Transitions.Message.ABORT);
                executionRequestList.addAll(executionRequests);
            }
        } catch (IllegalArgumentException ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID,
                    "Invalid Execution Request ID : " + executionRequestDetails.getExecutionRequestId(), ex);
        } catch (IllegalStateException ex) {
            CFBTLogger.logError(logger, "testExecutionBatchEmergencyStop", "Execution Request cannot be stopped", ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, "Execution Request cannot be stopped", ex);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception e) {
            CFBTLogger.logError(logger, "testExecutionBatchEmergencyStop", "Execution Request cannot be stopped", e);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, "Execution Request cannot be stopped",
                    e);
        }

        return Response.ok().entity(executionRequestList).build();
    }

    @Override
    public Response testExecutionBatchHalt(ExecutionRequestDetails executionRequestDetails) {
        List<ExecutionRequest> executionRequestList = new ArrayList<>();
        try {
            ExecutionRequestDAO requestDAO = ExecutionRequestDAO.getInstance(dbConnectionFactory);
            if (executionRequestDetails != null && !StringUtils.isBlank(executionRequestDetails.getExecutionRequestId())) {
                CFBTLogger.logInfo(logger, CalEventEnum.ABORT_EXECUTION, "CFBT API: Execution Halt = " + executionRequestDetails.getExecutionRequestId());
                ExecutionRequest request = requestDAO.getById(executionRequestDetails.getExecutionRequestId());

                //Remove the request from Queue
                Queue.triggerDequeueRequest(dbConfig, request);

                //Halt the request.
                RequestThreadHandler threadHandler = new RequestThreadHandler(dbConfig, request.getQueueName());
                executionRequestList.add(threadHandler.triggerEvent(request, Transitions.Message.HALT));

                DeploymentHandler.processDeployWaitingRequests(dbConfig, request.getQueueName());
            } else {
                List<ExecutionRequest> executionRequests = new ArrayList<>();
                executionRequests.addAll(requestDAO.getByStatus(Status.queuedStatuses()));
                RequestThreadHandler threadHandler = new RequestThreadHandler(dbConfig, Queue.RELEASE_VETTING_QUEUE_NAME);

                threadHandler.asyncHandler(executionRequests, Transitions.Message.HALT);
                executionRequestList.addAll(executionRequests);
            }
        } catch (IllegalArgumentException ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID,
                    "Invalid Execution Request ID : " + executionRequestDetails.getExecutionRequestId(), ex);
        } catch (IllegalStateException ex) {
            CFBTLogger.logError(logger, "testExecutionBatchHalt", "Exception trying to halt", ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, "Exception trying to halt", ex);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception e) {
            CFBTLogger.logError(logger, "testExecutionBatchHalt", "Exception trying to halt", e);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, "Exception trying to halt", e);
        }
        return Response.ok().entity(executionRequestList).build();
    }

    @Override
    public Response notifyAndDownloadOrDeleteTestPackage(String userInfo,
            NotifyPackageActionRequest notifyPackageActionRequest) {
        String errorMessage = "An exception occured while notifying the test package action. Root cause: ";
        try {

            if (notifyPackageActionRequest == null) {
                CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR,
                        "NotifyPackageActionRequest should not be null or empty.", null);
            }

            TestPackage testPackage = notifyPackageActionRequest.getTestPackage();
            String action = notifyPackageActionRequest.getAction();
            String nodeIPAddress = notifyPackageActionRequest.getNodeIPAddress();

            PackageAction packageAction = PackageAction.getAction(action);

            if (testPackage == null || action == null) {
                CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR,
                        "TestPackage and action should not be null or empty.", null);
            }

            if (testPackage.getId() == null || testPackage.getFileName() == null || testPackage.getSha1() == null) {
                CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR,
                        "TestPackage 'id', 'fileName' & 'sha1' should not be null or empty.", null);
            }
            
            Executor executor = new Executor();

            // Notify to execserv nodes to perform the test package action
            executor.notifyPackageAction(userInfo, nodeIPAddress, packageAction, testPackage);

        } catch (IllegalArgumentException ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, errorMessage + ex, ex);
        } catch (BusinessException ex) {
            CFBTLogger.logError(logger, CFBTManagementService.class.getCanonicalName(), errorMessage, ex);
            throw ex;
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, errorMessage + ex, ex);
        }
        return Response.status(204).build();
    }
    
    @Override
    public Response testRun(String userInfo, RunRequest runRequest) {
        CFBTLogger.logInfo(logger, "testRun",
                "CFBT API: Executing a RunRequest = " + runRequest + "user = " + userInfo);

        User user = new User(userInfo);
        List<Test> tests = null;
        SystemUnderTest systemUnderTest = runRequest.getSystemUnderTest();
        String trReleaseId = runRequest.getTrReleaseId();

        if (!rbac.hasRole(userInfo, RBAC.ADMINISTRATOR)) {
            // If not ADMIN, set the default priority as Low
            runRequest.setPriority(2);
        }

        runRequest.validate();
        if (runRequest.thisHasTests()) {
            tests = stringIdsToTests(runRequest.getTestIds());
        } else if (runRequest.thisIsRerun()) {
            try {
                ExecutionRequest prevRequest = ExecutionRequestDAO.getInstance(dbConnectionFactory).getById(runRequest.getExecutionRequestId());
                tests = prevRequest.getTests();
                trReleaseId = prevRequest.getTrReleaseId();
                systemUnderTest = new SystemUnderTest();
                systemUnderTest.setDataCenter(prevRequest.getDatacenter());
                runRequest.setSystemUnderTest(systemUnderTest);
                runRequest.setSyntheticLocation(prevRequest.getSyntheticLocation());
            } catch (Exception ex) {
                CFBTExceptionUtil.throwBusinessException(CommonError.UNPROCESSABLE_ENTITY, "Error loading the previous request",
                        ex);
            }
        }
        validateExecution(user, runRequest.getDcg(), systemUnderTest, null, runRequest);
        if (runRequest.getRunNow()) {
            try{
                ExecutionRequestRepository executionRequestRepository = new ExecutionRequestRepository();
                List<ExecutionRequest> inProgressExecutionRequests = executionRequestRepository.getRunNowInProgressRequests(dbConnectionFactory);
                if (inProgressExecutionRequests != null &&  inProgressExecutionRequests.size() > 0) {

                    if(checkAnyTestAlreadyInProgress(inProgressExecutionRequests, runRequest)) {
                        CFBTLogger.logInfo(logger, "testRun", "There is already RunNow execution in progress - count: " + inProgressExecutionRequests.size() + " user = " + userInfo);
                        CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, "There is already one running execution", null);
                    }

                }
            } catch(BusinessException ex) {
                throw ex;
            } catch(Exception ex) {
                CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, "Not able to schedule execution", null);
            }
        }
        ExecutionRequest request = null;
        Response response = null;
        try {
            response = this.testRunExec(tests, user, trReleaseId, runRequest.getRequestType(), runRequest.getPriority(),
                null, runRequest.getSystemUnderTest(), runRequest.getRunNow(), runRequest.getSyntheticLocation());
            if(response != null) {
                request = (ExecutionRequest) response.getEntity();
            }
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                        "Not able to get execution " + "request", ex);
        }

        return Response.ok().entity(request).build();
    }

    public boolean checkAnyTestAlreadyInProgress(List<ExecutionRequest> inProgressExecutionRequests, RunRequest runRequest){

        //For Non-Synthetic Executions, The current behaviour is retained.
        if(StringUtils.isBlank(runRequest.getSyntheticLocation()) ){
            return true;
        }

        //For Synthetic executions, Check for any Test execution is already in progress.
        List<String> runNowRequestTestsIDs = runRequest.getTestIds();
        if(CollectionUtils.isNotEmpty(runNowRequestTestsIDs)) {
            for (ExecutionRequest executionRequest : inProgressExecutionRequests) {
                if(CollectionUtils.isNotEmpty(executionRequest.getTests())){
                    Set<String> inProgressTestIds = new HashSet<String>();
                    for(Test test :  executionRequest.getTests()){
                        inProgressTestIds.add(test.getId());
                    }
                    if(runNowRequestTestsIDs.stream().anyMatch(inProgressTestIds::contains)){
                        CFBTLogger.logInfo(logger, "testRun", "validateInProgressRunNowRequests: Test already running in RunNow Execution. ");
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Creates an Execution Request consisting of the tests that cover provided components.
     * 
     * @param userInfo
     *            the user info from the header
     * @param releaseRequest
     *            the {@link ReleaseRequest}
     * @return the {@link ReleaseTest}
     */
    @Override
    public Response createReleaseTest(String userInfo, ReleaseRequest releaseRequest) {
        CFBTLogger.logInfo(logger, "createReleaseTest",
                "CFBT API: Creating a releaseTest = " + releaseRequest + "user = " + userInfo);

        List<Test> testsToRun = new ArrayList<>();
        User user = new User(userInfo);
        try {
            releaseRequest.validate();
        } catch(Exception e) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, e.getMessage(), null);
        }
        validateExecution(user, releaseRequest.getDataCenter(), releaseRequest.getSystemUnderTest(), releaseRequest, null);

        String releaseDeployReason = "";
        if (Boolean.TRUE.equals(releaseRequest.getDeployOnly())) {
            releaseDeployReason = releaseRequest.getDeployReason().toString();
        }
        
        String callbackIdentifier = null;
        if (releaseRequest.getCallbackIdentifier() != null) {
            callbackIdentifier = releaseRequest.getCallbackIdentifier().toString();
        }

        OverrideDetails override = new OverrideDetailsRepo().getOverrideStatus(dbConnectionFactory, releaseRequest);

        // if not an override or deploy only, it's a normal request
        //  search tests to run based on components passed in
        if (Boolean.FALSE.equals(releaseRequest.getDeployOnly()) || !OverrideDetails.Status.OVERRIDDEN.equals(override.getStatus())) {
            try {
                ComponentList componentList = new ComponentList();
                componentList.setComponents(releaseRequest.getComponents());
                TestRepository testRepository = new TestRepository();
                testsToRun = testRepository.searchTest(dbConnectionFactory, componentList, releaseRequest.getSystemUnderTest().getDataCenter());
                testsToRun = testRepository.filterEnabledTestForUser(testsToRun,user,releaseRequest.getUser());
            } catch (IllegalArgumentException ex) {
                CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID, "Invalid Parameters", ex);
            } catch (Exception ex) {
                CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                        "Unable to load tests " + ex.getMessage(), ex);
            }
        }

        ReleaseTest newReleaseTest = null;
        Response response = null;
        try (MongoConnection c = dbConnectionFactory.newConnection()) {
            ReleaseTest releaseTest = ReleaseTest.builder()
                    .deployReadyCallbackURL(releaseRequest.getDeployReadyCallbackURL())
                    .components(releaseRequest.getComponents())
                    .releaseVehicle(releaseRequest.getReleaseVehicle().toString())
                    .testingCompleteCallbackURL(releaseRequest.getTestingCompleteCallbackURL())
                    .deploymentEstimatedDuration(releaseRequest.deploymentEstimatedDurationSeconds())
                    .initialDeploymentEstimatedDuration(releaseRequest.deploymentEstimatedDurationSeconds())
                    .releaseVehicle(releaseRequest.getReleaseVehicle().toString()).override(override)
                    .isRollback(releaseRequest.getIsRollback())
                    .deployReadyCallbackPayload(releaseRequest.getDeployReadyCallbackPayload())
                    .testingCompleteCallbackPayload(releaseRequest.getTestingCompleteCallbackPayload())
                    .deployReadyCallbackStatus(ReleaseTest.CallStatus.NOT_COMPLETE)
                    .testingCompleteCallbackStatus(ReleaseTest.CallStatus.NOT_COMPLETE)
                    .changeId(releaseRequest.getChangeId()).originalChangeId(override.getChangeId())
                    .statusUpdateCallbackURL(releaseRequest.getStatusUpdateCallbackURL())
                    .statusUpdateCallbackStatus(ReleaseTest.CallStatus.NOT_COMPLETE)
                    .callbackIdentifier(callbackIdentifier)
                    .deployOnly(releaseRequest.getDeployOnly())
                    .deployReason(releaseDeployReason)
                    .isRetry(releaseRequest.getIsRetry())
                    .serviceId(releaseRequest.getServiceId()).build();

            response = testRunExec(testsToRun, user, releaseRequest.getReleaseId(),
                     Type.RELEASE, releaseRequest.getPriority(), releaseTest, releaseRequest.getSystemUnderTest(), false);
            if(response != null) {
                ExecutionRequest request = (ExecutionRequest) response.getEntity();
                newReleaseTest = ReleaseTest.createFromExecutionRequest(request);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), "Error creating Release Test", ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, ex.getMessage(), ex);
        }
        if (response != null && response.getStatus() == 201) {
            return Response.status(Response.Status.CREATED).entity(newReleaseTest).build();
        } else {
            return Response.ok().entity(newReleaseTest).build();
        }
    }
    
    /**
     * Validates the execution request.  Currently this checks the data center and user values.
     * It will throwBusinessException if any issues found.
     * 
     * @param user
     *            the user info for this request
     * @param dcg
     *           the passed in dcg value
     * @param systemUnderTest
     *           the passed in {@link SystemUnderTest}
     */
    private void validateExecution(User user, String dcg, SystemUnderTest systemUnderTest,ReleaseRequest releaseRequest, RunRequest runRequest) {
        // datacenter field is deprecated and should no longer be passed in.
        // Instead, system under test may be specified if the request needs to be executed against specific datacenter.
        StringBuilder validationMessage = new StringBuilder("");
        if(dcg != null) {
             validationMessage.append("Datacenter ").append(dcg).append(" is no longer support. Use system under test instead. ");
        }
        DatacenterConfigRepository datacenterConfigRepository = new DatacenterConfigRepository();
        if(systemUnderTest == null || (systemUnderTest.hasNoValidField())) { //If systemUnderTest is not specified then use default release vetting datacenter.
            systemUnderTest = new SystemUnderTest();
            try {
                systemUnderTest.setDataCenter(datacenterConfigRepository.getReleaseVettingDatacenterName(dbConnectionFactory));
            } catch (Exception ex) {
                CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, "An exception occurred while fetching default release vetting datacenter. Exception - ", ex);
            }
            if (runRequest != null) {
                runRequest.setSystemUnderTest(systemUnderTest);
            } else if (releaseRequest != null) {
                releaseRequest.setSystemUnderTest(systemUnderTest);
            }
        } else {
            if(StringUtils.isBlank(systemUnderTest.getDomain()) && 
                    StringUtils.isBlank(systemUnderTest.getDataCenter())) {
                validationMessage.append("Specify either domain or dataCenter under systemUnderTest. ");
            } else if(!StringUtils.isBlank(systemUnderTest.getDomain()) && 
                    !StringUtils.isBlank(systemUnderTest.getDataCenter())) {
                validationMessage.append("Specify either domain or dataCenter under systemUnderTest not together. ");
            } else if(!StringUtils.isBlank(systemUnderTest.getDomain()) && 
                    systemUnderTest.getDomain().contains(".")) {
                validationMessage.append("Specify domain under systemUnderTest with correct name for example cfbtexecserv94113. ");
            }
        }
        //Check if the datacenter is valid.
        try {
            datacenterConfigRepository.validateDatacenter(dbConnectionFactory, systemUnderTest.getDataCenter());
        } catch (IllegalArgumentException ex) {
            validationMessage.append("An exception occurred while checking if the datacenter is valid. " + ex);
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, "An exception occurred while checking if the datacenter is valid. Exception - ", ex);
        }

        if (StringUtils.isNotBlank(validationMessage.toString())) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, validationMessage.toString(), null);
        }

        Feature campOn = null;
        try {
            campOn = FeatureDAO.getInstance().readWithName(dbConnectionFactory.newConnection(), "CampOn");
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, "Unable to load features to check for camp on",
                    ex);
        }
        if (checkFeatureEnabled("CampOn") &&
                !(user.getScopes() != null && user.getScopes().contains("PP_CFBT_Administrator"))) {
            // note that with this check being earlier, we won't have recorded the request.  revisit to record this if still needed
            validationMessage.append("System is in Camp On mode.  Only administrators can execute tests. ");
        }
        if (StringUtils.isNotBlank(validationMessage.toString())) {
            CFBTExceptionUtil.throwBusinessException(CommonError.SERVICE_UNAVAILABLE, validationMessage.toString(), null);
        }
    }

    private Response testRunExec(List<Test> tests, User user, String releaseId, Type requestType,
                                 int priority, ReleaseTest release, SystemUnderTest systemUnderTest, boolean runNow) {
        return testRunExec(tests, user, releaseId, requestType, priority,  release,  systemUnderTest,  runNow, null);
    }

    private Response testRunExec(List<Test> tests, User user, String releaseId, Type requestType,
                                 int priority, ReleaseTest release, SystemUnderTest systemUnderTest, boolean runNow, String syntheticLocation) {
        ExecutionRequest request = null;
        try {
            // setup the request object
            request = ExecutionRequest.builder()
                                    .defaults()
                                    .tests(tests)
                                    .requestTime(DateUtil.currentDateTimeISOFormat())
                                    .numberTests(tests.size())
                                    .requestUser(user.getUserId())
                                    .trReleaseId(releaseId)
                                    .type(requestType)
                                    .datacenter(systemUnderTest.getDataCenter())
                                    .releaseTest(release)
                                    .systemUnderTest(systemUnderTest)
                                    .priority(priority)
                                    .runNow(runNow)
                                    .syntheticLocation(syntheticLocation)
                                    .build();

            if (runNow) {
                //Perform parallel execution
                request.setParameterGroup(Constants.GROUP_SET2);
            } else {
                request.setParameterGroup(Constants.GROUP_SET1);
            }
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.UNPROCESSABLE_ENTITY, "Error creating the request",
                    ex);
        }
        boolean isDuplicate = false;
        try {
            RequestHandler requestHandler = new RequestHandler();
            CFBTExceptionList exceptionList = new CFBTExceptionList(dbConfig.getConnectionFactory());
            exceptionList.filter(request);

            // ensure the tests are all clear but Ids before request gets processed and stored into database
            request.getTests().forEach((test) -> {
                test.clearAllButIds();
            });
            request = requestHandler.processRequest(request);
            isDuplicate = requestHandler.isDuplicateRequest();

        } catch (IllegalArgumentException ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, ex.getMessage(), ex);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), "Error creating Release Test", ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, ex.getMessage(), ex);
        }
        if(isDuplicate) {
            return Response.ok().entity(request).build();
        } else {
            return Response.status(Response.Status.CREATED).entity(request).build();
        }
    }

    @Override
    public Response complete(String userInfo, String id, CompleteRequest completeRequest) {
        if (!ObjectId.isValid(id)) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID, "Invalid Release Test ID: " + id,
                    null);
        }

        if (completeRequest == null) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Cannot complete without a proper request", null);
        }

        if (Action.RELEASE.equals(completeRequest.getAction())) {
            if (completeRequest.getOverrideDetails() == null) {
                CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Cannot override without override details", null);
            }
 
            if (StringUtils.isBlank(completeRequest.getOverrideDetails().getOverrideApprover())) {
                CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "An override must have an approver", null);
            }

            if (StringUtils.isBlank(completeRequest.getOverrideDetails().getOverrideReason())) {
                CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "An override must have a reason", null);
            }
        }

        ReleaseTest updatedReleaseTest = null;
        try {
 
            ExecutionRequest request = ExecutionRequestDAO.getInstance(dbConnectionFactory).getById(id);

            if (completeRequest.getOverrideDetails() != null) {
                completeRequest.getOverrideDetails().setRequester(new User(userInfo).getUserId());
                request.getReleaseTest().setOverride(completeRequest.getOverrideDetails());
                request.getReleaseTest().getOverride().setStatus(OverrideDetails.Status.OVERRIDDEN);
                request.getReleaseTest().getOverride().setPreapproved(true);

                if (StringUtils.isBlank(completeRequest.getOverrideDetails().getReleaseId())) {
                    request.getReleaseTest().getOverride().setReleaseId(request.getTrReleaseId());
                }

                if (StringUtils.isBlank(completeRequest.getOverrideDetails().getReleaseTestId())) {
                    request.getReleaseTest().getOverride().setReleaseTestId(request.getId());
                }
            }

            request.getReleaseTest().setCompletionAction(completeRequest.getAction());

            ExecutionRequest updatedRequest = null;
            RequestThreadHandler handler = new RequestThreadHandler(dbConfig, request.getQueueName());

            // Treat a cancel like an abort.
            if (Action.isCanceledAction(completeRequest.getAction())) {
                //Remove the request from Queue
                Queue.triggerDequeueRequest(dbConfig, request);

                //Cancel the Request
                updatedRequest = handler.completeRequest(request, Message.ABORT);
            } else {
                updatedRequest = handler.completeRequest(request, Message.COMPLETE);
            }
            updatedReleaseTest = ReleaseTest.createFromExecutionRequest(updatedRequest);
            DeploymentHandler.processDeployWaitingRequests(dbConfig, request.getQueueName());
            rbac.secureTestData(userInfo, updatedReleaseTest.getExecutionRequest().getTests());
        } catch (IllegalArgumentException ex) {
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), ex.getLocalizedMessage(), ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Invalid Request.", ex);
        } catch (IllegalStateException ex) {
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), ex.getLocalizedMessage(), ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, "Unable to complete the release test.", ex);
        } catch (Exception ex) {
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), ex.getLocalizedMessage(), ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, ex.getLocalizedMessage(), ex);
        }

        return Response.ok().entity(updatedReleaseTest).build();
    }

    @Override
    public Response deployComplete(String userInfo, String id) {
        // Load the release test
        ReleaseTest releaseTest;
        ReleaseTest updatedReleaseTest = null;
        ReleaseTestRepo releaseTestRepo = new ReleaseTestRepo(dbConfig.getConnectionFactory());
        try {
            releaseTest = releaseTestRepo.getById(id);
            final String msg = "Error trying to mark Release Test as deploy complete.";
            if (releaseTest == null || releaseTest.getExecutionRequest() == null) {
                CFBTLogger.logError(logger, CFBTManagementService.class.toString(), msg);
                CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, msg, null);
            }

            ExecutionRequest.Status status = releaseTest.getExecutionRequest().getStatus();
            if (!ExecutionRequest.Status.DEPLOYING.equals(status)
                    && !ExecutionRequest.Status.DEPLOY_WAITING.equals(status)) {
                CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, "This release test on '"
                        + status.toString()
                        + "' status cannot be marked as deploy complete. Only release test on 'DEPLOYING' status can be marked as deploy complete.",
                        null);
            }

            DeploymentHandler deploymentHandler = new DeploymentHandler(dbConfig, releaseTest.getExecutionRequest().getQueueName());
            updatedReleaseTest = deploymentHandler.deployComplete(releaseTest);
        } 
        catch (BusinessException ex) {
            throw ex;
        }
        catch (Exception exception) {
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), exception.getLocalizedMessage(),
                    exception);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    exception.getLocalizedMessage(), exception);
        }
        return Response.ok().entity(updatedReleaseTest).build();
    }

    @Override
    public Response extendDeploy(String userInfo, String id, ExtendDeployment extendDeployment) {
        ReleaseTestRepo releaseTestRepo = new ReleaseTestRepo(dbConfig.getConnectionFactory());
        ReleaseTest releaseTest = null;
        ReleaseTest updatedReleaseTest = null;
        final String msg = "Error trying to extend deployment SLA time.";
        if (extendDeployment != null && extendDeployment.getExtendedTimeInSeconds() < 1) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Invalid duration.",
                    null); 
        }
        try {
            releaseTest = releaseTestRepo.getById(id);
            if (releaseTest == null || releaseTest.getExecutionRequest() == null) {
                CFBTLogger.logError(logger, CFBTManagementService.class.toString(), msg);
                CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, msg, null);
            }

            DeploymentHandler deploymentHandler = new DeploymentHandler(dbConfig, releaseTest.getExecutionRequest().getQueueName());
            deploymentHandler.extendDeploy(releaseTest, extendDeployment);
            // Reload the release test after deploy request is handled.
            updatedReleaseTest = releaseTestRepo.getById(id);
            ExecutionRequest request = updatedReleaseTest.getExecutionRequest();
            updatedReleaseTest = request.getReleaseTest();
            request.setReleaseTest(null);
            updatedReleaseTest.setExecutionRequest(request);
        } catch (Exception ex) {
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), msg, ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, msg, ex);
        }
        return Response.ok().entity(updatedReleaseTest).build();
    }

    @Override
    public Response changePosition(String userInfo, String id, ChangePositionRequest changePosition) {
        if (!ObjectId.isValid(id)) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID, "Invalid Release Test ID: " + id, null);
        }

        ReleaseTest releaseTest = null;

        try {
            ExecutionRequest request = ExecutionRequestDAO.getInstance(dbConfig.getConnectionFactory()).getById(id);
            Queue queue = Queue.releaseVettingQueue(dbConfig);

            request = queue.changePosition(request, changePosition.getPositionOffset());

            releaseTest = ReleaseTest.createFromExecutionRequest(request);
        } catch (IllegalArgumentException ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Problem with input", ex);
        } catch (Exception ex) {
             CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, "Problem trying to change the position", ex);
        }

        return Response.ok().entity(releaseTest).build();
    }

    @Override
    public Response testingComplete(String executionRequestId) {
        CFBTLogger.logInfo(logger, "testingComplete",
                "CFBT API: Tests Have Finished for Request = " + executionRequestId);

        ExecutionRequestDAO executionRequestDAO = ExecutionRequestDAO.getInstance(dbConfig.getConnectionFactory());
        ExecutionRequest transitionedRequest = null;
        if (StringUtils.isEmpty(executionRequestId)) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Invalid execution request id.",
                    null);
        }
        try {
            ExecutionRequest request = executionRequestDAO.getById(executionRequestId);
            RequestThreadHandler threadHandler = new RequestThreadHandler(dbConfig, request.getQueueName());

            transitionedRequest = threadHandler.triggerEvent(request, Message.COMPLETE_TESTS);
        } catch (Exception ex) {
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), ex.getLocalizedMessage(), ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, ex.getLocalizedMessage(), ex);
        }
        return Response.ok().entity(transitionedRequest).build();
    }
    
    /**
     * This method is responsible for finding out a {@link Feature} enabled or not
     * @param featureName - Name of the feature
     * @return true if feature is enabled all other conditions it is false
     */
    private boolean checkFeatureEnabled(String featureName) {
        Feature feature = null;
        try {
            feature = FeatureDAO.getInstance().readWithName(dbConnectionFactory.newConnection(), featureName);
        } catch (Exception ex) {
            //eat the exception and send the enabled as false
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), ex.getLocalizedMessage(), ex);
        }
        if(feature != null && feature.getEnabledFlag()) {
            return true;
        }
        return false;
    }

    /**
     * Update various parts of the cluster, mostly this will be the enableTestRun
     * @param updateRequest
     *            The details of the cluster to update.
     * @return The updated cluster.
     */
    @Override
    public Response updateClusterDetails(ClusterUpdateRequest updateRequest) {
        CFBTLogger.logInfo(logger, CalEventEnum.CLUSTER_DISCOVERY,
                "CFBT API: Update Cluster Details = " + updateRequest);
        
        ClusterInfo clusterInfo = null;
        ClusterDetails clusterDetails = null;

        if (updateRequest == null || updateRequest.getNodeProperties() == null) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Invalid request", null);
        }
        
        ApplicationPropertiesInfo applicationPropertiesInfoInfo = new ApplicationPropertiesInfo(config, dbConnectionFactory);
        int defaultNumThreads = applicationPropertiesInfoInfo.getNumberOfThreads();
        
        try {
            for (NodeUpdateProperties nodeUpdates : updateRequest.getNodeProperties()) {
                int numOfThreads;
                //This is to support backward compatibility.
                if (nodeUpdates.getNumberConfiguredThreads() != null) {
                    CFBTLogger.logInfo(logger, CFBTManagementService.class.getCanonicalName(), "Update of numberConfiguredThreads is deprecated.");
                    if (nodeUpdates.getNumberConfiguredThreads() == 0) {
                        nodeUpdates.setEnableTestRun(Boolean.FALSE);
                    } else {
                        nodeUpdates.setEnableTestRun(Boolean.TRUE);
                    }
                }
                if (Boolean.TRUE.equals(nodeUpdates.getEnableTestRun())) {
                    numOfThreads = defaultNumThreads;
                } else {
                    numOfThreads = 0;
                }
                
                Executor executor = new Executor();
                if (nodeUpdates.getEnableTestRun() == null) {
                    CFBTLogger.logError(logger, CFBTManagementService.class.getCanonicalName(), "Invalid request. enableTestRun flag must be passed for updateClusterDetails api operation.");
                    CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR,
                            "Invalid request. 'enableTestRun' flag must be passed for updateClusterDetails api operation.",
                            null);
                }
                executor.updateConfiguredThreads(nodeUpdates.getIpAddress(), numOfThreads);
                clusterInfo = new ClusterInfo(dbConnectionFactory, config, applicationPropertiesInfoInfo);
                clusterInfo.updateEnableTestRunAndConfiguredThreads(dbConnectionFactory, nodeUpdates.getIpAddress(), numOfThreads, nodeUpdates.getEnableTestRun());
            }
            clusterInfo = new ClusterInfo(dbConnectionFactory, config, applicationPropertiesInfoInfo);
            clusterDetails = clusterInfo.getClusterDetails();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, "Error updating the cluster",
                    ex);
        }
        return Response.ok().entity(clusterDetails).build();
    }

    /**
     * This method returns the Execution Request ID based on the id passed in.
     *
     * @param executionRequestId
     *            The Request ID were're querying.
     * @return Returns associated execution request id.
     */
    @Override
    public ExecutionRequest getExecutionRequest(String executionRequestId) {
        if (!ObjectId.isValid(executionRequestId)) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID,
                    "Invalid Execution Request ID: " + executionRequestId, null);
        }
        ExecutionRequest request = null;

        if (executionRequestId == null) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID,
                    "Error Request ID is null unable to load", null);
        }

        try {
            ExecutionRequestDAO requestDAO = ExecutionRequestDAO.getInstance(dbConnectionFactory);
            TestDAO testDAO = TestDAO.getInstance(dbConnectionFactory);
            request = requestDAO.getById(executionRequestId);
            testDAO.loadTests(request);

            if (request.thisIsRelease()) {
                SpecialMessage special = new SpecialMessage(dbConfig, Queue.RELEASE_VETTING_QUEUE_NAME);
                special.addSpecialMessage(request);
            }
        } catch (IllegalArgumentException ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID,
                    "Error trying to load ExecutionRequest Id with id = " + executionRequestId, ex);
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Error trying to load ExecutionRequest Id with id = " + executionRequestId, ex);
        }

        if (request == null) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID,
                    "Test or Execution ID does not exist for id = " + executionRequestId, null);
        }

        return request;
    }

    /**
     * This method returns a list of pending, in-progress, and recently completed execution requests.
     * @param userInfo the user info from the header
     * @param request the system status request data
     * @return response containing list of execution requests
     */
    @Override
    public Response systemStatusExecutionRequests(String userInfo, SystemStatusRequest request) {

        List<ExecutionRequest> executionRequestList = new ArrayList<>();
        DateTime dateTimeFrom;

        if (request != null && StringUtils.isNotBlank(request.getDateFrom())) {
            String dateFrom = request.getDateFrom();
            String dateTimeStartPattern = DateUtil.dateFormatPattern(dateFrom);
            dateTimeFrom = DateUtil.checkDateFormat(dateFrom, dateTimeStartPattern);
        } else {
            // If dateFrom time is not provided, then default to the last 4 hours.
            dateTimeFrom = DateTime.parse(DateUtil.currentDateTimeISOFormat()).toDateTimeISO().minusHours(4);
        }

        ApplicationPropertiesInfo applicationPropertiesInfo = new ApplicationPropertiesInfo(config, dbConnectionFactory);
        int pendingLimit = applicationPropertiesInfo.getSystemStatusPendingLimit();
        int completedLimit = applicationPropertiesInfo.getSystemStatusCompletedLimit();

        try {
            ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(dbConnectionFactory);
            executionRequestList.addAll(dao.getSystemStatus(dateTimeFrom, pendingLimit, completedLimit, request.getSynthetic(), config.getBoolean("shouldShowSyntheticsOnCFBT", false)));
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, "Error trying to read execution requests", ex);
        }

        for (ExecutionRequest executionRequest : executionRequestList) {
            executionRequest.getTests().forEach((test) -> {
                test.clearAllButIds();
            });
        }

        return Response.ok().entity(executionRequestList).build();

    }

    @Override
    public Response getReleaseTests(String userInfo, int pageNum, int size, String queuestatus,
            boolean includePendingTestCompleteCallback) {
        if (pageNum == 0) {
            pageNum = 1;
        }
        if (size == 0) {
            size = 5;
        }
        if (queuestatus != null) {
            try {
                ExecutionRequest.Status.valueOf(queuestatus);
            } catch (IllegalArgumentException ex) {
                CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR,
                        "Wrong status value , " + "Expected PENDING/RUNNING Actual : " + queuestatus, ex);
            }
        }

        List<ReleaseTest> releaseTestList = new ArrayList<>();
        List<String> statusList = new ArrayList<>();
        if ("PENDING".equals(queuestatus)) {
            statusList.add(ExecutionRequest.Status.PENDING.toString());
        } else if ("RUNNING".equals(queuestatus)) {
            statusList.addAll(Status.runningStatuses());
        } else {
            statusList.add(ExecutionRequest.Status.PENDING.toString());
            statusList.addAll(Status.runningStatuses());
        }
        try {
            List<ExecutionRequest> executionRequestList = ExecutionRequestDAO.getInstance(dbConnectionFactory)
                    .getByStatus(statusList);
            for (ExecutionRequest er : executionRequestList) {
                if (Type.RELEASE.equals(er.getType())) {
                    ReleaseTest releaseTest = er.getReleaseTest();
                    er.setReleaseTest(null);
                    releaseTest.setExecutionRequest(er);
                    releaseTestList.add(releaseTest);
                }
            }
            if (includePendingTestCompleteCallback) {
                List<ReleaseTest> callbackNotCompeleteList = ReleaseTestDAO.getInstance(dbConnectionFactory)
                        .readByTestingCompleteCallbackStatus(dbConfig.getConnectionFactory().newConnection(),
                                ReleaseTest.CallStatus.NOT_COMPLETE);
                if (callbackNotCompeleteList != null && callbackNotCompeleteList.size() > 0) {
                    for (ReleaseTest eachReleaseTest : callbackNotCompeleteList) {
                        // Compares the releaseTestList and callbackNotCompeleteList using the execution Request ID
                        // to confirm the unique releaseTest object is added to the list.
                        List<String> releaseIdList = releaseTestList.stream()
                                .filter(releaseTest -> releaseTest.getExecutionRequest().getId()
                                        .equals(eachReleaseTest.getExecutionRequest().getId()))
                                .map(releaseTest -> releaseTest.getExecutionRequest().getId())
                                .collect(Collectors.toList());
                        if (releaseIdList == null || releaseIdList.isEmpty()) {
                            releaseTestList.add(eachReleaseTest);
                        }
                    }
                }
            }

            // Sort by position
            releaseTestList.sort((r1, r2) -> r1.getExecutionRequest().getPosition() - r2.getExecutionRequest().getPosition());
            SpecialMessage special = new SpecialMessage(dbConfig, Queue.RELEASE_VETTING_QUEUE_NAME);
            special.addSpecialMessage(releaseTestList);

        } catch (Exception e) {
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), e.getMessage());
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "An error occured while trying to retrieve the release Tests list. ", e);
        }

        Page<ReleaseTest> page = new Page<>(releaseTestList, pageNum, size, releaseTestList.size());
        PagedResource<ReleaseTest> pagedResource = new PagedResource<>(page, "page", "size");
        return Response.ok().entity(pagedResource).build();
    }

    @Override
    public Response getReleaseTest(String userInfo, String id) {
        if (!ObjectId.isValid(id)) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID, "Invalid Release Test ID: " + id,
                    null);
        }

        ReleaseTest releaseTest = null;
        final String msg = "Unable to load Release Test. ";
        try {
            ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(dbConnectionFactory);
            ExecutionRequest request = dao.getById(id);
            SpecialMessage special = new SpecialMessage(dbConfig, request.getQueueName());
            special.addSpecialMessage(request);
            releaseTest = ReleaseTest.createFromExecutionRequest(request);
        } catch (IllegalArgumentException ex) {
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), msg, ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID,
                    "Invalid release test id." + ex.getMessage(), ex);
        } catch (Exception ex) {
            CFBTLogger.logError(logger, CFBTManagementService.class.toString(), msg);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, msg + ex.getMessage(), ex);
        }

        return Response.ok().entity(releaseTest).build();
    }

    /**
     * Returns all of the data centers present in DatacenterConfig collection.
     * 
     * @return Response
     */
    @Override
    public Response getDatacenters() {
        DatacenterProxyManager dcProxy = new DatacenterProxyManager();
        Datacenters datacenters = dcProxy.getDatacenters(dbConnectionFactory);
        return Response.ok().entity(datacenters).build();
    }
    
    /**
     * This method is responsible for updating the datacenters into the Mongo .
     * 
     * @param datacenters
     *            - data need to be updated
     * @return updated documents of Datacenter collection
     */
    @Override
    public Response updateDatacenters(Datacenters datacenters) {
        Datacenters datacentersUpdated = null;
        DatacenterConfigRepository datacenterConfigRepo = new DatacenterConfigRepository();
        datacenterConfigRepo.validateDataCenter(datacenters);
        try {
            datacentersUpdated = datacenterConfigRepo.updateDataCenters(dbConnectionFactory, datacenters);
            DatacenterProxyManager dataCenterProxyManager= new DatacenterProxyManager();
            dataCenterProxyManager.requestUpdateProxy(); //publish datacenter proxy control message through kafka
            TestRepository testRepo = new TestRepository();
            testRepo.updateTestConfigurations(dbConnectionFactory, datacenters.getDatacenters());
        } catch (Exception ex) {
            CFBTLogger.logError(CalEventEnum.UPDATE_DATACENTERS, "Error updating DataCenter collection", ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Error while updating DataCenter ", ex);
        }
        return Response.ok().entity(datacentersUpdated).build();
    }
    
    @Override
    public Response getClusterDetails() {
        ClusterDetails clusterDetails = null;
        try {
            ClusterInfo clusterInfo = new ClusterInfo(dbConnectionFactory, config);
            clusterDetails = clusterInfo.getClusterDetails();
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Error while getting the cluster details. " + ex.getMessage(), ex);
        }
        return Response.ok().entity(clusterDetails).build();
    }
    

    /**
     * This method will be responsible for getting List of Feature available in CFBT System
     * 
     * @param status
     *            - Status can be "ACTIVE", "DEPRECATED". If nothing of them specified then it will fetch all the status
     *            information
     * @param componentType
     *            - Type of components which can "CFBTEXECSERV","CFBTDATAMANSERV","CFBTAPISERV", "CFBTCONFIGWATCHSERV",
     *            "CFBTAPISERV". If nothing is specified then it will grab all components.
     * @return List of Feature from Feature collection.
     */
    @Override
    public Response getFeatures(List<String> status, String componentType) {
        FeatureList featuresList = null;
        FeatureRepository featuresRepository = new FeatureRepository();
        try {
            featuresList = featuresRepository.getFeatures(dbConnectionFactory, status, componentType);
        } catch (Exception ex) {
            CFBTLogger.logError(CalEventEnum.GET_FEATURES, "Not able to get the features", ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, "Not able to get the features",
                    ex);
        }
        return Response.ok().entity(featuresList).build();
    }


    /**
     * This method is responsible for adding a new feature in the features collection
     * 
     * @param feature
     *            - specific feature need to be inserted in the feature collection
     * @return List of features after insertion
     */
    @Override
    public Response insertFeature(String userInfo, Feature feature) {
        Feature featureInserted = null;
        FeatureRepository featuresRepository = new FeatureRepository();
        try {
            JSonPatchProcessor jsonPatchProcessor = new JsonPatchProcessorImpl();
            featureInserted = featuresRepository.insertFeature(dbConnectionFactory, userInfo, feature, jsonPatchProcessor);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            CFBTLogger.logError(CalEventEnum.INSERT_FEATURES, "Not able to add new feature into the Feature collection",
                    ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Not able to add new feature into " + "the Feature collection", ex);
        }
        return Response.ok().entity(featureInserted).build();
    }

    /**
     * This method is responsible for updating the feature collection with respect to a specific feature name.
     * 
     * @param userId
     *            - The user who initiated the call
     * @param name
     *            - The name of the feature
     * @param patchList
     *            - List of jsonpatch for updating
     * @return Feature document
     */
    @Override
    public Response updateFeature(String userId, String name, List<JsonPatch> patchList) {
        Feature feature = null;
        try {
            FeatureChange featureChange = new FeatureChange(name, patchList);
            feature = FeatureManager.instance().changeFeature(dbConnectionFactory, featureChange, userId);
        } catch (BusinessException ex) {
            CFBTLogger.logError(CalEventEnum.UPDATE_FEATURES,
                    "Not able to update feature " + name + " into the features " + "collection", ex);
            throw ex;
        } catch (JsonPatchException ex) {
            CFBTLogger.logError(CalEventEnum.UPDATE_FEATURES, "Not able to update feature the features collection", ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR,
                    "Not able to update feature " + name + " into the features collection", ex);
        } catch (Exception ex) {
            CFBTLogger.logError(CalEventEnum.UPDATE_FEATURES, "Not able to update feature the features collection", ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Not able to update feature " + name + " into the features collection", ex);
        }
        if (feature == null) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Not able to update feature " + name + " into the features collection", null);
        }
        return Response.ok().entity(feature).build();
    }

    /**
     * This method is responsible for fetching a specific feature with respect to the name.
     * 
     * @param featureName
     *            - Name of the feature
     * @return fetched Feature object.
     */
    @Override
    public Response getFeatureWithName(String featureName) {
        Feature feature = null;
        FeatureRepository featureRepository = new FeatureRepository();
        try {
            feature = featureRepository.getFeatureByName(dbConnectionFactory, featureName);
        } catch (Exception ex) {
            CFBTLogger.logError(CalEventEnum.GET_FEATURES, "Not able to get the features with name " + featureName, ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Not able to get the features " + "with name " + featureName, ex);
        }
        if (feature == null) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID,
                    "Feature not found with name " + featureName, null);
        }
        return Response.ok().entity(feature).build();
    }
    
    /**
     * Returns all of the slackNotification config in SlackNotification collection.
     * 
     * @return Response
     */
    @Override
    public Response getSlackNotification() {
        SlackNotificationDAO slackNotificationDAO = SlackNotificationDAO.getInstance(dbConnectionFactory);
        SlackNotification slackNotification = null;
        try {
            slackNotification = slackNotificationDAO.getSlackNotification();
        } catch (Exception ex) {
            CFBTLogger.logError(CalEventEnum.SYSTEMCONFIG, "Exception while retriving slack notification config ", ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Exception while retriving " + "slack notification config ", ex);
        }
        return Response.ok().entity(slackNotification).build();
    }

    /**
     * Updates the slackNotification config as modified from cfbtnodeweb-admin Returns all of the slackNotification
     * config in SlackNotification collection.
     * 
     * @return Response
     */
    @Override
    public Response updateSlackNotification(SlackNotification slackNotification) {
        SlackNotificationDAO slackNotificationDAO = SlackNotificationDAO.getInstance(dbConnectionFactory);
        try {
            slackNotification = slackNotificationDAO.updateSxlackNotification(slackNotification);
        } catch (Exception ex) {
            CFBTLogger.logError(CalEventEnum.SYSTEMCONFIG, "Exception while retriving slack notification config ", ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Exception while retriving " + "slack notification config ", ex);
        }
        return Response.ok().entity(slackNotification).build();
    }

    /**
     * Get the Application Properties.
     *
     * @return ApplicationProperties object
     */
    @Override
    public Response getApplicationProperties(String userInfo) {
        CFBTLogger.logInfo(logger, "getApplicationProperties", "CFBT API: getApplicationProperties called by User - " + userInfo);
        ApplicationPropertiesInfo applicationPropertiesInfoInfo = new ApplicationPropertiesInfo(config, dbConnectionFactory);
        ApplicationProperties applicationProperties = null;
        try {
            applicationProperties = applicationPropertiesInfoInfo.getApplicationProperty(userInfo);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Error while fetching Application Properties. " + ex.getMessage(), ex);
        }
        return Response.ok(applicationProperties).build();
    }

    /**
     * Updating the application property configuration followed by updating the cluster if needed.
     *
     * @return Updated ApplicationProperties object
     */
    @Override
    public Response updateApplicationProperty(String userInfo, List<JsonPatch> patchList) {
        CFBTLogger.logInfo(logger, "updateApplicationProperty",
                "CFBT API: updateApplicationProperty called by User - " + userInfo);
        ApplicationPropertiesInfo applicationPropertiesInfo = new ApplicationPropertiesInfo(config, dbConnectionFactory);
        ApplicationProperty applicationProperty = null;
        ApplicationProperties applicationProperties = null;
        ClusterUpdateRequest clusterUpdateRequest = null;
        String errorMessage = "An exception occurred while updating the Application Property. Root cause: ";
        try {
            applicationProperty = applicationPropertiesInfo.updateApplicationProperty(userInfo, patchList);
            if (applicationProperty != null) {
                applicationProperties = applicationPropertiesInfo.getApplicationProperties(applicationProperty);
            }
            ApplicationPropertyJsonPatchProcessor patchProcessor = applicationPropertiesInfo.getJsonPatchProcessor();
            if (patchProcessor != null) {
                clusterUpdateRequest = applicationPropertiesInfo.clusterUpdateRequest(patchProcessor, applicationProperty.nodeConfiguration());
                if (clusterUpdateRequest != null) { //only update Cluster if there is change in number of default threads
                    updateClusterDetails(clusterUpdateRequest); // notify all nodes with updated node configuration
                }
            }
        } catch (IllegalArgumentException ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, errorMessage + ex, ex);
        } catch (ForbiddenException ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, errorMessage + ex, ex);
        } catch (BusinessException ex) {
            CFBTLogger.logError(logger, CFBTManagementService.class.getCanonicalName(), errorMessage, ex);
            throw ex;
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Error while updating Application Property. " + ex.getMessage(), ex);
        }
        return Response.ok(applicationProperties).build();
    }

    /**
     * Validates the Components Status Request.
     * It will throw BusinessException if any issue found.
     *
     * @param componentsStatusRequest The {@link ComponentsStatusRequest} input
     */
    private void validateComponentsStatusRequest(ComponentsStatusRequest componentsStatusRequest) {
        if (componentsStatusRequest == null || componentsStatusRequest.getComponents() == null || componentsStatusRequest.getComponents().isEmpty()) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "The componentsStatusRequest must include non-empty components list.", null);
        }
        for (String eachComponent : componentsStatusRequest.getComponents()) {
            if (eachComponent == null || eachComponent.isEmpty()) {
                CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "The component in the list cannot be null or empty.", null);
            }
        }
        SystemUnderTest systemUnderTest = componentsStatusRequest.getSystemUnderTest();
        if (systemUnderTest != null && (StringUtils.isBlank(systemUnderTest.getDataCenter()) || StringUtils.isNotBlank(systemUnderTest.getDomain()))) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "The 'systemUnderTest' must include only valid 'dataCenter' field.", null);
        }
    }

    /**
     * Check if the components are covered by the release vetting cfbt tests against specified/default systemUnderTest.
     * @param userInfo
     * @param componentsStatusRequest
     * @return The {@link ComponentsStatus} object
     */
    @Override
    public Response hasTests(String userInfo, ComponentsStatusRequest componentsStatusRequest) {
        CFBTLogger.logInfo(logger, "hasTests", "CFBT API: determine if the set of components have release vetting tests");
        validateComponentsStatusRequest(componentsStatusRequest);

        List<String> components = componentsStatusRequest.getComponents();
        SystemUnderTest systemUnderTest = componentsStatusRequest.getSystemUnderTest();
        String errorMessage = "An exception occurred while checking if components are covered by release vetting cfbt tests for the specified/ default systemUnderTest. Root cause: ";
        ComponentsStatus componentStatus = new ComponentsStatus();
        String datacenter = null;
        try {
            DatacenterConfigRepository datacenterConfigRepository = new DatacenterConfigRepository();
            String datacenterInfo = null;
            if (systemUnderTest == null) {
                datacenter = datacenterConfigRepository.getReleaseVettingDatacenterName(dbConnectionFactory);
                datacenterInfo = "the default datacenter - ";
            } else if (StringUtils.isNotBlank(systemUnderTest.getDataCenter())) {
                datacenter = systemUnderTest.getDataCenter();
                datacenterInfo = "the provided datacenter - ";
            }
            CFBTLogger.logInfo(logger, "hasTests", "Checking if the set of components have release vetting tests for " + datacenterInfo + datacenter);
            //Validate if the datacenter is valid.
            datacenterConfigRepository.validateDatacenter(dbConnectionFactory, datacenter);

            boolean hasTests = false;
            CFBTExceptionList exceptionList = new CFBTExceptionList(dbConfig.getConnectionFactory());
            exceptionList.removeExempted(components);
            if (!components.isEmpty()) {
                try {
                    List<com.paypal.sre.cfbt.data.test.Component> coveredComponents = TestDAO.getInstance(dbConnectionFactory).getCoveredComponents(true, datacenter);
                    for (String providedComponent : components) {
                        for (com.paypal.sre.cfbt.data.test.Component coveredComponent : coveredComponents) {
                            if (coveredComponent.getName().equalsIgnoreCase(providedComponent)) {
                                hasTests = true;
                                break;
                            }
                        }
                    }
                } catch (Exception ex) {
                    CFBTLogger.logError(logger, "hasTests", "Error trying to determine if components have tests " + ex.getMessage());
                }
            }

            if (!hasTests) {
                hasTests = exceptionList.isAllTests(components);
            }
            componentStatus.setHasTests(hasTests);
        } catch (IllegalArgumentException ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, errorMessage + ex, ex);
        } catch (BusinessException ex) {
            CFBTLogger.logError(logger, "hasTests", errorMessage, ex);
            throw ex;
        } catch (Exception ex) {
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, errorMessage + ex, ex);
        }
        return Response.ok().entity(componentStatus).build();
    }
    @Override
    public ComponentList getComponentList(String userInfo, boolean covered, String dataCenter){
        CFBTLogger.logInfo(logger,"getComponentList",
                "CFBTManagement API:Get components with covered="+covered+"user="+userInfo);
        ComponentList componentList = new ComponentList();

        try{
            List<com.paypal.sre.cfbt.data.test.Component>components=TestDAO.getInstance(dbConnectionFactory).getCoveredComponents(true,dataCenter);
            componentList.setComponents(components);
        }catch(Exception ex){
            CFBTLogger.logInfo(logger,"getComponentList",
                    "Error trying to load test components with covered="+covered+"Error="+ex.getMessage());
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Error trying to load test components with covered="+covered,ex);
        }
        return componentList;
    }


}
