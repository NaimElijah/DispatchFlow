package com.dispatchflow.liveops.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The key that makes an alert once: its kind plus the IDs its kind names, in a fixed text form
 * (FR-157, DL §5, ADR-107). An alert is raised once per key, and acknowledging it silences the key
 * for good; where a new fact should alert again, the key includes that fact.
 *
 * @param value the key's text, {@code KIND:part:part…}
 */
public record DedupeKey(String value) {

    private static final String SEPARATOR = ":";

    /** Checks the text is present. */
    public DedupeKey {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Builds a key from a kind and its parts, in the order given.
     *
     * @param kind the alert's kind
     * @param ids the UUIDs the kind keys on
     * @param numbers the numbers the kind keys on, after the UUIDs
     * @return the key
     */
    static DedupeKey of(AlertKind kind, List<UUID> ids, List<Integer> numbers) {
        return new DedupeKey(Stream.concat(
                        Stream.of(kind.name()),
                        Stream.concat(
                                ids.stream().map(UUID::toString),
                                numbers.stream().map(String::valueOf)))
                .collect(Collectors.joining(SEPARATOR)));
    }
}
