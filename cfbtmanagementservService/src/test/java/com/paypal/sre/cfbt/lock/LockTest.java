/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.lock;

import com.paypal.sre.cfbt.management.CFBTTestResourceClient;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.request.DatabaseConfigFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.configuration.Configuration;
import org.mockito.Matchers;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

/**
 * Unit tests for the lock.
 */
public class LockTest {

    @BeforeSuite
    public void init() {
        CFBTTestResourceClient testresourceservClient = mock(CFBTTestResourceClient.class);
        Configuration config = mock(Configuration.class);
        when(config.getInt(Matchers.anyString(), Matchers.anyInt())).thenReturn(5);
        ConfigManager.setConfiguration(config, null, testresourceservClient);
    }
    
    @Test
    public void basicLockTest() {
        DatabaseConfigFactory dbFactory = new DatabaseConfigFactory();
        DatabaseConfig db = dbFactory.databaseConfig("lock1");

        try {
            DBLock lock = new DBLock(db, "test1");

            lock.lockAsync(5, (data) -> {
                try {                
                    lock.unlock(data.getLockKey());
                } catch (Exception ex) {
                    Assert.fail(ex.getMessage());
                }
            });
            
        } catch (Exception ex) {
            Assert.fail(ex.getMessage());
        }
    }

    @Test
    public void twoLockTest() {
        DatabaseConfigFactory dbFactory = new DatabaseConfigFactory();
        DatabaseConfig db = dbFactory.databaseConfig("lock2");

        try {
            DBLock lock = new DBLock(db, "test2");
            lock.lockAsync(10, (data) -> {
                try {
                    lock.unlock(data.getLockKey());
                } catch (Exception ex) {
                    Assert.fail(ex.getMessage());
                }
            });

            lock.lockAsync(10, (data) -> {
                try {
                    lock.unlock(data.getLockKey());
                } catch (Exception ex) {
                    Assert.fail(ex.getMessage());
                }
            });       
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.getMessage());
        }
    }

    @Test
    public void lockTimeoutFailure() {
        DatabaseConfigFactory dbFactory = new DatabaseConfigFactory();
        DatabaseConfig db = dbFactory.databaseConfig("lock3");

        try {
            DBLock lock = new DBLock(db, "test3");

            lock.lockAsync(5, (data) -> {
            
            });
            lock.lockAsync(5, (data) -> {
                Assert.fail("Should have timed out already");
            });

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void unlockFailure() {
        DatabaseConfigFactory dbFactory = new DatabaseConfigFactory();
        DatabaseConfig db = dbFactory.databaseConfig("lock4");

        try {
            DBLock lock = new DBLock(db, "test");
            lock.lockAsync(5, (data) -> {
                try {                
                    lock.unlock("random");
                } catch (Exception ex) {
                    Logger.getLogger(LockTest.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
            lock.lockAsync(5, (data) -> {
                try {               
                    Assert.fail("Should fail because we're trying to get a lock that we cannot get.");
                } catch (Exception ex) {
                    Logger.getLogger(LockTest.class.getName()).log(Level.SEVERE, null, ex);
                }
            });           

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void nullTest() {
        DatabaseConfigFactory dbFactory = new DatabaseConfigFactory();
        DatabaseConfig db = dbFactory.databaseConfig("lock5");

        try {
            DBLock lock = new DBLock(db, null);
            Assert.fail("Should have thrown an exception");
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            DBLock lock = new DBLock(db, "testNull");
            lock.lockAsync(5, (data) -> {
                try {
                    lock.unlock(null);
                    Assert.fail("Should have thrown an exception");

                } catch (Exception ex) {
                    Logger.getLogger(LockTest.class.getName()).log(Level.SEVERE, null, ex);
                }               
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
