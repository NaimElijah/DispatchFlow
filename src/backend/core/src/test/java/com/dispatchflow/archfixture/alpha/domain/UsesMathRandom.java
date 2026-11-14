package com.dispatchflow.archfixture.alpha.domain;

/** Rule 4: Math.random(). */
public final class UsesMathRandom {

    static double roll() {
        return Math.random();
    }
}
