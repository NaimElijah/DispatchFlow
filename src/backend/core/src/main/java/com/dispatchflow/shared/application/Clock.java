package com.dispatchflow.shared.application;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The time, as the application layer reads it (TS §2.1 rule 3, TS §6.4, NFR-13, NFR-175). The
 * domain never reads the clock (DL §3.9 rule 2): a use case asks this port and passes {@code now}
 * and {@code today} in. The system clock's adapter serves production; a clock a test can set comes
 * with the end-to-end profile (NFR-175).
 */
public interface Clock {

    /**
     * The current instant, cut to the microsecond. PostgreSQL keeps an instant to the microsecond,
     * so an instant with nanoseconds would come back from a save as another one, and an aggregate
     * whose rules compare it could then fail its load check.
     *
     * @return now, with no part below a microsecond
     */
    Instant now();

    /**
     * Today's date in {@code Asia/Jerusalem}, the one zone of the system (BR-31, ADR-088).
     *
     * @return the calendar day {@link #now()} falls on in Israel
     */
    LocalDate today();
}
