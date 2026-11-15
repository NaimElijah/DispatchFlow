package com.dispatchflow.planning.domain.plan;

import java.time.Instant;

/**
 * How Planning's commands record a time that a clock stepped back would put before an earlier one
 * (ADR-112). Each {@code restore} refuses times out of order, so a command never records one: it
 * takes the later of {@code now} and the time it must follow. A command is never refused for it,
 * because the caller is a worker or a listener that could only retry with the same clock.
 */
final class TimeOrder {

    private TimeOrder() {}

    /** The time a command records: {@code now}, or {@code earliest} when {@code now} is before it. */
    static Instant notBefore(Instant now, Instant earliest) {
        return now.isBefore(earliest) ? earliest : now;
    }
}
