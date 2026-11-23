package com.dispatchflow.archfixture.theta.application.api;

/** Another module's use case, as an {@code api}, which a split use case implements. */
public interface Solve {

    /** Solves, opening its own transactions. */
    void solve();
}
