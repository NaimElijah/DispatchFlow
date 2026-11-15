package com.dispatchflow.archfixture.zeta.lower.adapter.out;

import com.dispatchflow.archfixture.zeta.lower.application.api.ImplementedOutsideTheService;

/**
 * Rule 4: the one implementation of {@code ImplementedOutsideTheService}, named for it but in an
 * adapter.
 */
public final class ImplementedOutsideTheServiceService implements ImplementedOutsideTheService {

    @Override
    public void act() {}
}
