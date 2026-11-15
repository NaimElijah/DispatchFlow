package com.dispatchflow.archfixture.zeta.lower.application.spi;

/** Rule 4: an spi interface must have an arrow that names the module implementing it. */
public interface UsageMissingFromTheMap {

    /** Whether a thing is in use. */
    boolean inUse(int thing);
}
