package com.paypal.sre.cfbt.management.dal;


import org.bson.Document;
import org.bson.types.ObjectId;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;

public class ReleaseTestRepo {
    private final ReleaseTestDAO releaseTestDAO;
    private final ExecutionRequestDAO executionRequestDAO;
    
    public ReleaseTestRepo(MongoConnectionFactory factory) {
        releaseTestDAO = ReleaseTestDAO.getInstance(factory);
        executionRequestDAO = ExecutionRequestDAO.getInstance(factory);
    }
    
    /**
     * Method to retrieve the {@link ReleaseTest} for a give release test id.
     * 
     * @param id id of the release test.
     * @return instance of {@link ReleaseTest}
     * @throws Exception
     */
    public ReleaseTest getById(String id) throws Exception {
        ReleaseTest releaseTest;

        try (MongoConnection c = ConfigManager.getDatabaseConfig().getConnectionFactory().newConnection()) {
            releaseTest = releaseTestDAO.readById(c, id);
            
            if (releaseTest == null) {
                throw new IllegalArgumentException("Was not able to load id = " + id);
            }

            if (releaseTest.getExecutionRequest() != null) {
                String requestId = releaseTest.getExecutionRequest().getId();

                if (requestId != null && !requestId.isEmpty()) {
                    ExecutionRequest request = executionRequestDAO.getById(id);
                    releaseTest.setExecutionRequest(request);
                }
            }
        }

        return releaseTest;
    }

    /**
     * This method updates the estimated deployment duration.
     *
     * @param dB                  instance of {@link MongoConnectionFactory}
     * @param id                  id of the {@link ReleaseTest}
     * @param extendTimeInSeconds time in seconds.
     * @return
     * @throws Exception
     */
    public ReleaseTest updateDeploymentEstimatedDuration(MongoConnectionFactory dB, String id, int extendTimeInSeconds)
            throws Exception {
        ReleaseTest releaseTest = getById(id);

        if (releaseTest != null) {
            int updatedDeploymentEstimatedDuration = releaseTest.getDeploymentEstimatedDuration() + extendTimeInSeconds;
            releaseTestDAO.update(dB.newConnection(), new Document("_id", new ObjectId(id)), new Document("$set",
                    new Document("releaseTest.deploymentEstimatedDuration", updatedDeploymentEstimatedDuration)),
                    false);
            releaseTest.setDeploymentEstimatedDuration(updatedDeploymentEstimatedDuration);
        }

        return releaseTest;
    }
}
