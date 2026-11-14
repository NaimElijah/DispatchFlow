package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.GeoPoint;
import java.time.Instant;

/** DL §7.3: no personal data in an event. */
public record EventWithALocation(EventId eventId, Instant occurredAt, GeoPoint where) implements DomainEvent {}
