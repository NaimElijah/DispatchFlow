package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.api.Solve;
import org.springframework.transaction.annotation.Transactional;

/** Rule 2: a transactional service that calls, through an {@code api}, a split use case that implements it. */
@Transactional
public class CallsASplitApi {

    private final Solve solve;

    /** @param solve the other module's use case */
    public CallsASplitApi(Solve solve) {
        this.solve = solve;
    }

    /** Calls it inside a transaction of its own. */
    public void apply() {
        solve.solve();
    }
}
