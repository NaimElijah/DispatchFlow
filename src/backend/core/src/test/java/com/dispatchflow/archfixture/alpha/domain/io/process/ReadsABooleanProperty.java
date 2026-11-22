package com.dispatchflow.archfixture.alpha.domain.io.process;

/** Rule 4: Boolean.getBoolean reads a system property. */
public final class ReadsABooleanProperty {

    static boolean strict() {
        return Boolean.getBoolean("dispatchflow.strict");
    }
}
