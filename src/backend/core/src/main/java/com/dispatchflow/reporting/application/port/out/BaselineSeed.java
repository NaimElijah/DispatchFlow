package com.dispatchflow.reporting.application.port.out;

/**
 * The seed a baseline is solved with, Reporting's own, so the same orders, fleet, seed and budget give
 * the same baseline (FR-39, NFR-13). Reporting can't use Planning's seed, a domain type of another
 * module (DL §2.3 rule 1).
 *
 * <p>Built in M5.
 *
 * @param value the seed
 */
public record BaselineSeed(long value) {}
