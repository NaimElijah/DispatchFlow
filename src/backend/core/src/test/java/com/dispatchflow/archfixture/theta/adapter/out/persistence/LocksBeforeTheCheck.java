package com.dispatchflow.archfixture.theta.adapter.out.persistence;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import com.dispatchflow.shared.adapter.out.persistence.OpenTransaction;

/** Rule 6: an adapter that takes its lock first and checks for a transaction only afterwards. */
public class LocksBeforeTheCheck implements Ledgers {

    private final LedgerRows rows;

    LocksBeforeTheCheck(LedgerRows rows) {
        this.rows = rows;
    }

    @Override
    public long count() {
        rows.lockLedgers();
        OpenTransaction.require("count");
        return rows.countAll();
    }
}
