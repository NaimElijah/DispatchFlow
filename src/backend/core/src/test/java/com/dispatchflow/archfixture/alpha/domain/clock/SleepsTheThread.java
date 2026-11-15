package com.dispatchflow.archfixture.alpha.domain.clock;

/** Rule 2: Thread.sleep waits on the system clock. */
public final class SleepsTheThread {

    static void pause() throws InterruptedException {
        Thread.sleep(1);
    }
}
