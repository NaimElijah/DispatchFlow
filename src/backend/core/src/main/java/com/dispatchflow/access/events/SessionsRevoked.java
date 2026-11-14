package com.dispatchflow.access.events;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;
import java.util.Objects;

/**
 * Every token and session of an account stopped being valid (DL §7.2): its roles, password or
 * email changed, it was deactivated, or an Admin ended its sessions (NFR-90, FR-187, BR-123). The
 * live-updates adapter hears it and closes the account's open streams (NFR-188).
 *
 * <p>The revocation itself is the account's new generation, which the token check reads at once
 * (NFR-177), so nothing waits for this event. The event carries that generation, so a redelivered
 * event closes only the streams opened under an older one.
 *
 * @param eventId the event's ID, naming the account and the version the change is saved at
 * @param occurredAt when the sessions were revoked
 * @param account the account whose sessions were revoked
 * @param generation the account's generation after the revocation; streams opened under a lower one
 *     close
 */
public record SessionsRevoked(EventId eventId, Instant occurredAt, AccountId account, long generation)
        implements DomainEvent {

    /** Checks every part is present. */
    public SessionsRevoked {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(account, "account");
    }
}
