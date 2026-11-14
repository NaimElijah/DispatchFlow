package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.FreshIds;
import java.time.Instant;

/** DL §7.3: a building block is not a value an event carries. */
public record EventWithFreshIds(EventId eventId, Instant occurredAt, FreshIds ids) implements DomainEvent {}
