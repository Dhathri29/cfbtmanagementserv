package com.paypal.test.logging;

import com.paypal.test.jaws.logging.SimpleLogger;
import com.paypal.test.jaws.logging.SimpleLoggerSettings;

/**
 * This class is responsible to handle logging of the events.
 **/
public final class AppLogger {

    private AppLogger() {
        // defeat all instantiation
    }

    private static SimpleLogger appBaseLogger = null;

    /**
     * This logger allows applications to have independently configured user and developer event level logging. In
     * addition, it can be configured to echo either developer or user level logs to stderr.
     *
     */

    public static synchronized SimpleLogger getLogger() {
        if (appBaseLogger == null) {
            appBaseLogger = SimpleLogger.getLogger(getDefaultLoggerSettings());
        }
        return appBaseLogger;
    }

    /**
     * This class represents the collections of settings which are consumed by SimpleLogger. Use this class to configure
     * your customized logger.
     * 
     */

    private static SimpleLoggerSettings getDefaultLoggerSettings() {
        SimpleLoggerSettings settings = new SimpleLoggerSettings();
        return settings;
    }
}
