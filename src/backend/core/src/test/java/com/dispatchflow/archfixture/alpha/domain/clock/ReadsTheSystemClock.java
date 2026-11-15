package com.dispatchflow.archfixture.alpha.domain.clock;

import java.time.Clock;

/** Rule 2: Clock.systemUTC(). */
public final class ReadsTheSystemClock {

    static Clock clock() {
        return Clock.systemUTC();
    }
}
