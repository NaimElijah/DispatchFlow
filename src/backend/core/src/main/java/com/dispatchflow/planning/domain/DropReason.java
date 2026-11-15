package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.util.Objects;

/**
 * Why an order is unserved, with the fact that caused it (FR-18, FR-144). It is FR-223's full list,
 * in the order the optimizer tests it (ADR-053, as ADR-100 amends it), and it mirrors the Python
 * {@code drops.py}, which alone decides the reason (BR-42). A fact's limit lies in the day, but the
 * time compared with it is never capped, so it may pass the date's end (ADR-112, ADR-113).
 */
public sealed interface DropReason {

    /**
     * Whether this reason is about the order's location, which a dispatcher can't place by hand
     * until a solve finds it again (FR-237).
     *
     * @return {@code true} for a missing or unreachable location
     */
    default boolean isAboutLocation() {
        return this instanceof MissingLocation || this instanceof UnreachableLocation;
    }

    /** The order's customer has no coordinates (BR-14). */
    record MissingLocation() implements DropReason {}

    /**
     * No vehicle can drive a route on the date at all, for example because every depot lacks
     * coordinates (FR-197, ADR-100). It comes before every per-vehicle test. Its fact is the date's
     * vehicle exclusions, which the solve's result and the plan hold, so it carries no field
     * (ADR-112).
     */
    record NoVehicleAvailable() implements DropReason {}

    /** No usable vehicle can reach the order by road and return to its end depot (FR-66). */
    record UnreachableLocation() implements DropReason {}

    /** A dispatcher took the order's stop off its route (FR-146). */
    record RemovedByDispatcher() implements DropReason {}

    /**
     * The order's pin can't be honoured (FR-228).
     *
     * @param pin what the order is pinned to
     * @param targetOut {@code true} when that vehicle or driver drives no route in the solve, {@code
     *     false} when it can't serve the order even alone
     */
    record PinNotHonoured(PinTarget pin, boolean targetOut) implements DropReason {

        /** Checks the pin is present. */
        public PinNotHonoured {
            Objects.requireNonNull(pin, "pin");
        }
    }

    /**
     * No vehicle that can reach the order can carry it, even alone (FR-223, FR-144).
     *
     * @param weight the order's weight
     * @param volume the order's volume
     * @param largestWeightCapacity the largest weight capacity among those vehicles
     * @param largestVolumeCapacity the largest volume capacity among those vehicles
     */
    record Capacity(Weight weight, Volume volume, Weight largestWeightCapacity, Volume largestVolumeCapacity)
            implements DropReason {

        /** Checks every part is present. */
        public Capacity {
            Objects.requireNonNull(weight, "weight");
            Objects.requireNonNull(volume, "volume");
            Objects.requireNonNull(largestWeightCapacity, "largestWeightCapacity");
            Objects.requireNonNull(largestVolumeCapacity, "largestVolumeCapacity");
        }
    }

    /**
     * The earliest arrival any vehicle that can carry the order makes is after its window; never the
     * reason for a leave-at-door order (BR-121).
     *
     * @param windowEnd when the order's window ends
     * @param earliestArrival the earliest such arrival, possibly past the date's end
     */
    record TimeWindow(DaySeconds windowEnd, SolveTime earliestArrival) implements DropReason {

        /** Checks both times are present. */
        public TimeWindow {
            Objects.requireNonNull(windowEnd, "windowEnd");
            Objects.requireNonNull(earliestArrival, "earliestArrival");
        }
    }

    /**
     * Depot to order to depot fits no vehicle's driver's shift (BR-06, BR-105, BR-119).
     *
     * @param latestReturn the binding limit: the shift end, less the break reserve where one applies
     * @param earliestReturn the earliest return, serving the order alone, possibly past the date's
     *     end
     */
    record Shift(DaySeconds latestReturn, SolveTime earliestReturn) implements DropReason {

        /** Checks both times are present. */
        public Shift {
            Objects.requireNonNull(latestReturn, "latestReturn");
            Objects.requireNonNull(earliestReturn, "earliestReturn");
        }
    }

    /**
     * Depot to order to depot doesn't fit before the end depot closes (FR-195, BR-46).
     *
     * @param depotCloses when the end depot closes
     * @param earliestReturn the earliest return, serving the order alone, possibly past the date's
     *     end
     */
    record DepotHours(DaySeconds depotCloses, SolveTime earliestReturn) implements DropReason {

        /** Checks both times are present. */
        public DepotHours {
            Objects.requireNonNull(depotCloses, "depotCloses");
            Objects.requireNonNull(earliestReturn, "earliestReturn");
        }
    }

    /** A vehicle limit of FR-133 prevents serving the order (FR-201); the optimizer doesn't produce it yet. */
    record VehicleLimit() implements DropReason {}

    /** The order would fit alone, and other orders took its place; no single cause is named (Q-51). */
    record Displaced() implements DropReason {}
}
