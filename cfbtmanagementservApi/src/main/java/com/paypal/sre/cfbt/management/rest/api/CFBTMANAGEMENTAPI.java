package com.paypal.sre.cfbt.management.rest.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.PATCH;
import com.ebayinc.platform.services.jsonpatch.JsonPatch;
import com.paypal.sre.cfbt.data.Feature;
import com.paypal.sre.cfbt.data.execapi.ChangePositionRequest;
import com.paypal.sre.cfbt.data.execapi.ClusterDetails;
import com.paypal.sre.cfbt.data.execapi.ClusterUpdateRequest;
import com.paypal.sre.cfbt.data.execapi.CompleteRequest;
import com.paypal.sre.cfbt.data.execapi.Datacenters;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequestDetails;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequestList;
import com.paypal.sre.cfbt.data.execapi.ExtendDeployment;
import com.paypal.sre.cfbt.data.execapi.FeatureList;
import com.paypal.sre.cfbt.data.execapi.NotifyPackageActionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.data.execapi.RunRequest;
import com.paypal.sre.cfbt.data.execapi.SystemStatusRequest;
import com.paypal.sre.cfbt.data.execapi.ApplicationProperties;
import com.paypal.sre.cfbt.data.execapi.ComponentList;
import com.paypal.sre.cfbt.data.execapi.ComponentsStatus;
import com.paypal.sre.cfbt.data.execapi.ComponentsStatusRequest;
import com.paypal.sre.cfbt.data.notification.SlackNotification;
import com.paypal.sre.cfbt.shared.PagedResource;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Api(value = "cfbt", authorizations = {})
@Path("/")
@Consumes({ MediaType.APPLICATION_JSON }) 
@Produces({ MediaType.APPLICATION_JSON })
public interface CFBTMANAGEMENTAPI {

    @GET
    @Produces({ MediaType.TEXT_PLAIN })
    @Path("/status")
    Response status();

    // =======================================================

    @ApiOperation(value = "Emergency Stop All Executions", notes = "This will emergency stop all executions still going on. "
            + "No post processing will be performed and tests will be "
            + "stopped in the middle; likely resulting in NON dollar "
            + "neutral transactions!  As such, it should only be used as an emergency. If it passed the optional "
            + "aExecutionRequestId, it will only emergency-stop that one execution_request.", response = ExecutionRequest.class, responseContainer="List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful Operation"),
            @ApiResponse(code = 404, message = "Execution Request ID does not exist"),
            @ApiResponse(code = 403, message = "Message not appropriate for current state of the system"),
            @ApiResponse(code = 500, message = "Internal server error")
        })
    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("execution-requests/emergency-stop")
    Response testExecutionBatchEmergencyStop(
            @ApiParam(name = "executionRequestDetails", required = false) ExecutionRequestDetails executionRequestDetails);

    // =======================================================

    @ApiOperation(value = "Graceful Halt", notes = "This will ask all currently executing batches to stop "
            + "after the current test is completed and then run the post "
            + "processing afterwards.  This is a clean way to stop long "
            + "batches from running.  It may take a couple minutes to  "
            + "complete stopping however.  It should result in dollar "
            + "neutral transactions (as long as the individual tests are "
            + "complying with that directive).  If it passed the optional "
            + "execution_request_id parameter, it will only halt that one execution_request.", response = ExecutionRequest.class, responseContainer="List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful Operation"),
            @ApiResponse(code = 404, message = "Execution Request ID does not exist"),
            @ApiResponse(code = 403, message = "Message not appropriate for current state of the system"),
            @ApiResponse(code = 500, message = "Internal server error")
        })

    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("execution-requests/halt")
    Response testExecutionBatchHalt(
            @ApiParam(name = "executionRequestDetails", required = false) ExecutionRequestDetails executionRequestDetails);

 // =======================================================
    
    @POST
    @Path("packages/notify")
    @ApiOperation(
            value = "Notify execserv node/s to download or delete the test package.",
            notes = "Notify and download/ delete the test package (local copy) in the execserv node/s"
    )
    @ApiResponses(value = {
        @ApiResponse(code = 204, message = "Notification successful", response = void.class),
        @ApiResponse(code = 400, message="Invalid notify package action request", response = void.class),
        @ApiResponse(code = 500, message="Notification unsuccessful", response=void.class)
    })
    Response notifyAndDownloadOrDeleteTestPackage(@HeaderParam("X-CFBT-USER-INFO") String userInfo, 
            @ApiParam(name="notifyPackageActionRequest", required=true) NotifyPackageActionRequest notifyPackageActionRequest);
    
    @ApiOperation(
            value = "Create a Test Execution Requests",
            notes = "Execute the passed in test id's (this will create an " + 
	            "execution request).  If an execution_request_id is " +
		    "passed in, make a copy of that test batch execution " +
		    "(basically copying the tests to be run) into a new " +
		    "execution and start that.  If configuration_time is " +
		    "passed in, execute all tests affected by the currently " +
		    "modified components. Will return an error if the timestamp " +
		    "passed in does not match the timestamp in the system " +
		    "configuration record. User id will be inferred from the " +
		    "security data passed in through the request header.",
            response = ExecutionRequestList.class
    )
    @ApiResponses(value={
        @ApiResponse(code = 200, message = "Set of test requests successfully queued."),
        @ApiResponse(code = 404, message = "Test ID does not exist"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    /**
     * This method returns a list of tests executed.
     * @return  A list of test batches executed.
     */
    @POST
    @Path("execution-requests")
    Response testRun(@HeaderParam("X-CFBT-USER-INFO") String userInfo, @ApiParam(name="runRequest", required=true) RunRequest runRequest);

    @ApiOperation(
            value = "Creates a new Release Test",
            notes = "",
            response = ReleaseTest.class
    )
    @ApiResponses(value={
        @ApiResponse(code = 201, message = "Release Test successfully created."),
        @ApiResponse(code = 200, message = "Release Test already exists."),
        @ApiResponse(code = 400, message = "Problems in the request"),
        @ApiResponse(code = 500, message = "Internal server error")
    })   
    @POST
    @Path("release-tests")    
    Response createReleaseTest(@HeaderParam("X-CFBT-USER-INFO") String userInfo, @ApiParam(name="releaseRequest", required=true) ReleaseRequest releaseRequest);
     
    @ApiOperation(
            value = "Allows the client to signal to the system the release decisioning is complete.",
            notes = "",
            response = ReleaseTest.class
    )
    @ApiResponses(value={
        @ApiResponse(code = 200, message = "Complete operation processed correctly"),
        @ApiResponse(code = 400, message = "Problems in the request"),
        @ApiResponse(code = 403, message = "Message not appropriate for current state of the system"),
        @ApiResponse(code = 500, message = "Internal server error")
    })      
    @POST
    @Path("release-tests/{id}/complete")
    Response complete(@HeaderParam("X-CFBT-USER-INFO") String userInfo, @PathParam("id") String id, @ApiParam(name="completeRequest", required=true) CompleteRequest completeRequest);

    @ApiOperation(
            value = "Allows the client to signal the deployment was complete",
            notes = "",
            response = ReleaseTest.class
    )
    @ApiResponses(value={
        @ApiResponse(code = 200, message = "Deployment complete operation received properly"),
        @ApiResponse(code = 400, message = "Problems in the request"),
        @ApiResponse(code = 403, message = "Message not appropriate for current state of the system"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @POST    
    @Path("release-tests/{id}/deploy-complete")
    Response deployComplete(@HeaderParam("X-CFBT-USER-INFO") String userInfo, @PathParam("id") String id);

    @ApiOperation(
            value = "Allows the client to request more time to deploye the component(s).",
            notes = "",
            response = ReleaseTest.class
    )
    @ApiResponses(value={
        @ApiResponse(code = 200, message = "Extend deployment request processed correctly."),
        @ApiResponse(code = 400, message = "Problems in the request"),
        @ApiResponse(code = 403, message = "Message not appropriate for current state of the system"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @POST
    @Path("release-tests/{id}/extend-deploy")
    Response extendDeploy(@HeaderParam("X-CFBT-USER-INFO") String userInfo, @PathParam("id") String id, @ApiParam(name="extendDeployment", required=true) ExtendDeployment extendDeployment);

    @ApiOperation(
            value = "Allows the client to change the priority position in the queue.",
            notes = "",
            response = ReleaseTest.class
    )
    @ApiResponses(value={
        @ApiResponse(code = 200, message = "Change priority request processed correctly."),
        @ApiResponse(code = 400, message = "Problems in the request"),
        @ApiResponse(code = 403, message = "Message not appropriate for current state of the system"),
        @ApiResponse(code = 500, message = "Internal server error")
    })    
    @POST
    @Path("release-tests/{id}/change-position")
    Response changePosition(@HeaderParam("X-CFBT-USER-INFO") String userInfo, @PathParam("id") String id, @ApiParam(name="changePosition", required=true) ChangePositionRequest changePosition);
    
    @ApiOperation(
            value = "Request the paginated list of release tests, queued and running.",
            notes = "If includePendingTestCompleteCallback is true, then this list will include all the release test which are yet to make a test complete callback in the last 30 minutes.",
            response = PagedResource.class
    )
    @ApiResponses(value={
        @ApiResponse(code = 200, message = "Request processed correctly"),
        @ApiResponse(code = 400, message = "Problems in the request"),
        @ApiResponse(code = 500, message = "Internal server error")
    })  
    @GET
    @Path("release-tests")
    Response getReleaseTests(@HeaderParam("X-CFBT-USER-INFO") String userInfo,
            @QueryParam("page") int page_number, @QueryParam("size") int size, @QueryParam("queuestatus") String queuestatus, @QueryParam("includePendingTestCompleteCallback") boolean includePendingTestCompleteCallback);
    
    @ApiOperation(
            value = "Request a specific Release Test.",
            notes = "",
            response = ReleaseTest.class
    )   
    @ApiResponses(value={
        @ApiResponse(code = 200, message = "Complete operation processed correctly"),
        @ApiResponse(code = 400, message = "Problems in the request"),
        @ApiResponse(code = 404, message = "Release test does not exist"),
        @ApiResponse(code = 500, message = "Internal server error")
    }) 
    @GET
    @Path("release-tests/{id}")
    Response getReleaseTest(@HeaderParam("X-CFBT-USER-INFO") String userInfo, @PathParam("id") String id);   

    /**
     * This method return the specified execution request id.
     * @param aExecutionRequestId The unique identifier of the request.
     * @return The Execution Request.
     */
    @ApiOperation(
            value="Get the execution request",
            notes="This will return a single Execution Request, the results and details.",
            response = ExecutionRequest.class
    )
    @ApiResponses(value={
        @ApiResponse(code=404, message="Execution ID does not exist")
    })
    @GET
    @Path("execution-requests/{execution-request-id}")
    ExecutionRequest getExecutionRequest(@PathParam("execution-request-id") String aExecutionRequestId);

    @ApiOperation(
            value = "Allows the client to signal to the system that the last test in the request was completed.",
            notes = "",
            response = Response.class
    )
    @ApiResponses(value={
        @ApiResponse(code = 200, message = "Testing complete operation processed correctly"),
        @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @POST
    @Path("execution-requests/{id}/testing-complete")
    Response testingComplete(@PathParam("id") String executionRequestId);

    /**
     * This method returns a list of pending, in-prgress, and recently completed execution requests.
     * @return A list of execution requests.
     */
    @ApiOperation(
        value = "Execution Request List",
        notes = "Returns a list of pending, in-progress, and recently completed execution requests",
        response = ExecutionRequest.class,
        responseContainer = "List"
    )
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @POST
    @Path("execution-requests/system-status")
    Response systemStatusExecutionRequests(
        @HeaderParam("X-CFBT-USER-INFO") String userInfo,
        @ApiParam(name = "systemStatusRequest", required = false) SystemStatusRequest request
    );

    // =======================================================
    
    @ApiOperation(
            value = "Update Cluster Details",
            notes = "Enable or disable the test run for the specified node.",
            response = ClusterUpdateRequest.class,
            responseContainer = "List"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Updated the cluster successfully"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @POST
    @Path("clusterDetails/update")
    Response updateClusterDetails(@ApiParam(name = "clusterUpdateRequest", required = true) ClusterUpdateRequest updateRequest);

    // =======================================================
    @ApiOperation(
            value = "Datacenter List",
            notes = "This API returns a list of supported datacenters.",
            response = Datacenters.class
    )
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("/datacenters")
    Response getDatacenters();
    
    // =======================================================
    
    @ApiOperation(
        value = "Datacenter List",
        notes = "This API updates the list of supported datacenters.",
        response = Datacenters.class
    )
    @PUT
    @Produces({MediaType.APPLICATION_JSON})
    @Path("/datacenters")
    Response updateDatacenters(Datacenters datacenters);
    
    // =======================================================

    @ApiOperation(
            value = "Get Node Details",
            notes = "Retrieve the cluster details for debug and visibility.",
            response = ClusterDetails.class
    )
    @ApiResponses(value = {
            @ApiResponse(code=500, message="Internal server error")
    })
    @GET
    @Path("clusterDetails")
    Response getClusterDetails(); 
    

    @GET
    @Path("features")
    @Produces({"application/json"})
    @ApiOperation(
        value = "Gets feature list.", 
        notes = "This request retrieves all the features from Feature collection",
        response = FeatureList.class
    )
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "successful operation", response = FeatureList.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = void.class) }
    )
    Response getFeatures(@QueryParam("status")List<String> status, @QueryParam("componentType")String componentType);
    
    // =======================================================

    
    @GET
    @Path("features/{feature_name}")
    @Produces({"application/json"})
    @ApiOperation(
        value = "Gets feature list.", 
        notes = "This request retrieves all the features from Feature collection",
        response = Feature.class
    )
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "successful operation", response = FeatureList.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = void.class) }
    )
    Response getFeatureWithName(@PathParam("feature_name" ) String featureName);
    
    // =======================================================
    
    @POST
    @Path("features")
    @ApiOperation(
            value = "New Feature document",
            notes = "Insert new Feature document",
            response = Feature.class
    )
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "successful operation", response = Feature.class),
        @ApiResponse(code=400, message="Invalid parameters", response = void.class),
        @ApiResponse(code=403, message="Trying to insert same object", response = void.class),
         @ApiResponse(code=500, message="Internal server error", response=void.class)
    })
    Response insertFeature(@HeaderParam("X-CFBT-USER-INFO") String userInfo, Feature features);
    
    // =======================================================
    
    @PATCH
    @io.swagger.jaxrs.PATCH
    @Path("features/{feature_id}")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    @ApiOperation(
        value = "Updates Feature.", 
        notes = "This request updates a specific feature changed by the user.", 
        response = Feature.class
    )
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "successful operation", response = Feature.class),
        @ApiResponse(code = 404, message = "Not a valid feature", response = void.class),
        @ApiResponse(code = 500, message = "Internal Server Error.", response = void.class) })
    Response updateFeature(@HeaderParam("X-CFBT-USER-INFO") String userInfo,
            @PathParam("feature_id" ) String featureId, 
            @ApiParam(name = "patchList", required = true) List<JsonPatch> patchList);

    // =======================================================

    @ApiOperation(
            value = "Application Property ",
            notes = "This API returns the application properties",
            response = ApplicationProperties.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "successful operation", response = ApplicationProperties.class),
            @ApiResponse(code = 400, message = "Bad request", response = void.class),
            @ApiResponse(code = 401, message = "Unauthorized operation", response = void.class),
            @ApiResponse(code = 500, message = "Internal Server Error", response = void.class)
    })
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("/application-properties")
    Response getApplicationProperties(@HeaderParam("X-CFBT-USER-INFO") String userInfo);

    //=======================================================

    @ApiOperation(
            value = "Update application property.",
            notes = "Update application property.",
            response = ApplicationProperties.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = ApplicationProperties.class),
            @ApiResponse(code = 400, message = "JSONPatch is invalid", response = void.class),
            @ApiResponse(code = 401, message = "Unauthorized operation", response = void.class),
            @ApiResponse(code = 403, message = "The requested field Update is not allowed", response = void.class),
            @ApiResponse(code = 404, message = "Application Property record does not exist", response = void.class),
            @ApiResponse(code = 500, message = "Internal Server Error", response = void.class)
    })
    @PATCH
    @io.swagger.jaxrs.PATCH
    @Path("/application-property")
    Response updateApplicationProperty(
            @HeaderParam("X-CFBT-USER-INFO") String userInfo,
            @ApiParam(name = "patchList", required = true) List<JsonPatch> patchList);

    //=======================================================

    @ApiOperation(value = "SlackNotification", notes = "This API returns the SlackNotification object", response = SlackNotification.class)
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/slack-notifications")
    Response getSlackNotification();

    // =======================================================
    @PATCH
    @io.swagger.jaxrs.PATCH
    @ApiOperation(value = "Slack Notification", notes = "This API modify the SlackNotification configuration in the DB and then returns the updated SlackNotification object", response = SlackNotification.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful Operation", response = SlackNotification.class),
            @ApiResponse(code = 400, message = "Bad Request", response = void.class),
            @ApiResponse(code = 401, message = "Unauthorized Operation", response = void.class),
            @ApiResponse(code = 500, message = "Internal Server Error", response = void.class) })
    @Path("/slack-notifications")
    Response updateSlackNotification(
            @ApiParam(name = "slackNotification", required = true) SlackNotification slackNotification);

    // =======================================================
    @ApiOperation(value = "API to check if the components are covered by the release vetting cfbt tests for the specified/default (i.e. cfbt11) systemUnderTest dataCenter.", notes = "This API will return true in the response if it has the release vetting tests for the specified/default (i.e. cfbt11) systemUnderTest dataCenter that cover the specified components in the request.", response = ComponentsStatus.class)
    @ApiResponses(value = { @ApiResponse(code = 500, message = "Internal Server Error"), @ApiResponse(code = 400, message = "If components list or systemUnderTest is not valid.") })
    @POST
    @Path("components-status")
    Response hasTests(@HeaderParam("X-CFBT-USER-INFO") String userInfo, @ApiParam(name="componentsStatusRequest", required=true) ComponentsStatusRequest componentsStatusRequest);
//=======================================================

    @ApiOperation(
            value="GetComponentList",
            notes="Retrieves the component lists supported by CFBT system",
            response=ComponentList.class
    )
    @ApiResponses(value={
            @ApiResponse(code=500,message="InternalServerError")
    })
    @GET
    @Path("components")
    ComponentList getComponentList(@HeaderParam("X-CFBT-USER-INFO")String userInfo,
                                  @QueryParam("covered")boolean covered, @QueryParam("dataCenter")String dataCenter);
}
