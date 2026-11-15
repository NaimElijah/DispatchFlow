package com.dispatchflow.archfixture.zeta.lower.application.spi;

/** Rule 4: an spi interface must be implemented by the module its arrow names, not another one. */
public interface UsageImplementedByTheWrongModule {

    /** Whether a thing is in use. */
    boolean inUse(int thing);
}
