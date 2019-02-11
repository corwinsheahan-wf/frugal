package com.workiva.frugal.server;

/**
 * Provides an interface with which to handle events from an FServer.
 */
public interface FServerEventHandler {

    /**
     * Called when the FServer encounters a request that took an excess amount
     * of time to begin processing.
     */
    void onHighWatermark(long duration);
}
