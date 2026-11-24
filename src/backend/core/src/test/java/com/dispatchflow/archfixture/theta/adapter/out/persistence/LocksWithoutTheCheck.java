package com.dispatchflow.archfixture.theta.adapter.out.persistence;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;

/** Rule 6: an adapter that takes a transaction-scoped lock without checking for a transaction. */
public class LocksWithoutTheCheck implements Ledgers {

    private final LedgerRows rows;

    LocksWithoutTheCheck(LedgerRows rows) {
        this.rows = rows;
    }

    @Override
    public long count() {
        rows.lockLedgers();
        return rows.countAll();
    }
}
