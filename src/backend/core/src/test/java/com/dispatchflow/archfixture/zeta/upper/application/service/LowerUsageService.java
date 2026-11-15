package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.spi.LowerUsage;

/** Rule 4: the one implementation of the spi {@code LowerUsage}, outside the module that owns it. */
public final class LowerUsageService implements LowerUsage {

    @Override
    public boolean inUse(int thing) {
        return false;
    }
}
