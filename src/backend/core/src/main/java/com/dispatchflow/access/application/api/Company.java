package com.dispatchflow.access.application.api;

import java.util.Optional;

/**
 * The company's profile, which Access owns (FR-125, FR-184, ADR-104): Customers &amp; Orders reads
 * a new customer's defaults from it, and Messaging the name and language its messages use (FR-349,
 * FR-382).
 *
 * <p>Built in M2.
 */
public interface Company {

    /**
     * The company's profile now.
     *
     * <p>Built in M2.
     *
     * @return the name, the language, the default window and the default service time, or empty
     *     before the first Admin set a profile up; each caller chooses how it refuses then
     */
    Optional<CompanyFacts> profile();
}
