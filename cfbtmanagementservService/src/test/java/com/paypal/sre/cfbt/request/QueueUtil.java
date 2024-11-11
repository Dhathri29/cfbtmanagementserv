/*
 * (C) 2020 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 */
public class QueueUtil {
    static void queueAndWait(ExecutionRequest request, Queue queue, QueueMonitor queueMonitor) throws InterruptedException, ExecutionException, TimeoutException, Exception {
        CompletableFuture localResult = new CompletableFuture<>();

        queueMonitor.subscribe((List<ExecutionRequest> request1) -> {
            localResult.complete(request1);
        });
        queue.enqueue(request, request.getPriority());

        localResult.get(10, TimeUnit.SECONDS);
    }    
}
