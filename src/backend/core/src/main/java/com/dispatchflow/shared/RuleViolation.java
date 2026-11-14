package com.dispatchflow.shared;

import java.util.Objects;

/**
 * Thrown when a domain method or value refuses, carrying the one named error that says why (DL
 * §3.8). Where every refusal must be listed at once, {@link RuleViolations} is thrown instead.
 *
 * <p>The message is the error's code alone, because the exception can reach a log and an error's
 * fields are for the response.
 */
public final class RuleViolation extends RuntimeException {

    private final DomainError error;

    /**
     * Creates the refusal.
     *
     * @param error the named error
     */
    public RuleViolation(DomainError error) {
        super(Objects.requireNonNull(error, "error").code());
        this.error = error;
    }

    /**
     * The named error.
     *
     * @return the error this refusal carries
     */
    public DomainError error() {
        return error;
    }
}
