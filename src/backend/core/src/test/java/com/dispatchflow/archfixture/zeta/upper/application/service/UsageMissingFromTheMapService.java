package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.spi.UsageMissingFromTheMap;

/** Rule 4: the one implementation of a {@code lower} spi that no arrow names. */
public final class UsageMissingFromTheMapService implements UsageMissingFromTheMap {

    @Override
    public boolean inUse(int thing) {
        return false;
    }
}
