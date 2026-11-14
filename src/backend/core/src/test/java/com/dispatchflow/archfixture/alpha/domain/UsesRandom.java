package com.dispatchflow.archfixture.alpha.domain;

import java.util.Random;

/** Rule 4: a seeded Random is still randomness. */
public final class UsesRandom {

    static int roll() {
        return new Random(1).nextInt(6);
    }
}
