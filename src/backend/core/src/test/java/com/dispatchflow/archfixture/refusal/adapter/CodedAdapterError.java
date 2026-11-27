package com.dispatchflow.archfixture.refusal.adapter;

/** Clean: a coded exception outside the application layer, which an adapter records itself. */
public final class CodedAdapterError extends RuntimeException {

    /** The error's code. */
    public static final String CODE = "CODED_ADAPTER_ERROR";

    /** Creates the error. */
    public CodedAdapterError() {
        super(CODE);
    }

    /**
     * The error's code.
     *
     * @return the code
     */
    public String code() {
        return CODE;
    }
}
