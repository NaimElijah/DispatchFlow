package com.dispatchflow.archfixture.alpha.domain.exception;

/** Rule 5: new IllegalStateException. */
public final class CreatesAnIllegalState {

    static void refuse() {
        throw new IllegalStateException("refused");
    }
}
