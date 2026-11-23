package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;

/** Rule 2: a service with no @Transactional. */
public class ForgetsItsTransaction {

    private final Ledgers ledgers;

    /**
     * @param ledgers the ledgers
     */
    public ForgetsItsTransaction(Ledgers ledgers) {
        this.ledgers = ledgers;
    }

    /** Counts outside any transaction. */
    public void count() {
        ledgers.count();
    }
}
