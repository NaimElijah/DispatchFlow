package com.dispatchflow.archfixture.alpha.domain.logging;

/** Rule 4: System.Logger. */
public final class LogsWithTheSystemLogger {

    static System.Logger log() {
        return System.getLogger("alpha");
    }
}
