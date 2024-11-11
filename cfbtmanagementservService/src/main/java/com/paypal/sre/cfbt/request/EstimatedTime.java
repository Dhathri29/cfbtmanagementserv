package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequestStatistics;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestStatisticsDAO;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.util.HashMap;

/**
 * Class that is responsible to calculate the estimated Start time for a execution request to be transition from PENDING
 * to the next appropriate state.
 * 
 */
public class EstimatedTime {
    static final int DEFAULT_TEST_EXECUTION_DURATION = 600;

    private int testExecutionDuration;
    private static final Logger LOGGER = LoggerFactory.getLogger(EstimatedTime.class);
    private HashMap<String,ExecutionRequestStatistics> currentStats;

    /**
     * @param db
     *            the {@link MongoConnectionFactory} instance
     */
    public EstimatedTime(MongoConnectionFactory db) {
        ExecutionRequestStatisticsDAO statsDAO = ExecutionRequestStatisticsDAO.getInstance(db);
        this.currentStats = statsDAO.getCurrentStatistics();
        if (this.currentStats.containsKey(ExecutionRequestStatistics.ALL_STATS)) {
            this.testExecutionDuration = this.currentStats.get(ExecutionRequestStatistics.ALL_STATS).getNinetyPercentileDuration();
        }

        if (this.testExecutionDuration == 0) {
            this.testExecutionDuration = DEFAULT_TEST_EXECUTION_DURATION;
        }
    }

    /**
     * The estimated total time needed for the supplied request from
     * the time it gets picked up from the queue to the time it completes.
     * This calculation is based on the 90th percentile deploy time plus
     * 90th percentile execution time plus the percentage of rollbacks times
     * 90th percentile deploy time.
     * @param request {@link ExecutionRequest}
     * @return length of time in seconds.
     */
    public int estimatedRequestTime(ExecutionRequest request) {
        int length = 0;
        ExecutionRequestStatistics stats = getStats(request);

        length += getDeployEstimate(request, stats);
        length += getRollbackEstimate(request, stats);
        length += getExecutionEstimate(request);

        return length;
    }

    private ExecutionRequestStatistics getStats(ExecutionRequest request) {
        ExecutionRequestStatistics stats = null;
        if (request.getReleaseTest() != null && request.getReleaseTest().getReleaseVehicle() != null &&
                currentStats.containsKey(request.getReleaseTest().getReleaseVehicle())) {
            stats = currentStats.get(request.getReleaseTest().getReleaseVehicle());
        } else if (currentStats.containsKey(ExecutionRequestStatistics.ALL_STATS)) {
            stats = currentStats.get(ExecutionRequestStatistics.ALL_STATS);
        } else {
            stats = new ExecutionRequestStatistics();
        }
        return stats;
    }

    private int getRollbackEstimate(ExecutionRequest request, ExecutionRequestStatistics stats) {
        int estimate = 0;
        if (!request.checkFastPass() && request.thisIsRelease()) {
            estimate = (int)(stats.getNinetyPercentileDeploy() * stats.getRollbackPercentage() / 100.0);
        }
        return estimate;
    }

    private int getDeployEstimate(ExecutionRequest request, ExecutionRequestStatistics stats) {
        int estimate = 0;
        if (request.thisIsRelease()) {
            estimate = stats.getNinetyPercentileDeploy();
            // If there is no stored deploy time in the DB, then use the asked for amount of time
            if (estimate == 0) {
                estimate = request.getReleaseTest().getDeploymentEstimatedDuration();
            }
        }
        return estimate;
    }

    private int getExecutionEstimate(ExecutionRequest request) {
        int estimate = 0;
        if (!request.checkFastPass()) {
            estimate = testExecutionDuration;
        }
        return estimate;
    }

    /**
     * Method to calculate the estimated start time for the new execution request based on the previous execution
     * request in queue.
     *
     * @param previousRequest
     *            - the previous {@link ExecutionRequest} in the queue
     *            - we estimate how long the previous request will take
     *            - to determine the start of the current request
     * @return the estimated start time as an ISO compliant string.
     */
    public String estimatedStartTime(ExecutionRequest previousRequest) {
        if (previousRequest != null) {
            try {
                DateTime dateTime = DateUtil.dateTimeUTC(previousRequest.getEstimatedStartTime());
                ExecutionRequestStatistics stats = getStats(previousRequest);
                switch (previousRequest.getStatus()) {
                case PENDING: {
                    // if it's pending it will take the full time still
                    dateTime = dateTime.plusSeconds(estimatedRequestTime(previousRequest));
                    break;
                }
                case DEPLOY_WAITING:
                case DEPLOYING: {
                    int deployEstimate = getDeployEstimate(previousRequest, stats);
                    int remainingTimeToDeploy = (int) (deployEstimate
                            - DateUtil.getElapsedTimeInSeconds(previousRequest.getReleaseTest().getDeploymentStart()));
                    dateTime = dateTime.plusSeconds(Math.max(0, remainingTimeToDeploy));
                    dateTime = dateTime.plusSeconds(getExecutionEstimate(previousRequest));
                    dateTime = dateTime.plusSeconds(getRollbackEstimate(previousRequest, stats));
                    break;
                }
                case TESTING_COMPLETE: {
                    int remainingTimeToRollback = (int) (getDeployEstimate(previousRequest, stats)
                            - DateUtil.getElapsedTimeInSeconds(previousRequest.getReleaseTest().getRollbackStart()));
                    dateTime = dateTime.plusSeconds(Math.max(0, remainingTimeToRollback));
                    break;
                }
                case IN_PROGRESS:{
                    dateTime = DateUtil.currentDateTimeUTC();
                    int remainingTestExecutionTime = (int) (getExecutionEstimate(previousRequest)
                            - DateUtil.getElapsedTimeInSeconds(previousRequest.getExecutionStart()));
                    dateTime = dateTime.plusSeconds(Math.max(0, remainingTestExecutionTime));
                    dateTime = dateTime.plusSeconds(getRollbackEstimate(previousRequest, stats));
                    break;
                }
                default: {
                    dateTime = DateUtil.currentDateTimeUTC();
                    break;
                }
                }
                // if even after our calculation, this current request is exceeding how long it should have taken,
                // then reset to now to at least be closer and not in the past.
                DateTime now = DateUtil.currentDateTimeUTC();
                if (dateTime.isBefore(now)) {
                    dateTime = now;
                }
                return DateUtil.dateTimeISOFormat(dateTime);
            } catch (Exception e) {
                CFBTLogger.logError(LOGGER, EstimatedTime.class.getName(), e.getMessage(), e);
                CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                        "An error occurred while trying set the estimated Start time. ", e);
            }
        }
        return DateUtil.currentDateTimeISOFormat();
    }
}