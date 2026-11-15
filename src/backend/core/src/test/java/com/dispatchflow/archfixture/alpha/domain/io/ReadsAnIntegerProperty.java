package com.dispatchflow.archfixture.alpha.domain.io;

import org.jspecify.annotations.Nullable;

/** Rule 4: Integer.getInteger reads a system property. */
public final class ReadsAnIntegerProperty {

    static @Nullable Integer stopLimit() {
        return Integer.getInteger("dispatchflow.stop-limit");
    }
}
