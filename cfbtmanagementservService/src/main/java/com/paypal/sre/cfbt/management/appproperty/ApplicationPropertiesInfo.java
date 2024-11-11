package com.paypal.sre.cfbt.management.appproperty;

import com.ebayinc.platform.services.jsonpatch.JsonPatch;
import com.paypal.platform.error.api.BusinessException;
import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.data.Activity;
import com.paypal.sre.cfbt.data.execapi.*;
import com.paypal.sre.cfbt.dataaccess.ActivityDAO;
import com.paypal.sre.cfbt.management.cluster.ClusterInfo;
import com.paypal.sre.cfbt.management.dal.ApplicationPropertyDAO;
import com.paypal.sre.cfbt.management.rest.impl.CFBTManagementService;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.request.User;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import org.apache.commons.configuration.Configuration;
import org.slf4j.LoggerFactory;
import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible to handle Application Properties info such as node configurations, waitTimeThresholds.
 */
public class ApplicationPropertiesInfo {
    private Configuration config;
    private MongoConnectionFactory mongoConnectionFactory;
    private ApplicationPropertyJsonPatchProcessor appPropertyJsonPatchProcessor;
    private static final ApplicationPropertyDAO APPLICATIONPROPERTY_DAO = ApplicationPropertyDAO.getInstance();
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ApplicationPropertiesInfo.class);

    public ApplicationPropertiesInfo() {
    }

    public ApplicationPropertiesInfo(Configuration config, MongoConnectionFactory mongoConnectionFactory) {
        this.config = config;
        this.mongoConnectionFactory = mongoConnectionFactory;
        this.appPropertyJsonPatchProcessor = new ApplicationPropertyJsonPatchProcessor();
    }

    /**
     * @return The default number of threads for the cluster nodes from existing
     * DB data or from config if it not available in the DB.
     */
    public int getNumberOfThreads() {
        int numConfiguredThreads = this.getDefaultNumberOfThreads();

        NodeConfiguration nodeConfiguration = this.findOrInsertNodeConfiguration();

        if (nodeConfiguration != null && nodeConfiguration.getNumberOfThreads() != null) {
            numConfiguredThreads = nodeConfiguration.getNumberOfThreads();
        }
        return numConfiguredThreads;
    }

    /**
     * @return The cutoff duration (from DB or from config if it not available
     * in the DB) in minutes indicating when the cluster nodes are considered
     * dead.
     */
    public int getNodeIsDeadInMinutes() {
        int nodeIsDeadInMinutes = this.getDefaultNodeIsDeadInMinutes();

        NodeConfiguration nodeConfiguration = this.findOrInsertNodeConfiguration();

        if (nodeConfiguration != null && nodeConfiguration.getNodeIsDeadInMinutes() != null) {
            nodeIsDeadInMinutes = nodeConfiguration.getNodeIsDeadInMinutes();
        }
        return nodeIsDeadInMinutes;
    }

    /**
     * @return The cutoff duration (from DB or from config if it not available
     * in the DB) in minutes indicating when the cluster nodes are considered
     * down but not dead.
     */
    public int getThreadIsDownInMinutes() {
        int threadIsDownInMinutes = this.getDefaultThreadIsDownInMinutes();

        NodeConfiguration nodeConfiguration = this.findOrInsertNodeConfiguration();

        if (nodeConfiguration != null && nodeConfiguration.getThreadIsDownInMinutes() != null) {
            threadIsDownInMinutes = nodeConfiguration.getThreadIsDownInMinutes();
        }
        return threadIsDownInMinutes;
    }

    /**
     * @return The maximum number of pending execution requests that the System Status page will display.
     */
    public int getSystemStatusPendingLimit() {
        try {
            ApplicationPropertyDAO appDAO = ApplicationPropertyDAO.getInstance();
            MongoConnection mongoConnection = mongoConnectionFactory.newConnection();
            ApplicationProperty property = appDAO.findOrInsertSystemStatusLimits(mongoConnection);
            return property.getSystemStatusPendingLimit();
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, "getSystemStatusPendingLimit", "CFBT API: Error while fetching system status pending limit.", ex);
            return ApplicationProperty.DEFAULT_SYSTEM_STATUS_PENDING_LIMIT;
        }
    }

    /**
     * @return The maximum number of completed execution requests that the System Status page will display.
     */
    public int getSystemStatusCompletedLimit() {
        try {
            ApplicationPropertyDAO appDAO = ApplicationPropertyDAO.getInstance();
            MongoConnection mongoConnection = mongoConnectionFactory.newConnection();
            ApplicationProperty property = appDAO.findOrInsertSystemStatusLimits(mongoConnection);
            return property.getSystemStatusCompletedLimit();
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, "getSystemStatusCompletedLimit", "CFBT API: Error while fetching system status completed limit.", ex);
            return ApplicationProperty.DEFAULT_SYSTEM_STATUS_COMPLETED_LIMIT;
        }
    }

    /**
     * @return The existing/ inserted {@link NodeConfiguration} object from the
     * DB.
     */
    private NodeConfiguration findOrInsertNodeConfiguration() {

        int numConfiguredThreads = this.getDefaultNumberOfThreads();
        int threadIsDownInMinutes = this.getDefaultThreadIsDownInMinutes();
        int nodeIsDeadInMinutes = this.getDefaultNodeIsDeadInMinutes();
        NodeConfiguration nodeConfiguration = null;
        try {
            //Retrieve Node configuration for the node from the DB. If it's not present in the DB then insert the default config into DB.
            ApplicationProperty appProperty = APPLICATIONPROPERTY_DAO.findOrInsertNodeConfiguration(mongoConnectionFactory, numConfiguredThreads, nodeIsDeadInMinutes, threadIsDownInMinutes);

            if (appProperty != null) {
                nodeConfiguration = appProperty.nodeConfiguration();
            }
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, ApplicationPropertiesInfo.class.getCanonicalName(), "Error while updating node configuration. Exception - " + ex);
        }
        return nodeConfiguration;
    }

    /**
     * @return The config default number of threads, for the cluster nodes.
     */
    private int getDefaultNumberOfThreads() {
        return config.getInt("numberOfThreads", 2);
    }

    /**
     * @return The config default cutoff duration in minutes indicating when the
     * cluster nodes are considered down but not dead.
     */
    private int getDefaultThreadIsDownInMinutes() {
        return config.getInt("threadIsDownInMinutes", 15);
    }

    /**
     * @return The config default cutoff duration in minutes indicating when the
     * cluster nodes are considered dead.
     */
    private int getDefaultNodeIsDeadInMinutes() {
        return config.getInt("nodeIsDeadInMinutes", 4 * 60);
    }

    /**
     * Getting application property entries
     *
     * @return The {@link ApplicationProperties} object
     */
    public ApplicationProperties getApplicationProperty(String userInfo) {
        ApplicationProperty appProperty = null;
        ApplicationProperties applicationProperties = new ApplicationProperties();
        User user = new User(userInfo);
        if (user.validateAdminUser(userInfo)) {
            try {
                ApplicationPropertyDAO applicationPropertyDAO = ApplicationPropertyDAO.getInstance();
                MongoConnection mongoConnection = mongoConnectionFactory.newConnection();
                appProperty = applicationPropertyDAO.getApplicationProperty(mongoConnection);
                if (appProperty == null) {
                    CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID,
                            "No record found for ApplicationProperty. ", null);
                }

                //Insert default wait time thresholds if it's already not present in the existing DB record. This check can be removed once initial values are inserted into DB.
                if (appProperty.noWaitTimeThresholds()) {
                    appProperty = applicationPropertyDAO.findOrInsertWaitTimeThresholds(mongoConnection, appProperty);
                }
                if (appProperty.getComponentMappingThreshold() == null) {
                    appProperty = applicationPropertyDAO.findOrInsertComponentMappingThreshold(mongoConnection, appProperty);
                }
                if (appProperty.noSystemStatusLimits()) {
                    appProperty = applicationPropertyDAO.findOrInsertSystemStatusLimits(mongoConnection);
                }
                if (appProperty != null) {
                    applicationProperties = getApplicationProperties(appProperty);
                }

            } catch (Exception ex) {
                CFBTLogger.logError(LOGGER, "getApplicationProperty", "CFBT API: Error while fetching application properties.", ex);
                CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                        "Error while fetching application properties. Exception: ", ex);
            }
        }
        return applicationProperties;
    }

    /**
     * Update the application property.
     *
     * @param userInfo  The user information
     * @param patchList List of {@link JsonPatch} objects
     * @return The updated {@link ApplicationProperty} object
     */
    public ApplicationProperty updateApplicationProperty(String userInfo, List<JsonPatch> patchList) {
        ApplicationProperty applicationProperty = null;
        User user = new User(userInfo);
        if (user.validateAdminUser(userInfo)) {
            if (patchList == null || patchList.isEmpty()) {
                CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Valid json patch list needs to be specified. ", null);
            }

            ApplicationPropertyDAO applicationPropertyDAO = ApplicationPropertyDAO.getInstance();
            String errorMessage = "An exception occurred while updating the Application Property. Root cause: ";
            try {
                MongoConnection mongoConnection = mongoConnectionFactory.newConnection();
                ApplicationProperty appProperty = applicationPropertyDAO.getApplicationProperty(mongoConnection);
                if (appProperty == null) {
                    CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID, "No record found to update an ApplicationProperty. ", null);
                }

                //Process Json for ApplicationProperty
                appPropertyJsonPatchProcessor.processApplicationPropertyJson(user.getUserId(), appProperty, patchList);

                //Update the application property in DB
                applicationProperty = applicationPropertyDAO.updateApplicationProperty(mongoConnection, appProperty.getId(),
                        appPropertyJsonPatchProcessor);

                // If applicationProperty update in DB is successful then add the activity log.
                try {
                    List<Activity> activities = appPropertyJsonPatchProcessor.getUpdateActivities();
                    if (activities != null && !activities.isEmpty() && applicationProperty != null) {
                        ActivityDAO activityDao = ActivityDAO.getInstance();
                        activityDao.createActivities(mongoConnection, activities);
                    }
                } catch (Exception ex) {
                    CFBTLogger.logError(LOGGER, CFBTManagementService.class.getCanonicalName(),
                            "Exception occurred while updating activities for application property record update. Exception: " + ex, ex);
                }
            } catch (IllegalArgumentException ex) {
                CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, errorMessage + ex, ex);
            } catch (ForbiddenException ex) {
                CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, errorMessage + ex, ex);
            } catch (BusinessException ex) {
                CFBTLogger.logError(LOGGER, CFBTManagementService.class.getCanonicalName(), errorMessage, ex);
                throw ex;
            } catch (Exception ex) {
                CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, errorMessage + ex, ex);
            }
        }
        return applicationProperty;
    }

    /**
     * This will return ApplicationProperty API Schema.
     *
     * @param applicationProperty The {@link ApplicationProperty} object
     * @return The {@link ApplicationProperties} object
     */
    public ApplicationProperties getApplicationProperties(ApplicationProperty applicationProperty) {
        ApplicationProperties applicationProperties = new ApplicationProperties();
        if (applicationProperty != null) {
            applicationProperties.setId(applicationProperty.getId());
            applicationProperties.setMediumWaitTimeThreshold(applicationProperty.getMediumWaitTimeThreshold());
            applicationProperties.setLargeWaitTimeThreshold(applicationProperty.getLargeWaitTimeThreshold());
            applicationProperties.setNormalWaitTimeMessage(applicationProperty.getNormalWaitTimeMessage());
            applicationProperties.setMediumWaitTimeMessage(applicationProperty.getMediumWaitTimeMessage());
            applicationProperties.setLargeWaitTimeMessage(applicationProperty.getLargeWaitTimeMessage());
            applicationProperties.setThreadIsDownInMinutes(applicationProperty.getThreadIsDownInMinutes());
            applicationProperties.setNodeIsDeadInMinutes(applicationProperty.getNodeIsDeadInMinutes());
            applicationProperties.setNodeClusterActiveInMinutes(applicationProperty.getNodeClusterActiveInMinutes());
            applicationProperties.setNumberOfThreads(applicationProperty.getNumberOfThreads());
            applicationProperties.setExemptComponents(applicationProperty.getExemptComponents());
            applicationProperties.setAllTestsComponents(applicationProperty.getAllTestsComponents());
            applicationProperties.setComponentMappingThreshold(applicationProperty.getComponentMappingThreshold());
            applicationProperties.setSystemStatusPendingLimit(applicationProperty.getSystemStatusPendingLimit());
            applicationProperties.setSystemStatusCompletedLimit(applicationProperty.getSystemStatusCompletedLimit());
        }
        return applicationProperties;
    }

    /**
     * @return The {@link ApplicationPropertyJsonPatchProcessor} object
     */
    public ApplicationPropertyJsonPatchProcessor getJsonPatchProcessor() {
        return this.appPropertyJsonPatchProcessor;
    }

    /**
     * Generate the ClusterUpdateRequest object with the given properties for updating cluster
     *
     * @param patchProcessor    The {@link ApplicationPropertyJsonPatchProcessor} object
     * @param nodeConfiguration The {@link NodeConfiguration} object
     * @return The {@link ClusterUpdateRequest} object
     */
    public ClusterUpdateRequest clusterUpdateRequest(ApplicationPropertyJsonPatchProcessor patchProcessor, NodeConfiguration nodeConfiguration) {
        ClusterUpdateRequest updateRequest = null;
        try {
            // If node configuration for the cluster nodes is updated then accordingly update the running number
            // of threads for the cluster nodes.
            if (patchProcessor.isFieldUpdated("numberOfThreads") && nodeConfiguration != null
                    && nodeConfiguration.getNumberOfThreads() != null) {
                ClusterInfo clusterInfo = new ClusterInfo(mongoConnectionFactory, config, this);
                Map<String, ClusterInfo.Node> theCluster = clusterInfo.getFullCluster();
                updateRequest = new ClusterUpdateRequest();
                List<NodeUpdateProperties> nodeUpdateProperties = new ArrayList<>();
                for (Map.Entry<String, ClusterInfo.Node> entry : theCluster.entrySet()) {
                    ClusterInfo.Node thisNode = entry.getValue();
                    NodeUpdateProperties nodeUpdateProperty = new NodeUpdateProperties();
                    nodeUpdateProperty.setIpAddress(entry.getKey());
                    nodeUpdateProperty.setEnableTestRun(thisNode.getEnableTestRun());
                    nodeUpdateProperties.add(nodeUpdateProperty);
                }
                updateRequest.setNodeProperties(nodeUpdateProperties);
            }
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, CFBTManagementService.class.getCanonicalName(),
                    "Exception occurred while updating running threads for new node configuration. Exception: "
                            + ex,
                    ex);
        }
        return updateRequest;
    }
}
