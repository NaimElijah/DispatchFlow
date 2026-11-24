package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule 3: a transactional record, which Spring can't subclass.
 *
 * @param ledgers the ledgers
 */
@Transactional
public record TransactionalRecord(Ledgers ledgers) {

    /** Counts. */
    public void count() {
        ledgers.count();
    }
}
