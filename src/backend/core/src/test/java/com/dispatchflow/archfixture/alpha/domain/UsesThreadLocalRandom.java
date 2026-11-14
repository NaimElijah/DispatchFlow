package com.dispatchflow.archfixture.alpha.domain;

import java.util.concurrent.ThreadLocalRandom;

/** Rule 4: ThreadLocalRandom. */
public final class UsesThreadLocalRandom {

    static int roll() {
        return ThreadLocalRandom.current().nextInt(6);
    }
}
