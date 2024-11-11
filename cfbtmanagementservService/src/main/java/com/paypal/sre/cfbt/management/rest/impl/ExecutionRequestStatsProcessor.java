package com.paypal.sre.cfbt.management.rest.impl;

import java.util.*;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import com.ebay.kernel.cal.api.CalTransaction;
import com.ebay.kernel.cal.api.sync.CalTransactionFactory;
import com.paypal.infra.util.cal.CalType;
import com.paypal.sre.cfbt.data.execapi.ReleaseRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestStatisticsDAO;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.DateUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequestStatistics;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.scheduler.RunOnceCommand;

/**
 * Scheduler job to calculate the different statistics related to {@link ExecutionRequest}
 */
@Component
public class ExecutionRequestStatsProcessor extends RunOnceCommand {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionRequestStatsProcessor.class);

    @Inject
    private DatabaseConfig dbConfig;
    private MongoConnectionFactory dbFactory;

    private long timeDurationLimit;
    private ExecutionRequestDAO requestDAO;
    private ExecutionRequestStatisticsDAO statsDAO;

    @PostConstruct
    public void init() {
        timeDurationLimit = Long.parseLong(ConfigManager.getConfiguration().getString("timeDurationLimit"));
        dbFactory = dbConfig.getConnectionFactory();
        requestDAO = ExecutionRequestDAO.getInstance(dbFactory);
        statsDAO = ExecutionRequestStatisticsDAO.getInstance(dbFactory);
    }

    /**
     * Scheduled Process to update the execution request statistics.
     *
     * The cron expression is configured to execute the task at 11 PM every day.
     */
    @Scheduled(cron = "0 0 23 * * ?")
    public void updateExecutionRequestStats() {
        final CalTransaction calTransaction = CalTransactionFactory.create(CalType.URL.toString());
        calTransaction.setName("CFBT.ExecutionRequestStats");
        calTransaction.addData("CFBT.ExecutionRequestStats: Calculating stats over last 7 days for execution requests");
        CFBTLogger.logInfo(logger, CFBTLogger.CalEventEnum.EXECUTION_REQUEST_STATS_PROCESSOR,
                "Start executing the task updateExecutionRequestStats");
        try {
            executeOnce(dbFactory, "updateExecutionRequestStats", timeDurationLimit);
            calTransaction.setStatus("0");
        } catch(Exception ex) {
            CFBTLogger.logError(logger, CFBTLogger.CalEventEnum.EXECUTION_REQUEST_STATS_PROCESSOR,
                    "Failure executing the updateExecutionRequestStats task", ex);
            calTransaction.setStatus(ex);
        } finally {
            calTransaction.completed();
        }
    }

    @Override
    protected void execute() {
        updateStatistics();
    }

    /**
     * Method to calculate and update the statistics. The following statistics are calculated based on the past 7 days
     * PASSED/FAILED/ERROR {@link ExecutionRequest} data : 90th percentile execution and deploy and percentage rollback
     * duration.
     */
    private void updateStatistics() {

        try {

            DateTime currentDateTime = DateTime.now(DateTimeZone.UTC);
            String toDate = DateUtil.dateTimeISOFormat(currentDateTime);
            // Consider only last 7 days data.
            String fromDate = DateUtil.dateTimeISOFormat(currentDateTime.minusDays(7));

            List<ExecutionRequest> executionRequests = requestDAO
                    .getExecutionRequests(DateUtil.trimDateTimeString(fromDate), DateUtil.trimDateTimeString(toDate));
            List<ExecutionRequest> eligibleRequests = new ArrayList<>();

            if (executionRequests == null) {
                CFBTLogger.logInfo(logger, CFBTLogger.CalEventEnum.EXECUTION_REQUEST_STATS_PROCESSOR,
                        "There are no execution requests for period between " + fromDate + " & " + toDate);
                return;
            }
            for (ExecutionRequest er : executionRequests) {
                if (ExecutionRequest.ResultStatus.PASS.equals(er.getResultStatus())
                        || ExecutionRequest.ResultStatus.FAILURE.equals(er.getResultStatus())
                        || ExecutionRequest.ResultStatus.ERROR.equals(er.getResultStatus())
                        || ExecutionRequest.ResultStatus.PASS_WITH_WARNING.equals(er.getResultStatus())) {
                    eligibleRequests.add(er);
                }
            }

            HashMap<String, List<Integer>> executionDurationMap = new HashMap<>();
            HashMap<String, List<Integer>> deploymentDurationMap = new HashMap<>();
            HashMap<String, Integer> rollbackMap = new HashMap<>();
            HashMap<String, Integer> totalReleasesMap = new HashMap<>();
            executionDurationMap.put(ExecutionRequestStatistics.ALL_STATS,new ArrayList<>());
            deploymentDurationMap.put(ExecutionRequestStatistics.ALL_STATS,new ArrayList<>());
            rollbackMap.put(ExecutionRequestStatistics.ALL_STATS,0);
            totalReleasesMap.put(ExecutionRequestStatistics.ALL_STATS,0);
            for (ReleaseRequest.ReleaseVehicle vehicle : ReleaseRequest.ReleaseVehicle.values()) {
                executionDurationMap.put(vehicle.toString(),new ArrayList<>());
                deploymentDurationMap.put(vehicle.toString(),new ArrayList<>());
                rollbackMap.put(vehicle.toString(),0);
                totalReleasesMap.put(vehicle.toString(),0);
            }
            for (ExecutionRequest er : eligibleRequests) {
                int executionDuration = getExecutionDuration(er);
                executionDurationMap.get(ExecutionRequestStatistics.ALL_STATS).add(executionDuration);
                if (er.getReleaseTest() != null &&
                        ExecutionRequest.Type.RELEASE.equals(er.getType()) &&
                        er.getReleaseTest().getReleaseVehicle() != null) {
                    String rv = er.getReleaseTest().getReleaseVehicle();
                    int deployDuration = getDeployDuration(er);
                    executionDurationMap.get(rv).add(executionDuration);
                    totalReleasesMap.replace(ExecutionRequestStatistics.ALL_STATS,totalReleasesMap.get(ExecutionRequestStatistics.ALL_STATS)+1);
                    totalReleasesMap.replace(rv,totalReleasesMap.get(rv)+1);
                    if (ReleaseTest.Action.ROLLBACK.equals(er.getReleaseTest().getCompletionAction())) {
                        rollbackMap.replace(ExecutionRequestStatistics.ALL_STATS,rollbackMap.get(ExecutionRequestStatistics.ALL_STATS)+1);
                        rollbackMap.replace(rv,rollbackMap.get(rv)+1);
                    }
                    if (deployDuration > 0) {
                        deploymentDurationMap.get(ExecutionRequestStatistics.ALL_STATS).add(deployDuration);
                        deploymentDurationMap.get(rv).add(deployDuration);
                    }
                }
            }

            for (ReleaseRequest.ReleaseVehicle vehicle : ReleaseRequest.ReleaseVehicle.values()) {
                String rv = vehicle.toString();
                // if there were no releases of one of the types, making this be at least one makes the rollback percentage
                // be 0 instead of NaN from dividing by 0
                if (totalReleasesMap.get(rv) == 0) {
                    totalReleasesMap.replace(rv, 1);
                }
                ExecutionRequestStatistics newStats = new ExecutionRequestStatistics();
                newStats.setReleaseVehicle(rv);
                newStats.setNinetyPercentileDuration(calculatePercentileDuration(executionDurationMap.get(rv), 90));
                newStats.setNinetyPercentileDeploy(calculatePercentileDuration(deploymentDurationMap.get(rv), 90));
                newStats.setRollbackPercentage((double) rollbackMap.get(rv) / (double) totalReleasesMap.get(rv) * 100.0);
                newStats.setDateTime(toDate);
                statsDAO.insert(newStats);
            }
            // if there were no releases at all, making this be at least one makes the rollback percentage
            // be 0 instead of NaN from dividing by 0
            if (totalReleasesMap.get(ExecutionRequestStatistics.ALL_STATS) == 0) {
                totalReleasesMap.replace(ExecutionRequestStatistics.ALL_STATS, 1);
            }
            ExecutionRequestStatistics newStats = new ExecutionRequestStatistics();
            newStats.setReleaseVehicle(ExecutionRequestStatistics.ALL_STATS);
            newStats.setNinetyPercentileDuration(calculatePercentileDuration(executionDurationMap.get(ExecutionRequestStatistics.ALL_STATS), 90));
            newStats.setNinetyPercentileDeploy(calculatePercentileDuration(deploymentDurationMap.get(ExecutionRequestStatistics.ALL_STATS), 90));
            newStats.setRollbackPercentage((double) rollbackMap.get(ExecutionRequestStatistics.ALL_STATS) / (double) totalReleasesMap.get(ExecutionRequestStatistics.ALL_STATS) * 100.0);
            newStats.setDateTime(toDate);
            statsDAO.insert(newStats);

        } catch (Exception ex) {
            CFBTLogger.logError(logger, CFBTLogger.CalEventEnum.EXECUTION_REQUEST_STATS_PROCESSOR,
                    "There was an exception processing execution request statistics. " + ex.toString());
        }
    }

    /**
     * Get the execution duration for an execution request.
     *
     * @param er
     *            - the {@link ExecutionRequest}
     * @return the execution duration
     */
    private int getExecutionDuration(ExecutionRequest er) {
        int duration = 0;
        if (er.getExecutionStart() != null) {
            DateTime startDateTime = new DateTime(er.getExecutionStart(), DateTimeZone.UTC);
            DateTime endDateTime = new DateTime(er.getExecutionComplete(), DateTimeZone.UTC);
            Period elapsedTime = new Period(startDateTime, endDateTime);
            duration = elapsedTime.getHours() * 3600 + elapsedTime.getMinutes() * 60 + elapsedTime.getSeconds();
        }
        return duration;
    }

    /**
     * Get the deployment duration for an execution request.
     *
     * @param er
     *            - the {@link ExecutionRequest}
     * @return the execution duration
     */
    private int getDeployDuration(ExecutionRequest er) {
        int duration = 0;
        if (er.getReleaseTest() != null && ExecutionRequest.Type.RELEASE.equals(er.getType()) &&
                er.getReleaseTest().getDeploymentComplete() != null) {
            DateTime startDateTime = new DateTime(er.getReleaseTest().getDeploymentStart(), DateTimeZone.UTC);
            DateTime endDateTime = new DateTime(er.getReleaseTest().getDeploymentComplete(), DateTimeZone.UTC);
            Period elapsedTime = new Period(startDateTime, endDateTime);
            duration = elapsedTime.getHours() * 3600 + elapsedTime.getMinutes() * 60 + elapsedTime.getSeconds();
        }
        return duration;
    }

    /**
     * Method to calculate the specified percentile value from the provided {@link List} of {@link Integer}
     *
     * @param durationList
     *            - the {@link List} of {@link Integer} representing the durations.
     * @param percentile
     *            - the percentile value that need to be calculated.
     * @return the calculated percentile value
     */
    private int calculatePercentileDuration(List<Integer> durationList, int percentile) {
        int percentileIndex = 1;

        if (durationList == null) {
            return 0;
        }

        if (durationList.size() > 0) {
            Collections.sort(durationList);
            percentileIndex = (int) Math.ceil(((double) percentile / (double) 100) * (double) durationList.size());
            return durationList.get(percentileIndex - 1);
        } else {
            return 0;
        }
    }
}
