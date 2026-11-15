package com.dispatchflow.access.application.api;

/**
 * A use case needs the company profile, and no Admin has set one up yet. It is not a domain
 * refusal and not a missing record of the caller's own, so it is no {@code DomainError} and no
 * {@code NotFound}: the web adapter answers it with 409 and {@link #code()}. A caller that reads
 * {@link Company#profile()} and finds it empty throws it.
 *
 * <p>Built in M2.
 */
public final class CompanyNotSetUp extends RuntimeException {

    /** The stable code the web adapter answers with, beside the 409 status. */
    public static final String CODE = "COMPANY_NOT_SET_UP";

    /** Creates the error. */
    public CompanyNotSetUp() {
        super("the company profile is not set up yet");
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
