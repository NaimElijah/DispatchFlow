package com.dispatchflow.archfixture.zeta.lower.application.spi;

/** Rule 4: an spi interface with one implementation, outside its own module. */
public interface LowerUsage {

    /** Whether the other module still uses a thing of this one. */
    boolean inUse(int thing);
}
