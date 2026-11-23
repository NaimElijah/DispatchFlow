package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.application.port.out.InboundReply;
import com.dispatchflow.shared.PhoneNumber;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * One kept {@link InboundReply}, a record never changed (DL §3.2, ADR-127 decision 6), keyed by the
 * provider's identifier for it (NFR-199). The reply log writes it with one native {@code INSERT …
 * ON CONFLICT DO NOTHING}, so a repeated provider call adds nothing, and never reads it back; the
 * mapping lets Hibernate check the table against it when the application starts.
 */
@Entity
@Immutable
@Table(schema = "messaging", name = "inbound_reply")
class StoredReply {

    @Id
    private String providerId;

    private PhoneNumber phone;

    private String text;

    private Instant receivedAt;

    /** For Hibernate. */
    protected StoredReply() {}
}
