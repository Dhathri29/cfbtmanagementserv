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

/**
 * This is a pause state, waiting for all the fast pass requests in queue to complete deployments.
 */
public class DeployWaiting extends RequestState {
    ExecutionRequestDAO dao;
    ExecutionRequest request;
    private final Scheduler scheduler;
    private final DatabaseConfig dbConfig;
    
    public DeployWaiting(ExecutionRequest request, DatabaseConfig db, Scheduler scheduler) {
        this.dao = ExecutionRequestDAO.getInstance(db.getConnectionFactory());
        this.request = request;
        this.scheduler = scheduler;
        this.dbConfig = db;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutionRequest deployWaiting() throws Exception {
        if(this.request.isLegacyIntegrationModel()) {
            //cancel the timeout schedule.
            scheduler.cancel(this.request.getId(), Timeout.DEPLOYMENT_TIMEOUT.toString(), this.dbConfig.getConnectionFactory());
        }
        return dao.transitionToDeployWaiting(request);
    }   
}
