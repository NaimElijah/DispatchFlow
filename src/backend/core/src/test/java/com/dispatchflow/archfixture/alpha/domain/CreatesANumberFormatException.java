package com.dispatchflow.archfixture.alpha.domain;

/** Rule 5: a NumberFormatException is an IllegalArgumentException. */
public final class CreatesANumberFormatException {

    static int refuse() {
        throw new NumberFormatException("refused");
    }
}
