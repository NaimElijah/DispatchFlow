package com.dispatchflow.shared.adapter.out.clock;

import com.dispatchflow.shared.IsraelDateTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * The {@link com.dispatchflow.shared.application.Clock} of production: the system's UTC clock,
 * cut to the microsecond that PostgreSQL keeps, with today's date read in Israel (ADR-088), never
 * in the JVM's zone or the source clock's.
 */
@Component
public final class SystemClock implements com.dispatchflow.shared.application.Clock {

    private final java.time.Clock source;

    /** Creates the clock over the system's UTC clock; the one Spring calls. */
    public SystemClock() {
        this(java.time.Clock.systemUTC());
    }

    /**
     * Creates the clock over a given source, so a test can fix the time.
     *
     * @param source the clock to read; its zone is ignored
     */
    SystemClock(java.time.Clock source) {
        this.source = source;
    }

    /**
     * The current instant, cut to the microsecond.
     *
     * @return now, with no part below a microsecond
     */
    @Override
    public Instant now() {
        return Instant.now(source).truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Today's date in {@code Asia/Jerusalem}.
     *
     * @return the calendar day {@link #now()} falls on in Israel
     */
    @Override
    public LocalDate today() {
        return LocalDate.ofInstant(now(), IsraelDateTime.ZONE);
    }
}
