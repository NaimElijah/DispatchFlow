package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;

/** Rule 7: a split use case that reads the database itself, outside any transaction. */
public final class SplitButHoldsLedgers {

    private final Ledgers ledgers;
    private final SplitButHoldsLedgersChange change;

    /**
     * @param ledgers the ledgers, read with no transaction
     * @param change its transaction
     */
    public SplitButHoldsLedgers(Ledgers ledgers, SplitButHoldsLedgersChange change) {
        this.ledgers = ledgers;
        this.change = change;
    }

    /** Reads, then changes. */
    public void apply() {
        ledgers.count();
        change.apply();
    }
}
