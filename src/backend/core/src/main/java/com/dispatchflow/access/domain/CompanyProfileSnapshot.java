package com.dispatchflow.access.domain;

import com.dispatchflow.shared.Version;
import java.util.Objects;
import java.util.Optional;

/**
 * The company profile as stored: what {@link CompanyProfile#snapshot()} gives and {@link
 * CompanyProfile#restore} takes. Its values check themselves, and no rule ties them together.
 *
 * @param version the stored version
 * @param name the company's name (FR-125, FR-382)
 * @param language the default interface language a new user starts with (FR-125, FR-182)
 * @param defaultWindow the time window a new customer starts with (FR-125)
 * @param defaultServiceTime the service duration a new customer starts with, once an Admin set it
 *     (FR-184)
 */
public record CompanyProfileSnapshot(
        Version version,
        CompanyName name,
        Language language,
        DefaultWindow defaultWindow,
        Optional<DefaultServiceTime> defaultServiceTime) {

    /** Checks every part is present. */
    public CompanyProfileSnapshot {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(defaultWindow, "defaultWindow");
        Objects.requireNonNull(defaultServiceTime, "defaultServiceTime");
    }
}
