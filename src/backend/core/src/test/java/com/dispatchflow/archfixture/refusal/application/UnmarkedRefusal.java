package com.dispatchflow.archfixture.refusal.application;

/** Flagged: an application refusal with a code that doesn't say it is one. */
public final class UnmarkedRefusal extends RuntimeException {

    /** The refusal's code. */
    public static final String CODE = "UNMARKED_REFUSAL";

    /** Creates the refusal. */
    public UnmarkedRefusal() {
        super(CODE);
    }

    /**
     * The refusal's code.
     *
     * @return the code
     */
    public String code() {
        return CODE;
    }
}
