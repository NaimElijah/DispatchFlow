package com.dispatchflow.archfixture.alpha.adapter.out.persistence;

import com.dispatchflow.archfixture.alpha.domain.AlphaCapacity;

/** Allowed: the persistence adapter reads a snapshot under its lock. */
public final class ReadsUnderLockInPersistence {

    static AlphaCapacity read() {
        return AlphaCapacity.readUnderLock();
    }
}
