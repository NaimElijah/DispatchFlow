package com.dispatchflow.archfixture.alpha.domain.io.thread;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Rule 4: a ThreadPoolExecutor runs tasks on threads of its own. */
public final class BuildsAThreadPoolExecutor {

    static ThreadPoolExecutor pool() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }
}
