package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One uncancelled order of a date, as Customers &amp; Orders answers (DL §2.2, BR-22). Planning
 * builds its {@code PublishingChecks.DateOrder} from it and the optimizer's order inputs. Live
 * Operations takes only its location and its last change time: for the live map (FR-397), and the
 * stored coordinates that Navigate sends (FR-325). A stop's window and door flag come to Live
 * Operations from Planning's {@code PublishedRoutes}, as its revision took them. Its location is a
 * kernel {@link GeoPoint}, so it prints redacted.
 *
 * <p>{@code Order} holds only its last change time, so {@code placedAt} has no source yet: the
 * time the order joined its date, set by {@code place}, {@code reschedule} and a put-back to
 * another date (BR-71), is not recorded yet.
 *
 * <p>To Be Implemented.
 *
 * @param order the order
 * @param attempt its current attempt (FR-258)
 * @param location its customer's coordinates, or empty when they are not known yet, so the order
 *     drops as missing location (BR-14)
 * @param outsideServiceArea whether its customer's location was geocoded outside the service area
 *     and carries that flag, so the order drops as an unreachable location (FR-320); never for a
 *     location placed by hand, which lies inside the service area, or a missing one
 * @param window its time window on the date, its own or its customer's default (BR-49)
 * @param weight its weight demand (BR-50)
 * @param volume its volume demand (BR-50)
 * @param serviceTime its service time, its own or its customer's default (BR-04, BR-124)
 * @param priority its priority (BR-53)
 * @param leaveAtDoor whether it may be left at the door, so its window is a preference (FR-364,
 *     BR-119)
 * @param placedAt when it joined the date: placed on it, rescheduled to it, or put back to it from
 *     another date, which a publication compares with the solve's request (BR-71)
 * @param lastChangedAt when it last changed, placing included (FR-397, BR-82)
 */
public record OrderFact(
        OrderId order,
        Attempt attempt,
        Optional<GeoPoint> location,
        boolean outsideServiceArea,
        TimeWindow window,
        Weight weight,
        Volume volume,
        Duration serviceTime,
        Priority priority,
        boolean leaveAtDoor,
        Instant placedAt,
        Instant lastChangedAt)
        implements PersonalData {

    /** Checks every part is present, possibly empty. */
    public OrderFact {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(serviceTime, "serviceTime");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(placedAt, "placedAt");
        Objects.requireNonNull(lastChangedAt, "lastChangedAt");
    }

    /**
     * A form that hides the location.
     *
     * @return {@code OrderFact[redacted]}
     */
    @Override
    public String toString() {
        return "OrderFact[redacted]";
    }
}
