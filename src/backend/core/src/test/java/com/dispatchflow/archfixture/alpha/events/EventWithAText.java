package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;

/** DL §7.3: no free text in an event. */
public record EventWithAText(EventId eventId, Instant occurredAt, String note) implements DomainEvent {}
