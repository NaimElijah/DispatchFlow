package com.dispatchflow.archfixture.alpha.domain.io;

/** Rule 4: ClassLoader.getSystemResource reads the classpath. */
public final class ReadsAResourceThroughAClassLoader {

    static boolean hasDefaults() {
        return ClassLoader.getSystemResource("defaults.properties") != null;
    }
}
