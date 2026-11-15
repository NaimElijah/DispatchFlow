package com.dispatchflow.archfixture.alpha.domain.exception;

/** Rule 5: new IllegalArgumentException. */
public final class CreatesAnIllegalArgument {

    static void refuse() {
        throw new IllegalArgumentException("refused");
    }
}
