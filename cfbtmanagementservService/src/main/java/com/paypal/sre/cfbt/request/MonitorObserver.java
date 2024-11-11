/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import java.util.List;

/**
 * Interface to get status for our monitoring service.
 */
public interface MonitorObserver {

    /**
     * Whenever a state transition occurs this method is called.
     * @param request {@link ExecutionRequest} list.
     */
    void onEvent(List<ExecutionRequest> request);    
}