package com.dispatchflow.archfixture.alpha.domain.io;

/** Rule 4: Class.getResource reads the classpath. */
public final class ReadsAResourceThroughItsClass {

    static boolean hasDefaults() {
        return ReadsAResourceThroughItsClass.class.getResource("defaults.properties") != null;
    }
}
