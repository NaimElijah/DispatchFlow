package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/** Clean: a service that changes nothing, read-only. */
@Transactional(readOnly = true)
public class ReadsLedgers {

    private final Ledgers ledgers;

    /**
     * @param ledgers the ledgers
     */
    public ReadsLedgers(Ledgers ledgers) {
        this.ledgers = ledgers;
    }

    /**
     * Reads the count.
     *
     * @return how many ledgers there are
     */
    public long count() {
        return ledgers.count();
    }
}
