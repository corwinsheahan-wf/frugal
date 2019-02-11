package com.workiva.frugal.server;

/**
 * Provides an interface with which to handle events from an FServer.
 */
public interface FServerEventHandler {

    /**
     * Called when the FServer encounters a request that took an excess amount
     * of time to begin processing.
     */
    void onHighWatermark(String correlationId, long duration);

    /**
     * Called when a new request is received, before being put into any work queues.
     */
    void onNewRequest(String correlationId);

    /**
     * Called when a request has been successfully processed.
     */
    void onFinishedRequest(String correlationId);
}
