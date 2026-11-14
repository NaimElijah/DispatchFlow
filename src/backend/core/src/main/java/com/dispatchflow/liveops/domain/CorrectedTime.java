package com.dispatchflow.liveops.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * When a driver's report happened, by the server's clock (DL §3.3, ADR-087). Every report keeps the
 * time its device recorded and the time the server received it (BR-44). The device's clock offset is
 * the receipt time minus the device's clock when it sent the report, and the corrected time is the
 * device time plus that offset (BR-114), cut to the receipt time if it would be later (BR-110).
 *
 * @param deviceTime when the device says the report was made
 * @param deviceClockAtSend the device's clock when it sent the report ({@code X-Device-Time})
 * @param receivedAt when the server received it
 */
public record CorrectedTime(Instant deviceTime, Instant deviceClockAtSend, Instant receivedAt) {

    /** How far inside the report's span a contact must fall to hold it (BR-133). */
    public static final Duration CONTACT_MARGIN = Duration.ofMinutes(2);

    /** Checks every part is present. */
    public CorrectedTime {
        Objects.requireNonNull(deviceTime, "deviceTime");
        Objects.requireNonNull(deviceClockAtSend, "deviceClockAtSend");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    /**
     * Creates the time.
     *
     * @param deviceTime when the device says the report was made
     * @param deviceClockAtSend the device's clock when it sent the report
     * @param receivedAt when the server received it
     * @return the time
     */
    public static CorrectedTime of(Instant deviceTime, Instant deviceClockAtSend, Instant receivedAt) {
        return new CorrectedTime(deviceTime, deviceClockAtSend, receivedAt);
    }

    /**
     * The report's time on the server's clock, never later than its receipt (BR-110, BR-114).
     *
     * @return the corrected time
     */
    public Instant corrected() {
        Instant moved = deviceTime.plus(Duration.between(deviceClockAtSend, receivedAt));
        return moved.isAfter(receivedAt) ? receivedAt : moved;
    }

    /**
     * The span in which a contact from the reporting account shows that the phone was online after
     * the time it claims (BR-133): from 2 minutes after the corrected time to 2 minutes before the
     * receipt, both ends included (ADR-107).
     *
     * @return the window, or empty when the report arrived within 4 minutes of its corrected time,
     *     so a report sent online is never held
     */
    public Optional<ContactWindow> contactWindow() {
        Instant from = corrected().plus(CONTACT_MARGIN);
        Instant to = receivedAt.minus(CONTACT_MARGIN);
        return from.isAfter(to) ? Optional.empty() : Optional.of(new ContactWindow(from, to));
    }
}
