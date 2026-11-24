package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/** Clean on its own: the Change of a use case that breaks rule 2. */
@Transactional
public class SplitButTransactionalChange {

    private final Ledgers ledgers;

    /**
     * @param ledgers the ledgers
     */
    public SplitButTransactionalChange(Ledgers ledgers) {
        this.ledgers = ledgers;
    }

    /** Counts. */
    public void apply() {
        ledgers.count();
    }
}
