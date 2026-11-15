package com.dispatchflow.archfixture.zeta.side.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.spi.UsageImplementedByTheWrongModule;

/** Rule 4: the one implementation of a {@code lower} spi, outside {@code lower} but not where its arrow points. */
public final class WrongModuleUsageService implements UsageImplementedByTheWrongModule {

    @Override
    public boolean inUse(int thing) {
        return false;
    }
}
