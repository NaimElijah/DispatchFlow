package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.api.ImplementedElsewhere;

/** Rule 4: an implementation of another module's api, in the wrong module. */
public final class ImplementedElsewhereService implements ImplementedElsewhere {

    @Override
    public void act() {}
}
