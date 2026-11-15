package com.dispatchflow.planning.application.port.out;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * The fixed values every solve is scored and checked with, which the core sends in each problem
 * (ADR-029, ADR-121): the door-window rate (BR-120), the lateness rate and the moved-stop cost
 * (BR-115), and whether routes reserve time for breaks (FR-81, BR-105). They are tuned constants,
 * fixed before the study's measured runs, so no Admin changes them on screen and they are not
 * planning settings (ADR-121).
 *
 * <p>The configuration adapter binds them from the core's configuration at M0.
 *
 * <p>Built in M0.
 */
public interface SolveConfiguration {

    /**
     * The values the core is configured with.
     *
     * <p>Built in M0.
     *
     * @return the values
     */
    Values values();

    /**
     * The configured values, each in the unit the optimizer's {@code PenaltyScale} takes it in.
     *
     * <p>Built in M0.
     *
     * @param doorWindowRate the seconds a leave-at-door stop adds to the objective for each second
     *     it is served outside its window, in solves and re-optimizations (BR-120)
     * @param latenessRate the seconds a re-optimization adds for each second a stop is served past
     *     its hard window's end; a daily solve keeps hard windows and takes none (BR-03, BR-115)
     * @param movedStopCost what a re-optimization adds for each stop that leaves its vehicle; a daily
     *     solve has no stop on a vehicle yet and takes none (BR-115)
     * @param breaksReserved whether routes reserve time for driver breaks (FR-81, BR-105)
     */
    record Values(Factor doorWindowRate, Factor latenessRate, Duration movedStopCost, boolean breaksReserved) {

        /**
         * Checks every part is present and the cost is not negative.
         *
         * @throws IllegalArgumentException when the moved-stop cost is negative
         */
        public Values {
            Objects.requireNonNull(doorWindowRate, "doorWindowRate");
            Objects.requireNonNull(latenessRate, "latenessRate");
            Objects.requireNonNull(movedStopCost, "movedStopCost");
            if (movedStopCost.isNegative()) {
                throw new IllegalArgumentException("movedStopCost is negative");
            }
        }
    }

    /**
     * Seconds of objective per second of a measured time, such as {@code 5} for BR-115's and
     * BR-120's starting values. It is held in its normal form, so {@code 5.0} and {@code 5} are equal.
     *
     * <p>Built in M0.
     *
     * @param value the factor, zero or more
     */
    record Factor(BigDecimal value) {

        /**
         * Checks the factor and normalizes it.
         *
         * @throws IllegalArgumentException when it is below zero
         */
        public Factor {
            Objects.requireNonNull(value, "value");
            if (value.signum() < 0) {
                throw new IllegalArgumentException("factor is below zero");
            }
            value = value.stripTrailingZeros();
        }
    }
}
