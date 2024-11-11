/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.data.execapi;

/**
 * This is API schema for 'ApplicationProperty' data.
 */
public class ApplicationProperties {
    private String id = null;
    private Integer numberOfThreads = null;
    private Integer nodeIsDeadInMinutes = null;
    private Integer threadIsDownInMinutes = null;
    private Integer nodeClusterActiveInMinutes = null;
    private Integer mediumWaitTimeThreshold = null;
    private Integer largeWaitTimeThreshold = null;
    private String normalWaitTimeMessage = null;
    private String mediumWaitTimeMessage = null;
    private String largeWaitTimeMessage = null;
    private String exemptComponents = null;
    private String allTestsComponents = null;
    private Integer componentMappingThreshold = null;
    private Integer systemStatusPendingLimit = null;
    private Integer systemStatusCompletedLimit = null;

    /**
     * @return The unique id for record.
     */
    public String getId() {
        return id;
    }

    /**
     * @param id The unique id for record.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return The number of configured threads for the cluster nodes.
     */
    public Integer getNumberOfThreads() {
        return numberOfThreads;
    }

    /**
     * @param numberOfThreads The number of configured threads for the
     *                        cluster nodes.
     */
    public void setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
    }

    /**
     * @return The cutoff duration in minutes indicating when the cluster nodes
     * are considered dead.
     */
    public Integer getNodeIsDeadInMinutes() {
        return nodeIsDeadInMinutes;
    }

    /**
     * @param nodeIsDeadInMinutes The cutoff duration in minutes indicating when
     *                            the cluster nodes are considered dead.
     */
    public void setNodeIsDeadInMinutes(int nodeIsDeadInMinutes) {
        this.nodeIsDeadInMinutes = nodeIsDeadInMinutes;
    }

    /**
     * @return The cutoff duration in minutes indicating when the cluster nodes
     * are considered down but not dead.
     */
    public Integer getThreadIsDownInMinutes() {
        return threadIsDownInMinutes;
    }

    /**
     * @param threadIsDownInMinutes The cutoff duration in minutes indicating
     *                              when the cluster nodes are considered down but not dead.
     */
    public void setThreadIsDownInMinutes(int threadIsDownInMinutes) {
        this.threadIsDownInMinutes = threadIsDownInMinutes;
    }

    /**
     * @return The default cutoff duration in minutes for which active nodes need to be checked.
     */
    public Integer getNodeClusterActiveInMinutes() {
        return nodeClusterActiveInMinutes;
    }

    /**
     * @param nodeClusterActiveInMinutes The default cutoff duration in
     *                                   minutes for which active nodes need to be checked.
     */
    public void setNodeClusterActiveInMinutes(Integer nodeClusterActiveInMinutes) {
        this.nodeClusterActiveInMinutes = nodeClusterActiveInMinutes;
    }

    /**
     * @return The Medium Wait Time Threshold limit.
     */
    public Integer getMediumWaitTimeThreshold() {
        return mediumWaitTimeThreshold;
    }

    /**
     * @param mediumWaitTimeThreshold The Medium Wait Time Threshold limit.
     */
    public void setMediumWaitTimeThreshold(Integer mediumWaitTimeThreshold) {
        this.mediumWaitTimeThreshold = mediumWaitTimeThreshold;
    }

    /**
     * @return The Large Wait Time Threshold limit.
     */
    public Integer getLargeWaitTimeThreshold() {
        return largeWaitTimeThreshold;
    }

    /**
     * @param largeWaitTimeThreshold The Large Wait Time Threshold limit.
     */
    public void setLargeWaitTimeThreshold(Integer largeWaitTimeThreshold) {
        this.largeWaitTimeThreshold = largeWaitTimeThreshold;
    }

    /**
     * @return The general message for Normal Wait Time thresholds.
     */
    public String getNormalWaitTimeMessage() {
        return normalWaitTimeMessage;
    }

    /**
     * @param normalWaitTimeMessage The general message for Normal Wait Time thresholds.
     */
    public void setNormalWaitTimeMessage(String normalWaitTimeMessage) {
        this.normalWaitTimeMessage = normalWaitTimeMessage;
    }

    /**
     * @return The general message for Medium Wait Time thresholds.
     */
    public String getMediumWaitTimeMessage() {
        return mediumWaitTimeMessage;
    }

    /**
     * @param mediumWaitTimeMessage The general message for Medium Wait Time thresholds.
     */
    public void setMediumWaitTimeMessage(String mediumWaitTimeMessage) {
        this.mediumWaitTimeMessage = mediumWaitTimeMessage;
    }

    /**
     * @return The general message for Large Wait Time thresholds.
     */
    public String getLargeWaitTimeMessage() {
        return largeWaitTimeMessage;
    }

    /**
     * @param largeWaitTimeMessage The general message for Large Wait Time thresholds.
     */
    public void setLargeWaitTimeMessage(String largeWaitTimeMessage) {
        this.largeWaitTimeMessage = largeWaitTimeMessage;
    }

    /**
     * @return A comma seperated list of components that should be exempt from testing.
     */
    public String getExemptComponents() {
        return exemptComponents;
    }

    /**
     * @param exemptComponents A comma seperated list of components that should be exempt from testing.
     */
    public void setExemptComponents(String exemptComponents) {
        this.exemptComponents = exemptComponents;
    }

    /**
     * @return A comma seperated list of components that should have all tests mapped to them.
     */
    public String getAllTestsComponents() {
        return allTestsComponents;
    }

    /**
     * @param allTestsComponents A comma seperated list of components that should have all tests mapped to them.
     */
    public void setAllTestsComponents(String allTestsComponents) {
        this.allTestsComponents = allTestsComponents;
    }

    /**
     * @return The number of test executions in which a component is not exercised, at which point the component mapping is removed.
     */
    public Integer getComponentMappingThreshold() {
        return componentMappingThreshold;
    }

    /**
     * @param componentMappingThreshold The number of test executions in which a component is not exercised, at which point the component mapping is removed.
     */
    public void setComponentMappingThreshold(int componentMappingThreshold) {
        this.componentMappingThreshold = componentMappingThreshold;
    }

    /**
     * @return The maximum number of pending execution requests that the System Status page will display.
     */
    public Integer getSystemStatusPendingLimit() {
        return systemStatusPendingLimit;
    }

    /**
     * @param systemStatusPendingLimit The maximum number of pending execution requests that the System Status page will display.
     */
    public void setSystemStatusPendingLimit(int systemStatusPendingLimit) {
        this.systemStatusPendingLimit = systemStatusPendingLimit;
    }

    /**
     * @return The maximum number of completed execution requests that the System Status page will display.
     */
    public Integer getSystemStatusCompletedLimit() {
        return systemStatusCompletedLimit;
    }

    /**
     * @param systemStatusCompletedLimit The maximum number of completed execution requests that the System Status page will display.
     */
    public void setSystemStatusCompletedLimit(int systemStatusCompletedLimit) {
        this.systemStatusCompletedLimit = systemStatusCompletedLimit;
    }

}
