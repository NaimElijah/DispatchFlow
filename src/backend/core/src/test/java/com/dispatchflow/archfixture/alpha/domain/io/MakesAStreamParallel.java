package com.dispatchflow.archfixture.alpha.domain.io;

import java.util.stream.IntStream;

/** Rule 4: BaseStream.parallel runs the stream on the common pool's threads. */
public final class MakesAStreamParallel {

    static int sum(int count) {
        return IntStream.range(0, count).parallel().sum();
    }
}
