package com.dispatchflow.access.application.api;

/**
 * The actor's roles give the capability only with a limited grant, a cell of the permission matrix
 * that starts "Only", and the call asks for more than that grant covers. The web adapter's role check
 * lets a limited grant through, because the limit depends on what the call asks, so the use case
 * refuses it itself (ADR-068, ADR-104). It is not a domain refusal and not a missing record, so it is
 * no {@code DomainError} and no {@code NotFound}: the web adapter answers it with 403 and
 * {@link #code()}. A call that names a record by its ID, where the limit depends on what that record
 * holds, answers {@code NotFound} instead, as {@code ProofQueries} does, so a 403 never tells a
 * caller which IDs exist.
 *
 * <p>Built in M4.
 */
public final class GrantLimitExceeded extends RuntimeException {

    /** The stable code the web adapter answers with, beside the 403 status. */
    public static final String CODE = "GRANT_LIMIT_EXCEEDED";

    /** Creates the error. */
    public GrantLimitExceeded() {
        super("the actor's grant does not cover this call");
    }

    /**
     * The stable code.
     *
     * @return {@value #CODE}
     */
    public String code() {
        return CODE;
    }
}
