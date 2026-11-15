package com.dispatchflow.archfixture.zeta.lower.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.spi.UsageImplementedAtHome;

/** Rule 4: an implementation of the module's own spi, inside the module. */
public final class UsageImplementedAtHomeService implements UsageImplementedAtHome {

    @Override
    public boolean inUse(int thing) {
        return false;
    }
}
