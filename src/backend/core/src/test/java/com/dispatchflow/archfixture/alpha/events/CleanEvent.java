package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;

/**
 * An event with every allowed field type; a static constant is not a field the rule checks. A list
 * of numbers holds boxes, because a Java list can't hold an {@code int}.
 */
public record CleanEvent(
        EventId eventId,
        Instant occurredAt,
        StopId stop,
        DeliveryDate date,
        Version version,
        AlphaEventKind kind,
        int count,
        long total,
        boolean late,
        List<StopId> stops,
        List<AlphaEventKind> kinds,
        List<Integer> counts)
        implements DomainEvent {

    public static final String TYPE = "CleanEvent";
}
