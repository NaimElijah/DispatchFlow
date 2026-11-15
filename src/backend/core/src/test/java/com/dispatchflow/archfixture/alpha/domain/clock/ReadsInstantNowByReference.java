package com.dispatchflow.archfixture.alpha.domain.clock;

import java.time.Instant;
import java.util.function.Supplier;

/** Rule 2: Instant::now. */
public final class ReadsInstantNowByReference {

    static Supplier<Instant> clock() {
        return Instant::now;
    }
}
