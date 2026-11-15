package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.archfixture.alpha.domain.personal.ContactName;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;

/**
 * DL §7.3: no personal data in an event. The event allow-list already refuses this type, so this
 * fixture would pass without the {@code PersonalData} check; {@link EventWithAPersonalEnum} is the
 * one that proves that check.
 */
public record EventWithAContactName(EventId eventId, Instant occurredAt, ContactName contact) implements DomainEvent {}
