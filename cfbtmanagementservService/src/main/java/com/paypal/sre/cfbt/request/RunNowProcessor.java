package com.paypal.sre.cfbt.request;

import com.ebay.kernel.cal.api.CalStatus;
import com.ebay.kernel.cal.api.CalTransaction;
import com.ebay.kernel.cal.api.sync.CalTransactionFactory;
import com.paypal.infra.util.cal.CalType;
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
import org.slf4j.LoggerFactory;

/**
 * This class is responsible to handler execution request which needs to be run immediately.
 * (For example: Release request with No tests except remediation/ rollback requests.)
 */
public class RunNowProcessor implements RequestProcessor {
    private RequestProcessor requestProcessor;
    private final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(RunNowProcessor.class);
    private final DatabaseConfig dbConfig;

    public RunNowProcessor() {
        dbConfig = ConfigManager.getDatabaseConfig();
    }

    @Override
    public void setNextHandler(RequestProcessor requestProcessor) {
        this.requestProcessor = requestProcessor;
    }

    @Override
    public ExecutionRequest process(ExecutionRequest request) throws Exception {

        if (request.checkRunNow()) {
            request.markAsRunNow(true);
            try {
                ExecutionRequestDAO.getInstance(dbConfig.getConnectionFactory()).insert(request, null);
            } catch (Exception e) {
                throw new IllegalArgumentException();
            }
            if (request.getReleaseTest() != null) {
                request.getReleaseTest().setId(request.getId());
            }

            final CalTransaction calTransaction = CalTransactionFactory.create(CalType.URL.toString());
            calTransaction.setName("CFBT.RunNowProcessor");
            calTransaction.addData("CFBT RunNowProcessor: transitionPendedRequest");
            CFBTLogger.logInfo(LOGGER, RunNowProcessor.class.getCanonicalName(), "Transitioning " + request.getId());

            try {
                StateMachineFactory.getNextState(request, dbConfig, new Scheduler(), Transitions.Message.BEGIN).begin();
                calTransaction.setStatus(CalStatus.SUCCESS);
            } catch (Exception ex) {
                CFBTLogger.logError(LOGGER, RunNowProcessor.class.getName(), ex.getMessage(), ex);
                calTransaction.setStatus(ex);
            } finally {
                calTransaction.completed();
            }
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
        if (request.thisIsRunNow()) {
            RequestState state = StateMachineFactory.getNextState(request, dbConfig, new Scheduler(), message);
            //TBD: Move this in StateMachineFactory(PossibleState,AllowedMessages)
            switch (message) {
                case DEPLOY_COMPLETE: {
                    transitionedRequest = state.deployComplete();
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
                case COMPLETE: {
                    transitionedRequest = state.complete();
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
     * This method triggers timeout.
     *
     * @param request Instance of {@link ExecutionRequest}.
     * @param action  Action that would result due to timeout.
     */
    public void triggerTimeout(ExecutionRequest request, ReleaseTest.Action action) throws Exception {
        if (request.thisIsRunNow()) {
            try {
                ExecutionRequest loadedRequest = ExecutionRequestDAO.getInstance(dbConfig.getConnectionFactory()).getById(request.getId());

                if (loadedRequest == null) {
                    throw new IllegalStateException("This request is not found");
                }
                RequestState state = StateMachineFactory.getNextState(request, dbConfig, new Scheduler(), Transitions.Message.TIMEOUT);
                state.timeout(action);
            } catch (Exception ex) {
                ex.printStackTrace();
                CFBTLogger.logError(LOGGER, RunNowProcessor.class.getCanonicalName(), ex.getMessage());
            }
        } else {
            if (requestProcessor != null) {
                requestProcessor.triggerTimeout(request, action);
            }
        }
    }
}
