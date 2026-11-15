package com.dispatchflow.archfixture.zeta.lower.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.api.ImplementedUnderAnotherName;

/**
 * Rule 4: the one implementation of {@code ImplementedUnderAnotherName}, which is not its {@code
 * XService}.
 */
public final class ImplementedUnderAnotherNameHandler implements ImplementedUnderAnotherName {

    @Override
    public void act() {}
}
