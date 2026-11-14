package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.RuleViolation;
import java.time.Instant;

/** DL §7.3: a building block is not a value an event carries. */
public record EventWithARuleViolation(EventId eventId, Instant occurredAt, RuleViolation refusal)
        implements DomainEvent {}
