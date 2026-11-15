package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.OptOutRecord;
import com.dispatchflow.messaging.domain.PhoneConsent;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;
import java.util.Optional;

/**
 * Whether a phone opted out, as a dispatcher sees it on a customer or an order, with who made the
 * latest change and when (FR-380, FR-363). The entry singles out one person's phone, so it prints
 * redacted.
 *
 * <p>Built in M12.
 *
 * @param consent whether messages may go to the phone
 * @param latest the latest entry of its history, which decides; empty when it has none
 */
public record OptOutStandingView(PhoneConsent consent, Optional<OptOutRecord> latest) implements PersonalData {

    /** Checks both parts are present, possibly empty. */
    public OptOutStandingView {
        Objects.requireNonNull(consent, "consent");
        Objects.requireNonNull(latest, "latest");
    }

    /**
     * A form that names the standing and hides the entry.
     *
     * @return the consent, redacted
     */
    @Override
    public String toString() {
        return "OptOutStandingView[consent=" + consent + ", redacted]";
    }
}
