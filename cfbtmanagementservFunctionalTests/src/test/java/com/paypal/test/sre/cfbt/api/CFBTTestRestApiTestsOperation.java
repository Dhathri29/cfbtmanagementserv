package com.paypal.test.sre.cfbt.api;

import java.util.HashMap;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.testng.annotations.Listeners;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.paypal.selion.platform.asserts.SeLionAsserts;
import com.paypal.test.jaws.http.rest.PayPalRestClient;
import com.paypal.test.jaws.logging.JawsLogger;
import com.paypal.test.logging.AppLogger;
import com.paypal.test.sre.utilities.RestApiUtils;
import com.paypal.test.sre.utilities.TestNGListeners;
import com.paypal.test.sre.utilities.ValidateHTTPStatus;

/**
 * This class is to handle cfbtmanagementserv Rest API Tests
 **/
@Listeners({ TestNGListeners.class })
public class CFBTTestRestApiTestsOperation {

    HashMap<String, Object> pathValue = new HashMap<String, Object>();
    CFBTManagementServRequest cfbtManagementServRequest;
    
    public CFBTTestRestApiTestsOperation() {
        super();
        cfbtManagementServRequest = new CFBTManagementServRequest();
    }

    /**
     * This method is used to remove the mappings from the 'pathValue' HashMap if it's not empty.
     */

    @BeforeTest
    public void clearbeforehashMap() {
        pathValue.clear();
    }

    /**
     * The method to check if application status is returning httpSuccess
     * 
     */
    @Test(testName = "TestsGetApiValidOperation", groups = { "cfbtmanagementserv", "small","large" })
    public void verifyTestsGetApiValidOperation() throws Exception {
        PayPalRestClient<String> client = RestApiUtils.restClientBasePlainText("status");
        String response = RestApiUtils.doGetAndReturnPlainTextResponse(client);
        AppLogger.getLogger().info(("Get Response : - " + response));
        ValidateHTTPStatus.httpSuccess(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());
    }

    /**
     * The method to check if base RestURL is returning httpNotFound for an invalid apiOperation
     */
    @Test(testName = "TestsGetApiHttpNotFound", groups = {"cfbtmanagementserv", "small", "large"})
    public void verifyTestsGetApihttpNotFoundOperation() throws Exception {
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("DummyTestOperation");
        JsonNode response = RestApiUtils.doGet(client);
        AppLogger.getLogger().info(("Get Response : - " + response));
        ValidateHTTPStatus.httpNotFound(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());
    }

    /**
     * Method to Create Release Test from cfbtmanagementserv
     *
     */
    @Test(testName = "createReleaseTest", groups = { "cfbtmanagementserv", "small", "large"})
    public void createReleaseTest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode input = objectMapper.readValue(cfbtManagementServRequest.createReleaseTestRequest("cfbtmanagementserv_FT_createReleaseTest"), JsonNode.class);
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("release-tests");
        JsonNode getResponse = RestApiUtils.doPost(client, input);
        String releaseVehicle = getResponse.findValue("releaseVehicle").asText();
        SeLionAsserts.assertEquals(releaseVehicle, "Altus-ALM", "releaseVehicle is Altus-ALM");
        ValidateHTTPStatus.httpCreated(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());
    }

    /**
     * Method to get release Test from releaseTest Execution id on cfbtmanagementserv
     *
     */
    @Test(testName = "getReleaseTest", groups = { "cfbtmanagementserv", "small", "mini", "large"})
    public void getReleaseTest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode input = objectMapper.readValue(cfbtManagementServRequest.createReleaseTestRequest("cfbtmanagementserv_FT_getReleaseTest"), JsonNode.class);
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("release-tests");
        JsonNode getResponse = RestApiUtils.doPost(client, input);
        ValidateHTTPStatus.httpCreated(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());
        String releaseTestID = getResponse.findValue("id").asText();
        PayPalRestClient<JsonNode> clientreleaseTestID = RestApiUtils
                .restParameterClientBase("release-tests/" + releaseTestID);
        JsonNode releaseTestIDResponse = RestApiUtils.doGet(clientreleaseTestID);
        String releaseVehicle = releaseTestIDResponse.findValue("releaseVehicle").asText();
        SeLionAsserts.assertEquals(releaseVehicle, "Altus-ALM", "releaseVehicle is Altus-ALM");
        JawsLogger.getLogger().info(releaseVehicle.toString());
        ValidateHTTPStatus.httpSuccess(clientreleaseTestID.getStatus().getReasonPhrase(),
                clientreleaseTestID.getStatus().getStatusCode());
    }

    /**
     * Method to get all release Tests execution details from cfbtmanagementserv
     *
     */
    @Test(testName = "getReleaseTestList", groups = { "cfbtmanagementserv", "small", "large" })
    public void getAllReleaseTestList() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode input = objectMapper.readValue(cfbtManagementServRequest.createReleaseTestRequest("cfbtmanagementserv_FT_getReleaseTestList"), JsonNode.class);
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("release-tests");
        JsonNode getResponse = RestApiUtils.doPost(client, input);
        ValidateHTTPStatus.httpCreated(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());
        PayPalRestClient<JsonNode> clientreleaseTestID = RestApiUtils.restParameterClientBase("release-tests");
        RestApiUtils.doGet(clientreleaseTestID);
        ValidateHTTPStatus.httpSuccess(clientreleaseTestID.getStatus().getReasonPhrase(),
                clientreleaseTestID.getStatus().getStatusCode());
    }


    /**
     * Method to Emergency Stop All Executions
     *
     */
    @Test(testName = "emergencyStopAllExecutions", groups = { "cfbtmanagementserv", "small","large"})
    public void emergencyStopAllExecutions() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode inputRequest1 = objectMapper.readValue(cfbtManagementServRequest.createReleaseTestRequest("cfbtmanagementserv_FT_emergencyStopAllExecutions1"), JsonNode.class);
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("release-tests");
        JsonNode getResponse1 = RestApiUtils.doPost(client, inputRequest1);
        ValidateHTTPStatus.httpCreated(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());

        String executionRequestIdForEmergencyStop = getResponse1.findValue("id").asText();

        ObjectMapper objectMapper2 = new ObjectMapper();
        JsonNode inputRequest2 = objectMapper2.readValue(cfbtManagementServRequest.createReleaseTestRequest("cfbtmanagementserv_FT_emergencyStopAllExecutions2"), JsonNode.class);
        JsonNode getResponse2 = RestApiUtils.doPost(client, inputRequest2);
        ValidateHTTPStatus.httpCreated(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());

        Thread.sleep(15000);
        String emergencyStopPayLoad = "{\"executionRequestId\":\"\"}";
        JsonNode inputForStop = objectMapper.readValue(emergencyStopPayLoad, JsonNode.class);
        PayPalRestClient<JsonNode> stopClient = RestApiUtils
                .restParameterClientBase("execution-requests/emergency-stop");
        JsonNode getResponseforStop = RestApiUtils.doPost(stopClient, inputForStop);
        JawsLogger.getLogger().info(getResponseforStop.toString());
        ValidateHTTPStatus.httpSuccess(stopClient.getStatus().getReasonPhrase(),
                stopClient.getStatus().getStatusCode());

        Thread.sleep(15000);
        PayPalRestClient<JsonNode> clientreleaseTestID = RestApiUtils
                .restParameterClientBase("execution-requests/" + executionRequestIdForEmergencyStop);
        JsonNode releaseTestIDResponse = RestApiUtils.doGet(clientreleaseTestID);
        String releaseStatus = releaseTestIDResponse.findValue("status").asText();
        SeLionAsserts.assertEquals(releaseStatus, "COMPLETED", "releaseStatus is COMPLETED");
        JawsLogger.getLogger().info(releaseStatus.toString());
        ValidateHTTPStatus.httpSuccess(clientreleaseTestID.getStatus().getReasonPhrase(),
                clientreleaseTestID.getStatus().getStatusCode());
    }


    /**
     * Method to Emergency Stop Single Execution Request
     *
     */
    @Test(testName = "emergencyStopSingleExecution", groups = { "cfbtmanagementserv", "small", "large"})
    public void emergencyStopSingleExecution() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode inputRequest1 = objectMapper.readValue(cfbtManagementServRequest.createReleaseTestRequest("cfbtmanagementserv_FT_emergencyStopSingleExecution1"), JsonNode.class);
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("release-tests");
        JsonNode getResponse1 = RestApiUtils.doPost(client, inputRequest1);
        ValidateHTTPStatus.httpCreated(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());

        String executionRequestIdForEmergencyStop = getResponse1.findValue("id").asText();

        ObjectMapper objectMapper2 = new ObjectMapper();
        JsonNode inputRequest2 = objectMapper2.readValue(cfbtManagementServRequest.createReleaseTestRequest("cfbtmanagementserv_FT_emergencyStopSingleExecution2"), JsonNode.class);
        JsonNode getResponse2 = RestApiUtils.doPost(client, inputRequest2);
        ValidateHTTPStatus.httpCreated(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());
        String executionRequestIdNotToStop = getResponse2.findValue("id").asText();

        Thread.sleep(15000);
        String emergencyStopPayLoad = "{\"executionRequestId\":\"" + executionRequestIdForEmergencyStop +"\"}";
        JsonNode inputForStop = objectMapper.readValue(emergencyStopPayLoad, JsonNode.class);
        PayPalRestClient<JsonNode> stopClient = RestApiUtils
                .restParameterClientBase("execution-requests/emergency-stop");
        JsonNode getResponseforStop = RestApiUtils.doPost(stopClient, inputForStop);
        JawsLogger.getLogger().info(getResponseforStop.toString());
        ValidateHTTPStatus.httpSuccess(stopClient.getStatus().getReasonPhrase(),
                stopClient.getStatus().getStatusCode());

        Thread.sleep(15000);
        PayPalRestClient<JsonNode> clientreleaseTestID = RestApiUtils
                .restParameterClientBase("execution-requests/" + executionRequestIdForEmergencyStop);
        JsonNode releaseTestIDResponse = RestApiUtils.doGet(clientreleaseTestID);
        ValidateHTTPStatus.httpSuccess(clientreleaseTestID.getStatus().getReasonPhrase(),
                clientreleaseTestID.getStatus().getStatusCode());
        String releaseStatus = releaseTestIDResponse.findValue("status").asText();
        JawsLogger.getLogger().info("Status for execution-request " + executionRequestIdForEmergencyStop + " is: " + releaseStatus.toString());
        SeLionAsserts.assertEquals(releaseStatus, "COMPLETED", "releaseStatus is not COMPLETED");

        PayPalRestClient<JsonNode> clientreleaseTestID2 = RestApiUtils
                .restParameterClientBase("execution-requests/" + executionRequestIdNotToStop);
        JsonNode releaseTestIDResponse2 = RestApiUtils.doGet(clientreleaseTestID2);
        ValidateHTTPStatus.httpSuccess(clientreleaseTestID2.getStatus().getReasonPhrase(),
                clientreleaseTestID2.getStatus().getStatusCode());
        String releaseStatus2 = releaseTestIDResponse2.findValue("status").asText();
        JawsLogger.getLogger().info("Status for execution-request " + executionRequestIdNotToStop + " is: " + releaseStatus2.toString());
        SeLionAsserts.assertNotEquals(releaseStatus2, "COMPLETED", "releaseStatus is COMPLETED");
    }


    /**
     * Method to check that the deployment phase of the release is completed and that the testing phase may proceed
     *
     */
    @Test(testName = "createReleaseTestDeployComplete", groups = {"cfbtmanagementserv", "small", "large"})
    public void createReleaseTestDeployComplete() throws Exception {
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("release-tests");
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode input = objectMapper.readValue(cfbtManagementServRequest.createReleaseTestRequest("cfbtmanagementserv_FT_createReleaseTestDeployComplete"), JsonNode.class);
        JsonNode getResponse = RestApiUtils.doPost(client, input);
        String releaseTestID = getResponse.findValue("id").asText();
        String releaseStatus = getResponse.findValue("status").asText();
        if (((releaseTestID != null) && (releaseStatus == "DEPLOYING"))) {
            PayPalRestClient<JsonNode> createReleaseTestDeployCompleteClient = RestApiUtils
                    .restParameterClientBase("release-tests/" + releaseTestID + "/deploy-complete");
            JsonNode deployCompleteInput = objectMapper.readValue(cfbtManagementServRequest.deployComplete,
                    JsonNode.class);
            JsonNode getReleaseTestGetResponse = RestApiUtils.doPost(createReleaseTestDeployCompleteClient,
                    deployCompleteInput);
            JawsLogger.getLogger().info("getReleaseTestGetResponse  : " + getReleaseTestGetResponse.toString());
        } else {
            JawsLogger.getLogger().info(
                    "This release test on 'PENDING' status cannot be marked as deploy complete. Only release test on 'DEPLOYING' status can be marked as deploy complete");
        }
    }


    /**
     * Method to check that the deployment phase of the release is getting CANCELLED that the testing phase should not proceed
     */
    @Test(testName = "createReleaseTestCancel", groups = {"cfbtmanagementserv", "small", "large"})
    public void createReleaseTestCancel() throws Exception {
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("release-tests");
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode input = objectMapper.readValue(cfbtManagementServRequest.createReleaseTestRequest("cfbtmanagementserv_FT_createReleaseTestCancel"), JsonNode.class);
        JsonNode getResponse = RestApiUtils.doPost(client, input);
        String releaseTestID = getResponse.findValue("id").asText();
        PayPalRestClient<JsonNode> createReleaseTestDeployCompleteClient = RestApiUtils
                .restParameterClientBase("release-tests/" + releaseTestID + "/complete");
        JsonNode deployCompleteInput = objectMapper.readValue(cfbtManagementServRequest.releaseStatusCancel,
                JsonNode.class);
        JsonNode getReleaseTestGetResponse = RestApiUtils.doPost(createReleaseTestDeployCompleteClient,
                deployCompleteInput);
        JawsLogger.getLogger().info("getReleaseTestGetResponse  : " + getReleaseTestGetResponse.toString());
    }


    /**
     * This method verifies Patch operation to update the ApplicationProperties.
     *
     */
    @Test(testName = "patchApplicationProperties", groups = {"cfbtmanagementserv", "small", "large"})
    public void patchApplicationProperties() throws Exception {

        JsonNode response = RestApiUtils.doGet(RestApiUtils.restParameterClientBase("application-properties"));
        Integer nodeIsDeadInMinutes = response.findValue("nodeIsDeadInMinutes").intValue();

        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("application-property");
        pathValue.put("/nodeIsDeadInMinutes", 240);
        pathValue.put("/nodeClusterActiveInMinutes", 15);
        ArrayNode input = RestApiUtils.getRequestPatchId("replace", pathValue);
        JsonNode patchResponse = RestApiUtils.doPatch(client, input);
        JawsLogger.getLogger().info(patchResponse.textValue());
        Integer updatedNodeIsDeadInMinutes = patchResponse.findValue("nodeIsDeadInMinutes").intValue();
        Integer updatedodeClusterActiveInMinutes = patchResponse.findValue("nodeClusterActiveInMinutes").intValue();
        SeLionAsserts.assertTrue(updatedNodeIsDeadInMinutes.equals(240), "Value of nodeIsDeadInMinutes is not updated to 240");
        SeLionAsserts.assertTrue(updatedodeClusterActiveInMinutes.equals(15), "Value of nodeClusterActiveInMinutes is not updated to 15");
    }

    /**
     * Method to validate Application Properties get call from cfbtmanagementserv
     *
     */
    @Test(testName = "getApplicationProperties", groups = {"cfbtmanagementserv", "small", "large"})
    public void getApplicationProperties() throws Exception {
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("application-properties");
        JsonNode getResponse = RestApiUtils.doGet(client);
        AppLogger.getLogger().info(getResponse.textValue());
        JawsLogger.getLogger().info(getResponse.textValue());
        ValidateHTTPStatus.httpSuccess(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());

    }

    /**
     * Method to verify get call for the features.
     *
     */
    @Test(testName = "getFeatures", groups = {"cfbtmanagementserv", "small", "large"})
    public void getFeatures() throws Exception {
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("features?componentType='cfbtmanagementserv'");
        JsonNode getResponse = RestApiUtils.doGet(client);
        AppLogger.getLogger().info(getResponse.textValue());
        JawsLogger.getLogger().info(getResponse.textValue());
        ValidateHTTPStatus.httpSuccess(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());
    }

    /**
     * Method to verify get call for the Cluster Details
     *
     */
    @Test(testName = "getClusterDetails", groups = {"cfbtmanagementserv", "small", "large"})
    public void getClusterDetails() throws Exception {
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("clusterDetails");
        JsonNode getResponse = RestApiUtils.doGet(client);
        AppLogger.getLogger().info(getResponse.textValue());
        JawsLogger.getLogger().info(getResponse.textValue());
        ValidateHTTPStatus.httpSuccess(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());
    }

    /**
     * Method to get the feature with Name
     *
     */
    @Test(testName = "getFeatureWithName", groups = {"cfbtmanagementserv", "large"})
    public void getFeatureWithName() throws Exception {
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("features/Remediation_Quiet_Period");
        JsonNode getResponse = RestApiUtils.doGet(client);
        AppLogger.getLogger().info(getResponse.textValue());
        JawsLogger.getLogger().info(getResponse.textValue());
        ValidateHTTPStatus.httpSuccess(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());
    }

    /**
     * Method to verify the call with invalid PayLoad ExecutionRequest
     * 
     */
    @Test(testName = "invalidPayLoadExecutionRequest", groups = { "cfbtexecserv", "large"})
    public void invalidPayLoadExecutionRequest() throws Exception {
        PayPalRestClient<JsonNode> client = RestApiUtils.restParameterClientBase("execution-requests");
        String executionRequestPayLoad = "{}";
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode input = objectMapper.readValue(executionRequestPayLoad, JsonNode.class);
        JsonNode getResponse = RestApiUtils.doPost(client, input);
        ValidateHTTPStatus.httpBadRequest(client.getStatus().getReasonPhrase(), client.getStatus().getStatusCode());
    }

}
