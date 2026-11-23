package com.dispatchflow.archfixture.theta.adapter.out.persistence;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import com.dispatchflow.shared.adapter.out.persistence.OpenTransaction;

/** Clean: an adapter that checks for a transaction before it takes a transaction-scoped lock. */
public class LocksAfterTheCheck implements Ledgers {

    private final LedgerRows rows;

    LocksAfterTheCheck(LedgerRows rows) {
        this.rows = rows;
    }

    @Override
    public long count() {
        OpenTransaction.require("count");
        rows.lockLedgers();
        return rows.countAll();
    }
}
