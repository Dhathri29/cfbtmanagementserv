/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.lock;

import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;

/**
 * Provide a distributed lock that leverages a table to manage distributed coordination.
 */
public class DBLock {
    private LockDAO dao = null;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(DBLock.class);
    private final String name;
    ExecutorService executerService = Executors.newCachedThreadPool();
    private final int lockHoldTime;

    /**
     * The public constructor, identifying the lock's name.
     * @param db {@link DatabaseConfig} access to the db.
     * @param name The name of the lock.
     * @throws java.lang.Exception
     */
    public DBLock(DatabaseConfig db, String name) throws Exception {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Name cannot be null");
        }
        this.name = name;           

        dao = LockDAO.getInstance(db.getConnectionFactory());
        dao.insertIfNotFound(name);

        lockHoldTime = ConfigManager.getConfiguration().getInt("cfbtmanagementserv.dblock.lockHoldTime", 5);
    }

    /**
     * An intent to obtain a lock which will be granted asynchronously.
     * @param timeout Automatically release the lock after this amount of time has elapsed.
     * @param callback
     * @throws java.lang.Exception
     */
    public void lockAsync(int timeout, Consumer<LockData> callback) throws Exception {
        executerService.submit(() -> {
            LockData data = getLock(timeout, dao);
            callback.accept(data);
        });         
    }

    /**
     * An intent to obtain a lock which will be granted asynchronously.
     * @param timeout Automatically release the lock after this amount of time has elapsed.
     * @return {@link LockData}
     * @throws java.lang.Exception
     */
    public LockData lock(int timeout) throws Exception {
        return getLock(timeout, dao);
    }
    
    /**
     * Release the lock when complete allowing others access.
     * @param key Identify the lock to be released with the UUID generated when the lock was granted.
     * @throws java.lang.Exception
     */
    public void unlock(String key) throws Exception {
        dao.unlock(key);
    }

    /**
     * Internal fucntion to obtain the lock.
     * @param timeout Automatically release the lock after this amount of time has elapsed.
     * @param dao Access to the db.
     * @return {@link LockData}
     */
    private LockData getLock(int timeout, LockDAO dao) {
        DateTime timeoutTime = DateUtil.currentDateTimeUTC().plusSeconds(timeout);

        while (DateUtil.currentDateTimeUTC().isBefore(timeoutTime)) {            
            try {
                DateTime lockHoldDate = DateUtil.currentDateTimeUTC().plusSeconds(lockHoldTime);

                boolean lockObtained = dao.lock(name, lockHoldDate);
                if (lockObtained) {
                    LockData data = dao.read(name);
                    return data;
                }

                Thread.sleep(500);
            } catch (Exception ex) {
                CFBTLogger.logError(logger, DBLock.class.getName(), ex.getMessage(), ex);
            }
        }

        CFBTLogger.logError(logger, DBLock.class.getName(), "Unable to get the lock with timeout = " + timeout);
        throw new IllegalStateException("Unable to get the lock with timeout = " + timeout); 
    }
}
