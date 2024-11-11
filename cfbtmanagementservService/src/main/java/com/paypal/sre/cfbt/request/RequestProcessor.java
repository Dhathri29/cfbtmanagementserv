package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions;

/**
 * Interface to handle execution requests.
 */
public interface RequestProcessor {
    void setNextHandler(RequestProcessor requestProcessor);
    ExecutionRequest process(ExecutionRequest request) throws Exception;
    ExecutionRequest triggerStateEvent(ExecutionRequest request, Transitions.Message message) throws Exception;
    void triggerTimeout(ExecutionRequest request, ReleaseTest.Action action) throws Exception;
}
