package com.dispatchflow.archfixture.alpha.adapter.out.persistence;

import com.dispatchflow.archfixture.alpha.domain.AlphaAggregate;
import com.dispatchflow.archfixture.alpha.domain.AlphaCapacity;

/** Allowed: the persistence adapter rebuilds aggregates. */
public final class RestoresInPersistence {

    static AlphaAggregate load() {
        return AlphaAggregate.restore(1);
    }

    static AlphaCapacity read() {
        return AlphaCapacity.readUnderLock();
    }
}
