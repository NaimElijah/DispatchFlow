package com.dispatchflow.archfixture.shared.adapter.out;

import java.time.Instant;

/**
 * Rule 2: a class directly in the kernel's {@code adapter.out}, in no adapter of its own, is still
 * the kernel's, so the purity rules hold it (ADR-130 decision 5).
 */
public final class DirectOutboundReadsTheClock {

    private DirectOutboundReadsTheClock() {}

    static Instant now() {
        return Instant.now();
    }
}
