package com.dispatchflow.archfixture.alpha.domain.io;

import java.lang.ref.Cleaner;

/** Rule 4: Cleaner.create starts a thread of its own. */
public final class RegistersWithACleaner {

    static Cleaner cleaner() {
        return Cleaner.create();
    }
}
