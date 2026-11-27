package com.dispatchflow.archfixture.refusal.application;

/** Flagged: an application refusal whose code is only a constant, with no {@code code()}. */
public final class CodedByAConstant extends RuntimeException {

    /** The refusal's code. */
    public static final String CODE = "CODED_BY_A_CONSTANT";

    /** Creates the refusal. */
    public CodedByAConstant() {
        super(CODE);
    }
}
