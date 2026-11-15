package com.dispatchflow.access.application.api;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

/**
 * The company's profile, as other modules read it (FR-125, FR-184, ADR-104). Customers &amp;
 * Orders makes a new customer's {@code OrderDefaults} from the window and the service time, and
 * Messaging makes its {@code CompanyName} and {@code MessageLanguage} from the name and the
 * language.
 *
 * <p>Built in M2.
 *
 * @param name the company's name, normalized, at most 19 UTF-16 units (FR-382)
 * @param language the default interface language (FR-182)
 * @param defaultWindowOpens when a new customer's default window opens, on no particular date
 * @param defaultWindowCloses when it closes, after {@code defaultWindowOpens}
 * @param defaultServiceTime a new customer's default service time, in whole seconds; empty until
 *     an Admin sets one (FR-184)
 */
public record CompanyFacts(
        String name,
        CompanyLanguage language,
        LocalTime defaultWindowOpens,
        LocalTime defaultWindowCloses,
        Optional<Duration> defaultServiceTime) {

    /** Checks every part is present, possibly empty. */
    public CompanyFacts {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(defaultWindowOpens, "defaultWindowOpens");
        Objects.requireNonNull(defaultWindowCloses, "defaultWindowCloses");
        Objects.requireNonNull(defaultServiceTime, "defaultServiceTime");
    }
}
