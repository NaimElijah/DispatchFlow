package com.dispatchflow.fleet.application.service.driver;

import com.dispatchflow.audit.application.api.CallContext;

/**
 * The nightly retention job's driver step: {@code Driver.anonymize} removes the name and phone of
 * every driver retired a year ago or more, in Israel's calendar, and keeps the record (BR-98,
 * NFR-184, ADR-075). Each record is anonymized in its own transaction, so one stale save doesn't
 * hold back the rest; the next night retries it. A driver whose retention isn't over refuses with
 * {@code RetentionNotOver}, and the job moves on.
 *
 * <p>It lists the drivers due, then anonymizes them one by one, and opens no transaction of its
 * own: {@link AnonymizeRetiredDriversChange} holds each driver's (ADR-129).
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
public final class AnonymizeRetiredDrivers {

    private final AnonymizeRetiredDriversChange change;

    /**
     * Creates the job.
     *
     * @param change the transaction that lists the drivers due, and each driver's own
     */
    public AnonymizeRetiredDrivers(AnonymizeRetiredDriversChange change) {
        this.change = change;
    }

    /**
     * Anonymizes the drivers whose retention is over.
     *
     * <p>Built in M13.
     *
     * @param call the job's server step
     * @return how many drivers were anonymized
     */
    public int anonymizeDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
