package com.dispatchflow.archfixture.alpha.domain;

/** A snapshot read under a lock, whose factory only persistence may call; again() shows it may call its own. */
public final class AlphaCapacity {

    private AlphaCapacity() {}

    public static AlphaCapacity readUnderLock() {
        return new AlphaCapacity();
    }

    static AlphaCapacity again() {
        return readUnderLock();
    }
}
