/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.execution.request.statemachine;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;

/**
 * Base class for all Execution Request States.
 */
abstract public class RequestState {

    /**
     * The signal sent when all test executions have finished.
     * @return {@link ExecutionRequest}
     * @throws java.lang.Exception Should be overriden
     */
    public ExecutionRequest testsComplete() throws Exception {
        throw new UnsupportedOperationException(this.getClass().getName() + "does not support testsComplete" );
    }

    /**
     * The signal sent when the client has decisioned the request after tests have finished.
     * 
     * @return {@link ExecutionRequest}
     * @throws Exception Should be overriden
     */
    public ExecutionRequest complete() throws Exception {
        throw new UnsupportedOperationException(this.getClass().getName() + "does not support complete");
    }

    /**
     * The signal to pick up the Execution Request from the queue.
     * @return  {@link ExecutionRequest}
     * @throws Exception Should be overriden
     */
    public ExecutionRequest begin() throws Exception {
        throw new UnsupportedOperationException(this.getClass().getName() + "does not support begin"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * The signal to hold the deploying state.
     * @return  {@link ExecutionRequest}
     * @throws Exception Should be overriden
     */
    public ExecutionRequest deployWaiting() throws Exception {
        throw new UnsupportedOperationException(this.getClass().getName() + "does not support begin");
    }

    /**
     * Message signaling deployment finished.
     * @return {@link ExecutionRequest}
     * @throws Exception On Transition
     */
    public ExecutionRequest deployComplete() throws Exception {
        throw new UnsupportedOperationException(this.getClass().getName() + "does not support deployComplete"); 
    }

    /**
     * Cancel pending test executions, immediately stop running executions.
     *
     * @return {@link ExecutionRequest}
     * @throws Exception Should be overriden
     */
    public ExecutionRequest abort() throws Exception {
        throw new UnsupportedOperationException(this.getClass().getName() + "does not support abort");
    }

    /**
     * Cancel test executions that have not yet started, allow those that have started to finish.
     * @return {@link ExecutionRequest}
     * @throws java.lang.Exception
     */
    public ExecutionRequest halt() throws Exception {
        throw new UnsupportedOperationException(this.getClass().getName() + "does not support halt" );
    }

    /**
     * To signal timeout.
     * @return {@link ExecutionRequest}
     * @throws Exception
     */
    public ExecutionRequest timeout(ReleaseTest.Action action) throws Exception {
        throw new UnsupportedOperationException(this.getClass().getName() + "does not support timeout"); 
    }
}
