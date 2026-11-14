package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;
import java.util.List;

/** DL §7.3: a value marked as personal data is refused even where its type would be allowed. */
public record EventWithAPersonalEnum(EventId eventId, Instant occurredAt, List<PersonalEventKind> kinds)
        implements DomainEvent {}
