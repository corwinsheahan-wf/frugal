package com.workiva.frugal.server;

import java.util.Map;

/**
 * Provides an interface with which to handle events from an FServer.
 */
public interface FServerEventHandler {

    void onRequestReceived(Map<Object, Object> ephemeralProperties);
    void onRequestStarted(Map<Object, Object> ephemeralProperties);
    void onRequestEnded(Map<Object, Object> ephemeralProperties);
}
