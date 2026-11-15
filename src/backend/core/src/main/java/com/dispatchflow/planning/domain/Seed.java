package com.dispatchflow.planning.domain;

/**
 * The seed a solve runs with, so the same input, seed and budget give the same solution (NFR-13).
 * The use case draws it from a seeded generator; the domain never makes one.
 *
 * <p>Any {@code long} is a seed, negative ones included. The optimizer takes no seed yet: its
 * solver port takes one ({@code solve(problem, start, budget, seed)}, ADR-044), but no adapter or
 * contract operation passes it. The contract that adds it settles the range both sides accept, and
 * this record then checks it (ADR-112).
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
