package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.messaging.domain.MessagingRetention;
import com.dispatchflow.messaging.domain.OutgoingMessageId;
import com.dispatchflow.messaging.domain.message.DailySendCap;
import com.dispatchflow.messaging.domain.message.OutgoingMessage;
import com.dispatchflow.messaging.domain.message.StopMessages;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.adapter.out.persistence.OpenTransaction;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Keeps each stop's {@link StopMessages} in {@code messaging.stop_messages} and its messages in
 * {@code messaging.outgoing_message}, and the reply answers counted toward the daily cap in {@code
 * messaging.counted_answer}.
 *
 * <p>A day's cap counts the messages that reached, during that Israel day, a state that counts
 * toward it ({@code MessageState.countsTowardTheDailyCap}), because a message keeps no claim time
 * once its state moves on. A message reaches every such state when it is claimed or later, so a
 * claim made today is never missed; one claimed late yesterday whose state moved on today counts
 * today too, which errs on the side of NFR-196's limit.
 */
@Component
class JpaStopMessagesRepository implements StopMessagesRepository {

    // The entity's name, which the shared save also gives a conflict as its kind.
    private static final String KIND = StopMessages.class.getSimpleName();

    /** The migration's name for the uniqueness of a stop's message by type and sequence (NFR-191). */
    private static final String MESSAGE_ONCE = "outgoing_message_once";

    private final AllStopMessages stopMessages;
    private final VersionedSave versionedSave;

    /**
     * Creates the adapter.
     *
     * @param stopMessages the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     */
    JpaStopMessagesRepository(AllStopMessages stopMessages, VersionedSave versionedSave) {
        this.stopMessages = Objects.requireNonNull(stopMessages, "stopMessages");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
    }

    @Override
    public Optional<StopMessages> get(StopId stop) {
        return stopMessages.findById(stop.value());
    }

    /**
     * {@inheritDoc}
     *
     * <p>It goes through the shared save, which turns a first save that loses the race for the
     * stop's key into {@code StaleVersion}.
     */
    @Override
    public void add(StopMessages messages) {
        versionedSave.save(messages, stopMessages::save);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A message a trigger added is numbered from the messages loaded, so two triggers that change
     * one loaded version give their messages the same type and sequence. Hibernate inserts the new
     * message before it updates the root, so the later one breaks the messages' uniqueness before
     * its version is checked. The messages keep that uniqueness themselves, so only a concurrent
     * save breaks it, and the violation answers {@code StaleVersion} as the version check would
     * have. Any other constraint violation passes through.
     */
    @Override
    public void save(StopMessages messages) {
        try {
            versionedSave.save(messages, stopMessages::save);
        } catch (ConstraintViolationException | DataIntegrityViolationException violation) {
            if (breaksMessageOnce(violation)) {
                throw new StaleVersion(KIND, messages.stop().value(), violation);
            }
            throw violation;
        }
    }

    @Override
    public List<StopId> withPendingMessages() {
        return stopMessages.withClaimableMessages().stream().map(StopId::of).toList();
    }

    @Override
    public DailySendCap capOn(LocalDate day) {
        OpenTransaction.require("capOn");
        stopMessages.lockDay(day.toString());
        return counted(day);
    }

    @Override
    public boolean countAnswer(OutgoingMessageId answer, LocalDate day) {
        OpenTransaction.require("countAnswer");
        stopMessages.lockDay(day.toString());
        if (stopMessages.isAnswerCounted(answer.value())) {
            return true;
        }
        if (!counted(day).hasRoom()) {
            return false;
        }
        stopMessages.countAnswer(answer.value(), day);
        return true;
    }

    @Override
    public Optional<Instant> lastAcceptedTo(PhoneNumber phone) {
        return stopMessages.lastAcceptedTo(phone);
    }

    @Override
    public int deleteExpired(Instant now) {
        return stopMessages.deleteExpired(
                now, IsraelDateTime.ZONE.getId(), MessagingRetention.MESSAGES_AND_REPLIES.toString());
    }

    private static boolean breaksMessageOnce(RuntimeException violation) {
        for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint
                    && MESSAGE_ONCE.equals(constraint.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    /** The day's cap as stored; the caller holds the day's lock. */
    private DailySendCap counted(LocalDate day) {
        Instant from = day.atStartOfDay(IsraelDateTime.ZONE).toInstant();
        Instant until = day.plusDays(1).atStartOfDay(IsraelDateTime.ZONE).toInstant();
        DailySendCap messages = DailySendCap.counting(stopMessages.reachingTheirStateBetween(from, until).stream()
                .map(OutgoingMessage::state)
                .toList());
        return new DailySendCap(messages.claimedToday() + Math.toIntExact(stopMessages.answersCountedOn(day)));
    }
}
