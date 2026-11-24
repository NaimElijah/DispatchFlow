package com.dispatchflow.archfixture.shared.adapter.out.clock;

import java.time.Clock;
import java.time.Instant;

/**
 * Allowed: a shared outbound adapter other than persistence is no part of the kernel either, so it
 * may read the system clock (ADR-130 decision 5).
 */
public final class SystemClockReadsTheClock {

    private SystemClockReadsTheClock() {}

    static Instant now() {
        return Clock.systemUTC().instant();
    }
}
