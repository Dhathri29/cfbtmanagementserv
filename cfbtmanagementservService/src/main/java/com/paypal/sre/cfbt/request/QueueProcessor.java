package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.execution.request.statemachine.Transitions;
import com.paypal.sre.cfbt.management.dal.DatabaseConfig;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible to handler execution request which needs to be queued.
 * (For example: Release vetting request.)
 */
public class QueueProcessor implements RequestProcessor{

    private final Logger mLogger = LoggerFactory.getLogger(QueueProcessor.class);
    private RequestProcessor requestProcessor;
    private final DatabaseConfig dbConfig;
    private final String queueName;

    public QueueProcessor() {
        dbConfig = ConfigManager.getDatabaseConfig();
        queueName = Queue.RELEASE_VETTING_QUEUE_NAME;
    }

    public QueueProcessor(DatabaseConfig dbConfig, String queueName) {
        this.dbConfig = dbConfig;
        this.queueName = queueName;
    }

    @Override
    public void setNextHandler(RequestProcessor requestProcessor) {
        this.requestProcessor = requestProcessor;
    }

    @Override
    public ExecutionRequest process(ExecutionRequest request) throws Exception {
        Queue queue = Queue.releaseVettingQueue(ConfigManager.getDatabaseConfig());
        Scheduler scheduler = new Scheduler();
        QueueMonitor monitor = new QueueMonitor(queue, scheduler, dbConfig, queueName);
        monitor.monitorQueueEvents();
        request = queue.enqueue(request, request.getPriority());

        if (request.getReleaseTest() != null && request != null) {
            request.getReleaseTest().setId(request.getId());
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
        Queue queue = new Queue(dbConfig, request.getQueueName());
        Scheduler scheduler = new Scheduler();
        QueueMonitor monitor = new QueueMonitor(queue, scheduler, dbConfig, queueName);
        return (monitor.triggerStateEvent(request, message));
    }

    /**
     * This method triggers timeout.
     *
     * @param request Instance of {@link ExecutionRequest}.
     * @param action  Action that would result due to timeout.
     */
    public void triggerTimeout(ExecutionRequest request, ReleaseTest.Action action) throws Exception {
        Queue queue = new Queue(dbConfig, request.getQueueName());
        Scheduler scheduler = new Scheduler();
        QueueMonitor monitor = new QueueMonitor(queue, scheduler, dbConfig, queueName);
        monitor.triggerTimeout(request, action);
    }
}
