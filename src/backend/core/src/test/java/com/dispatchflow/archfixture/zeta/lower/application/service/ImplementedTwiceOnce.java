package com.dispatchflow.archfixture.zeta.lower.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.api.ImplementedTwice;

/** Rule 4: the first of two implementations of {@code ImplementedTwice}. */
public final class ImplementedTwiceOnce implements ImplementedTwice {

    @Override
    public void act() {}
}
