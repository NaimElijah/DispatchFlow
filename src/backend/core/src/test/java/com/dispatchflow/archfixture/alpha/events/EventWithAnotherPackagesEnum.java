package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.DayOfWeek;
import java.time.Instant;

/** DL §7.3: an enum must be the events package's own. */
public record EventWithAnotherPackagesEnum(EventId eventId, Instant occurredAt, DayOfWeek day) implements DomainEvent {}
