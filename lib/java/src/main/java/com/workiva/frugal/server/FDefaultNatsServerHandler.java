package com.workiva.frugal.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default event handler for an FNatsServer.
 */
public class FDefaultNatsServerHandler implements FServerEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FDefaultNatsServerHandler.class);

    @Override
    public void onHighWatermark(String correlationId, long duration) {
        LOGGER.warn(String.format(
                "request spent %d ms in the transport buffer, your consumer might be backed up", duration));
    }

    @Override
    public void onNewRequest(String correlationId) {
        // Do nothing by default
    }

    @Override
    public void onFinishedRequest(String correlationId) {
        // Do nothing by default
    }
}
