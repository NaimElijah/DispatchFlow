package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.spi.LowerUsage;

/** Rule 4: the one implementation of the spi {@code LowerUsage}, in the module its arrow names. */
public final class LowerUsageService implements LowerUsage {

    @Override
    public boolean inUse(int thing) {
        return false;
    }
}
