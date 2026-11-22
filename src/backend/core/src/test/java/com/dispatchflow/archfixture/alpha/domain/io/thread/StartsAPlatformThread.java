package com.dispatchflow.archfixture.alpha.domain.io.thread;

/** Rule 4: Thread.ofPlatform builds one of the process's threads. */
public final class StartsAPlatformThread {

    static Thread aside(Runnable task) {
        return Thread.ofPlatform().unstarted(task);
    }
}
