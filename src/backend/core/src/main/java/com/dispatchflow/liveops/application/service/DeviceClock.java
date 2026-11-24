package com.dispatchflow.liveops.application.service;

import java.time.Instant;
import java.util.Objects;

/**
 * What a phone says about the time of a report: when the device says it happened, and the device's
 * clock when it sent it, the {@code X-Device-Time} header (BR-44, BR-114, ADR-087). The use case
 * reads the server's receipt time from the {@code Clock} and builds the domain's {@code
 * CorrectedTime} from the three, which checks the device's instants.
 *
 * <p>To Be Implemented.
 *
 * @param deviceTime when the device says the report or the position was made
 * @param deviceClockAtSend the device's clock when it sent the request
 */
public record DeviceClock(Instant deviceTime, Instant deviceClockAtSend) {

    /** Checks both instants are present. */
    public DeviceClock {
        Objects.requireNonNull(deviceTime, "deviceTime");
        Objects.requireNonNull(deviceClockAtSend, "deviceClockAtSend");
    }
}
