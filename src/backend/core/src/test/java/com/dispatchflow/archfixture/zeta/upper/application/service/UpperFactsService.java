package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.upper.application.api.UpperFacts;

/** Rule 4: the one implementation of {@code UpperFacts}, in its own module. */
public final class UpperFactsService implements UpperFacts {

    @Override
    public int count() {
        return 0;
    }
}
