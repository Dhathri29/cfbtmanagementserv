package com.paypal.sre.cfbt.management.rest.impl;

import org.apache.commons.configuration.Configuration;

import com.paypal.sre.cfbt.management.CFBTTestResourceClient;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;

/**
 * Store the configuration in a static location so others can easily get it later.
 */
public class ConfigManager {

    private static Configuration CONFIG;
    private static DatabaseConfig DBCONFIG;
    private static CFBTTestResourceClient TEST_RESOURCE_SERV_CLIENT;

    /**
     * Return the getConfiguration of the Configuration.
     * 
     * @return Configuration getConfiguration.
     */
    public static Configuration getConfiguration() {
        return CONFIG;
    }

    /**
     * .
     * 
     * @return The {@link DatabaseConfig} object
     */
    public static DatabaseConfig getDatabaseConfig() {
        return DBCONFIG;
    }
    
    /**
     * @return the {@link CFBTTestResourceClient}
     */
    public static CFBTTestResourceClient getTestResourceServClient() {
        return TEST_RESOURCE_SERV_CLIENT;
    }

    /**
     * Somebody early on in the initialization needs to set the configuration
     * 
     * @param config   {@link Configuration}
     * @param dbConfig {@link DatabaseConfig}
     * @param testresourceservClient {@link CFBTTestResourceClient}
     */
    public static void setConfiguration(Configuration config, DatabaseConfig dbConfig,
            CFBTTestResourceClient testresourceservClient) {
        CONFIG = config;
        DBCONFIG = dbConfig;
        TEST_RESOURCE_SERV_CLIENT = testresourceservClient;
    }

}
