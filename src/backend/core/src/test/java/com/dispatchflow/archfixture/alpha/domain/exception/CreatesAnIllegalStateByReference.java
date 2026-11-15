package com.dispatchflow.archfixture.alpha.domain.exception;

import java.util.function.Supplier;

/** Rule 5: IllegalStateException::new. */
public final class CreatesAnIllegalStateByReference {

    static Supplier<IllegalStateException> refusal() {
        return IllegalStateException::new;
    }
}
