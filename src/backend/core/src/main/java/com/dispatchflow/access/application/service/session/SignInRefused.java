package com.dispatchflow.access.application.service.session;

/**
 * The one refusal every refused sign-in gets (NFR-145, NFR-225): an address no account holds, a
 * wrong password, a deactivated, anonymized or role-less account, and an account with no password
 * yet. {@link SignIn} throws it in place of the domain's specific refusal, so neither the answer nor
 * its code tells a caller whether the address has an account or why it can't sign in; the specific
 * code goes only to the refused attempt's audit record.
 *
 * <p>It is no {@code DomainError}, because the domain refuses each case by its own name: the web
 * adapter answers it with 401 and {@link #code()}. Its message names nothing about the attempt
 * (NFR-19).
 *
 * <p>Built in M5.
 */
public final class SignInRefused extends RuntimeException {

    /** The stable code the web adapter answers with, beside the 401 status. */
    public static final String CODE = "SIGN_IN_REFUSED";

    /** Creates the error. */
    public SignInRefused() {
        super("the sign-in is refused");
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
