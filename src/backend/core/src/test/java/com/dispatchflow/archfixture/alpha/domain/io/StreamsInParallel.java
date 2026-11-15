package com.dispatchflow.archfixture.alpha.domain.io;

import java.util.List;

/** Rule 4: Collection.parallelStream runs on the common pool's threads. */
public final class StreamsInParallel {

    static long blanks(List<String> texts) {
        return texts.parallelStream().filter(String::isBlank).count();
    }
}
