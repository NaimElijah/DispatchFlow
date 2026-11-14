package com.dispatchflow.archfixture.alpha.domain;

import java.time.Instant;

/** Rule 2: Instant.now(). */
public final class ReadsInstantNow {

    static Instant now() {
        return Instant.now();
    }
}
