package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.message.DailySendCap;
import com.dispatchflow.messaging.domain.message.StopMessages;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Where each stop's {@link StopMessages} is kept, keyed by its stop, each message unique by its
 * stop, type and sequence (DL §3.6, §4.6, NFR-191).
 *
 * <p>Built in M12.
 */
public interface StopMessagesRepository {

    /**
     * One stop's messages.
     *
     * <p>Built in M12.
     *
     * @param stop the stop
     * @return the messages, or empty when the stop has had no trigger
     */
    Optional<StopMessages> get(StopId stop);

    /**
     * Stores a stop's messages on its first trigger, in the caller's transaction.
     *
     * <p>Built in M12.
     *
     * @param messages the stop's messages, at {@code Version.NEW}
     * @throws StaleVersion when another trigger stored the stop's messages first; the caller retries
     *     the trigger as a change
     */
    void add(StopMessages messages);

    /**
     * Stores a stop's changed messages, checking the version they were loaded at (BR-76, DL §11).
     *
     * <p>Built in M12.
     *
     * @param messages the stop's messages
     * @throws StaleVersion when a trigger or the sender saved them since they were loaded; the
     *     sender retries its claim (DL §11)
     */
    void save(StopMessages messages);

    /**
     * The stops with a message the sender may claim: pending, or of unknown outcome and retryable
     * (DL §9.5).
     *
     * <p>Built in M12.
     *
     * @return the stops, each once, the oldest trigger first
     */
    List<StopId> withPendingMessages();

    /**
     * The messages claimed on an Israel day that count toward the cap of 1,000 (NFR-196, ADR-088,
     * ADR-108).
     *
     * <p>Built in M12.
     *
     * @param day the day, in {@code Asia/Jerusalem}
     * @return the cap as it stands
     */
    DailySendCap capOn(LocalDate day);

    /**
     * When a message to a phone was last accepted by the provider, which decides whether an opt-out
     * is confirmed (FR-363, ADR-108). Phones are compared in their stored E.164 form (FR-377).
     *
     * <p>Built in M12.
     *
     * @param phone the phone
     * @return the latest acceptance, or empty when no message to it was ever accepted
     */
    Optional<Instant> lastAcceptedTo(PhoneNumber phone);

    /**
     * Deletes every stop's messages whose retention has ended: 90 days after the last of them
     * reached its state (NFR-184, ADR-108).
     *
     * <p>Built in M12.
     *
     * @param now when the retention job runs
     * @return how many stops' messages were deleted
     */
    int deleteExpired(Instant now);
}
