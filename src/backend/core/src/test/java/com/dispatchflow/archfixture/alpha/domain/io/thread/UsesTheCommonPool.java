package com.dispatchflow.archfixture.alpha.domain.io.thread;

import java.util.concurrent.ForkJoinPool;

/** Rule 4: a ForkJoinPool runs tasks on threads of its own. */
public final class UsesTheCommonPool {

    static void runAside(Runnable task) {
        ForkJoinPool.commonPool().execute(task);
    }
}
