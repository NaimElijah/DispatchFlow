package com.dispatchflow.archfixture.alpha.domain.randomness;

/** Rule 4: Math.random(). */
public final class UsesMathRandom {

    static double roll() {
        return Math.random();
    }
}
