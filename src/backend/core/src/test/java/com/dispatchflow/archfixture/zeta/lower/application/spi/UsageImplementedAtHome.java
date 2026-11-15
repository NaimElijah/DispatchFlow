package com.dispatchflow.archfixture.zeta.lower.application.spi;

/** Rule 4: an spi interface must be implemented outside the module that owns it. */
public interface UsageImplementedAtHome {

    /** Whether a thing is in use. */
    boolean inUse(int thing);
}
