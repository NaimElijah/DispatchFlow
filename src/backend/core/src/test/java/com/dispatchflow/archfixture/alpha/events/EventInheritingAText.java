package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import java.time.Instant;

/** DL §7.3: free text inherited from a supertype. */
public final class EventInheritingAText extends AbstractNotedEvent implements DomainEvent {

    private final EventId eventId;
    private final Instant occurredAt;

    public EventInheritingAText(EventId eventId, Instant occurredAt, String note) {
        super(note);
        this.eventId = eventId;
        this.occurredAt = occurredAt;
    }

    @Override
    public EventId eventId() {
        return eventId;
    }

    @Override
    public Instant occurredAt() {
        return occurredAt;
    }
}
