package com.dispatchflow.archfixture.alpha.domain;

/** Rule 4: System.getenv. */
public final class ReadsTheEnvironment {

    static String home() {
        return System.getenv("HOME");
    }
}
