package com.dispatchflow.archfixture.alpha.domain.io.classpath;

/** Rule 4: Class.getResource reads the classpath. */
public final class ReadsAResourceThroughItsClass {

    static boolean hasDefaults() {
        return ReadsAResourceThroughItsClass.class.getResource("defaults.properties") != null;
    }
}
