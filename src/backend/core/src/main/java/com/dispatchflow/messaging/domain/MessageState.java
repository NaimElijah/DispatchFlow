package com.dispatchflow.messaging.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Where one message is in its lifecycle (DL §9.5). A message ends accepted or dropped.
 */
public sealed interface MessageState {

    /** Why a message was dropped. */
    enum DropCause {
        /** Still pending 15 minutes after its trigger: it never left (NFR-190). */
        STALE,
        /**
         * Still sending 15 minutes after its trigger (NFR-190). It may have left, so the provider's
         * later acceptance is still recorded (ADR-065, ADR-108).
         */
        STALE_WHILE_SENDING,
        /** The provider refused it. */
        REFUSED,
        /**
         * Its send's outcome is unknown, and the provider doesn't drop repeats (NFR-198) or 15
         * minutes have passed since its trigger (NFR-190).
         */
        OUTCOME_UNKNOWN,
        /** The phone opted out after the message was triggered (FR-363). */
        OPTED_OUT
    }

    /**
     * When the message reached this state.
     *
     * @return the time; for {@link Pending}, the trigger's
     */
    Instant since();

    /**
     * Whether the message counts toward the day's cap (NFR-196): it was claimed, so it may have
     * left. That is a sending, unknown or accepted message, and one dropped while its outcome was
     * unknown (ADR-108).
     *
     * @return {@code true} for those
     */
    default boolean countsTowardTheDailyCap() {
        return switch (this) {
            case Sending sending -> true;
            case Unknown unknown -> true;
            case Accepted accepted -> true;
            case Dropped(Instant at, DropCause cause) ->
                cause == DropCause.OUTCOME_UNKNOWN || cause == DropCause.STALE_WHILE_SENDING;
            case Pending pending -> false;
        };
    }

    /**
     * Whether the message has ended.
     *
     * @return {@code true} for {@link Accepted} and {@link Dropped}
     */
    default boolean hasEnded() {
        return this instanceof Accepted || this instanceof Dropped;
    }

    /**
     * Triggered and waiting for the sender.
     *
     * @param since the trigger time
     */
    record Pending(Instant since) implements MessageState {

        /** Checks the time is present. */
        public Pending {
            Objects.requireNonNull(since, "since");
        }
    }

    /**
     * Claimed by the sender, whose call to the provider is under way.
     *
     * @param since when it was claimed
     */
    record Sending(Instant since) implements MessageState {

        /** Checks the time is present. */
        public Sending {
            Objects.requireNonNull(since, "since");
        }
    }

    /**
     * The provider accepted it.
     *
     * @param since when
     * @param reference the provider's identifier for it
     */
    record Accepted(Instant since, ProviderReference reference) implements MessageState {

        /** Checks every part is present. */
        public Accepted {
            Objects.requireNonNull(since, "since");
            Objects.requireNonNull(reference, "reference");
        }
    }

    /**
     * The send timed out or the sender stopped mid-call, and the provider drops repeats by
     * DispatchFlow's reference, so it may be claimed again (NFR-198).
     *
     * @param since when the outcome became unknown
     */
    record Unknown(Instant since) implements MessageState {

        /** Checks the time is present. */
        public Unknown {
            Objects.requireNonNull(since, "since");
        }
    }

    /**
     * Never sent, or never known to be.
     *
     * @param since when it was dropped
     * @param cause why
     */
    record Dropped(Instant since, DropCause cause) implements MessageState {

        /** Checks every part is present. */
        public Dropped {
            Objects.requireNonNull(since, "since");
            Objects.requireNonNull(cause, "cause");
        }
    }
}
