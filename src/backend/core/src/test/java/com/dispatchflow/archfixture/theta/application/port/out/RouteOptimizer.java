package com.dispatchflow.archfixture.theta.application.port.out;

/** An outside-call port, named like production's, which no transaction may hold. */
public interface RouteOptimizer {

    /** Calls the optimizer. */
    void solve();
}
