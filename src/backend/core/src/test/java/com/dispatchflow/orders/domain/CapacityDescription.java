package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a test sees of a {@link DateCapacity}: its date, how many orders it counts, which of some
 * orders it holds, and which of some references another order already carries. The persistence
 * tests read a capacity the repository built through it, because those answers are the package's
 * own.
 *
 * @param date the date
 * @param count how many uncancelled orders it counts
 * @param held the orders asked about that it holds
 * @param referencesTaken the references asked about that an order of the date carries
 */
public record CapacityDescription(
        DeliveryDate date, int count, Set<OrderId> held, Set<ExternalReference> referencesTaken) {

    /**
     * Describes a capacity.
     *
     * @param capacity the capacity
     * @param orders the orders to ask about
     * @param references the references to ask about
     * @return what it answers
     */
    public static CapacityDescription of(
            DateCapacity capacity, List<OrderId> orders, List<ExternalReference> references) {
        return new CapacityDescription(
                capacity.date(),
                capacity.count(),
                orders.stream().filter(capacity::holds).collect(Collectors.toUnmodifiableSet()),
                references.stream()
                        .filter(reference -> capacity.referenceTakenByAnother(reference, Optional.empty()))
                        .collect(Collectors.toUnmodifiableSet()));
    }
}
