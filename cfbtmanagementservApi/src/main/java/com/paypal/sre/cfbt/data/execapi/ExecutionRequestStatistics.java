package com.paypal.sre.cfbt.data.execapi;

/**
 * Holds Execution Request related statistics.
 *
 */
public class ExecutionRequestStatistics {
    public static final String ALL_STATS = "All";
    private String releaseVehicle;

    private String dateTime;

    private int ninetyPercentileDuration;

    private int ninetyPercentileDeploy;

    private double rollbackPercentage;

    /**
     * @return the releaseVehicle for this set of statistics.  "All" signifies all releases
     */
    public String getReleaseVehicle() {
        return this.releaseVehicle;
    }

    /**
     * @param releaseVehicle
     *            the release vehicle for this set of statistics.
     */
    public void setReleaseVehicle(String releaseVehicle) {
        this.releaseVehicle = releaseVehicle;
    }

    /**
     * @return the 90th percentile value for the duration of execution requests
     */
    public int getNinetyPercentileDuration() {
        return this.ninetyPercentileDuration;
    }

    /**
     * @param ninetyPercentileDuration
     *            the 90th percentile value for the duration of execution requests
     */
    public void setNinetyPercentileDuration(int ninetyPercentileDuration) {
        this.ninetyPercentileDuration = ninetyPercentileDuration;
    }

    /**
     * @return the dateTime (as ISO string), when the statistics was recorded
     */
    public String getDateTime() {
        return dateTime;
    }

    /**
     * @param dateTime
     *            the dateTime in ISO string, when the statistics was recorded
     */
    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }

    /**
     * @return the 90th percentile value for the deploy time of execution requests in the last 7 days
     */
    public int getNinetyPercentileDeploy() {
        return this.ninetyPercentileDeploy;
    }

    /**
     * @param ninetyPercentileDeploy
     *            the 90th percentile value for the deploy time of execution requests in the last 7 days
     */
    public void setNinetyPercentileDeploy(int ninetyPercentileDeploy) {
        this.ninetyPercentileDeploy = ninetyPercentileDeploy;
    }

    /**
     * @return he percentage of time an execution request rolled back in the last 7 days
     */
    public double getRollbackPercentage() {
        return this.rollbackPercentage;
    }

    /**
     * @param rollbackPercentage
     *            the percentage of time an execution request rolled back in the last 7 days
     */
    public void setRollbackPercentage(double rollbackPercentage) {
        this.rollbackPercentage = rollbackPercentage;
    }
}
