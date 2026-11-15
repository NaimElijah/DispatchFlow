package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;

/** DL §7.3: an event's flag is a boolean, never a box that could be null. */
public record EventWithABoxedFlag(EventId eventId, Instant occurredAt, Boolean late) implements DomainEvent {}
