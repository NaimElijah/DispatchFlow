package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.port.out.RouteOptimizer;

/** Clean: a use case split around its outside call; it holds its Change and no transaction. */
public final class SolvesLedgers {

    private final RouteOptimizer optimizer;
    private final SolvesLedgersChange change;

    /**
     * @param optimizer the optimizer
     * @param change the change
     */
    public SolvesLedgers(RouteOptimizer optimizer, SolvesLedgersChange change) {
        this.optimizer = optimizer;
        this.change = change;
    }

    /** Calls out first, then changes in one transaction. */
    public void solve() {
        optimizer.solve();
        change.apply();
    }
}
