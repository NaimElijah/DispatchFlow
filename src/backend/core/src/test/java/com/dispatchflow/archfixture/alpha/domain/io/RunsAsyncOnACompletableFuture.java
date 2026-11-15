package com.dispatchflow.archfixture.alpha.domain.io;

import java.util.concurrent.CompletableFuture;

/** Rule 4: CompletableFuture.supplyAsync runs its task on the common pool's threads. */
public final class RunsAsyncOnACompletableFuture {

    static CompletableFuture<Integer> count() {
        return CompletableFuture.supplyAsync(() -> 1);
    }
}
