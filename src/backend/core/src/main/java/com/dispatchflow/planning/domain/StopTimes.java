package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.domain.PlanningError.InvalidStopTimes;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;

/**
 * When a vehicle reaches a stop, starts serving it and leaves it, as the validator timed it (FR-17,
 * FR-23, DL §10.2). The times are whole seconds from one date's start (ADR-048, ADR-098), and may
 * pass its end on a route that breaks a time rule (ADR-112, ADR-113).
 *
 * @param arrival when the vehicle reaches the stop
 * @param start when service starts; later than the arrival only after a wait (BR-03)
 * @param departure when the vehicle leaves (BR-04)
 */
public record StopTimes(SolveTime arrival, SolveTime start, SolveTime departure) {

    /**
     * Checks the times.
     *
     * @throws RuleViolation with {@link InvalidStopTimes} when they are out of order or on different
     *     dates
     */
    public StopTimes {
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(departure, "departure");
        boolean oneDate = arrival.date().equals(start.date()) && start.date().equals(departure.date());
        if (!oneDate || start.seconds() < arrival.seconds() || departure.seconds() < start.seconds()) {
            throw new RuleViolation(new InvalidStopTimes());
        }
    }

    /**
     * Creates the times.
     *
     * @param arrival when the vehicle reaches the stop
     * @param start when service starts
     * @param departure when the vehicle leaves
     * @return the times
     * @throws RuleViolation with {@link InvalidStopTimes} when they are out of order or on different
     *     dates
     */
    public static StopTimes of(SolveTime arrival, SolveTime start, SolveTime departure) {
        return new StopTimes(arrival, start, departure);
    }
}
