package com.paypal.sre.cfbt.management.kafka;

/**
 * Enum representing the cfbt Kafka topics
 */
public enum Topic {
    Test_Execution_Events("cfbtmanagementserv.kafka.topics.test-execution", "cfbt.cfbtmanagementserv.test-execution"),
    Executor_Control_Events("cfbtmanagementserv.kafka.topics.executor-control",
            "cfbt.cfbtmanagementserv.executor-control-events"),
    Executor_Thread_Control_Events("cfbtmanagementserv.kafka.topics.executor-thread-control",
            "cfbt.cfbtmanagementserv.executor-thread-control-events"),
    Feature_Control_Events("cfbtmanagementserv.kafka.topics.feature-control",
            "cfbt.cfbtmanagementserv.feature-control-events"),
    Datacenter_Proxy_Control_Events("cfbtmanagementserv.kafka.topics.datacenter-proxy-control",
            "cfbt.cfbtmanagementserv.datacenter-proxy-control-events");

    private String name;
    private String property;

    private Topic(String property, String name) {
        this.property = property;
        this.name = name;
    }

    /**
     * @return the name of the topic.
     */
    public String getName() {
        return name;
    }

    /**
     * @return the property name for the topic.
     */
    public String getProperty() {
        return property;
    }
}
