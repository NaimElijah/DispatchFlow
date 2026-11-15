package com.dispatchflow.archfixture.zeta.lower.application.spi;

/** Rule 4: an spi interface must have an implementation. */
public interface UsageUnimplemented {

    /** Whether a thing is in use. */
    boolean inUse(int thing);
}
