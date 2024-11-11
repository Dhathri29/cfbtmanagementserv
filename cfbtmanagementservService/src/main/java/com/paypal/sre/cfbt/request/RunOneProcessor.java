/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.execution.request.statemachine.RequestState;
import com.paypal.sre.cfbt.execution.request.statemachine.StateMachineFactory;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.dal.ExecutionRequestDAO;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * This class is responsible to handler execution request which needs to be run one at a time.
 * (For example: Release request coming thru' 'Execute Now' option.)
 */
@Component
public class RunOneProcessor implements RequestProcessor{
    
    private final Logger LOGGER = LoggerFactory.getLogger(RunOneProcessor.class);
    private RequestProcessor requestProcessor;
    
    private List<Runnable> inprogressThreads = new ArrayList<>();
    private final DatabaseConfig dbConfig;

    public RunOneProcessor() {
        dbConfig = ConfigManager.getDatabaseConfig();
    }

    public void scheduleProcessing(ExecutionRequest request) {
        Runnable inprogressThread = new Runnable() {
            @Override
            public void run() {
                try{
                    StateMachineFactory.getNextState(request, dbConfig, new Scheduler(), Transitions.Message.BEGIN).begin();
                }catch(Exception ex) {
                    CFBTLogger.logError(LOGGER, RunOneProcessor.class.getCanonicalName(), "Execute runOne request failed with exception: " + ex.getMessage());
                }
                inprogressThreads.remove(this);
            }
        };
        new Thread(inprogressThread).start();
        inprogressThreads.add(inprogressThread);
    }

    @Override
    public void setNextHandler(RequestProcessor requestProcessor) {
        this.requestProcessor = requestProcessor;
    }

    @Override
    public ExecutionRequest process(ExecutionRequest request) throws Exception{
        if (request.getRunNow()) {
            try {
                ExecutionRequestDAO.getInstance(dbConfig.getConnectionFactory()).insert(request, null);
            } catch (Exception e) {
                throw new IllegalArgumentException();
            }
            if (request.getReleaseTest() != null) {
                request.getReleaseTest().setId(request.getId());
            }

            //Perform Parallel Execution
            this.scheduleProcessing(request);
            return request;

        } else {
            if (requestProcessor != null) {
                return requestProcessor.process(request);
            }
        }
        return request;
    }

    /**
     * Trigger a state event.
     * @param request The {@link ExecutionRequest} object
     * @param message The message that triggers the transition.
     * @return The {@link ExecutionRequest}
     * @throws Exception
     */
    @Override
    public ExecutionRequest triggerStateEvent(ExecutionRequest request, Transitions.Message message) throws Exception {
        ExecutionRequest transitionedRequest = null;
        if(request.getRunNow()) {
            RequestState state = StateMachineFactory.getNextState(request, dbConfig, new Scheduler(), message);
            //TBD: Move this in StateMachineFactory(PossibleState,AllowedMessages)
            switch (message) {
                case COMPLETE_TESTS: {
                    transitionedRequest = state.testsComplete();
                    break;
                }
                case ABORT: {
                    transitionedRequest = state.abort();
                    break;
                }
                case HALT: {
                    transitionedRequest = state.halt();
                    break;
                }
                default:
                    break;
            }
        } else {
            if (requestProcessor != null) {
                return requestProcessor.triggerStateEvent(request, message);
            }
        }
        return transitionedRequest;
    }

    /**
     * To trigger timeout event.
     * @param request Instance of {@link ExecutionRequest}.
     * @param action  Action that would result due to timeout.
     * @throws Exception
     */
    @Override
    public void triggerTimeout(ExecutionRequest request, ReleaseTest.Action action) throws Exception {
        if (request.getRunNow()) {
            try {
                ExecutionRequest loadedRequest = ExecutionRequestDAO.getInstance(dbConfig.getConnectionFactory()).getById(request.getId());

                if (loadedRequest == null) {
                    throw new IllegalStateException("This request is not found");
                }
                RequestState state = StateMachineFactory.getNextState(request, dbConfig, new Scheduler(), Transitions.Message.TIMEOUT);
                state.timeout(action);
            } catch (Exception ex) {
                ex.printStackTrace();
                CFBTLogger.logError(LOGGER, RunOneProcessor.class.getCanonicalName(), ex.getMessage());
            }
        } else {
            if (requestProcessor != null) {
                requestProcessor.triggerTimeout(request, action);
            }
        }
    }
}
