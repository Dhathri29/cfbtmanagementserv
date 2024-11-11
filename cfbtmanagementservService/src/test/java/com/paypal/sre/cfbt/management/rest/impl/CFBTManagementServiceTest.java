package com.paypal.sre.cfbt.management.rest.impl;

import static org.mockito.Mockito.mock;

import javax.ws.rs.core.Response;

import org.apache.commons.configuration.Configuration;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.paypal.sre.cfbt.management.CFBTTestResourceClient;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.rest.api.CFBTMANAGEMENTAPI;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFakeFactory;

/**
 * Unit Test for {@link CFBTManagementService}.
 */

public class CFBTManagementServiceTest {

    private static MongoConnectionFakeFactory mDB;
    private final Configuration mockConfig = mock(Configuration.class);
    private final DatabaseConfig mockDbConfig = mock(DatabaseConfig.class);
    private final CFBTTestResourceClient mockCfbtTestResourceClient = mock(CFBTTestResourceClient.class);

    @BeforeClass
    public void init() throws Exception {
        mDB = new MongoConnectionFakeFactory("", "", "FakeDatabase", "", "");
        mDB.setMock(true);
        try (MongoConnection c = mDB.newConnection()) {
            // TODO: Add fake data
        }
    }

    @Test(enabled = true)
    public void testStatus() {
        CFBTMANAGEMENTAPI cfbtService = new CFBTManagementService(mockConfig, mockDbConfig, mockCfbtTestResourceClient);
        Response result = cfbtService.status();
        Assert.assertEquals(result.getEntity(), "Hello CFBT User! I am ready to serve you.");
    }

}
