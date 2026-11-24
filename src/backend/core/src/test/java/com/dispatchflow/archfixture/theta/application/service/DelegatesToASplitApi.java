package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.api.Solve;

/** Clean: a service that calls a split use case as a later call, and so opens no transaction. */
public final class DelegatesToASplitApi {

    private final Solve solve;

    /** @param solve the other module's use case */
    public DelegatesToASplitApi(Solve solve) {
        this.solve = solve;
    }

    /** Calls it outside any transaction, so it commits before it pushes. */
    public void apply() {
        solve.solve();
    }
}
