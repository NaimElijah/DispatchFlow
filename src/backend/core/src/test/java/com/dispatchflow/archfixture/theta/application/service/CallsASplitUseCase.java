package com.dispatchflow.archfixture.theta.application.service;

import org.springframework.transaction.annotation.Transactional;

/** Rule 2: a transactional service that calls a split use case, which then pushes inside its transaction. */
@Transactional
public class CallsASplitUseCase {

    private final SolvesLedgers solves;

    /** @param solves the split use case */
    public CallsASplitUseCase(SolvesLedgers solves) {
        this.solves = solves;
    }

    /** Calls it inside a transaction of its own. */
    public void apply() {
        solves.solve();
    }
}
