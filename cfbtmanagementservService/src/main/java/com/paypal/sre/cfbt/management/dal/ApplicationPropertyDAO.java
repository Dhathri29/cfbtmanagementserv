/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.management.dal;

import com.mongodb.BasicDBObject;
import com.mongodb.client.result.UpdateResult;
import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.management.appproperty.ApplicationProperty;
import com.paypal.sre.cfbt.management.appproperty.ApplicationPropertyJsonPatchProcessor;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import org.apache.commons.lang.StringUtils;
import org.bson.Document;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for Data Access operations for ApplicationProperty
 * collection.
 */
public class ApplicationPropertyDAO extends AbstractDAO<ApplicationProperty> {

    private static final ApplicationPropertyDAO mInstance = new ApplicationPropertyDAO("ApplicationProperty");
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationPropertyDAO.class);

    /**
     * Constructor of singleton instance for this object.
     *
     * @param aCollectionName the name of the collection
     */
    public ApplicationPropertyDAO(String aCollectionName) {
        super(aCollectionName, ApplicationProperty.class);
    }

    /**
     * Accessor for singleton instance of this object.
     *
     * @return the {@link ApplicationPropertyDAO} instance.
     */
    public static ApplicationPropertyDAO getInstance() {
        return mInstance;
    }

    /**
     * This method is responsible for finding the node configuration in
     * ApplicationProperty collection. If it is not found, it will insert it
     * into DB.
     *
     * @param db - The {@link MongoConnectionFactory} object
     * @param numberOfThreads - The number of Configured Threads
     * @param nodeIsDeadInMinutes The cutoff duration in minutes indicating when
     * the cluster nodes are considered dead.
     * @param threadIsDownInMinutes The cutoff duration in minutes indicating
     * when the cluster nodes are considered down but not dead.
     * @return Updated {@link ApplicationProperty} object
     * @throws Exception On error while updating the node configuration in
     * Application Property.
     */
    public ApplicationProperty findOrInsertNodeConfiguration(MongoConnectionFactory db, int numberOfThreads, int nodeIsDeadInMinutes, int threadIsDownInMinutes) throws Exception {
        try (MongoConnection mongoConnection = db.newConnection()) {
            Document querySpec = new Document("$exists", true);
            Document query = new Document("numberOfThreads", querySpec);
            query.append("nodeIsDeadInMinutes", querySpec);
            query.append("threadIsDownInMinutes", querySpec);

            BasicDBObject updateField = new BasicDBObject();
            BasicDBObject updateSpec = new BasicDBObject();
            updateField.put("numberOfThreads", numberOfThreads);
            updateField.put("nodeIsDeadInMinutes", nodeIsDeadInMinutes);
            updateField.put("threadIsDownInMinutes", threadIsDownInMinutes);
            updateSpec.put("$setOnInsert", updateField);
            String id = super.findOrInsert(mongoConnection, query, updateSpec);

            ApplicationProperty appProperty = null;
            if (StringUtils.isNotBlank(id)) {
                appProperty = super.readOne(mongoConnection, id);
            }
            return appProperty;
        }
    }

    /**
     * This method is responsible to update the nodeClusterMinutesActive field
     * in ApplicationProperty collection if it is not already present.
     *
     * @param db - The {@link MongoConnectionFactory} object
     * @param nodeClusterMinutesActive - The default cutoff duration in minutes
     * for which active nodes need to be checked.
     * @return Updated {@link ApplicationProperty} object
     * @throws Exception On error while updating the cluster active in minutes
     * in Application Property.
     */
    public ApplicationProperty findAndUpdateNodeClusterActiveInMinutes(MongoConnectionFactory db, int nodeClusterMinutesActive) throws Exception {
        try (MongoConnection mongoConnection = db.newConnection()) {

            Document query = new Document("nodeClusterActiveInMinutes", null);
            BasicDBObject updateField = new BasicDBObject();
            BasicDBObject updateSpec = new BasicDBObject();
            updateField.put("nodeClusterActiveInMinutes", nodeClusterMinutesActive);
            updateSpec.put("$set", updateField);

            String id = super.findAndModify(mongoConnection, query, updateSpec);

            if (StringUtils.isNotBlank(id)) {
                return super.readOne(mongoConnection, id);
            }
            ApplicationProperty appProperty = this.getApplicationProperty(mongoConnection);
            if (appProperty != null && appProperty.getNodeClusterActiveInMinutes() == null) {
                CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, "Error while updating node configuration for 'nodeClusterMinutesActive'.", null);
            }
            return appProperty;
        }
    }

    /**
     * This method is responsible to update the waitTimeThresholds default values
     * in ApplicationProperty collection if it is not already present.
     *
     * @param mongoConnection      - The {@link MongoConnection} object
     * @param applicationProperty - The {@link ApplicationProperty} object
     * @return Updated {@link ApplicationProperty} object
     * @throws Exception On error while updating the wait time thresholds related fields in Application Property.
     */
    public ApplicationProperty findOrInsertWaitTimeThresholds(MongoConnection mongoConnection, ApplicationProperty applicationProperty) throws Exception {

        Document query = new Document("mediumWaitTimeThreshold", null);
        query.append("largeWaitTimeThreshold", null);
        query.append("normalWaitTimeMessage", null);
        query.append("mediumWaitTimeMessage", null);
        query.append("largeWaitTimeMessage", null);

        BasicDBObject updateField = new BasicDBObject();
        BasicDBObject updateSpec = new BasicDBObject();
        updateField.put("mediumWaitTimeThreshold", applicationProperty.getDefaultMediumWaitTimeThreshold());
        updateField.put("largeWaitTimeThreshold", applicationProperty.getDefaultLargeWaitTimeThreshold());
        updateField.put("normalWaitTimeMessage", applicationProperty.getDefaultNormalWaitTimeMessage());
        updateField.put("mediumWaitTimeMessage", applicationProperty.getDefaultMediumWaitTimeMessage());
        updateField.put("largeWaitTimeMessage", applicationProperty.getDefaultLargeWaitTimeMessage());
        updateSpec.put("$set", updateField);

        String id = super.findAndModify(mongoConnection, query, updateSpec);

        if (StringUtils.isNotBlank(id)) {
            return super.readOne(mongoConnection, id);
        }
        return this.getApplicationProperty(mongoConnection);
    }

    /**
     * This method is responsible to update the componentMappingThreshold default value
     * in ApplicationProperty collection if it is not already present.
     *
     * @param mongoConnection      - The {@link MongoConnection} object
     * @param applicationProperty - The {@link ApplicationProperty} object
     * @return Updated {@link ApplicationProperty} object
     * @throws Exception On error while updating the wait time thresholds related fields in Application Property.
     */
    public ApplicationProperty findOrInsertComponentMappingThreshold(MongoConnection mongoConnection, ApplicationProperty applicationProperty) throws Exception {

        Document query = new Document("componentMappingThreshold", null);

        BasicDBObject updateField = new BasicDBObject();
        BasicDBObject updateSpec = new BasicDBObject();
        updateField.put("componentMappingThreshold", applicationProperty.getDefaultComponentMappingThreshold());
        updateSpec.put("$set", updateField);

        String id = super.findAndModify(mongoConnection, query, updateSpec);

        if (StringUtils.isNotBlank(id)) {
            return super.readOne(mongoConnection, id);
        }
        return this.getApplicationProperty(mongoConnection);
    }

    /**
     * This method is responsible to update the system status limits default values
     * in ApplicationProperty collection if it is not already present.
     *
     * @param mongoConnection     - The {@link MongoConnection} object
     * @return Updated {@link ApplicationProperty} object
     * @throws Exception On error while updating the system status limits in Application Property.
     */
    public ApplicationProperty findOrInsertSystemStatusLimits(MongoConnection mongoConnection) throws Exception {

        Document query = new Document("systemStatusPendingLimit", null);
        query.append("systemStatusCompletedLimit", null);

        BasicDBObject updateField = new BasicDBObject();
        BasicDBObject updateSpec = new BasicDBObject();
        updateField.put("systemStatusPendingLimit", ApplicationProperty.DEFAULT_SYSTEM_STATUS_PENDING_LIMIT);
        updateField.put("systemStatusCompletedLimit", ApplicationProperty.DEFAULT_SYSTEM_STATUS_COMPLETED_LIMIT);
        updateSpec.put("$set", updateField);

        String id = super.findAndModify(mongoConnection, query, updateSpec);

        if (StringUtils.isNotBlank(id)) {
            return super.readOne(mongoConnection, id);
        }
        return this.getApplicationProperty(mongoConnection);
    }

    /**
     * This method is responsible to get {@link ApplicationProperty} by id in
     * the DB.
     *
     * @param mongoConnection The {@link MongoConnection}
     * @param appPropertyId An unique id of applicationProperty record.
     * @return applicationProperty The {@link ApplicationProperty} object
     * @throws java.lang.Exception On error accessing Mongo.
     */
    public ApplicationProperty getApplicationProperty(MongoConnection mongoConnection, String appPropertyId) throws Exception {
        return super.readOne(mongoConnection, appPropertyId);
    }

    /**
     * This method is responsible for reading the ApplicationProperty from the
     * DB.
     *
     * @param mongoConnection - The {@link MongoConnection} object
     * @return The {@link ApplicationProperty} object
     * @throws Exception On error while retrieving the node configuration from
     * the ApplicationProperty collection.
     */
    public ApplicationProperty getApplicationProperty(MongoConnection mongoConnection) throws Exception {
        List<ApplicationProperty> foundApplicationProperty = super.read(mongoConnection, null);
        ApplicationProperty appProperty = null;
        if (foundApplicationProperty != null && foundApplicationProperty.size() > 0) {
            CFBTLogger.logInfo(LOGGER, ApplicationPropertyDAO.class.getCanonicalName(), "Found " + foundApplicationProperty.size() + " documents for ApplicationProperty.");
            appProperty = foundApplicationProperty.get(0);
        }
        return appProperty;
    }

    /**
     * This method is responsible for updating the record in the ApplicationProperty collection.
     *
     * @param mongoConnection - The {@link MongoConnection}
     * @param appPropertyId - AppProperty id
     * @param jsonPatchProcessor The {@link ApplicationPropertyJsonPatchProcessor} object
     * @return Updated {@link ApplicationProperty} object
     * @throws Exception On error while updating the Application Property record.
     */
    public ApplicationProperty updateApplicationProperty(MongoConnection mongoConnection, String appPropertyId, ApplicationPropertyJsonPatchProcessor jsonPatchProcessor) throws Exception {
        ApplicationProperty updatedAppProperty = null;
        Document applicationPropertyUpdate = jsonPatchProcessor.getDocumentToUpdate();
        if (applicationPropertyUpdate != null) {
            UpdateResult updateResult = null;
            Document update = new Document("$set", applicationPropertyUpdate);
            updateResult = super.update(mongoConnection, appPropertyId, update);

            if (updateResult == null) {
                CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR, "Error while updating the application property.", null);
            }
            if (updateResult.getMatchedCount() > 0) {
                CFBTLogger.logInfo(LOGGER, ApplicationPropertyDAO.class.getCanonicalName(),
                        "Application property record updated for application id = " + appPropertyId);
                updatedAppProperty = this.getApplicationProperty(mongoConnection, appPropertyId);
            }
        }
        return updatedAppProperty;
    }
}
