package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/** Rule 3: a transactional class with a final method, whose call skips the transaction. */
@Transactional
public class FinalMethodService {

    private final Ledgers ledgers;

    /**
     * @param ledgers the ledgers
     */
    public FinalMethodService(Ledgers ledgers) {
        this.ledgers = ledgers;
    }

    /** Counts with no transaction, because Spring cannot override it. */
    public final void count() {
        ledgers.count();
    }
}
