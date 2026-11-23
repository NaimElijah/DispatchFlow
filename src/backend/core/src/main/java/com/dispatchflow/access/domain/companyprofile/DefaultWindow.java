package com.dispatchflow.access.domain.companyprofile;

import com.dispatchflow.access.domain.AccessError.InvalidDefaultWindow;
import com.dispatchflow.shared.RuleViolation;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;
import java.util.Objects;

/**
 * The time window a new customer starts with, as Israel wall-clock times of any day (FR-125,
 * BR-49): it opens before it closes, both on one day (BR-35).
 *
 * <p>It is not the kernel's {@code TimeWindow}, which lies on one delivery date: a default belongs
 * to no date. Customers & Orders reads it through Access's API and makes its own window on each
 * order's date, where the kernel's value checks that date's skipped hour (BR-111; ADR-104).
 *
 * <p>It is stored in its profile's columns (ADR-126). Its constructor checks a rule, so Access's
 * persistence adapter registers an instantiator that rebuilds it through {@code
 * StoredStateInvalid.whileRestoring}, and a stored window that no longer opens before it closes is
 * an incident, not a refusal (ADR-127).
 *
 * @param opens when the window opens
 * @param closes when it closes
 */
@Embeddable
public record DefaultWindow(LocalTime opens, LocalTime closes) {

    /**
     * Checks the window.
     *
     * @throws RuleViolation with {@link InvalidDefaultWindow} when it does not open before it
     *     closes
     */
    public DefaultWindow {
        Objects.requireNonNull(opens, "opens");
        Objects.requireNonNull(closes, "closes");
        if (!opens.isBefore(closes)) {
            throw new RuleViolation(new InvalidDefaultWindow(opens, closes));
        }
    }
}
