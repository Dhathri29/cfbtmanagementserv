package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.test.Component;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.features.FeatureManager;
import com.paypal.sre.cfbt.shared.DateUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds logic for determining if a request {@link ExecutionRequest} can be batched
 *
 */
public final class BatchRules {
    private static final String EVERGREEN_USER = "_Evergreen_Tools";
    private static final String EVERGREEN_BATCHING_FEATURE = "Evergreen_Batching";
    private static final int EVERGREEN_MAX_WITH_TESTS = 10;
    private List<ExecutionRequest> batchedRequests = new ArrayList<>();
    private final ExecutionRequest newRequest;
    private final int priority;
    private final DatabaseConfig db;

    public BatchRules(DatabaseConfig db, List<ExecutionRequest> alreadyBatchedRequests, ExecutionRequest theNewRequest, int existingPriority)  {
        this.db = db;
        batchedRequests = alreadyBatchedRequests;
        newRequest = theNewRequest;
        priority = existingPriority;
    }

    private boolean checkNoTestBatchRule() {
        if (allFastPass() && newRequest.getPriority() != ExecutionRequest.HIGH_PRIORITY) {
            return true;
        }
        if (newRequest.checkFastPass() && allFastPass()) {
            return true;
        }
        if (newRequest.checkFastPass() && priority != ExecutionRequest.HIGH_PRIORITY) {
            return true;
        }
        return false;
    }

    private boolean isRetry(ExecutionRequest req) {
        if (req.getReleaseTest() == null) {
            return false;
        }
        if (req.getReleaseTest().getIsRetry() == null) {
            return false;
        }
        if (req.getReleaseTest().getIsRetry() == Boolean.TRUE) {
            return true;
        }
        return false;
    }

    /**
     * For evergreen batching, we need to verify:
     * - less than 10 with tests in batch
     * - not a retry
     * - user requesting is EVERGREEN_USER
     * - batch we're going into is is only EVERGREEN_USER or fastpass
     *
     * @return true iff the newRequest can be batched with the batchedRequests
     */
    private boolean checkEvergreenBatchRule() {
        if (FeatureManager.instance().checkFeatureOn(db.getConnectionFactory(), EVERGREEN_BATCHING_FEATURE)) {
            if (!isRetry(newRequest) &&
                numWithTests() < EVERGREEN_MAX_WITH_TESTS &&
                isEvergreen(newRequest) &&
                allEvergreen()) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldBatch() {
        // we don't batch adhoc requests in any way right now
        if (newRequest.thisIsAdhoc() || isAdhoc()) {
            return false;
        }

        // check each of our rules. if any applies then we can batch.
        // note that for now the two rules are hard coded as separate functions.
        if (!componentsInCommon(newRequest)) {
            if (checkNoTestBatchRule()) {
                return true;
            }
            if (checkEvergreenBatchRule()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if none of the requests in the list has tests.
     */
    private boolean allFastPass() {
        return batchedRequests.stream().allMatch((request) -> request.checkFastPass());
    }

    /**
     * @return true if this request is an evergreen request
     */
    private boolean isEvergreen(ExecutionRequest request) {
        return (EVERGREEN_USER.equals(request.getRequestUser()));
    }

    /**
     * @return the number of requests in the current batch which have tests
     */
    private int numWithTests() {
        int num = 0;
        for (ExecutionRequest request:batchedRequests) {
            if (!request.checkFastPass()) {
                num++;
            }
        }
        return num;
    }

    /**
     * @return true if all the requests in the queue are either evergreen or fastpass
     */
    private boolean allEvergreen() {
        return batchedRequests.stream().allMatch((request) -> isEvergreen(request) || request.checkFastPass());
    }

    private boolean isAdhoc() {
        if (batchedRequests.stream().anyMatch((request) -> (request.thisIsAdhoc()))) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if there are no components in request found in any of the components in batchedRequests
     *
     * @param request
     * @return true if no components in common, false otherwise.
     */
    private boolean componentsInCommon(ExecutionRequest request) {

        if (batchedRequests == null || request == null || request.thisIsAdhoc()) {
            return false;
        }

        for (ExecutionRequest thisRequest : batchedRequests) {
            if (thisRequest.thisIsRelease()) {
                for (Component component : thisRequest.getReleaseTest().getComponents()) {
                    for (Component newComponent : request.getReleaseTest().getComponents()) {
                        if (component.getName().equals(newComponent.getName())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}