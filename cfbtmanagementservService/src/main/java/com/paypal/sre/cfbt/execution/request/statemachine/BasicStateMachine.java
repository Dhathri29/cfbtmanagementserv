/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */

package com.paypal.sre.cfbt.execution.request.statemachine;

import com.google.common.base.Objects;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.Status;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions.Message;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.scheduler.Scheduler;

/**
 * The state machine for test suites without deployments or rollbacks.
 */
public class BasicStateMachine {

    /**
     * Return the list of supported state transitions for test execution suites.
     * 
     * @param request {@link ExecutionRequest} undergoing state transition.
     * @param db {@link DatabaseConfig} provides access to the database.
     * @param scheduler {@link Scheduler} provides access to timers.
     * @return list of transitions
     */
    public static Transitions getTransitions(ExecutionRequest request, DatabaseConfig db, Scheduler scheduler) {
        Transitions transitions = new Transitions();
                
        transitions.add(new Transitions.Transition((r, m) -> { return Objects.equal(Status.PENDING, r.getStatus()) && Objects.equal(Message.BEGIN, m);},
                        new InProgress(request, db, scheduler)));
        transitions.add(new Transitions.Transition((r, m) -> { return Objects.equal(Status.IN_PROGRESS, r.getStatus()) && Objects.equal(Message.COMPLETE_TESTS, m); },
                         new Complete(request, db, scheduler)));
        transitions.add(new Transitions.Transition((r, m) -> { return Objects.equal(Status.IN_PROGRESS, r.getStatus()) && Objects.equal(Message.TIMEOUT, m); },
                new Complete(request, db, scheduler)));
        transitions.add(new Transitions.Transition((r, m) -> { return Objects.equal(Status.TESTING_COMPLETE, r.getStatus()) && Objects.equal(Message.COMPLETE_TESTS, m); },
                new Complete(request, db, scheduler)));
        transitions.add(new Transitions.Transition((r,m) -> {return Objects.equal(Message.ABORT, m); }, 
                        new Complete(request, db, scheduler)));
        transitions.add(new Transitions.Transition((r,m) -> {return Objects.equal(Message.HALT, m); },
                        new Complete(request, db, scheduler)));
        return transitions;
    }
}
