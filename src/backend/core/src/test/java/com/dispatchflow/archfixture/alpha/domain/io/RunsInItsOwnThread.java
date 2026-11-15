package com.dispatchflow.archfixture.alpha.domain.io;

/** Rule 4: a subclass of Thread is one of the process's threads, through Thread's constructor. */
public final class RunsInItsOwnThread extends Thread {

    RunsInItsOwnThread(Runnable task) {
        super(task);
    }
}
