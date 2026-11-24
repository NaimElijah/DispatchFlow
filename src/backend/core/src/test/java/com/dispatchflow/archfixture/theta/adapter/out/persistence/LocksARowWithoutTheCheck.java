package com.dispatchflow.archfixture.theta.adapter.out.persistence;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;

/** Rule 6: an adapter that takes a weaker row lock, {@code FOR NO KEY UPDATE}, without the check. */
public class LocksARowWithoutTheCheck implements Ledgers {

    private final LedgerRows rows;

    LocksARowWithoutTheCheck(LedgerRows rows) {
        this.rows = rows;
    }

    @Override
    public long count() {
        return rows.countLockingTheRows();
    }
}
