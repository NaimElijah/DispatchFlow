package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.shared.StopId;
import java.util.Objects;

/**
 * Why a stop's recipient gets no messages, which the dispatcher sees (FR-378, FR-348, FR-350,
 * FR-363).
 *
 * <p>Built in M12.
 *
 * @param stop the stop
 * @param reason why it gets none, or that it may get them
 */
public record WhyNoMessagesView(StopId stop, Reason reason) {

    /** Checks both parts are present. */
    public WhyNoMessagesView {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(reason, "reason");
    }

    /**
     * What stands between a stop and its messages, the first that applies in this order.
     *
     * <p>Built in M12.
     */
    public enum Reason {
        /** Messages are off for the company (FR-350). */
        MESSAGES_OFF,
        /** Neither the order nor its customer has a phone (FR-348). */
        NO_PHONE,
        /** The phone chosen is not an Israeli mobile (FR-378). */
        NOT_AN_ISRAELI_MOBILE,
        /** The phone opted out (FR-363, FR-380). */
        OPTED_OUT,
        /** Nothing: messages may go to the stop's phone. */
        MAY_BE_MESSAGED
    }
}
