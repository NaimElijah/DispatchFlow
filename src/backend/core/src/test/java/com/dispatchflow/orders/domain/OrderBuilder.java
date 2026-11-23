package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.OrdersTestData.ACCESS;
import static com.dispatchflow.orders.domain.OrdersTestData.CUSTOMER;
import static com.dispatchflow.orders.domain.OrdersTestData.NOTE;
import static com.dispatchflow.orders.domain.OrdersTestData.NOW;
import static com.dispatchflow.orders.domain.OrdersTestData.ORDER;
import static com.dispatchflow.orders.domain.OrdersTestData.RECIPIENT;
import static com.dispatchflow.orders.domain.OrdersTestData.REFERENCE;
import static com.dispatchflow.orders.domain.OrdersTestData.TODAY;
import static com.dispatchflow.orders.domain.OrdersTestData.TOMORROW;
import static com.dispatchflow.orders.domain.OrdersTestData.emptyDate;
import static com.dispatchflow.orders.domain.OrdersTestData.kilograms;
import static com.dispatchflow.orders.domain.OrdersTestData.litres;
import static com.dispatchflow.orders.domain.OrdersTestData.tenToNoon;
import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.Priority;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The one builder of {@link Order}s for tests: {@link #placed()} goes through {@link Order#place}
 * alone, and {@link #build()} reaches the state the builder describes through it and the commands,
 * then saves it (ADR-127 decision 2): a later attempt through put-backs onto its own date, then a
 * cancellation or an anonymization.
 *
 * <p>{@link #build} fails when the commands reach another state than {@link #description}, the
 * state the builder describes.
 */
final class OrderBuilder {

    private static final StopState NO_FACT = new StopState.NoFact();

    private DeliveryDate date = TOMORROW;
    private Optional<ExternalReference> reference = Optional.of(REFERENCE);
    private Attempt attempt = Attempt.FIRST;
    private @Nullable Instant cancelledAt;
    private boolean anonymized;
    private Instant lastChangedAt = NOW;

    private OrderBuilder() {}

    static OrderBuilder anOrder() {
        return new OrderBuilder();
    }

    OrderBuilder on(DeliveryDate onDate) {
        this.date = onDate;
        return this;
    }

    OrderBuilder withoutReference() {
        this.reference = Optional.empty();
        return this;
    }

    /** At a later attempt, each before it dropped from its plan and put back onto its own date. */
    OrderBuilder atAttempt(int number) {
        this.attempt = Attempt.of(number);
        return this;
    }

    /** Cancelled at a time, which is then its last change (FR-58). */
    OrderBuilder cancelledAt(Instant at) {
        this.cancelledAt = at;
        return this;
    }

    /** Anonymized on the 90th day after its date (NFR-257). */
    OrderBuilder anonymized() {
        this.anonymized = true;
        return this;
    }

    /** Placed, and put back, at a time. */
    OrderBuilder lastChangedAt(Instant at) {
        this.lastChangedAt = at;
        return this;
    }

    /** Places the order on its date, which must not be before {@link OrdersTestData#TODAY}. */
    Order placed() {
        return Order.place(ORDER, CUSTOMER, details(), OrdersTestData.DEFAULTS, emptyDate(date), TODAY, lastChangedAt);
    }

    /**
     * The order this builder describes.
     *
     * @return the description {@link #build}'s order matches
     */
    OrderDescription description() {
        return new OrderDescription(
                ORDER,
                CUSTOMER,
                date,
                tenToNoon(date),
                kilograms("12.5"),
                litres("40"),
                ServiceTime.ofSeconds(180),
                Priority.of(2),
                true,
                anonymized ? Optional.empty() : reference,
                anonymized ? Recipient.NONE : RECIPIENT,
                anonymized ? Optional.empty() : Optional.of(NOTE),
                anonymized ? AccessDetails.NONE : ACCESS,
                attempt,
                cancelledAt == null ? new Order.State.Active() : new Order.State.Cancelled(cancelledAt),
                anonymized,
                cancelledAt == null ? lastChangedAt : cancelledAt);
    }

    /**
     * Places the order on its date as if that day were today, puts it back onto its own date until
     * it reaches its attempt, cancels or anonymizes it as described, and saves it. On its own date a
     * put-back raises no event (ADR-115), so the saved order has none to forget.
     *
     * @return the order, at version 0 with no pending event
     * @throws AssertionError when the commands reach another state than the builder describes
     */
    Order build() {
        Order order = Order.place(
                ORDER, CUSTOMER, details(), OrdersTestData.DEFAULTS, emptyDate(date), date.value(), lastChangedAt);
        while (order.attempt().compareTo(attempt) < 0) {
            order.putBack(
                    new Placement.Dropped(order.attempt(), Placement.PlanStanding.IN_FORCE),
                    NO_FACT,
                    order.window(),
                    emptyDate(date),
                    date.value(),
                    lastChangedAt);
        }
        if (cancelledAt != null) {
            order.cancel(NO_FACT, cancelledAt);
        }
        if (anonymized) {
            order.anonymize(date.value().plusDays(Order.RETENTION_DAYS));
        }
        if (!OrderDescription.of(order).equals(description())) {
            throw new AssertionError("the commands reached " + OrderDescription.of(order) + ", not " + description());
        }
        return asSaved(order);
    }

    /** {@link OrdersTestData#details}, without the reference when the builder has none. */
    private OrderDetails details() {
        OrderDetails full = OrdersTestData.details(date);
        return new OrderDetails(
                full.window(),
                full.weight(),
                full.volume(),
                full.serviceTime(),
                full.priority(),
                full.leaveAtDoor(),
                reference,
                full.recipient(),
                full.note(),
                full.access());
    }
}
