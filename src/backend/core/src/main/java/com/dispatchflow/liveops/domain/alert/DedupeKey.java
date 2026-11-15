package com.dispatchflow.liveops.domain.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
        return of(kind, ids, numbers, List.of());
    }

    /**
     * Builds a key from a kind and its parts, in the order given, ending with enum constants.
     *
     * @param kind the alert's kind
     * @param ids the UUIDs the kind keys on
     * @param numbers the numbers the kind keys on, after the UUIDs
     * @param names the enum constants the kind keys on, by name, after the numbers
     * @return the key
     */
    static DedupeKey of(AlertKind kind, List<UUID> ids, List<Integer> numbers, List<? extends Enum<?>> names) {
        List<String> parts = new ArrayList<>();
        parts.add(kind.name());
        ids.forEach(id -> parts.add(id.toString()));
        numbers.forEach(number -> parts.add(String.valueOf(number)));
        names.forEach(name -> parts.add(name.name()));
        return new DedupeKey(String.join(SEPARATOR, parts));
    }
}
