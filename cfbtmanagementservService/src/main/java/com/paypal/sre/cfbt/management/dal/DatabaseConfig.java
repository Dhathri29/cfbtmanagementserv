package com.paypal.sre.cfbt.management.dal;

import java.net.UnknownHostException;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import com.ebayinc.platform.security.SecretProvider;
import com.google.common.base.Strings;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import org.bson.Document;

/**
 * This Repository class contains the configuration for database execution. To be extended in the future to allow
 * abstraction for multiple DB types.
 */
@Repository
@Scope("singleton")
public class DatabaseConfig {

    protected static final String MONGO_PROTECTED_PASSWORD_KEY = "mongo.protected_password_key";
    protected static final String MONGO_ENDPOINT = "mongo.endpoint";
    protected static final String MONGO_DB_NAME = "mongo.dbName";
    protected String mongoTopoKey;
    protected static final int ASCENDING = 1;
    protected static final int DESCENDING = -1;

    public final static String SERVER_IPPORT_DELIMITER = "\\^";

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConfig.class);
    private MongoConnectionFactory mDB;

    @Inject
    private Configuration mConfig;

    @Inject
    SecretProvider secretProvider;

    @PostConstruct
    public void init() throws UnknownHostException, Exception {

        // XXX Only use on stage2!!!
        // On live, the password must come from the
        // protected.
        String databasePassword = mConfig.getString("databasePassword");
        if (databasePassword == null) {
            String protectedPasswordKey = getAndVerify(MONGO_PROTECTED_PASSWORD_KEY, "encrypted_cfbt_mongo_password");
            try {

                databasePassword = new String(secretProvider.getSecret(protectedPasswordKey), "ISO_8859_1");

            } catch (Exception e) {
                CFBTLogger.logError(CFBTLogger.CalEventEnum.MONGO_CONFIG,
                        "Unable to fetch protected password for mongo", e);
            }
        }

        String databaseHost = getAndVerify("databaseHost", "1:cfbt-dev-mongo-7285.ccg21.dev.paypalcorp.com:27017");
        String databaseName = getAndVerify("databaseName", "cfbtDev");
        String databaseUsername = getAndVerify("databaseUsername", "cfbt-dev");

        CFBTLogger.logInfo(LOG, CFBTLogger.CalEventEnum.MONGO_CONFIG,
                "host: " + databaseHost + " name: " + databaseName + " user: " + databaseUsername);

        mDB = new MongoConnectionFactory(databaseHost, databaseName, databaseUsername, databasePassword);
        try (MongoConnection c = mDB.newConnection()) {
            c.getDB();
        } catch (Exception e) {
            CFBTLogger.logInfo(LOG, CFBTLogger.CalEventEnum.SYSTEMCONFIG,
                    "CFBTPKGMAN INIT: Unable to connect with Mongo " + e.getMessage());
        }
	try (MongoConnection c = mDB.newConnection()) {
            addExecutionRequestIndex(c);
	} catch (Exception e) {
	    CFBTLogger.logInfo(LOG, CFBTLogger.CalEventEnum.SYSTEMCONFIG,
		    "CFBT INIT: Unable to add indices to collections " + e.getMessage());
	}
    }

    /**
     * Returns the MongoConnectionFactory for this configuration
     *
     * @return MongoConnectionFactory
     */
    public MongoConnectionFactory getConnectionFactory() {
        return mDB;
    }

    /**
     * @param key
     *            the configuration key to load
     * @param defaultValue
     *            default value returned if key not found or error
     * @return the read value or default value
     */
    protected String getAndVerify(String key, String defaultValue) {
        String value = mConfig.getString(key);
        if (Strings.isNullOrEmpty(value)) {
            value = defaultValue;
            CFBTLogger.logWarn(LOG, CFBTLogger.CalEventEnum.MONGO_CONFIG,
                    key + " key does not exist. Using default " + defaultValue);
        }
        return value;

    }

    private void addExecutionRequestIndex(MongoConnection c) {
        MongoDatabase db = c.getDB();

        {
            Document statusIndex = new Document("status", ASCENDING);
            IndexOptions indexOptions = new IndexOptions().background(true).name("status_1_background_");
            db.getCollection("ExecutionRequest").createIndex(statusIndex, indexOptions);
        }
        {
            Document statusIndex = new Document("queueName", ASCENDING);
            IndexOptions indexOptions = new IndexOptions().background(true).name("queueName_1_background_");
            db.getCollection("ExecutionRequest").createIndex(statusIndex, indexOptions);
        }
        {
            Document executionRequestIndex = new Document("completionTime", DESCENDING);
            IndexOptions indexOptions = new IndexOptions().background(true).name("completionTime_-1_background_");
            db.getCollection("ExecutionRequest").createIndex(executionRequestIndex, indexOptions);
        }
    }
}
