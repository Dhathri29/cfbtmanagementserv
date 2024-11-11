/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.data.execapi;

/**
 * This class is responsible to hold default node configuration for the cluster
 * nodes.
 *
 */
public class NodeConfiguration {

    private Integer numberOfThreads = null;
    private Integer nodeIsDeadInMinutes = null;
    private Integer threadIsDownInMinutes = null;
    private Integer nodeClusterActiveInMinutes = null;

    /**
     * @return The number of default threads for the cluster nodes.
     */
    public Integer getNumberOfThreads() {
        return numberOfThreads;
    }

    /**
     *
     * @param numberOfThreads The default number of threads for the
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
     * @return The default cutoff duration in minutes for which active nodes
     * need to be checked.
     */
    public Integer getNodeClusterActiveInMinutes() {
        return nodeClusterActiveInMinutes;
    }

    /**
     *
     * @param nodeClusterActiveInMinutes The default cutoff duration in minutes
     * for which active nodes need to be checked.
     */
    public void setNodeClusterActiveInMinutes(Integer nodeClusterActiveInMinutes) {
        this.nodeClusterActiveInMinutes = nodeClusterActiveInMinutes;
    }
}
