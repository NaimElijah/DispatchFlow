package com.dispatchflow.archfixture.alpha.domain;

/** Not an aggregate, so its restore is not restricted. */
public record AlphaRecord(int amount) {

    public static AlphaRecord restore(int amount) {
        return new AlphaRecord(amount);
    }
}
