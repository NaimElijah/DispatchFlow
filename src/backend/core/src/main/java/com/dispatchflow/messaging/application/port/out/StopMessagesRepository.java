package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.OutgoingMessageId;
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
 * <p>Built in M12 and M13, method by method.
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
     * @throws StaleVersion when another trigger stored the stop's messages first; the caller
     *     retries the trigger as a change
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
     * The messages that count toward an Israel day's cap of 1,000 (NFR-196, ADR-088, ADR-108): the
     * stops' messages claimed that day that count ({@code MessageState.countsTowardTheDailyCap}),
     * and the reply answers counted that day through {@link #countAnswer}, each of which may have
     * left.
     *
     * <p>It takes the day's lock in the caller's transaction, held until that transaction ends, so
     * the sender's claim and {@link #countAnswer} on the same day run one at a time, and never both
     * take the cap's last place.
     *
     * <p>Built in M12.
     *
     * @param day the day, in {@code Asia/Jerusalem}
     * @return the cap as it stands
     */
    DailySendCap capOn(LocalDate day);

    /**
     * Counts a reply's answer, a thank-you, a format reminder or an opt-out confirmation, toward an
     * Israel day's cap, in the caller's transaction, before it is sent (NFR-196, ADR-108), but only
     * while the cap has room: below {@code DailySendCap.LIMIT}. It takes the same day's lock as
     * {@link #capOn}, which the sender's claim reads under, so the check and the count are one
     * atomic step. An answer keeps no state of its own and belongs to no stop, so this records only
     * that it was sent that day, for {@link #capOn} to count; counting the same answer twice
     * changes nothing, and answers as the first count did.
     *
     * <p>Built in M12.
     *
     * @param answer the answer's own ID, the reference the provider is given
     * @param day the day it is sent, in {@code Asia/Jerusalem}
     * @return {@code true} when the answer counts and may be sent, {@code false} when the cap was
     *     full and nothing was counted
     */
    boolean countAnswer(OutgoingMessageId answer, LocalDate day);

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
     * <p>Built in M13.
     *
     * @param now when the retention job runs
     * @return how many stops' messages were deleted
     */
    int deleteExpired(Instant now);
}
