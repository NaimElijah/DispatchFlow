package com.dispatchflow.archfixture.alpha.domain.io.classpath;

/** Rule 4: Class.getResourceAsStream reads the classpath. */
public final class ReadsAResourceAsAStream {

    static boolean hasDefaults() {
        return ReadsAResourceAsAStream.class.getResourceAsStream("defaults.properties") != null;
    }
}
