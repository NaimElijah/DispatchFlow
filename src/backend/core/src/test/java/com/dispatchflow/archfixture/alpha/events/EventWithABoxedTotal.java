package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;

/** DL §7.3: an event's total is a long, never a box that could be null. */
public record EventWithABoxedTotal(EventId eventId, Instant occurredAt, Long total) implements DomainEvent {}
