/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.execution.request.statemachine;

import com.google.common.base.Objects;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest.Status;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions.Message;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions.Transition;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import java.util.Arrays;
import java.util.List;

/**
 * The state machine when managing {@link ExecutionRequest} meant for releases.
 */
public class ReleaseStateMachine {
    
  
    private static Boolean isEqual(ExecutionRequest.Status status, ExecutionRequest req, Message msg1, Message msg2) {
        return status.equals(req.getStatus()) && msg1.equals(msg2);        
    }

    private static Boolean isEqual(List<ExecutionRequest.Status> statusList, ExecutionRequest req, Message msg1, Message msg2) {
        return statusList.stream().anyMatch(status -> status.equals(req.getStatus())) && msg1.equals(msg2);
    }
    
    /**
     * Return a list of transitions to support the state machine.
     * 
     * The begin message only makes sense if the request is pending.
     * deployComplete is only allowed in the Deploying State.
     * extendDeploy is allowed only in Deploying and Test Complete states.
     * The complete tests message only makes sense if result is in progress.
     * The abort, halt or cancel messages can come from any state, but the transition depends on the state it's in.
     * The complete message only makes sense if it's in testing complete.
     * 
     * @param request {@link ExecutionRequest}
     * @param db {@link DatabaseConfig}
     * @param scheduler {@link Scheduler}
     * @return {@link Transitions}
     */
    public static Transitions getTransitions(ExecutionRequest request, DatabaseConfig db, Scheduler scheduler) {
        Transitions transitions = new Transitions();
        boolean isFastPass = request.checkFastPass();
        
        transitions.add(new Transition((r, m) -> { return isEqual(Status.PENDING, r, Message.BEGIN, m);},
                        new Deploy(request, db, scheduler))); 
        transitions.add(new Transition((r, m) -> { return isEqual(Status.DEPLOYING, r, Message.DEPLOY_COMPLETE, m); },
                !isFastPass? new InProgress(request, db, scheduler):new Complete(request, db, scheduler)));
        transitions.add(new Transition((r, m) -> { return isEqual(Status.DEPLOYING, r, Message.DEPLOY_WAITING, m); },
                        new DeployWaiting(request, db, scheduler)));
        transitions.add(new Transition((r, m) -> { return isEqual(Status.DEPLOY_WAITING, r, Message.DEPLOY_COMPLETE, m); },
                        new InProgress(request, db, scheduler)));
        transitions.add(new Transition((r, m) -> { return isEqual(Arrays.asList(Status.IN_PROGRESS, Status.HALT_IN_PROGRESS), r, Message.COMPLETE_TESTS, m); },
                        new CompleteTests(request, db, scheduler)));
        transitions.add(new Transition((r, m) -> { return isEqual(Status.TESTING_COMPLETE, r, Message.COMPLETE_TESTS, m); },
                        new CompleteTests(request, db, scheduler)));
        transitions.add(new Transition((r, m) -> { return isEqual(Arrays.asList(Status.IN_PROGRESS, Status.HALT_IN_PROGRESS), r, Message.ABORT, m) ||
                                                          isEqual(Arrays.asList(Status.IN_PROGRESS, Status.HALT_IN_PROGRESS), r, Message.HALT, m);},
                        new CompleteTests(request, db, scheduler)));
        // If a request to halt or abort comes in any state other than in progress, transition immediately to complete.
        transitions.add(new Transition((r,m) -> {return (Objects.equal(Message.ABORT, m) || Objects.equal(Message.HALT,m)) &&
                                                        !Objects.equal(Status.IN_PROGRESS, r.getStatus());},
                        new Complete(request, db, scheduler)));
        transitions.add(new Transition((r, m) -> { return isEqual(Status.DEPLOYING, r, Message.TIMEOUT, m); },
                        new Complete(request, db, scheduler)));
        transitions.add(new Transition((r, m) -> { return isEqual(Status.TESTING_COMPLETE, r, Message.TIMEOUT, m); },
                        new Complete(request, db, scheduler)));
        transitions.add(new Transition((r, m) -> { return isEqual(Arrays.asList(Status.IN_PROGRESS, Status.HALT_IN_PROGRESS), r, Message.TIMEOUT, m); },
                        new CompleteTests(request, db, scheduler)));
        transitions.add(new Transition((r, m) -> { return isEqual(Status.TESTING_COMPLETE, r, Message.COMPLETE, m); },
                        new Complete(request, db, scheduler)));
        transitions.add(new Transition((r, m) -> { return isEqual(Arrays.asList(Status.IN_PROGRESS, Status.HALT_IN_PROGRESS), r, Message.COMPLETE, m); },
                        new CompleteTests(request, db, scheduler)));
        //If request to complete release comes in Deploying state (This may happen if pool does not exist in RVE) then transition to complete.
        transitions.add(new Transition((r, m) -> { return isEqual(Status.DEPLOYING, r, Message.COMPLETE, m); },
                        new Complete(request, db, scheduler)));
        return transitions;
    }


    /**
     * Return a list of transitions to support the state machine in case execution requests list.
     *
     *
     * @param requests The list of {@link ExecutionRequest}
     * @param db {@link DatabaseConfig}
     * @param scheduler {@link Scheduler}
     * @return {@link Transitions}
     */
    public static Transitions getTransitions(List<ExecutionRequest> requests, DatabaseConfig db, Scheduler scheduler) {
        Transitions transitions = new Transitions();
        transitions.add(new Transition((r, m) -> { return isEqual(Status.DEPLOY_WAITING, r, Message.DEPLOY_COMPLETE, m); },
                new InProgress(requests, db, scheduler)));

     return transitions;
    }
}
