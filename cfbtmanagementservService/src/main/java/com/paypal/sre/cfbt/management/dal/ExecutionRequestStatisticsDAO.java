package com.paypal.sre.cfbt.management.dal;

import java.util.HashMap;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequestStatistics;
import com.paypal.sre.cfbt.data.execapi.ReleaseRequest;
import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.CFBTLogger;

/**
 * This class is the singleton concrete implementation of the AbstractDAO class that handles
 * {@link ExecutionRequestStatistics} data operations in the MongoDB "ExecutionRequestStatistics" collection.
 * 
 */
public class ExecutionRequestStatisticsDAO extends AbstractDAO<ExecutionRequestStatistics> {    
    private final Logger LOGGER = LoggerFactory.getLogger(ExecutionRequestStatisticsDAO.class);
    private static ExecutionRequestStatisticsDAO INSTANCE = null;
    private MongoConnectionFactory db;

    /**
     * Accessor for singleton instance of this object.
     * 
     * @param db {@link MongoConnectionFactory}
     * @return the instance.
     */
    public static ExecutionRequestStatisticsDAO getInstance(MongoConnectionFactory db) {
        if (INSTANCE == null) {
            INSTANCE = new ExecutionRequestStatisticsDAO("ExecutionRequestStatistics", db);
        }
        return INSTANCE;
    }

    /**
     * Constructor of singleton instance for this object.
     * 
     * @param aCollectionName
     *            the name of the collection
     */
    public ExecutionRequestStatisticsDAO(String aCollectionName, MongoConnectionFactory db) {
        super(aCollectionName, ExecutionRequestStatistics.class);

        this.db = db;
    }

    public HashMap<String,ExecutionRequestStatistics> getCurrentStatistics() {
        HashMap<String,ExecutionRequestStatistics> stats = new HashMap<>();
        try (MongoConnection c = db.newConnection()) {
            Document sort = new Document("dateTime", -1);
            Document findBy;
            for (ReleaseRequest.ReleaseVehicle rv : ReleaseRequest.ReleaseVehicle.values()) {
                findBy = new Document("releaseVehicle", rv.toString());
                List<ExecutionRequestStatistics> stat = read(c, findBy, null, sort, 1);
                if (stat.size() > 0) {
                    stats.put(rv.toString(), stat.get(0));
                }
            }
            findBy = new Document("releaseVehicle", ExecutionRequestStatistics.ALL_STATS);
            List<ExecutionRequestStatistics> stat = read(c, findBy, null, sort, 1);
            if (stat.size() > 0) {
                stats.put(ExecutionRequestStatistics.ALL_STATS, stat.get(0));
            }
        } catch(Exception e) {
            CFBTLogger.logError(LOGGER, ExecutionRequestStatisticsDAO.class.getCanonicalName(), "Error trying to read the 90th Percentile Duration for test execution. ", e);
        }
        return stats;
    }

    /**
     * @param statistics
     *            - the {@link ExecutionRequestStatistics} to be inserted
     * @return the inserted {@link ExecutionRequestStatistics}
     * @throws Exception
     */
    public String insert(ExecutionRequestStatistics statistics) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            return super.insert(c, statistics);
        }
    }

}
