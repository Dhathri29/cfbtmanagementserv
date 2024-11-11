/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.management.appproperty;

import com.ebayinc.platform.services.jsonpatch.JsonPatch;
import com.paypal.sre.cfbt.data.execapi.NodeConfiguration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * This class is responsible to hold default ApplicationProperty data to be
 * stored in the DB.
 *
 */
public class ApplicationProperty {

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

    public static int DEFAULT_SYSTEM_STATUS_PENDING_LIMIT = 50;
    public static int DEFAULT_SYSTEM_STATUS_COMPLETED_LIMIT = 50;

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
     *
     * @param numberOfThreads The number of configured threads for the
     * cluster nodes.
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
     *
     * @param nodeIsDeadInMinutes The cutoff duration in minutes indicating when
     * the cluster nodes are considered dead.
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
     *
     * @param threadIsDownInMinutes The cutoff duration in minutes indicating
     * when the cluster nodes are considered down but not dead.
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
     *
     * @param nodeClusterActiveInMinutes The default cutoff duration in
     * minutes for which active nodes need to be checked.
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
     * @return The default medium wait time threshold
     */
    public int getDefaultMediumWaitTimeThreshold() {
        return 60;
    }

    /**
     * @return The default large wait time threshold
     */
    public int getDefaultLargeWaitTimeThreshold() {
        return 120;
    }

    /**
     * @return The default general message for Normal Wait Time threshold
     */
    public String getDefaultNormalWaitTimeMessage() {
        return "Deployments are proceeding normally. Expect a normal wait.";
    }

    /**
     * @return The default general message for Medium Wait Time threshold
     */
    public String getDefaultMediumWaitTimeMessage() {
        return "Expect a longer than normal wait.";

    }

    /**
     * @return The default general message for Large Wait Time threshold
     */
    public String getDefaultLargeWaitTimeMessage() {
        return "Expect a long wait.";
    }

    /**
     * @return The default component mapping threshold
     */
    public Integer getDefaultComponentMappingThreshold() {
        return 25;
    }

    /**
     * @return True if wait time thresholds related fields are not present.
     */
    public boolean noWaitTimeThresholds() {
        if (this.getMediumWaitTimeThreshold() == null & this.getLargeWaitTimeThreshold() == null
                && this.getNormalWaitTimeMessage() == null && this.getMediumWaitTimeMessage() == null &&
                this.getLargeWaitTimeMessage() == null) {
            return true;
        }
        return false;
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

    /**
     * @return True if system status limits are not present.
     */
    public boolean noSystemStatusLimits() {
        if (this.getSystemStatusPendingLimit() == null && this.getSystemStatusCompletedLimit() == null) {
            return true;
        }
        return false;
    }

    /**
     * @return The {@link NodeConfiguration} object
     */
    public NodeConfiguration nodeConfiguration() {
        NodeConfiguration nodeConfiguration = new NodeConfiguration();
        nodeConfiguration.setNodeIsDeadInMinutes(this.nodeIsDeadInMinutes);
        nodeConfiguration.setNumberOfThreads(this.numberOfThreads);
        nodeConfiguration.setThreadIsDownInMinutes(this.threadIsDownInMinutes);
        nodeConfiguration.setNodeClusterActiveInMinutes(this.nodeClusterActiveInMinutes);
        return nodeConfiguration;
    }

    /**
     * @return The map containing field and its associated description.
     */
    public HashMap<String, String> fieldDescription() {
        HashMap<String, String> fieldDescription = new HashMap<>();
        fieldDescription.put("numberOfThreads", "default number of Threads for the cluster nodes");
        fieldDescription.put("nodeIsDeadInMinutes", "default value of nodeIsDeadInMinutes for the cluster nodes");
        fieldDescription.put("threadIsDownInMinutes", "default value of threadIsDownInMinutes for the cluster nodes");
        fieldDescription.put("nodeClusterActiveInMinutes", "default value of nodeClusterActiveInMinutes for the cluster nodes");
        fieldDescription.put("mediumWaitTimeThreshold", "the lower limit for Medium Wait Time threshold");
        fieldDescription.put("largeWaitTimeThreshold", "the lower limit for Large Wait Time threshold");
        fieldDescription.put("mediumWaitTimeMessage", "the general message for Medium Wait Time threshold");
        fieldDescription.put("normalWaitTimeMessage", "the general message for Normal Wait Time threshold");
        fieldDescription.put("largeWaitTimeMessage", "the general message for Large Wait Time threshold");
        fieldDescription.put("exemptComponents", "list of components that should be exempt from testing");
        fieldDescription.put("allTestsComponents", "list of components that should have all tests mapped to them");
        fieldDescription.put("componentMappingThreshold", "number of test executions in which a component is not exercised, at which point the component mapping is removed.");
        fieldDescription.put("systemStatusPendingLimit", "maximum number of pending execution requests to display on the system status");
        fieldDescription.put("systemStatusCompletedLimit", "maximum number of completed execution requests to display on the system status");
        return fieldDescription;
    }

    /**
     * @return The map containing field and its associated allowed patch
     * operations.
     */
    public HashMap<String, List<JsonPatch.Op>> allowedFieldUpdateMap() {
        HashMap<String, List<JsonPatch.Op>> allowedFieldUpdateMap = new HashMap<>();
        allowedFieldUpdateMap.put("numberOfThreads", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("nodeIsDeadInMinutes", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("threadIsDownInMinutes", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("nodeClusterActiveInMinutes", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("mediumWaitTimeThreshold", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("largeWaitTimeThreshold", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("mediumWaitTimeMessage", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("normalWaitTimeMessage", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("largeWaitTimeMessage", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("exemptComponents", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("allTestsComponents", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("componentMappingThreshold", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("systemStatusPendingLimit", Arrays.asList(JsonPatch.Op.REPLACE));
        allowedFieldUpdateMap.put("systemStatusCompletedLimit", Arrays.asList(JsonPatch.Op.REPLACE));
        return allowedFieldUpdateMap;
    }
}
