package com.dispatchflow.archfixture.theta.adapter.out.persistence;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;

/** Clean: a persistence adapter that relies on its caller's transaction. */
public class PlainLedgers implements Ledgers {

    @Override
    public long count() {
        return 0;
    }
}
