package com.dispatchflow.archfixture.alpha.domain.io.process;

import org.jspecify.annotations.Nullable;

/** Rule 4: Long.getLong reads a system property. */
public final class ReadsALongProperty {

    static @Nullable Long solveLimit() {
        return Long.getLong("dispatchflow.solve-limit");
    }
}
