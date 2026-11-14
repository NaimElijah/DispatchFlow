package com.dispatchflow.archfixture.alpha.domain;

/** A supertype that carries free text into the errors that extend it. */
public abstract class AbstractReasonedError {

    protected final String reason;

    protected AbstractReasonedError(String reason) {
        this.reason = reason;
    }
}
