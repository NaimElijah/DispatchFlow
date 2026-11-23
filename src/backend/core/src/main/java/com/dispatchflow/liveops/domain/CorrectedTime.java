package com.dispatchflow.liveops.domain;

import com.dispatchflow.liveops.domain.LiveOpsError.DeviceTimeOutOfRange;
import com.dispatchflow.shared.RuleViolation;
import jakarta.persistence.Embeddable;
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
 * <p>It is stored in its owner's three columns (ADR-126). Its constructor checks a rule, so Live
 * Operations' persistence adapter registers an instantiator that rebuilds it through {@code
 * StoredStateInvalid.whileRestoring}.
 *
 * @param deviceTime when the device says the report was made
 * @param deviceClockAtSend the device's clock when it sent the report ({@code X-Device-Time})
 * @param receivedAt when the server received it
 */
@Embeddable
public record CorrectedTime(Instant deviceTime, Instant deviceClockAtSend, Instant receivedAt) {

    /** How far inside the report's span a contact must fall to hold it (BR-133). */
    public static final Duration CONTACT_MARGIN = Duration.ofMinutes(2);

    /**
     * The earliest device instant accepted: the start of year 1, as for a delivery date. The bound
     * is chosen, not where arithmetic fails: it keeps the corrected time and its contact window far
     * inside what an {@code Instant} holds, whatever the device sends (ADR-114).
     */
    private static final Instant EARLIEST_DEVICE_TIME = Instant.parse("0001-01-01T00:00:00Z");

    /** The latest device instant accepted: the end of year 9999 (ADR-114). */
    private static final Instant LATEST_DEVICE_TIME = Instant.parse("9999-12-31T23:59:59.999999999Z");

    /**
     * Checks every part is present, and that the device's two instants can be corrected.
     *
     * @throws RuleViolation with {@link DeviceTimeOutOfRange} when the device time or the device's
     *     clock lies outside the years 1 to 9999 (ADR-114)
     */
    public CorrectedTime {
        Objects.requireNonNull(deviceTime, "deviceTime");
        Objects.requireNonNull(deviceClockAtSend, "deviceClockAtSend");
        Objects.requireNonNull(receivedAt, "receivedAt");
        if (isOutOfRange(deviceTime) || isOutOfRange(deviceClockAtSend)) {
            throw new RuleViolation(new DeviceTimeOutOfRange());
        }
    }

    /**
     * Creates the time.
     *
     * @param deviceTime when the device says the report was made
     * @param deviceClockAtSend the device's clock when it sent the report
     * @param receivedAt when the server received it
     * @return the time
     * @throws RuleViolation with {@link DeviceTimeOutOfRange} when a device instant lies outside the
     *     years 1 to 9999
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

    private static boolean isOutOfRange(Instant deviceInstant) {
        return deviceInstant.isBefore(EARLIEST_DEVICE_TIME) || deviceInstant.isAfter(LATEST_DEVICE_TIME);
    }
}
