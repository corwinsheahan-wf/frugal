package com.workiva.frugal.server;

import com.workiva.frugal.FContext;

/**
 * Provides an interface with which to handle events from an FServer.
 */
public interface FServerEventHandler {

    void onRequestReceived(FContext fctx);
    void onRequestStarted(FContext fctx);
    void onRequestEnded(FContext fctx);
}
