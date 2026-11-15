package com.dispatchflow.archfixture.alpha.domain.io;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Rule 4: Executors creates a pool of the process's threads. */
public final class UsesAnExecutor {

    static ExecutorService pool() {
        return Executors.newSingleThreadExecutor();
    }
}
