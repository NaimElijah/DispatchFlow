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
import static com.dispatchflow.orders.domain.OrdersTestData.kilograms;
import static com.dispatchflow.orders.domain.OrdersTestData.litres;
import static com.dispatchflow.orders.domain.OrdersTestData.tenToNoon;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.Optional;

/**
 * The one builder of {@link Order}s for tests: {@link #placed()} goes through {@link Order#place}, and
 * {@link #restored()} through {@link Order#restore} for states a factory doesn't make, such as a
 * cancelled, put-back or anonymized order.
 */
final class OrderBuilder {

    private DeliveryDate date = TOMORROW;
    private Optional<ExternalReference> reference = Optional.of(REFERENCE);
    private Attempt attempt = Attempt.FIRST;
    private Order.State state = new Order.State.Active();
    private boolean anonymized;
    private Instant lastChangedAt = NOW;
    private Version version = Version.of(3);

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

    OrderBuilder atAttempt(int number) {
        this.attempt = Attempt.of(number);
        return this;
    }

    OrderBuilder cancelledAt(Instant at) {
        this.state = new Order.State.Cancelled(at);
        return this;
    }

    OrderBuilder anonymized() {
        this.anonymized = true;
        return this;
    }

    OrderBuilder lastChangedAt(Instant at) {
        this.lastChangedAt = at;
        return this;
    }

    /** Places the order on its date, which must not be before {@link OrdersTestData#TODAY}. */
    Order placed() {
        return Order.place(
                ORDER,
                CUSTOMER,
                OrdersTestData.details(date),
                OrdersTestData.DEFAULTS,
                OrdersTestData.emptyDate(date),
                TODAY,
                lastChangedAt);
    }

    OrderSnapshot snapshot() {
        return new OrderSnapshot(
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
                state,
                anonymized,
                lastChangedAt,
                version);
    }

    Order restored() {
        return Order.restore(snapshot());
    }
}
