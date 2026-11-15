package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.fleet.domain.FleetError.InvalidDepotHours;
import com.dispatchflow.shared.RuleViolation;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A depot's opening hours: one opening and one closing time in Israel that apply on every delivery
 * date (FR-129, BR-46, ADR-103). They are wall-clock times rather than {@code DaySeconds},
 * because {@code DaySeconds} count from one date's midnight and these hold on every date.
 *
 * @param opens when routes may first leave the depot
 * @param closes when routes must be back
 */
public record DepotHours(LocalTime opens, LocalTime closes) {

    /**
     * Checks the depot closes after it opens.
     *
     * @throws RuleViolation with {@link InvalidDepotHours} when the closing time is not after the
     *     opening time
     */
    public DepotHours {
        Objects.requireNonNull(opens, "opens");
        Objects.requireNonNull(closes, "closes");
        if (!opens.isBefore(closes)) {
            throw new RuleViolation(new InvalidDepotHours(opens, closes));
        }
    }

    /**
     * Creates the hours.
     *
     * @param opens the opening time
     * @param closes the closing time
     * @return the hours
     * @throws RuleViolation with {@link InvalidDepotHours} when the closing time is not after the
     *     opening time
     */
    public static DepotHours of(LocalTime opens, LocalTime closes) {
        return new DepotHours(opens, closes);
    }
}
