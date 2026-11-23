package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/** Rule 3: a final transactional class, which Spring cannot subclass. */
@Transactional
public final class FinalService {

    private final Ledgers ledgers;

    /**
     * @param ledgers the ledgers
     */
    public FinalService(Ledgers ledgers) {
        this.ledgers = ledgers;
    }

    /** Counts. */
    public void count() {
        ledgers.count();
    }
}
