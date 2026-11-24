package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import com.dispatchflow.archfixture.theta.application.port.out.RouteOptimizer;
import org.springframework.transaction.annotation.Transactional;

/** Rule 4: a transactional service that holds an outside-call port. */
@Transactional
public class WaitsOnTheOptimizer {

    private final RouteOptimizer optimizer;
    private final Ledgers ledgers;

    /**
     * @param optimizer the optimizer
     * @param ledgers the ledgers
     */
    public WaitsOnTheOptimizer(RouteOptimizer optimizer, Ledgers ledgers) {
        this.optimizer = optimizer;
        this.ledgers = ledgers;
    }

    /** Holds the transaction open across the call. */
    public void solve() {
        optimizer.solve();
        ledgers.count();
    }
}
