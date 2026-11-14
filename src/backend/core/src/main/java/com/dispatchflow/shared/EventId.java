package com.dispatchflow.shared;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A domain event's ID (DL §7.3): the event type, the raising aggregate, the version the change is
 * saved at, and the event's ordinal within that change. Optimistic locking lets one change commit
 * per aggregate and version, so the four together are unique without a random part. Repeated
 * deliveries are stopped by the listener's idempotency record, not by this ID.
 *
 * <p>Its canonical text form is {@code <eventType>:<aggregateId>:<version>:<ordinal>}, for example
 * {@code PlanPublished:0190a5e2-…:4:0}.
 *
 * @param eventType the event record's simple name
 * @param aggregateId the raising aggregate's UUID
 * @param version the version the raising change is saved at, at least 1
 * @param ordinal the event's position among the change's events, from 0
 */
public record EventId(String eventType, UUID aggregateId, Version version, int ordinal) {

    private static final String SEPARATOR = ":";
    private static final int PARTS = 4;
    private static final long FIRST_SAVED_VERSION = 1;
    private static final Pattern TYPE_NAME = Pattern.compile("[A-Z][A-Za-z0-9]*");

    /**
     * Checks the parts.
     *
     * @throws StoredStateInvalid when the type is not a simple type name, the version is below 1 or
     *     the ordinal is negative; outside {@link #parse} these only come from aggregate code, so
     *     the only way to meet them is a damaged stored ID
     */
    public EventId {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(version, "version");
        if (!TYPE_NAME.matcher(eventType).matches()) {
            throw new StoredStateInvalid(EventId.class, "the event type is not a simple type name");
        }
        if (version.value() < FIRST_SAVED_VERSION) {
            throw new StoredStateInvalid(EventId.class, "an event carries a saved version, 1 or more");
        }
        if (ordinal < 0) {
            throw new StoredStateInvalid(EventId.class, "an ordinal is never below zero");
        }
    }

    /**
     * Reads the canonical text form back.
     *
     * @param text a value {@link #toText()} produced
     * @return the ID
     * @throws StoredStateInvalid when the text is not in the canonical form
     */
    public static EventId parse(String text) {
        String[] parts = text.split(SEPARATOR, -1);
        if (parts.length != PARTS) {
            throw new StoredStateInvalid(EventId.class, "the text does not have four parts");
        }
        try {
            UUID aggregateId = UUID.fromString(parts[1]);
            // UUID.fromString also accepts shortened groups; only the canonical form round-trips.
            if (!aggregateId.toString().equals(parts[1])) {
                throw new StoredStateInvalid(EventId.class, "the aggregate ID is not a canonical UUID");
            }
            return new EventId(parts[0], aggregateId, Version.of(Long.parseLong(parts[2])), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException malformed) {
            throw new StoredStateInvalid(EventId.class, "the aggregate ID, version or ordinal is malformed", malformed);
        }
    }

    /**
     * The canonical text form.
     *
     * @return {@code <eventType>:<aggregateId>:<version>:<ordinal>}
     */
    public String toText() {
        return String.join(
                SEPARATOR,
                eventType,
                aggregateId.toString(),
                Long.toString(version.value()),
                Integer.toString(ordinal));
    }
}
