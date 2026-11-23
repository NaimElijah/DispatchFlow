package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** Rule 1: an events package carries no persistence annotation (ADR-126). */
@Embeddable
public record EventWithAJpaAnnotation(EventId eventId, Instant occurredAt) implements DomainEvent {}
