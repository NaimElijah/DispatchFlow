package com.dispatchflow.messaging.domain;

import java.util.Objects;

/**
 * Which fixed text a message has, with the facts that text gives (FR-349, ADR-031, ADR-108).
 * {@link MessageText#of} writes it after the company's name, in the company's language.
 */
public sealed interface Wording {

    /**
     * "Out for delivery", with the projected window (FR-351, FR-372).
     *
     * @param arrival the rounded arrival, the window's middle
     */
    record OutForDelivery(QuarterHour arrival) implements Wording {

        /** Checks the arrival is present. */
        public OutForDelivery {
            Objects.requireNonNull(arrival, "arrival");
        }
    }

    /** "Arriving in about 15 minutes" (FR-352). */
    record ArrivingSoon() implements Wording {}

    /**
     * "Time changed", with the new window (FR-353).
     *
     * @param arrival the rounded arrival, the window's middle
     */
    record TimeChanged(QuarterHour arrival) implements Wording {

        /** Checks the arrival is present. */
        public TimeChanged {
            Objects.requireNonNull(arrival, "arrival");
        }
    }

    /**
     * "Your delivery changed", with the dispatch phone (FR-354).
     *
     * @param dispatchPhone the company's dispatch phone
     */
    record DeliveryChanged(DispatchPhone dispatchPhone) implements Wording {

        /** Checks the phone is present. */
        public DeliveryChanged {
            Objects.requireNonNull(dispatchPhone, "dispatchPhone");
        }
    }

    /**
     * "Missed you", with the dispatch phone and not the failure reason (FR-355).
     *
     * @param dispatchPhone the company's dispatch phone
     */
    record MissedYou(DispatchPhone dispatchPhone) implements Wording {

        /** Checks the phone is present. */
        public MissedYou {
            Objects.requireNonNull(dispatchPhone, "dispatchPhone");
        }
    }

    /**
     * "Delivered", saying whether it was left at the door, and asking for a rating while the rating
     * question is on (FR-356).
     *
     * @param leftAtDoor whether the order was left at the door (FR-366)
     * @param asksForRating whether the message asks for a rating from 1 to 10 (FR-375)
     */
    record Delivered(boolean leftAtDoor, boolean asksForRating) implements Wording {}

    /**
     * A message that answers a reply (FR-358, FR-359, FR-363).
     *
     * @param message which one
     */
    record Answering(ReplyMessage message) implements Wording {

        /** Checks the message is present. */
        public Answering {
            Objects.requireNonNull(message, "message");
        }
    }
}
