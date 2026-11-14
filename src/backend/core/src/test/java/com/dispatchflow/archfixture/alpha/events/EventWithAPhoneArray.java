package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;

/** DL §7.3: an array hides personal data from the element check. */
public record EventWithAPhoneArray(EventId eventId, Instant occurredAt, PhoneNumber[] phones) implements DomainEvent {}
