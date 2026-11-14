package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;

/** DL §7.3: no personal data in an event. */
public record EventWithAPhone(EventId eventId, Instant occurredAt, PhoneNumber phone) implements DomainEvent {}
