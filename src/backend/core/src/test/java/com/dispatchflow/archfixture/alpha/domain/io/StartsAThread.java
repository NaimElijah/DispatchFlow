package com.dispatchflow.archfixture.alpha.domain.io;

/** Rule 4: new Thread creates one of the process's threads. */
public final class StartsAThread {

    static void runAside(Runnable task) {
        new Thread(task).start();
    }
}
