package com.dispatchflow.archfixture.beta.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;

/** An event whose simple name another module repeats. */
public record Reported(EventId eventId, Instant occurredAt, int count) implements DomainEvent {}
