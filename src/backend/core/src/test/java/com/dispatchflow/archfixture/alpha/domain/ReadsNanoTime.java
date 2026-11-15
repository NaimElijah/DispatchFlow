package com.dispatchflow.archfixture.alpha.domain;

/** Rule 2: System.nanoTime(). */
public final class ReadsNanoTime {

    static long nanos() {
        return System.nanoTime();
    }
}
