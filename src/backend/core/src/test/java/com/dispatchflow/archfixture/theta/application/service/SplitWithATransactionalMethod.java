package com.dispatchflow.archfixture.theta.application.service;

import org.springframework.transaction.annotation.Transactional;

/** Rule 2: a split use case that opens a transaction on one of its methods. */
public final class SplitWithATransactionalMethod {

    private final SplitWithATransactionalMethodChange change;

    /** @param change its transaction */
    public SplitWithATransactionalMethod(SplitWithATransactionalMethodChange change) {
        this.change = change;
    }

    /** Runs its Change inside a transaction of its own method. */
    @Transactional
    public void apply() {
        change.apply();
    }
}
