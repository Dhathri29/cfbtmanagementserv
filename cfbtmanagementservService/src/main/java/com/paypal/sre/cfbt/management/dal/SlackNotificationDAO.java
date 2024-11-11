/*
 * CFBT
 */
package com.paypal.sre.cfbt.management.dal;

import java.net.UnknownHostException;

import org.bson.Document;

import com.paypal.sre.cfbt.data.notification.SlackNotification;
import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;

/**
 * This class is the singleton concrete implementation of the AbstractDAO
 * class that handles {@link SlackNotification} data operations in the MongoDB 
 * "SlackNotification" collection.
 */
public class SlackNotificationDAO extends AbstractDAO<SlackNotification> {
    
    private static final SlackNotificationDAO mInstance = new SlackNotificationDAO("SlackNotification");
    private static MongoConnectionFactory db;

    /**
     * Constructor of singleton instance for this object.
     * 
     * @param aCollectionName the name of the collection
     */
    public SlackNotificationDAO(String aCollectionName) {
        super(aCollectionName,SlackNotification.class);
    }

    /**
     * Accessor for singleton instance of this object.
     * 
     * @return the instance.
     */
    public static SlackNotificationDAO getInstance(MongoConnectionFactory dbConnectionFactory) {
        db = dbConnectionFactory;
        return mInstance;
    }
    
    /**
     * Get the current Slack Notification Configuration, if not, insert a new record with default values.
     * @return SlackNotification
     * @throws Exception Indicates a general exception.
     */
    public SlackNotification getSlackNotification() throws Exception {
        SlackNotification slackNotification = null;
        try (MongoConnection c = db.newConnection()) {
            slackNotification = super.readOne(c, new Document("name", "SLACK_NOTIFICATION_CONFIG"));
            if (slackNotification == null) { // insert default record
                slackNotification = new SlackNotification();
                slackNotification.setChannelAllowed(true);
                slackNotification.setUserAllowed(true);
                super.insert(c, slackNotification);
            }
            return slackNotification;
        }
    }  
    
    /**
     * This method is responsible for updating the SlackNotification Configuration.
     * @param slackNotification - modified SlackNotification Configuration
     * @return SlackNotification object
     * @throws Exception Exceptions could be thrown when updating Mongo. 
     */
    public SlackNotification updateSlackNotification(SlackNotification slackNotification) throws Exception {
        SlackNotification slackNotificationFromDb = null;
        try (MongoConnection c = db.newConnection()) {
            slackNotificationFromDb = super.readOne(c, new Document("name", "SLACK_NOTIFICATION_CONFIG"));
            if (slackNotificationFromDb == null) {
                slackNotificationFromDb = slackNotification;
                super.insert(c, slackNotification);
            } else {
                this.update(slackNotification);
            }
        }
        return slackNotification;
    }
    
    /**
     * Updates the SlackNotification collection
     * @param slackNotification
     * @throws Exception 
     * @throws UnknownHostException 
     */
    public void update(SlackNotification slackNotification) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            Document updateDocument = new Document("channelAllowed", slackNotification.getChannelAllowed());
            updateDocument.append("userAllowed", slackNotification.getUserAllowed());
            updateDocument.append("redactedUsersForNotifications",
                    slackNotification.getRedactedUsersForNotifications());
            updateDocument.append("notifyAutoPromotedManifestUsers",
                    slackNotification.getNotifyAutoPromotedManifestUsers());
            Document update = new Document("$set", updateDocument);

            String configName = slackNotification.getName().isEmpty() ? "SLACK_NOTIFICATION_CONFIG"
                    : slackNotification.getName();

            Document findQuery = new Document("name", configName);
            super.findAndModify(c, findQuery, update);
        }
    }
}
