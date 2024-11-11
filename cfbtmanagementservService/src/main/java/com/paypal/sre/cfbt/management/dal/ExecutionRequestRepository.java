package com.paypal.sre.cfbt.management.dal;

import java.util.ArrayList;
import java.util.List;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;

import com.paypal.sre.cfbt.data.execapi.Execution;
import com.paypal.sre.cfbt.data.execapi.Execution.Status;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO.Statistics;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.DateUtil;

public class ExecutionRequestRepository {
    
    /**
     * This function updates the Execution Request based on the outcome of the individual test executed within the
     * request.
     *
     * @param mDB                       the {@link MongoConnectionFactory} object
     * @param execution                 the result of the test {@link Execution}
     * @param didTransitionToInprogress true, if the execution transitioned to in progress
     * @throws Exception usually thrown when trying to connect to Mongo.
     */
    public void updateFromExecution(MongoConnectionFactory mDB, Execution execution, boolean didTransitionToInprogress) throws Exception {
        if (execution == null)  {
            return;
        }

        try (MongoConnection c = mDB.newConnection()) {
            Status status = execution.getStatus();

            List<Statistics> statisticsList = new ArrayList<>();
            execution.getExecutionRequestIds().forEach((requestId) -> {
                statisticsList.add(new Statistics(mDB, requestId));
            });

            boolean completedExecution = false;

            for(Statistics stats: statisticsList) {
                if (Status.IN_PROGRESS.equals(status)) {
                    stats.addInProgressTests(1).update();
                } else if (execution.getReleaseVetting()) {
                    switch (status) {
                        case PASS: {
                            stats.addPassedTests(1).addInProgressTests(-1).update();
                            completedExecution = true;
                            break;
                        }
                        case FAIL: {
                            stats.addFailedTests(1).addInProgressTests(-1).update();
                            completedExecution = true;
                            break;
                        }
                        case ABORT: {
                            stats.addAbortedTests(1).addInProgressTests(-1).update();
                            completedExecution = true;
                            break;
                        }
                        case SKIP: {
                            stats.addSkippedTests(1).update();
                            completedExecution = true;
                            break;
                        }
                        case ERROR: {
                            // if the tests was not assigned any node and it transitioned to error,
                            // It is safe to assume that it was never transitioned to in-progress.
                            if (!didTransitionToInprogress) {
                                stats.addErroredTests(1).update();
                            } else {
                                stats.addErroredTests(1).addInProgressTests(-1).update();
                            }
                            completedExecution = true;
                            break;
                        }
                        case PASS_WITH_WARNING: {
                            stats.addWarningTests(1).addPassedTests(1).addInProgressTests(-1).update();
                            completedExecution = true;
                            break;
                        }
                        default: {
                            break;
                        }
                    }
                } else {
                    if (!didTransitionToInprogress) {
                        stats.addNonReleaseVettingTests(1).update();
                    } else {
                        stats.addNonReleaseVettingTests(1).addInProgressTests(-1).update();
                    }
                    completedExecution = true;
                }
            }

            if (completedExecution) {
                List<Document> objectIds = new ArrayList<>();
                execution.getExecutionRequestIds().forEach((id) -> {
                    objectIds.add(new Document("_id", new ObjectId(id)));
                });
                Document requestFilter = new Document("$or", objectIds);

                Document finishedFilter = new Document("$where","function() {"
                        + "var sum = 0;"
                        + "if (typeof this.skippedTests !== 'undefined') sum += this.skippedTests;"
                        + "if (typeof this.failedTests !== 'undefined') sum += this.failedTests;"
                        + "if (typeof this.passedTests !== 'undefined') sum += this.passedTests;"
                        + "if (typeof this.abortedTests !== 'undefined') sum += this.abortedTests;"
                        + "if (typeof this.testsInError !== 'undefined') sum += this.testsInError;"
                        + "if (typeof this.nonReleaseVettingTests !== 'undefined') sum += this.nonReleaseVettingTests;"
                        + "if (typeof this.testsFalsePositive !== 'undefined') sum += this.testsFalsePositive;"
                        + "if (typeof this.testsFalseNegative !== 'undefined') sum += this.testsFalseNegative;"
                        + "return sum >= this.numberTests;"
                        + "}"
                );

                Document statusInProgressFilter = new Document("status", ExecutionRequest.Status.IN_PROGRESS.toString());
                Document statusPendingFilter    = new Document("status", ExecutionRequest.Status.PENDING.toString());
                Document statusHaltInProgressFilter = new Document("status", ExecutionRequest.Status.HALT_IN_PROGRESS.toString());

                List<Document> orQuery = new ArrayList<>();
                orQuery.add(statusInProgressFilter);
                orQuery.add(statusPendingFilter);
                orQuery.add(statusHaltInProgressFilter);
                Document orFilter = new Document("$or", orQuery);

                List<Document> inProgressFilter = new ArrayList<>();
                inProgressFilter.add(requestFilter);
                inProgressFilter.add(finishedFilter);
                inProgressFilter.add(orFilter);
                Document andProgressFilter = new Document("$and", inProgressFilter);
                
                Document testingCompleteStatus =  new Document("status", ExecutionRequest.Status.TESTING_COMPLETE.toString());
                
                // Assume the Status was in progress and we're transitioning to completed.
                UpdateResult result = ExecutionRequestDAO.getInstance(mDB).update(c, andProgressFilter,
                        new Document("$set", testingCompleteStatus.append("executionComplete", DateUtil.currentDateTimeISOFormat())), false);
            }
        }
    }
    
    /**
     * This method is responsible for finding the execution request with 
     * {@link ExecutionRequest#runNow} set to true as well as
     * {@link ExecutionRequest#status} equal to Completed.
     * @param mDB {@link MongoConnectionFactory} object
     * @return Lit of {@link ExecutionRequest}
     * @throws Exception 
     */
    public List<ExecutionRequest> getRunNowInProgressRequests(MongoConnectionFactory mDB) throws Exception{
        List<ExecutionRequest> executionReqList = null;
        try (MongoConnection c = mDB.newConnection()) {
            List<Document> andQuery = new ArrayList<>();
                andQuery.add(new Document("runNow", true));
                andQuery.add(new Document("status", new Document("$ne", ExecutionRequest.Status.COMPLETED.toString())));
                Document andFilter = new Document("$and", andQuery);
                executionReqList = ExecutionRequestDAO.getInstance(mDB).read(c, andFilter);
        }
        return executionReqList;
    }

    /**
     * This method is responsible for returning all executions with {@link ExecutionRequest#status}
     * equal to IN_PROGRESS.
     * @param mDB {@link MongoConnectionFactory} object
     * @return List of {@link ExecutionRequest}
     * @throws Exception
     */
    public List<ExecutionRequest> getInProgressRequests(MongoConnectionFactory mDB) throws Exception{
        List<ExecutionRequest> inProgressList = null;
        try(MongoConnection c = mDB.newConnection()) {
            inProgressList = ExecutionRequestDAO.getInstance(mDB).read(
                    c, new Document("status", ExecutionRequest.Status.IN_PROGRESS.toString())
            );
        }
        return inProgressList;
    }
}
