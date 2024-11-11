package com.paypal.sre.cfbt.management.timeout;

import java.util.HashMap;
import java.util.Map;

public enum Timeout {
    DEPLOYMENT_TIMEOUT("deployment_timeout"),
    TEST_EXECUTION_REQUEST_TIMEOUT("test_execution_request_timeout"),
    REQUEST_COMPLETION_TIMEOUT("request_completion_timeout"),
    STUCK_TEST_TIMEOUT("stuck_test_timeout");

    private final String value;

    private static Map<String, Timeout> constants = new HashMap<String, Timeout>();

    static {
        for (Timeout c : Timeout.values()) {
            constants.put(c.value, c);
        }
    }

    private Timeout(String value) {
        this.value = value;
    }

    public String toString() {
        return this.value;
    }
    
    public static Timeout fromValue(String value) {
        Timeout constant = constants.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(
                    "'" + value + "' is not a valid type. Allowed values : " + constants.keySet());
        } else {
            return constant;
        }
    }
}
