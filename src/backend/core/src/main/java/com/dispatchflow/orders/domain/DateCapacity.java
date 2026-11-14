package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A date's uncancelled orders, read under the date's transaction-scoped advisory lock, and the
 * limit of {@value #LIMIT} they may reach (BR-101, BR-113, NFR-05, ADR-060; DL §5). Every method that
 * adds an order to a date requires one: {@link Order#place}, {@link Order#reschedule}, {@link
 * Order#putBack} and {@link ImportBatch#confirm}. Only the repository query that takes the lock
 * builds it, which an ArchUnit rule checks (DL §3.9 rule 6).
 *
 * <p>It also holds each order's reference, so the same snapshot decides whether a reference is free
 * on the date (FR-211). A cancelled order is not among them, so its reference no longer counts
 * (ADR-102).
 */
public final class DateCapacity implements PersonalData {

    /** The most uncancelled orders a date holds (BR-101, NFR-05). */
    public static final int LIMIT = 200;

    /**
     * One uncancelled order of the date.
     *
     * @param order the order
     * @param reference its outside reference, if it has one
     */
    public record DatedOrder(OrderId order, Optional<ExternalReference> reference) implements PersonalData {

        /** Checks both parts are given. */
        public DatedOrder {
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(reference, "reference");
        }

        /**
         * A form that hides the reference, because it holds personal data.
         *
         * @return {@code DatedOrder[redacted]}
         */
        @Override
        public String toString() {
            return "DatedOrder[redacted]";
        }
    }

    private final DeliveryDate date;
    private final int count;
    private final Set<OrderId> orders;
    private final Map<ExternalReference, Set<OrderId>> holders;

    private DateCapacity(DeliveryDate date, List<DatedOrder> uncancelled) {
        this.date = date;
        this.count = uncancelled.size();
        this.orders = uncancelled.stream().map(DatedOrder::order).collect(Collectors.toUnmodifiableSet());
        // Only looked up, never iterated, so the indexes' order never shows.
        this.holders = uncancelled.stream()
                .filter(dated -> dated.reference().isPresent())
                .collect(Collectors.groupingBy(
                        dated -> dated.reference().orElseThrow(),
                        Collectors.mapping(DatedOrder::order, Collectors.toUnmodifiableSet())));
    }

    /**
     * Builds the snapshot from the query that holds the date's lock.
     *
     * @param date the date
     * @param uncancelledOrders every uncancelled order of the date, failed ones included (BR-113)
     * @return the snapshot
     */
    public static DateCapacity readUnderLock(DeliveryDate date, List<DatedOrder> uncancelledOrders) {
        return new DateCapacity(Objects.requireNonNull(date, "date"), List.copyOf(uncancelledOrders));
    }

    /**
     * The date this snapshot is for; an order joining through it takes this date.
     *
     * @return the date
     */
    public DeliveryDate date() {
        return date;
    }

    /** How many uncancelled orders the date holds. */
    int count() {
        return count;
    }

    /** Whether an order is already among the date's, so it doesn't count again (BR-113). */
    boolean holds(OrderId order) {
        return orders.contains(order);
    }

    /** Whether an order of the date other than {@code self} carries the reference (FR-211). */
    boolean referenceTakenByAnother(ExternalReference reference, Optional<OrderId> self) {
        Set<OrderId> holding = holders.getOrDefault(reference, Set.of());
        return self.map(id -> holding.stream().anyMatch(holder -> !holder.equals(id)))
                .orElse(!holding.isEmpty());
    }

    /**
     * A form that hides the date's orders, because it holds personal data.
     *
     * @return {@code DateCapacity[redacted]}
     */
    @Override
    public String toString() {
        return "DateCapacity[redacted]";
    }
}
