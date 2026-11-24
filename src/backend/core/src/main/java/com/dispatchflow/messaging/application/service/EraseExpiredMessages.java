package com.dispatchflow.messaging.application.service;

import com.dispatchflow.audit.application.api.CallContext;

/**
 * The nightly retention job's Messaging step (NFR-184, NFR-193, NFR-260, ADR-075, ADR-108): it
 * deletes each stop's messages 90 days after the last reached its state, deletes replies 90 days
 * after receipt, and removes each due rating request's phone and comments through {@code
 * RatingRequest.removePersonalData}, one request per transaction, keeping its rating. The opt-out
 * history stays (NFR-202). The scheduler calls it at 03:00 {@code Asia/Jerusalem} (DL §10.3).
 *
 * <p>It opens no transaction of its own: {@link EraseExpiredMessagesChange} deletes the messages
 * and replies due in one, lists the requests due, and clears each request in one of its own
 * (ADR-129).
 *
 * <p>To Be Implemented. It comes with the whole nightly retention run (ADR-121). Rows: NFR-184,
 * NFR-193, NFR-260, ADR-108.
 */
public final class EraseExpiredMessages {

    private final EraseExpiredMessagesChange change;

    /**
     * Creates the job.
     *
     * @param change the transactions that delete the messages and replies due, list the requests
     *     due, and clear each request
     */
    public EraseExpiredMessages(EraseExpiredMessagesChange change) {
        this.change = change;
    }

    /**
     * Erases what is due. A request another writer saved meanwhile is left for the next night.
     *
     * <p>To Be Implemented.
     *
     * @param call the job's server step
     * @return how many stops' messages, replies and requests it deleted or cleared, together
     */
    public int erase(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
