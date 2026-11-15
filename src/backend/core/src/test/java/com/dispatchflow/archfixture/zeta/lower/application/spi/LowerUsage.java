package com.dispatchflow.archfixture.zeta.lower.application.spi;

/** Rule 4: an spi interface with one implementation, in the module its arrow names. */
public interface LowerUsage {

    /** Whether the other module still uses a thing of this one. */
    boolean inUse(int thing);
}
