package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/** Rule 2: a service whose transaction sits on a method, not its class. */
public class TransactionalOnAMethodOnly {

    private final Ledgers ledgers;

    /** @param ledgers the ledgers */
    public TransactionalOnAMethodOnly(Ledgers ledgers) {
        this.ledgers = ledgers;
    }

    /** Counts in a transaction of its own method. */
    @Transactional
    public void count() {
        ledgers.count();
    }
}
