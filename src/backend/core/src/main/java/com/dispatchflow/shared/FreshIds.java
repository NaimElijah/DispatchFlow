package com.dispatchflow.shared;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * A supply of new UUIDs for a method that creates an unknown number of child entities, such as the
 * routes and stops of a solve result. The use case fills it from {@code IdGenerator} before the
 * call, because the domain never makes IDs (DL §3.9 rule 3): up to 15 {@code RouteId}s and 200
 * {@code StopId}s per solve. A method that creates one child takes its ID instead.
 *
 * <p>It is consumed as it is drawn from, so it is not a value and is not shared between calls.
 */
public final class FreshIds {

    private final Deque<UUID> remaining;

    private FreshIds(List<UUID> ids) {
        this.remaining = new ArrayDeque<>(ids);
    }

    /**
     * Creates a supply.
     *
     * @param ids new UUIDs, drawn in this order
     * @return the supply
     */
    public static FreshIds of(List<UUID> ids) {
        return new FreshIds(List.copyOf(ids));
    }

    /**
     * Draws the next UUID; the caller wraps it in its typed ID.
     *
     * @return the next UUID supplied
     * @throws NoSuchElementException when the supply is used up: the use case filled too few, a
     *     programming error rather than a refusal, so no {@link DomainError} describes it
     */
    public UUID next() {
        if (remaining.isEmpty()) {
            throw new NoSuchElementException("FreshIds is used up: the use case supplied too few new IDs");
        }
        return remaining.removeFirst();
    }

    /**
     * How many UUIDs are left.
     *
     * @return the count not yet drawn
     */
    public int remaining() {
        return remaining.size();
    }
}
