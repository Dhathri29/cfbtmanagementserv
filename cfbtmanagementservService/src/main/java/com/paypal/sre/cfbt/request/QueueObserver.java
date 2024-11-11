/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions;

import java.util.List;

/**
 * The interface used to subscribe to enqueue events.
 */
public interface QueueObserver {    
    public void onDequeueEvent(List<ExecutionRequest> dequenedRequests);
    public void onEnqueueEvent(List<ExecutionRequest> runningRequests);
}
