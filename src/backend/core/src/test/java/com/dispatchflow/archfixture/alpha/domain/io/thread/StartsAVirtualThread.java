package com.dispatchflow.archfixture.alpha.domain.io.thread;

/** Rule 4: Thread.ofVirtual builds one of the process's threads. */
public final class StartsAVirtualThread {

    static Thread aside(Runnable task) {
        return Thread.ofVirtual().unstarted(task);
    }
}
