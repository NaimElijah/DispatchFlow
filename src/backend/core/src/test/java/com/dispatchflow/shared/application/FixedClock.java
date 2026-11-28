package com.dispatchflow.shared.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A {@link Clock} that never moves, for tests that call a use case or an adapter at a time of their
 * choosing. It answers the two values it is given, so a test can set {@code today} apart from {@code
 * now}, such as a date past a plan's; the two need not agree in Israel.
 */
public final class FixedClock implements Clock {

    private final Instant now;
    private final LocalDate today;

    /**
     * Creates the clock.
     *
     * @param now what {@link #now()} answers; cut to the microsecond, as the port promises
     * @param today what {@link #today()} answers
     */
    public FixedClock(Instant now, LocalDate today) {
        this.now = Objects.requireNonNull(now, "now");
        this.today = Objects.requireNonNull(today, "today");
    }

    @Override
    public Instant now() {
        return now;
    }

    @Override
    public LocalDate today() {
        return today;
    }
}
