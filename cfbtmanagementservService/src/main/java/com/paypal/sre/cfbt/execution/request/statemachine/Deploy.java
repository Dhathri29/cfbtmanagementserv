/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.execution.request.statemachine;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.timeout.Timeout;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import com.paypal.sre.cfbt.shared.DateUtil;

/**
 * This is a pause state, waiting for the components to get deployed before continuing.
 */
public class Deploy extends RequestState {
    private final ExecutionRequest request;
    private final ExecutionRequestDAO requestDAO;
    private final Scheduler scheduler;
    private final DatabaseConfig db;

    public Deploy(ExecutionRequest request, DatabaseConfig db, Scheduler scheduler) {
        this.request = request;
        this.requestDAO = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
        this.scheduler = scheduler;
        this.db = db;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutionRequest begin() throws Exception {
        ExecutionRequest updatedRequest = requestDAO.transtionToDeploying(request);

        if (updatedRequest.isLegacyIntegrationModel()) {
            int estimatedDeployDuration = this.request.getReleaseTest().getDeploymentEstimatedDuration();
            if (estimatedDeployDuration < 10) {
                // Add some buffer since our timeout scheduler runs every 10 seconds.
                estimatedDeployDuration = estimatedDeployDuration + 10;
            }
            String timeoutAt = DateUtil.currentDateTimeUTC().plusSeconds(estimatedDeployDuration).toString();

            // Schedule a timeout
            scheduler.schedule(this.request.getId(), Timeout.DEPLOYMENT_TIMEOUT.toString(), timeoutAt,
                    this.db.getConnectionFactory());
        }
        return updatedRequest;
    }
}
