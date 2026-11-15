package com.dispatchflow.archfixture.alpha.domain.clock;

import java.util.concurrent.TimeUnit;

/** Rule 2: TimeUnit.sleep waits on the system clock, as Thread.sleep does. */
public final class SleepsThroughATimeUnit {

    static void pause() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(1);
    }
}
