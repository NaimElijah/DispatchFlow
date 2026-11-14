package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;

/** DL §7.3: an event lives in an events package. */
public record EventOutsideEvents(EventId eventId, Instant occurredAt, int count) implements DomainEvent {}
