package com.dispatchflow.archfixture.alpha.application;

import com.dispatchflow.archfixture.alpha.domain.AlphaCapacity;

/** Rule 6: readUnderLock called from the application layer. */
public final class ReadsUnderLockOutsidePersistence {

    static AlphaCapacity read() {
        return AlphaCapacity.readUnderLock();
    }
}
