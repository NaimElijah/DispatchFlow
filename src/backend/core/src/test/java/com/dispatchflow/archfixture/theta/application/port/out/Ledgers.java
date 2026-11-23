package com.dispatchflow.archfixture.theta.application.port.out;

/** A repository port, which a transaction may hold. */
public interface Ledgers {

    /**
     * Counts the stored ledgers.
     *
     * @return how many there are
     */
    long count();
}
