package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.Objects;
import java.util.Optional;

/**
 * One manual edit of a ready plan, recorded so it can be undone and redone one at a time (FR-145).
 * The journal covers moves, removals, placements, pins and locks; a new edit clears redo, and an
 * applied solve or a removed stop clears the whole journal (ADR-101). Each entry holds what undoing
 * and redoing it need, so the journal is strictly last in, first out.
 */
public sealed interface Edit {

    /**
     * A stop moved within its route or to another (FR-22). Positions count from 0 in the route as it
     * was before and after the move.
     *
     * @param stop the stop, which keeps its ID
     * @param fromRoute the route it left
     * @param fromPosition its position there
     * @param toRoute the route it joined
     * @param toPosition its position there
     */
    record MovedStop(StopId stop, RouteId fromRoute, int fromPosition, RouteId toRoute, int toPosition)
            implements Edit {

        /** Checks the IDs are present. */
        public MovedStop {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(fromRoute, "fromRoute");
            Objects.requireNonNull(toRoute, "toRoute");
        }
    }

    /**
     * A dispatcher took a stop off its route into the drops (FR-146).
     *
     * @param stop the stop, whose ID undo restores
     * @param order its order
     * @param attempt its attempt
     * @param route the route it left
     * @param position its position there
     * @param plan its planned times, if it had them
     */
    record TookOff(StopId stop, OrderId order, Attempt attempt, RouteId route, int position, Optional<StopPlan> plan)
            implements Edit {

        /** Checks every part is present, possibly empty. */
        public TookOff {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(plan, "plan");
        }
    }

    /**
     * A dispatcher put a dropped order on a route (FR-235).
     *
     * @param order the order
     * @param attempt its attempt
     * @param stop the new stop's ID
     * @param route the route it joined
     * @param position its position there
     * @param before the reason it was dropped for, which undo restores
     */
    record Placed(OrderId order, Attempt attempt, StopId stop, RouteId route, int position, DropReason before)
            implements Edit {

        /** Checks every part is present. */
        public Placed {
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(before, "before");
        }
    }

    /**
     * An order was pinned (FR-91).
     *
     * @param order the order
     * @param before its pin before, if it had one
     * @param after its pin now
     */
    record Pinned(OrderId order, Optional<PinTarget> before, PinTarget after) implements Edit {

        /** Checks every part is present, possibly empty. */
        public Pinned {
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }
    }

    /**
     * An order's pin was removed.
     *
     * @param order the order
     * @param before the pin it had
     */
    record Unpinned(OrderId order, PinTarget before) implements Edit {

        /** Checks both parts are present. */
        public Unpinned {
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(before, "before");
        }
    }

    /**
     * A route was locked (FR-147).
     *
     * @param route the route
     * @param before its lock before, if it had one
     * @param after its lock now
     */
    record Locked(RouteId route, Optional<RouteLock> before, RouteLock after) implements Edit {

        /** Checks every part is present, possibly empty. */
        public Locked {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }
    }

    /**
     * A route's lock was removed.
     *
     * @param route the route
     * @param before the lock it had
     */
    record Unlocked(RouteId route, RouteLock before) implements Edit {

        /** Checks both parts are present. */
        public Unlocked {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(before, "before");
        }
    }
}
