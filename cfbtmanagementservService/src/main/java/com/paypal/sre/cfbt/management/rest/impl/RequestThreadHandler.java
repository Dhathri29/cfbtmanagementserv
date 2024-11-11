package com.paypal.sre.cfbt.management.rest.impl;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest.Action;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions;
import com.paypal.sre.cfbt.request.RunNowProcessor;
import com.paypal.sre.cfbt.request.RunOneProcessor;
import com.paypal.sre.cfbt.request.QueueProcessor;
import com.paypal.sre.cfbt.request.RequestProcessor;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.shared.CFBTLogger;

public class RequestThreadHandler implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestThreadHandler.class);
    private List<ExecutionRequest> requestList;
    private Transitions.Message message;
    private final DatabaseConfig dbConfig;
    private final String queueName;
    private List<RequestProcessor> requestProcessors;

    /**
     * Constructor for this object.
     * 
     * @param dbConfig The {@link DatabaseConfig}
     * @param queueName
     */
    public RequestThreadHandler(DatabaseConfig dbConfig, String queueName) {
        this.dbConfig = dbConfig;
        this.queueName = queueName;
        this.requestProcessors = new ArrayList<>();
        requestProcessors.add(new RunOneProcessor());
        requestProcessors.add(new RunNowProcessor());
        requestProcessors.add(new QueueProcessor(dbConfig, queueName));
        for (int i = 0; i < requestProcessors.size() - 1; i++) {
            requestProcessors.get(i).setNextHandler(requestProcessors.get(i+1));
        }
    }

    /**
     * Transition statuses is O(n) and can be expensive, so it's done asyncronously.
     *
     * @param requestList
     *            The list of requests to transition.
     * @param message
     */
    public void asyncHandler(List<ExecutionRequest> requestList, Transitions.Message message) {
        this.requestList = requestList;
        this.message = message;
        new Thread(this).start();
    }

    ExecutionRequest completeRequest(ExecutionRequest request, Transitions.Message message) throws Exception {
        if (request.checkCompleted()) {
            return request;
        }

        return triggerEvent(request, message);
    }

    /**
     * Method to trigger the state event for the halt and emergency stop signal.
     *
     * @param request
     * @param message
     * @return {@link ExecutionRequest} the transitioned request.
     * @throws Exception
     */
    public ExecutionRequest triggerEvent(ExecutionRequest request, Transitions.Message message) throws Exception {
        ExecutionRequest transitionedRequest = null;
        if(requestProcessors != null && !requestProcessors.isEmpty()) {
            transitionedRequest = (requestProcessors.get(0).triggerStateEvent(request, message));
        }

        if (transitionedRequest == null) {
            throw new IllegalStateException("Could not accept the transitioned state");
        }
        return transitionedRequest;
    }

    /**
     * Method to trigger the state event during timeout.
     * @param request instance of {@link ExecutionRequest}
     * @param action enum {@link Action}
     * @throws Exception
     */
    public void triggerTimeout(ExecutionRequest request, Action action) throws Exception {
        if(requestProcessors != null && !requestProcessors.isEmpty()) {
            requestProcessors.get(0).triggerTimeout(request, action);
        }
    }

    @Override
    public void run() {
        CFBTLogger.logInfo(LOGGER, "REQUEST_THREAD_HANDLER", "Request thread handler");
        try {
            // Transition pending requests first, then the running ones.
            for (ExecutionRequest request: requestList) {
                if (request.getStatus().equals(ExecutionRequest.Status.PENDING)) {
                    triggerEvent(request, message);
                }
            }
            for (ExecutionRequest request: requestList) {
                if (!request.getStatus().equals(ExecutionRequest.Status.PENDING)) {
                    triggerEvent(request, message);
                }
            }
        } catch (Exception ex) {
            CFBTLogger.logError(LOGGER, CFBTLogger.CalEventEnum.EMERGENCY_STOP,
                    "Error trying to process request threads : ", ex);
        }
    }
}
