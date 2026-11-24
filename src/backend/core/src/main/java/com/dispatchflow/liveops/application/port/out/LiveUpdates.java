package com.dispatchflow.liveops.application.port.out;

/**
 * Tells the open screens what changed, through the server-sent event stream's emitter registry
 * (ADR-026, TS §6.4). A use case calls it after its commit; a missed update costs a screen one
 * refetch, so it is no event (DL §7.1).
 *
 * <p>To Be Implemented.
 */
public interface LiveUpdates {

    /**
     * Sends an update to every open stream allowed to see it. It never fails the caller: a stream
     * that can't take it is dropped, and its client reads the state again when it reconnects.
     *
     * <p>To Be Implemented.
     *
     * @param update what changed
     */
    void publish(LiveUpdate update);
}
