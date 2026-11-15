package com.dispatchflow.archfixture.zeta.lower.application.spi;

/** Rules 1 and 4: an spi interface whose arrow names {@code peer}, which implements it. */
public interface PeerUsage {

    /** Whether the module {@code peer} still uses the thing asked about. */
    boolean inUse(UsageQuestion question);
}
