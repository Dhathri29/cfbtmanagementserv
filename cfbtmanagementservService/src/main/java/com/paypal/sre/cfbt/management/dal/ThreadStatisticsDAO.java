/*
 * (C) 2016 PayPal, Internal software, do not distribute.
 */

package com.paypal.sre.cfbt.management.dal;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.management.dal.ThreadStatistics.ThreadStatistic;

/**
 * This class is the singleton concrete implementation of the AbstractDAO
 * class that handles {@link ExecutionRequest} data operations in the MongoDB 
 * "ThreadStatistic" collection.
 *
 */
public class ThreadStatisticsDAO extends AbstractDAO<ThreadStatistic> {
    
    private static final ThreadStatisticsDAO mInstance = new ThreadStatisticsDAO("ThreadStatistics");
    
    /**
     * Constructor of singleton instance for this object.
     * 
     * @param aCollectionName the name of the collection
     */
    public ThreadStatisticsDAO(String aCollectionName) {
        super(aCollectionName,ThreadStatistic.class);
    }
    
    /**
     * Accessor for singleton instance of this object.
     * 
     * @return the instance.
     */
    public static ThreadStatisticsDAO getInstance() {
        return mInstance;
    }

}
