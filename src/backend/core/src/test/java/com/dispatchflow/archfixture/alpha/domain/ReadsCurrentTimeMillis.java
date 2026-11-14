package com.dispatchflow.archfixture.alpha.domain;

/** Rule 2: System.currentTimeMillis(). */
public final class ReadsCurrentTimeMillis {

    static long millis() {
        return System.currentTimeMillis();
    }
}
