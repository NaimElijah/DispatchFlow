package com.dispatchflow.archfixture.theta.application.service;

import org.springframework.transaction.annotation.Transactional;

/** Rule 2: a split use case that also opens a transaction of its own. */
@Transactional
public class SplitButTransactional {

    private final SplitButTransactionalChange change;

    /**
     * @param change the change
     */
    public SplitButTransactional(SplitButTransactionalChange change) {
        this.change = change;
    }

    /** Runs its Change inside a transaction it should not have. */
    public void apply() {
        change.apply();
    }
}
