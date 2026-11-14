package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;

/** DL §7.3: lists, never arrays. */
public record EventWithAnIdArray(EventId eventId, Instant occurredAt, StopId[] stops) implements DomainEvent {}
