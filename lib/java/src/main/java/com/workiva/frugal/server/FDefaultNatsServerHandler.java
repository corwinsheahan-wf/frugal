package com.workiva.frugal.server;

import com.workiva.frugal.FContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default event handler for an FNatsServer.
 */
public class FDefaultNatsServerHandler implements FServerEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FDefaultNatsServerHandler.class);

    private long highWatermark;

    public FDefaultNatsServerHandler(long highWatermark) {
        this.highWatermark = highWatermark;
    }

    @Override
    public void onRequestReceived(FContext fctx) {
        long now = System.currentTimeMillis();
        fctx.addEphemeralProperty("_request_received_millis", now);
    }

    @Override
    public void onRequestStarted(FContext fctx) {
        if (fctx.getEphemeralProperty("_request_received_millis") != null) {
            long started = (long) fctx.getEphemeralProperty("_request_received_millis");
            long duration = System.currentTimeMillis() - started;
            if (duration > highWatermark) {
                LOGGER.warn(String.format(
                        "request spent %d ms in the transport buffer, your consumer might be backed up", duration));
            }
        }
    }

    @Override
    public void onRequestEnded(FContext fctxs) {

    }
}
