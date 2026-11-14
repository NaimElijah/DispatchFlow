package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;
import java.util.List;

/** DL §7.3: a list of free text is free text. */
public record EventWithAListOfText(EventId eventId, Instant occurredAt, List<String> notes) implements DomainEvent {}
