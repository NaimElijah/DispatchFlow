package com.dispatchflow.fleet.events;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;
import java.util.Objects;

/**
 * A driver was retired (BR-43, ADR-103). Fleet's own listener calls Access's API to remove the
 * Driver role from the account, which keeps its other roles (FR-307, DL §2.3 rule 2); the call is
 * retried until it succeeds (DL §7.1, reason 2). It carries the account's ID only, never the
 * driver's name or phone.
 *
 * @param eventId the event's ID
 * @param occurredAt when the driver was retired
 * @param driver the retired driver's account
 */
public record DriverRetired(EventId eventId, Instant occurredAt, AccountId driver) implements DomainEvent {

    /** Checks every field is present. */
    public DriverRetired {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(driver, "driver");
    }
}
