package com.dispatchflow.archfixture.alpha.domain.io;

import java.util.concurrent.CompletionStage;

/** Rule 4: an *Async continuation on a CompletionStage runs on another thread. */
public final class ContinuesAsyncOnACompletionStage {

    static CompletionStage<Integer> twice(CompletionStage<Integer> count) {
        return count.thenApplyAsync(value -> value * 2);
    }
}
