package com.dispatchflow.archfixture.alpha.application;

import com.dispatchflow.archfixture.alpha.domain.AlphaAggregate;

/** Rule 6: restore called from the application layer. */
public final class RestoresOutsidePersistence {

    static AlphaAggregate load() {
        return AlphaAggregate.restore(1);
    }
}
