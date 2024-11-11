package com.paypal.test.sre.cfbt.api;

import com.paypal.test.jaws.logging.JawsLogger;
import com.paypal.test.sre.utilities.RestApiUtils;

/**
 * This class is responsible to handle the Request.
 */
public class CFBTManagementServRequest {

    RestApiUtils restApiUtils = new RestApiUtils();
    StringBuilder deployCompleteSB = new StringBuilder();
    StringBuilder completeSB = new StringBuilder();
    StringBuilder releaseTestSB = new StringBuilder();

    /**
     * This method is responsible to return the request to create Release Test with given releaseId.
     */
    public String createReleaseTestRequest(String releaseId) {
        String releaseTest = "{" +
                "\"user\" : \"ssalavi\"," +
                "\"components\" : [{\"name\" : \"cfbttestcomponentwithtests\", \"currentVersion\" : \"212.0-39501333.i686.rpm\"}]," +
                "\"testingCompleteCallbackURL\" : \"https://te-cfbt-altus.qa.paypal.com:8443/v1/cfbtqaserv/cfbtqa/testing-complete\"," +
                "\"deployReadyCallbackURL\" : \"https://te-cfbt-altus.qa.paypal.com:8443/v1/cfbtqaserv/cfbtqa/deploy-ready\"," +
                "\"statusUpdateCallbackURL\" : \"https://te-cfbt-altus.qa.paypal.com:8443/v1/cfbtqaserv/cfbtqa/status-update-alm\"," +
                "\"releaseId\" : \"" + releaseId + "\"," +
                "\"releaseVehicle\" : \"ALTUS_ALM\"," +
                "\"priority\" : 1," +
                "\"deploymentEstimatedDurationSeconds\" : 60," +
                "\"systemUnderTest\" : {\"dataCenter\" : \"msmaster\"}," +
                "\"serviceId\": \"serviceid-app:test\"" +
                " }";
        return releaseTest;

    }

    String deployComplete = "{" + "}";
    String releaseStatusRollBack = "{" + "\"releaseStatus\" : \"ROLLBACK\"}";
    String releaseStatusCancel = "{" + "\"releaseStatus\" : \"CANCEL\"}";

    /**
     * This method is responsible to create & return the URI for retrieving specific Release Test.
     */
    public String getReleaseTestRequestURI(String releaseId) {
        releaseTestSB.append(RestApiUtils.getBaseUrl());
        releaseTestSB.append("release-tests");
        releaseTestSB.append("/");
        releaseTestSB.append(releaseId);
        releaseTestSB.append("/");
        return releaseTestSB.toString();
    }

    /**
     * This method is responsible to create & return the URI for Release Test deploy-complete call.
     */
    public String deployCompleteURI(String releaseId) {
        deployCompleteSB.append(RestApiUtils.getBaseUrl());
        deployCompleteSB.append("release-tests");
        deployCompleteSB.append("/");
        deployCompleteSB.append(releaseId);
        deployCompleteSB.append("/");
        deployCompleteSB.append("deploy-complete");
        JawsLogger.getLogger().info(deployCompleteSB.toString());
        return deployCompleteSB.toString();
    }

    /**
     * This method is responsible to create & return the URI for Release Test complete call.
     */
    public void completeURI(String releaseId) {
        completeSB.append(RestApiUtils.getBaseUrl());
        completeSB.append("release-tests");
        completeSB.append("/");
        completeSB.append(releaseId);
        completeSB.append("/");
        completeSB.append("complete");
        JawsLogger.getLogger().info(deployCompleteSB.toString());

    }

}
