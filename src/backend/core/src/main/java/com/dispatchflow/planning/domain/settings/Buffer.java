package com.dispatchflow.planning.domain.settings;

import com.dispatchflow.planning.domain.PlanningError.NegativeBuffer;
import com.dispatchflow.shared.RuleViolation;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * The safety margin added to travel times, in percent: 10 to begin with, calibrated from pilot
 * arrivals (FR-227, DL §17 "Buffer"). It is held in its normal form.
 *
 * @param percent the margin, zero or more
 */
public record Buffer(BigDecimal percent) {

    /** The buffer FR-227 starts with: 10%. */
    public static final Buffer STARTING = new Buffer(BigDecimal.TEN);

    /**
     * Checks the margin and normalizes it.
     *
     * @throws RuleViolation with {@link NegativeBuffer} when it is below zero
     */
    public Buffer {
        Objects.requireNonNull(percent, "percent");
        if (percent.signum() < 0) {
            throw new RuleViolation(new NegativeBuffer());
        }
        percent = percent.stripTrailingZeros();
    }

    /**
     * Creates the buffer.
     *
     * @param percent the margin in percent
     * @return the buffer
     * @throws RuleViolation with {@link NegativeBuffer} when it is below zero
     */
    public static Buffer percent(BigDecimal percent) {
        return new Buffer(percent);
    }
}
