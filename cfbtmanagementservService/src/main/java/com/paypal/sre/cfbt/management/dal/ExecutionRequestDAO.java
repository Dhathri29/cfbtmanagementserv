package com.paypal.sre.cfbt.management.dal;

import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.Status;
import com.paypal.sre.cfbt.data.execapi.Test;
import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.dataaccess.DBOpLog;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.mongo.MongoDataMarshaller;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
public class ExecutionRequestDAO extends AbstractDAO<ExecutionRequest> {
    private static ExecutionRequestDAO INSTANCE = null;
    private final MongoConnectionFactory db;
    private final Logger mLogger = LoggerFactory.getLogger(DBOpLog.class);

    public static ExecutionRequestDAO getInstance(MongoConnectionFactory db) {
        if (INSTANCE == null) {
            INSTANCE = new ExecutionRequestDAO("ExecutionRequest", db);
        }
        return INSTANCE;
    }

    private ExecutionRequestDAO(String collectionName, MongoConnectionFactory db) {
        super(collectionName, ExecutionRequest.class);

        this.db = db;
    }

    public Statistics statistics(String id) {
        return new Statistics(db, id);
    }

    /**
     * Get by status on from the specified queue.
     * @param statusList list of statuses to pull.
     * @param queueName name of the queue
     * @return {@link List<ExecutionRequest>}
     * @throws Exception
     */
    public List<ExecutionRequest> getByStatusAndQueue(List<String> statusList, String queueName) throws Exception {
        List<ExecutionRequest> executionrequestlist = null;
        List<Document> statuses = new ArrayList<>();

        for (String eachStatus : statusList) {
            statuses.add(new Document("status", eachStatus));
        }

        Document statusFilter = new Document("$or", statuses);
        List<Document> filters = new ArrayList<>();
        filters.add(statusFilter);
        filters.add(new Document("queueName", queueName));

        try (MongoConnection c = db.newConnection()) {
            executionrequestlist = super.read(c, new Document("$and", filters));
            for (ExecutionRequest request : executionrequestlist) {
                setPercentComplete(request);
                setTimeToExecute(request);
            }
        }
        return executionrequestlist;
    }

    /**
     * Get by status regardless of queue.
     *
     * @param statusList list of statuses to pull.
     * @return {@link List<ExecutionRequest>}
     * @throws Exception
     */
    public List<ExecutionRequest> getByStatus(List<String> statusList) throws Exception {
        List<ExecutionRequest> executionrequestlist = null;
        List<Document> statuses = new ArrayList<>();

        for (String eachStatus : statusList) {
            statuses.add(new Document("status", eachStatus));
        }

        try (MongoConnection c = db.newConnection()) {
            executionrequestlist = super.read(c, new Document("$or", statuses));
            for (ExecutionRequest request : executionrequestlist) {
                setPercentComplete(request);
                setTimeToExecute(request);
            }
        }
        return executionrequestlist;
    }

    /**
     * Get active existing request if any.
     *
     * @param trReleaseId The trRelease id of an execution request to be searched
     *  @param datacenter The datacenter of an execution request to be searched
     * @return {@link List<ExecutionRequest>}
     * @throws Exception
     */
    public List<ExecutionRequest> getActiveRequests(String trReleaseId, String datacenter) throws Exception {
        List<ExecutionRequest> executionRequestList = null;
        List<String> statusList = new ArrayList<>();
        statusList.addAll(ExecutionRequest.Status.activeStatuses());

        List<Document> statuses = new ArrayList<>();
        for (String eachStatus : statusList) {
            statuses.add(new Document("status", eachStatus));
        }

        List<Document> queryDocument = new ArrayList<>();
        queryDocument.add(new Document("$or", statuses));
        queryDocument.add(new Document("trReleaseId", trReleaseId));
        queryDocument.add(new Document("datacenter", datacenter));

        try (MongoConnection c = db.newConnection()) {
            executionRequestList = super.read(c, new Document("$and", queryDocument));
            if (executionRequestList != null && !executionRequestList.isEmpty()) {
                for (ExecutionRequest request : executionRequestList) {
                    setPercentComplete(request);
                    setTimeToExecute(request);
                }

                executionRequestList.sort(Comparator.comparing(ExecutionRequest::getRequestTime, Comparator.reverseOrder()));
            }
        }
        return executionRequestList;
    }

    /**
     * Get execution requests that are pending, in-progress, or recently completed.
     * An execution request is considered recently completed if it was completed on
     * or after {@code dateTimeFrom}.
     *
     * @param dateTimeFrom earliest date and time considered recent
     * @return list of execution requests
     */
    public List<ExecutionRequest> getSystemStatus(DateTime dateTimeFrom, int pendingLimit, int completedLimit, Boolean isSynthetic, Boolean shouldShowSyntheticsOnCFBT) throws Exception {

        List<ExecutionRequest> executionRequestList = new ArrayList<>();

        // filter pending execution requests
        Document pendingFilter = new Document(
            "status", ExecutionRequest.Status.PENDING.toString()
        );

        // sort pending by ascending position and requestTime
        Document pendingSort = new Document("position", 1).append("requestTime", 1);

        // filter in-progress execution requests
        Document inProgressFilter = new Document(
            "status", new Document(
                "$nin", Arrays.asList(
                    ExecutionRequest.Status.PENDING.toString(),
                    ExecutionRequest.Status.COMPLETED.toString(),
                    ExecutionRequest.Status.HALTED.toString(),
                    ExecutionRequest.Status.STOPPED.toString()
                )
            )
        );

        // filter recently completed execution requests
        Document completedFilter = new Document(
            "$and", Arrays.asList(
                new Document(
                    "status", new Document(
                        "$in", Arrays.asList(
                            ExecutionRequest.Status.COMPLETED.toString(),
                            ExecutionRequest.Status.HALTED.toString(),
                            ExecutionRequest.Status.STOPPED.toString()
                        )
                    )
                ),
                new Document(
                    "completionTime", new Document(
                        "$gte", dateTimeFrom.toString().replaceAll(".000Z", "")
                    )
                )
            )
        );

        // sort recently completed by desending completion time
        Document completedSort = new Document("completionTime", -1);

        appendSyntheticFilters(isSynthetic, shouldShowSyntheticsOnCFBT, pendingFilter, inProgressFilter, completedFilter);

        try (MongoConnection c = db.newConnection()) {
            executionRequestList.addAll(super.read(c, pendingFilter, null, pendingSort, pendingLimit));
            executionRequestList.addAll(super.read(c, inProgressFilter));
            executionRequestList.addAll(super.read(c, completedFilter, null, completedSort, completedLimit));
            // percentComplete and timeToExecute values are not saved to
            // the database and must be calculated and set on the fly.
            for (ExecutionRequest request : executionRequestList) {
                setPercentComplete(request);
                setTimeToExecute(request);
            }
        }

        return executionRequestList;

    }

    /**
     * If request comes from the legacy client, like cfbtnodeweb, it will not contain synthetics fields in request.
     * In those cases we look in DB for docs with no mention of synthetics, these are old docs, along with these we
     * see for docs with synthetics as false.
     *
     * In other case when the request comes from new clients like dext, synthetics filed is expected to be present
     * and to be true, in those cases we look for as
     */
    private void appendSyntheticFilters(Boolean isSynthetic, boolean shouldShowSyntheticsOnCFBT, Document pendingFilter, Document inProgressFilter, Document completedFilter) {
        String appendKey = null;
        Object appendValue = null;

        // TODO: revisit this code as per comments here once Dext is ready,
        //  https://github.paypal.com/SiteReliability-R/cfbtmanagementserv/pull/310#pullrequestreview-4731349


        // for legacy requests
        if(isSynthetic == null){
            if(!shouldShowSyntheticsOnCFBT){
                appendKey = "$or";
                appendValue = Arrays.asList(new Document("isSynthetic", null), new Document("isSynthetic", false));
            }
        }
        // for new requests with synthetic fields.
        else{
            if(isSynthetic){
                appendKey = "isSynthetic";
                appendValue = true;
            }else{
                appendKey = "$or";
                appendValue = Arrays.asList(new Document("isSynthetic", null), new Document("isSynthetic", false));
            }
        }

        if(appendKey != null && appendValue !=null){
            pendingFilter.append(appendKey, appendValue);
            inProgressFilter.append(appendKey, appendValue);
            completedFilter.append(appendKey, appendValue);
        }
    }


    /**
     * Inserts request into the collection.
     *
     * @param request Execution request to be persisted.
     * @throws java.lang.Exception when there's an issue connecting to mongo.
     */
    public void insert(ExecutionRequest request) throws Exception {
        // Just in case.
        if (request.getReleaseTest() != null) {
            request.getReleaseTest().setExecutionRequest(null);
        }

        try (MongoConnection c = db.newConnection()) {
            final String id = super.insert(c, request);
            request.setId(id);

            // The releaseTest id = executionRequest id.
            if (request.getReleaseTest() != null) {
                request.getReleaseTest().setId(id);
                super.update(c, request, new Document("$set", new Document("releaseTest._id", new ObjectId(request.getId()))));
            }
        }
    }

    /**
     * Inserts request into the collection.
     *
     * @param request Execution request to be persisted.
     * @param queueName Insert it with the queue it's destined for.
     * @throws java.lang.Exception when there's an issue connecting to mongo.
     */
    public void insert(ExecutionRequest request, String queueName) throws  Exception {
        request.setQueueName(queueName);

        // Just in case.
        if (request.getReleaseTest() != null) {
            request.getReleaseTest().setExecutionRequest(null);
        }

        try (MongoConnection c = db.newConnection()) {
            final String id = super.insert(c, request);
            request.setId(id);

            // The releaseTest id = executionRequest id.
            if (request.getReleaseTest() != null) {
                request.getReleaseTest().setId(id);
                super.update(c, request, new Document("$set", new Document("releaseTest._id", new ObjectId(request.getId()))));
            }
        }
    }
    /**
     * Calculate the percent complete of the tests.
     * @param request {@link ExecutionRequest}
     */
    private void setPercentComplete(ExecutionRequest request) {
        if (request == null) return;

        int numFailedTests = request.getFailedTests();
        int numTests = request.getNumberTests();
        int numPassedTests = request.getPassedTests();
        int numErroredTests = request.getTestsInError();
        int numSkippedTests = request.getSkippedTests();
        int numAbortedTests = request.getAbortedTests();
        int numFalseNegatives = request.getTestsFalseNegative();
        int numFalsePositives = request.getTestsFalsePositive();
        int numNonReleaseVettingTests = request.getNonReleaseVettingTests();

        int totalCompletedTests = numFailedTests + numPassedTests + numErroredTests + numSkippedTests + numAbortedTests
                                   + numFalseNegatives + numFalsePositives + numNonReleaseVettingTests;

        double percentComplete = 0;

        if (numTests != 0) {
            percentComplete = (double)(totalCompletedTests * 100)/ numTests;
        }

        request.setPercentComplete(percentComplete);
    }

    private void setTimeToExecute(ExecutionRequest request) {
        if (request == null) return;

        // The value is already set, no need to do it again.
        if (request.getTimeToExecute() != 0) return;
        String startTime = request.getExecutionStart();
        String completeTime = request.getExecutionComplete();

        // It's impossible to calculate.
        if (startTime != null && completeTime != null) {

            DateTime startDate = DateUtil.dateTimeUTC(startTime);
            DateTime endDate = DateUtil.dateTimeUTC(request.getExecutionComplete());

            Period period = new Period(startDate, endDate);

            //I'm counting on this not taking longer than a day.
            int totalTimeInSeconds = period.getHours() * 3600 +
                                     period.getMinutes() * 60 +
                                     period.getSeconds();

            request.setTimeToExecute(totalTimeInSeconds);
        }
        else if (startTime == null) {
            request.setTimeToExecute(0);
        }
    }

    public List<ExecutionRequest> transitionToInProgress(List<ExecutionRequest> requests, boolean updateDeploymentComplete) throws Exception {
        Document updateValues = new Document("status",ExecutionRequest.Status.IN_PROGRESS.toString())
                .append("executionStart", DateUtil.currentDateTimeISOFormat());
        if (updateDeploymentComplete) {
            updateValues = updateValues.append("releaseTest.deploymentComplete", DateUtil.currentDateTimeISOFormat());
        }
        Document inProgressUpdate = new Document("$set", updateValues);
        List<Document> objectIds = new ArrayList<>();
        requests.forEach((exRequest) -> {
            objectIds.add(new Document("_id", new ObjectId(exRequest.getId())));
        });
        Document queryFilter = new Document("$or", objectIds);

        try (MongoConnection c = db.newConnection()) {
            UpdateResult result =  super.update(c, queryFilter, inProgressUpdate, false);

            if (result.getMatchedCount() > 0) {
                return super.read(c, queryFilter);
            } else {
                throw new IllegalArgumentException("Execution Request Ids not found");
            }
        }
    }

    public ExecutionRequest transitionToDeployWaiting(ExecutionRequest request) throws Exception {
        Document inProgressUpdate = new Document("$set",
                new Document("status",ExecutionRequest.Status.DEPLOY_WAITING.toString())
                        .append("releaseTest.deploymentComplete", DateUtil.currentDateTimeISOFormat()));

        try (MongoConnection c = db.newConnection()) {
            return super.findAndUpdate(c, request.getId(), inProgressUpdate);
        }
    }

    public ExecutionRequest updateTests(ExecutionRequest request, List<Test> tests) throws Exception {
        request.setTests(tests);

        try (MongoConnection c = db.newConnection()) {
            List<Document> documentList = new ArrayList<>();

            for (Test thisTest : tests) {
                documentList.add(MongoDataMarshaller.encode(thisTest));
            }
            Document testUpdate = new Document("$set", new Document("tests", documentList).append("numberTests", tests.size()));
            return super.findAndUpdate(c, request.getId(), testUpdate);
        }
    }

    /**
     * Returns (type {@link ExecutionRequest}) of from the ExecutionRequest collection.
     *
     * @param id an id for ExecutionRequest record
     * @return ExecutionRequest  the found execution request record
     * @throws java.net.UnknownHostException
     * @throws java.lang.Exception Exceptions thrown by Mongo.
     */
    public ExecutionRequest getById(String id) throws UnknownHostException, Exception  {
        ExecutionRequest request = null;

        if (id == null) {
            throw new IllegalArgumentException("The execution request ID cannot be null.");
        }

        try (MongoConnection c = db.newConnection()) {
            request = super.readOne(c, id);
            setPercentComplete(request);
            setTimeToExecute(request);
        }

        if (request == null) {
            return null;
        }
        return request;
    }

    /**
     * Returns the list of execution requests searched based on request ids .
     *
     * @param executionRequestsIds List if execution request ids
     * @return List of {@link ExecutionRequest}
     * @throws java.lang.Exception Exceptions thrown by Mongo.
     */
    public List<ExecutionRequest> getByIds(List<String> executionRequestsIds) throws Exception  {
        List<ExecutionRequest> executionrequestlist = new ArrayList<>();

        if (executionRequestsIds == null || executionRequestsIds.isEmpty()) {
            return executionrequestlist;
        }

        List<Document> objectIds = new ArrayList<>();
        executionRequestsIds.forEach((id) -> {
            objectIds.add(new Document("_id", new ObjectId(id)));
        });
        Document queryFilter = new Document("$or", objectIds);

        try (MongoConnection c = db.newConnection()) {
            executionrequestlist = super.read(c, queryFilter);

            if (executionrequestlist == null) {
                return null;
            }

            for (ExecutionRequest request : executionrequestlist) {
                setPercentComplete(request);
                setTimeToExecute(request);
            }
        }
        return executionrequestlist;
    }

    /**
     * Create a Mongo Document filter to that retrieves the ID but only if it's in the IN_PROGRESS state.
     * @param id The Request ID to filter.
     * @return The Document to use as a filter to conditionally retrieve the ID doc if it's IN_PROGRESS
     */
    private Document incompleteIDFilter(String id) {
        List<Document> idStatus = new ArrayList<>();
        idStatus.add(new Document("resultStatus",ExecutionRequest.ResultStatus.IN_PROGRESS.toString()));
        idStatus.add(new Document("_id", new ObjectId(id)));

        return new Document("$and", idStatus);
    }

   /**
     * This function updates all three statuses that are part of the {@link ExecutionRequest}
     *
     * @param request {@link ExecutionRequest}
     * @param recommendation {@link ExecutionRequest.ReleaseRecommendation}
     * @param result {@link ExecutionRequest.ResultStatus}
     * @param status {@link Status}
     * @return {@link ExecutionRequest}
     * @throws Exception generic exception
     */
    public ExecutionRequest updateResult(ExecutionRequest request,
                             ExecutionRequest.ReleaseRecommendation recommendation,
                             ExecutionRequest.ResultStatus result,
                             Status status, boolean requestRetry) throws Exception {

        // All statuses to be set to something.
        if (recommendation == null || result == null) {
            CFBTLogger.logError(mLogger, CFBTLogger.CalEventEnum.EXECUTION_UPDATE, "Error trying to update with null status");
            throw new IllegalArgumentException("Recommendation or result was null");
        }

        String completionTime = DateUtil.currentDateTimeISOFormat();
        Document updateStatus = new Document("releaseRecommendation", recommendation.toString())
                    .append("resultStatus", result.toString());

        if (status != null ) {
            updateStatus.append("status", status.toString());

            if (Status.isCompleteStatus(status) && request.getExecutionStart() != null) {
                updateStatus.append("executionComplete", completionTime);
            }
            if (Status.COMPLETED.equals(status)) {
                updateStatus.append("completionTime", completionTime);
            }
        }

        if(request.getReleaseTest() != null) {
            updateStatus.append("releaseTest.requestRetry", requestRetry);

            if(request.getReleaseTest().getCompletionAction() != null) {
                updateStatus.append("releaseTest.completionAction", request.getReleaseTest().getCompletionAction().toString());
            }
        }

        try (MongoConnection c = db.newConnection()) {
            Document filter = null;

            // The result status has to in progress in order to do a transition.
            filter = incompleteIDFilter(request.getId());

            UpdateResult updateResult = super.update(c, filter, new Document("$set", updateStatus), false);

            if (updateResult.getMatchedCount() > 0) {
                request.setReleaseRecommendation(recommendation);
                request.setResultStatus(result);
                if(request.getReleaseTest() != null) {
                    request.getReleaseTest().setRequestRetry(requestRetry);
                }
                if (status != null ) {
                    request.setStatus(status);

                    if (Status.isCompleteStatus(status) && request.getExecutionStart() != null) {
                        request.setExecutionComplete(completionTime);
                    }
                    if (Status.COMPLETED.equals(status)) {
                        request.setCompletionTime(completionTime);
                    }
                }
            }
        }

        return request;
    }

    /**
     * Transition to the deploying status.
     *
     * @param request {@link ExecutionRequest}
     * @return  {@link ExecutionRequest}
     * @throws java.lang.Exception
     */
    public ExecutionRequest transtionToDeploying(ExecutionRequest request) throws Exception {
        Document deploymentStatus = new Document("status", Status.DEPLOYING.toString());
        deploymentStatus.append("releaseTest.deploymentStart", DateUtil.currentDateTimeISOFormat());

        try (MongoConnection c = db.newConnection()) {
            return super.update(c, request, new Document("$set", deploymentStatus));
        }
    }

   /** Update status in the db.
     *
     * @param request {@link ExecutionRequest}
     * @param status {@link Status}
     * @return {@link ExecutionRequest}
     * @throws Exception
     */
    public ExecutionRequest updateState(ExecutionRequest request, Status status) throws Exception {
        try (MongoConnection c = db.newConnection()) {

            return super.update(c, request, new Document("$set", new Document("status", status.toString())));
        }
    }

    /**
     * Increment every request's position on the list as long as its not POSITIONLESS.
     * @param request {@link ExecutionRequest}
     * @throws Exception
     */
    public void incrementPosition(ExecutionRequest request) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            List<Document> andFilter = new ArrayList<>();
            andFilter.add(new Document("_id", new ObjectId(request.getId())));
            andFilter.add(new Document("position", new Document("$lt", ExecutionRequest.POSITIONLESS)));

            Document update = new Document("$inc", new Document("position", 1));
            update.append("$set", new Document("estimatedStartTime", request.getEstimatedStartTime()));

            UpdateResult result = update(c, new Document("$and", andFilter), update, true);

            if (result.getMatchedCount() > 0) {
                request.setPosition(request.getPosition() + 1);
            }
        }
    }

    /**
     * Decrement every request on the list by one as long as they are greater than 0 and less then POSITIONLESS.
     * @param request {@link ExecutionRequest}
     * @throws Exception
     */
    public void decrementPosition(ExecutionRequest request) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            List<Document> filter = new ArrayList<>();

            filter.add(new Document("position", new Document("$gt", 0)));
            filter.add(new Document("position", new Document("$lt", ExecutionRequest.POSITIONLESS)));
            filter.add(new Document("_id", new ObjectId(request.getId())));

            Document update = new Document("$inc", new Document("position", -1));
            update.append("$set", new Document("estimatedStartTime", request.getEstimatedStartTime()));

            UpdateResult result = update(c, new Document("$and", filter), update, false);

            if (result.getMatchedCount() > 0) {
                request.setPosition(request.getPosition() - 1);
            }
        }
    }

    /**
     * Updates the position and the estimatedStartTime
     * @param request {@link ExecutionRequest}
     * @throws Exception
     */
    public void updatePosition(ExecutionRequest request) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            Document update = new Document("position", request.getPosition());
            update.append("estimatedStartTime", request.getEstimatedStartTime());

            update(c, new Document("_id", new ObjectId(request.getId())), new Document("$set", update), false);
        }
    }

    /**
     * Assign the position and the priority.
     * @param newRequest {@link ExecutionRequest}
     * @param queueName Name of the queue this request is getting added to.
     * @throws Exception
     */
    public void setPositionAndPriority(ExecutionRequest newRequest, String queueName) throws Exception {
        Document setFields = new Document("position", newRequest.getPosition());
        setFields.append("priority", newRequest.getPriority());
        setFields.append("estimatedStartTime", newRequest.getEstimatedStartTime());
        setFields.append("queueName", queueName);

        try (MongoConnection c = db.newConnection()) {
            ExecutionRequest storedRequest = super.update(c, newRequest, new Document("$set", setFields));

            if (storedRequest == null) {
                super.insert(c, newRequest);
            }
        }
    }

    /**
     * Returns all pending requests that have not been assigned a position on the queue.
     * @param queueName Name of the queue the request belongs.
     * @return List of {@link ExecutionRequest}
     * @throws Exception
     */
    public List<ExecutionRequest> getAllPositionless(String queueName) throws Exception {
        List<ExecutionRequest> executionrequestlist = null;
        List<Document> filters = new ArrayList<>();

        filters.add(new Document("status", ExecutionRequest.Status.PENDING.toString()));
        filters.add(new Document("position", ExecutionRequest.POSITIONLESS));
        filters.add(new Document("queueName", queueName));

        Document findBy = new Document("$and", filters);

        try (MongoConnection c = db.newConnection()) {
            executionrequestlist = super.read(c, findBy);
        }
        return executionrequestlist;
    }

    /**
     * Returns every Execution Request that has a position on a queue and in the pended state.
     * @return {@link List<ExecutionRequest>}
     * @throws Exception
     */
    public List<ExecutionRequest> getPositionedPendedRequests(String queueName) throws Exception {
        List<ExecutionRequest> executionrequestlist = null;
        List<Document> filters = new ArrayList<>();

        filters.add(new Document("status", ExecutionRequest.Status.PENDING.toString()));
        filters.add(new Document("position", new Document("$lt", ExecutionRequest.POSITIONLESS)));
        filters.add(new Document("position", new Document("$gt", ExecutionRequest.DEQUEUED_POSITION)));
        filters.add(new Document("queueName", queueName));

        Document findBy = new Document("$and", filters);

        try (MongoConnection c = db.newConnection()) {
            executionrequestlist = super.read(c, findBy);
        }
        executionrequestlist.sort((r1, r2) -> r1.getPosition() - r2.getPosition());
        return executionrequestlist;
    }

    /**
     * /Bulk update the estimated start time of each request.
     * @param orderedRequests
     * @throws Exception
     */
    public void updateEstimatedStartTime(List<ExecutionRequest> orderedRequests) throws Exception {
        List<WriteModel<Document>> bulkData = new ArrayList<>();
        try (MongoConnection c = db.newConnection()) {
            orderedRequests.forEach((request) -> {
                bulkData.add(new UpdateOneModel<>(
                    new Document("_id", new ObjectId(request.getId())),
                    new Document("$set", new Document("estimatedStartTime", request.getEstimatedStartTime()))));
            });

            super.bulkUpdate(c, bulkData);
        }
    }

    /**
     * Get all pending requests in position zero.
     * @param queueName Name of the queue.
     * @return {@link List<ExecutionRequest>}
     * @throws UnknownHostException Mongo not available.
     * @throws Exception
     */
    public List<ExecutionRequest> getPendingPositionZero(String queueName) throws UnknownHostException, Exception {
        List<ExecutionRequest> executionrequestlist = null;
        List<Document> filters = new ArrayList<>();

        filters.add(new Document("status", ExecutionRequest.Status.PENDING.toString()));
        filters.add(new Document("position", 0));
        filters.add(new Document("queueName", queueName));

        Document findBy = new Document("$and", filters);

        try (MongoConnection c = db.newConnection()) {
            executionrequestlist = super.read(c, findBy);
        }
        return executionrequestlist;
    }

    public void transitionToComplete(ExecutionRequest request) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            String completionTime = DateUtil.currentDateTimeISOFormat();
            Document updateStatus = new Document("status", ExecutionRequest.Status.COMPLETED.toString());
            updateStatus.append("completionTime", completionTime);
            UpdateResult update = super.update(c, request.getId(), new Document("$set", updateStatus));

            if (update.getMatchedCount() > 0) {
                request.setStatus(Status.COMPLETED);
                request.setCompletionTime(completionTime);
            }
        }
    }

   /**
     * Tracks and updates Request statistics
     */
    public static class Statistics {
        private Document incDocument = null;
        private MongoConnectionFactory db;
        String id;
        private boolean clearInProgress = false;

        public Statistics(MongoConnectionFactory db, String id) {
            this.db = db;
            this.id = id;
        }

        private void appendToEnd(String key, long number) {
            if (incDocument == null) {
                incDocument = new Document(key, number);
            }
            else {
                incDocument.append(key, number);
            }
        }

        public Statistics addPassedTests(long passedTests) {
            appendToEnd("passedTests", passedTests);
            return this;
        }

        public Statistics addFailedTests(long failedTests) {
            appendToEnd("failedTests", failedTests);
            return this;
        }

        public Statistics addErroredTests(long erroredTests) {
            appendToEnd("testsInError", erroredTests);
            return this;
        }

        public Statistics addSkippedTests(long skippedTests) {
            appendToEnd("skippedTests", skippedTests);
            return this;
        }

        public Statistics addAbortedTests(long abortedTests) {
            appendToEnd("abortedTests", abortedTests);
            return this;
        }

        public Statistics addNonReleaseVettingTests(long nonReleaseVettingTests) {
            appendToEnd("nonReleaseVettingTests", nonReleaseVettingTests);
            return this;
        }

        public Statistics addInProgressTests(long inProgressTests) {
            appendToEnd("testsInProgress", inProgressTests);
            return this;
        }

        public Statistics addWarningTests(long warningTests) {
            appendToEnd("numWarnings", warningTests);
            return this;
        }

        public Statistics clearInProgressTests() {
            clearInProgress = true;
            return this;
        }

        public Statistics addRetriedTests(long retriedTests) {
            appendToEnd("testsRetried", retriedTests);
            return this;
        }

        /**
         * We're updating the database with all the statistics we've collected.
         * @param request Update the request with the updated statistics.
         * @throws Exception Mongo errors.
         */
        public void update(ExecutionRequest request) throws Exception {
            // Update the statistics first, then update the passed in request.
            update();

            ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(db);

            ExecutionRequest updatedRequest = dao.getById(id);

            request.setAbortedTests(updatedRequest.getAbortedTests());
            request.setFailedTests(updatedRequest.getFailedTests());
            request.setPassedTests(updatedRequest.getPassedTests());
            request.setSkippedTests(updatedRequest.getSkippedTests());
            request.setTestsInError(updatedRequest.getTestsInError());
            request.setNonReleaseVettingTests(updatedRequest.getNonReleaseVettingTests());
            request.setTestsRetried(updatedRequest.getTestsRetried());
        }

        /**
         * We're updating the database with all the statistics we've collected.
         * @throws Exception Mongo errors.
         */
        public void update() throws Exception {
             ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(db);

            try (MongoConnection c = db.newConnection()) {
                if (incDocument != null) {
                    if (clearInProgress) {
                        dao.update(c, id, new Document("$set", new Document("testsInProgress", 0)));
                    }
                    dao.update(c, id, new Document("$inc", incDocument));
                }
            }
        }


    }

    /**
     * Updates the status and the marks the execution complete. Today only used for
     * the abort case. The normal case this transition is done differently.
     * @param id     The {@link ExecutionRequest} unique id.
     * @param status {@link Status} to update
     * @throws Exception
     */
    public void markTestComplete(String id, Status status) throws Exception {
        ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(db);
        Document updateStatus = new Document("status", status.toString());
        if (Status.COMPLETED.equals(status)) {
            updateStatus.append("completionTime", DateUtil.currentDateTimeISOFormat());
        }
        try (MongoConnection c = db.newConnection()) {
            dao.update(c, id, new Document("$set", updateStatus));
        }
    }

    /**
     * Update the request status.
     * @param id     The {@link ExecutionRequest} unique id.
     * @param status {@link Status} to update
     * @throws Exception
     */
    public void updateStatus(String id, Status status) throws Exception {
        ExecutionRequestDAO dao = ExecutionRequestDAO.getInstance(db);
        Document updateStatus = new Document("status", status.toString());

        try (MongoConnection c = db.newConnection()) {
            dao.update(c, id, new Document("$set", updateStatus));
        }
    }

    /**
     * This function is responsible for getting all {@code ExecutionRequests} that
     * occurred between {@code dateFrom} and {@code dateTo}.  It will not load test
     * data.
     *
     * @param dateFrom start date
     * @param dateTo end date
     * @return {@link List<ExecutionRequest>}
     */
    public List<ExecutionRequest> getExecutionRequests(String dateFrom, String dateTo) throws Exception {
        List<ExecutionRequest> executionRequestList = new ArrayList<>();
        Integer total_count = 0;
        DateTime dateTimeStart;
        DateTime dateTimeEnd;
        if (dateFrom != null && dateTo != null) {
            String dateTimeStartPattern = DateUtil.dateFormatPattern(dateFrom);
            dateTimeStart = DateUtil.checkDateFormat(dateFrom, dateTimeStartPattern);
            String dateTimeEndPattern = DateUtil.dateFormatPattern(dateTo);
            dateTimeEnd = DateUtil.checkDateFormat(dateTo, dateTimeEndPattern);
            if (dateTimeEndPattern.matches("yyyy-MM-dd")) {
                dateTimeEnd = dateTimeEnd.plusSeconds(86399);
            }
        }
        else {
            return executionRequestList;
        }
        if (dateTimeStart.compareTo(dateTimeEnd) > 0) {
            return executionRequestList;
        }

        Document findBy = new Document();

        // Date Range query
        Document dateRangeQuery = new Document();
        dateRangeQuery.append("$gte", dateTimeStart.toString().replaceAll(".000Z", ""));
        dateRangeQuery.append("$lte", dateTimeEnd.toString().replaceAll(".000Z", ""));
        findBy.append("requestTime",  dateRangeQuery);

        try (MongoConnection c = db.newConnection()) {
            executionRequestList = read(c, findBy);

            if (executionRequestList != null) {
                for (ExecutionRequest request : executionRequestList) {
                    setPercentComplete(request);
                    setTimeToExecute(request);
                }
            }
        }
        return executionRequestList;
    }
}
