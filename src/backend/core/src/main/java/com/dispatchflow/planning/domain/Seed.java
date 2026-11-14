package com.dispatchflow.planning.domain;

/**
 * The seed a solve runs with, so the same input, seed and budget give the same solution (NFR-13).
 * The use case draws it from a seeded generator; the domain never makes one.
 *
 * @param value the seed
 */
public record Seed(long value) {

    /**
     * Creates the seed.
     *
     * @param value the seed
     * @return the seed
     */
    public static Seed of(long value) {
        return new Seed(value);
    }
}
