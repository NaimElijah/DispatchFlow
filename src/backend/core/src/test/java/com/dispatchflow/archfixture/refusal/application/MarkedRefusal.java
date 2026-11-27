package com.dispatchflow.archfixture.refusal.application;

import com.dispatchflow.shared.application.ApplicationRefusal;

/** Clean: an application refusal that says it is one. */
public final class MarkedRefusal extends RuntimeException implements ApplicationRefusal {

    /** The refusal's code. */
    public static final String CODE = "MARKED_REFUSAL";

    /** Creates the refusal. */
    public MarkedRefusal() {
        super(CODE);
    }

    @Override
    public String code() {
        return CODE;
    }
}
