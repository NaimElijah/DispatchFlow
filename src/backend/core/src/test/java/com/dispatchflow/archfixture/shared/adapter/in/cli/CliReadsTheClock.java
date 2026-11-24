package com.dispatchflow.archfixture.shared.adapter.in.cli;

import java.time.Instant;

/**
 * Allowed: an inbound adapter beside the kernel is no part of it, so it may read the system clock
 * (ADR-130 decision 5).
 */
public final class CliReadsTheClock {

    private CliReadsTheClock() {}

    static Instant now() {
        return Instant.now();
    }
}
