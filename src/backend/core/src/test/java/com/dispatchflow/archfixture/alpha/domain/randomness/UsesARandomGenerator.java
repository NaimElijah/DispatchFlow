package com.dispatchflow.archfixture.alpha.domain.randomness;

import java.util.random.RandomGenerator;

/** Rule 4: java.util.random. */
public final class UsesARandomGenerator {

    static int roll(RandomGenerator generator) {
        return generator.nextInt(6);
    }
}
