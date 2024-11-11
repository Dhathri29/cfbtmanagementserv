/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.execution.request.statemachine;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions.Message;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.scheduler.Scheduler;

import java.util.List;

/**
 * Contains a reusable factory for this to be shared.
 */
public class StateMachineFactory {
    public static RequestState getNextState(ExecutionRequest request, DatabaseConfig db, Scheduler scheduler, Message message) {
        if (request.thisIsAdhoc()) {
            Transitions transitions = BasicStateMachine.getTransitions(request, db, scheduler);

            return transitions.getNextState(request, message);
        }
        else if (request.thisIsRelease()) {
            Transitions transitions = ReleaseStateMachine.getTransitions(request, db, scheduler);
            return transitions.getNextState(request, message);
        }

        throw new IllegalArgumentException("Unable to determine next state");
    }

    /**
     * This method supports transition of multiple deploy-waiting requests to In-Progress.
     */
    public static RequestState getNextState(List<ExecutionRequest> requests, DatabaseConfig db, Scheduler scheduler, Message message) {
        if (requests.get(0).thisIsRelease()) {
            Transitions transitions = ReleaseStateMachine.getTransitions(requests, db, scheduler);
            return transitions.getNextState(requests.get(0), message);
        }

        throw new IllegalArgumentException("Unable to determine next state");
    }
}
