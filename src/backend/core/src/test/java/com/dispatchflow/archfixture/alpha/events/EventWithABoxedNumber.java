package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;

/** DL §7.3: an event's number is an int, never a box that could be null. */
public record EventWithABoxedNumber(EventId eventId, Instant occurredAt, Integer count) implements DomainEvent {}
