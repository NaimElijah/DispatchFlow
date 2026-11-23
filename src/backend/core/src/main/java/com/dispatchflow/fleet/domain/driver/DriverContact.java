package com.dispatchflow.fleet.domain.driver;

import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * A driver's personal fields: their name and the contact phone the web app shows beside their
 * route (FR-132). Anonymizing the driver removes both together (BR-98, ADR-075), and {@link
 * #toString()} hides them (NFR-19, ADR-106).
 *
 * <p>A driver embeds it in two columns, {@code name} and {@code phone}, both null once the driver is
 * anonymized. Its constructor only checks both parts are present, and each part's converter
 * rebuilds it through {@code StoredStateInvalid.whileRestoring}, so Hibernate's own record
 * instantiator builds it (ADR-126).
 *
 * @param name the driver's name
 * @param phone the driver's contact phone, never a recovery channel (FR-200)
 */
@Embeddable
public record DriverContact(DriverName name, PhoneNumber phone) implements PersonalData {

    /** Checks both are present. */
    public DriverContact {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(phone, "phone");
    }

    /**
     * A form that hides the name and the phone, because both are personal data.
     *
     * @return {@code DriverContact[redacted]}
     */
    @Override
    public String toString() {
        return "DriverContact[redacted]";
    }
}
