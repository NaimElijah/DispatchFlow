package com.dispatchflow.shared.application;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The time, as the application layer reads it (TS §2.1 rule 3, TS §6.4, NFR-13, NFR-175). The
 * domain never reads the clock (DL §3.9 rule 2): a use case asks this port and passes {@code now}
 * and {@code today} in. The system clock's adapter serves production, and the end-to-end profile's
 * adapter can be set by a test (NFR-175).
 *
 * <p>Built in M0.
 */
public interface Clock {

    /**
     * The current instant.
     *
     * <p>Built in M0.
     *
     * @return now
     */
    Instant now();

    /**
     * Today's date in {@code Asia/Jerusalem}, the one zone of the system (BR-31, ADR-088).
     *
     * <p>Built in M0.
     *
     * @return the calendar day {@link #now()} falls on in Israel
     */
    LocalDate today();
}
