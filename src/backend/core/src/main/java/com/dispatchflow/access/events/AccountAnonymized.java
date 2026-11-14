package com.dispatchflow.access.events;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;
import java.util.Objects;

/**
 * An account's personal data was removed, a year after its deactivation (NFR-258, NFR-260; DL
 * §7.2). Fleet hears it and anonymizes the driver record keyed by the same account, because Access
 * can't call Fleet, which depends on it (ADR-104).
 *
 * @param eventId the event's ID, naming the account and the version the change is saved at
 * @param occurredAt when the account was anonymized
 * @param account the account anonymized
 */
public record AccountAnonymized(EventId eventId, Instant occurredAt, AccountId account) implements DomainEvent {

    /** Checks every part is present. */
    public AccountAnonymized {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(account, "account");
    }
}
