package com.dispatchflow.archfixture.shared.adapter.util;

import java.time.Instant;

/**
 * Rule 2: a package below the kernel's adapter that is neither inbound nor outbound is still the
 * kernel's, so the purity rules hold it (ADR-130 decision 5).
 */
public final class AdapterUtilityReadsTheClock {

    private AdapterUtilityReadsTheClock() {}

    static Instant now() {
        return Instant.now();
    }
}
