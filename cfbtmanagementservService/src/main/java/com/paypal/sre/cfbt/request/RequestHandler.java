package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.Feature;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.features.FeatureManager;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The handler to process the execution request using chain of responsibility design pattern.
 */
public class RequestHandler {
    private final DatabaseConfig dbConfig;
    private final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);
    private boolean isDuplicateExecutionRequest = false;

    public RequestHandler() {
        dbConfig = ConfigManager.getDatabaseConfig();
    }

    /**
     * Process the new execution request.
     *
     * @param request The new {@link ExecutionRequest} request
     * @return If the new request is duplicate then return Existing executionRequest (when 'Release_Request_Uniqueness' feature allows).
     * Otherwise, return newly created request.
     */
    public ExecutionRequest processRequest(ExecutionRequest request) throws Exception {

        ExecutionRequest existingRequest = getExistingReleaseRequest(request);
        if (existingRequest != null) {
            isDuplicateExecutionRequest = true;
            if (existingRequest.thisIsRelease()) {
                SpecialMessage special = new SpecialMessage(dbConfig, existingRequest.getQueueName());
                special.addSpecialMessage(existingRequest);
            }
            return existingRequest;
        }

        validateIntegrationModel(request);

        List<RequestProcessor> requestProcessors = new ArrayList<>();
        requestProcessors.add(new RunOneProcessor());
        requestProcessors.add(new RunNowProcessor());
        requestProcessors.add(new QueueProcessor());

        for (int i = 0; i < requestProcessors.size() - 1; i++) {
            requestProcessors.get(i).setNextHandler(requestProcessors.get(i + 1));
        }

        return (requestProcessors.get(0).process(request));
    }

    /**
     * Check if the given request is already present and is currently active. If it's existing and not being rollback
     * then returns existing execution request. Otherwise, returns null.
     *
     * @param newRequest The new {@link ExecutionRequest} request
     * @return Existing executionRequest in the queue if existing request is not being rollback and new request is
     * duplicate. Otherwise returns null.
     */
    public ExecutionRequest getExistingReleaseRequest(ExecutionRequest newRequest) {
        //Request uniqueness is not applicable for ad-hoc tests.
        if (newRequest.thisIsAdhoc()) {
            return null;
        }
        List<ExecutionRequest> sameManifestRequests = new ArrayList<>();

        try {
            sameManifestRequests = ExecutionRequestDAO.getInstance(dbConfig.getConnectionFactory()).getActiveRequests(newRequest.getTrReleaseId(), newRequest.getDatacenter());
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, RequestHandler.class.getName(), "An exception occurred while trying to get active existing requests - " + ex.getMessage(), ex);
        }

        if (sameManifestRequests == null || sameManifestRequests.isEmpty()) {
            return null;
        } else {
            boolean existingHasRollbackInProgress = false;
            for (ExecutionRequest executionRequest : sameManifestRequests) {
                if (executionRequest.isRollbackInProgress()) {
                    existingHasRollbackInProgress = true;
                }
            }
            boolean hasMoreThanOneDuplicate = sameManifestRequests.size() > 1;
            //If the request for given TR-release-id  is being rollback then allow only one duplicate request to add into the Queue.
            if (existingHasRollbackInProgress && !hasMoreThanOneDuplicate) {
                return null;
            } else {
                return (sameManifestRequests.get(0));
            }
        }
    }

    /**
     * Check and update the integration model for the newRequest.
     *
     * @param newRequest The new {@link ExecutionRequest} request
     * @return Existing executionRequest in the queue if new request is duplicate.
     */
    public void validateIntegrationModel(ExecutionRequest newRequest) {
        //CFBT Deployment is applicable for Altus-ALM release-tests only.
        if (newRequest.thisIsAdhoc()) {
            return;
        }
        if ("Altus-ALM".equalsIgnoreCase(newRequest.getReleaseTest().getReleaseVehicle())) {
            newRequest.getReleaseTest().setIntegrationModel(ReleaseTest.IntegrationModel.FULL_DEPLOYMENT);
            if(newRequest.getReleaseTest() != null && StringUtils.isBlank(newRequest.getReleaseTest().getServiceId())) {
                throw new IllegalArgumentException("Valid 'serviceId' must be specified in the request when CFBT is handling deployment responsibilities.");
            }

        }

    }

    /**
     * Check if the request came for enqueue is duplicate or not.
     */
    public boolean isDuplicateRequest() {
        return isDuplicateExecutionRequest;
    }

}
