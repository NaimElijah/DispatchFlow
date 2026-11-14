package com.dispatchflow.archfixture.alpha.domain;

/** A snapshot read under a lock, whose factory only persistence may call. */
public final class AlphaCapacity {

    private AlphaCapacity() {}

    public static AlphaCapacity readUnderLock() {
        return new AlphaCapacity();
    }
}
