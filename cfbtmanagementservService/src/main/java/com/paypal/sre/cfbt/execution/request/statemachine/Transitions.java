/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.execution.request.statemachine;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.management.timeout.TimeoutManager;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.slf4j.LoggerFactory;

/**
 * Holds transitions based on the message received and the state it's currently in.
 */
public class Transitions {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Transitions.class);

    public enum Message {
        BEGIN("BEGIN"),
        COMPLETE("COMPLETE"),
        COMPLETE_TESTS("COMPLETE_TESTS"),
        DEPLOY_WAITING("DEPLOY_WAITING"),
        DEPLOY_COMPLETE("DEPLOY_COMPLETE"),
        EXTEND_DEPLOY("EXTEND_DEPLOY"),
        ABORT("ABORT"),
        HALT("HALT"), 
        TIMEOUT("TIMEOUT");
        
        private String value;
        
        Message(String value) {
            this.value = value;
        }  
        
        @Override
        public String toString() {
            return value;
        }
    }
    
    static public class Transition {
        private BiFunction<ExecutionRequest, Message, Boolean> condition;
        private RequestState state;

        Transition(BiFunction<ExecutionRequest, Message, Boolean> condition, RequestState state) {
            this.condition = condition;
            this.state = state;
        }
        
        public BiFunction<ExecutionRequest, Message, Boolean> getCondition() {
            return condition;
        }

        public RequestState getState() {
            return state;
        }
    }
    
    private final List<Transition> transitions = new ArrayList<>();
 
    /**
     * Add a transition to the system.
     * @param transition
     */
    public void add(Transition transition) {
        transitions.add(transition);
    }
    
    /**
     * Get transitions from the system.
     * @return 
     */
    public List<Transition> getTransition() {
        return transitions;
    }

 
    /**
     * Retrieve the state transition for this particular message and current state.
     * @param request {@link ExecutionRequest}
     * @param msg {@link Message} received from an external client.
     * @return RequestState The state object designed to process the state transition.
     */    
    public RequestState getNextState(ExecutionRequest request, Message msg) {
        StringBuilder stringBuilder = new StringBuilder("State transition, id = " + request.getId());
        stringBuilder.append(", Current state = ").append(request.getStatus());
        stringBuilder.append(", Message received = ").append(msg);

        CFBTLogger.logInfo(LOGGER, Transitions.class.getCanonicalName(), stringBuilder.toString());

        for (Transition transition : transitions) {
            if (transition.getCondition().apply(request, msg)) {
                return transition.getState();
            }
        }

        if (request.checkCompleted()) {
            //Cancel any existing schedules related to the request
            TimeoutManager manager = new TimeoutManager();
            manager.cancelTimeoutEvents(request);
        }

        throw new IllegalStateException("State transition not supported for request: id = " + request.getId() + ", msg = " + msg + ", status = " + request.getStatus());
    }
}
