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
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
@Repository
@Transactional
class JpaStopMessagesRepository implements StopMessagesRepository {

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

    @Override
    public void save(StopMessages messages) {
        versionedSave.save(messages, stopMessages::save);
    }

    @Override
    public List<StopId> withPendingMessages() {
        return stopMessages.withClaimableMessages().stream().map(StopId::of).toList();
    }

    @Override
    public DailySendCap capOn(LocalDate day) {
        stopMessages.lockDay(day.toString());
        return counted(day);
    }

    @Override
    public boolean countAnswer(OutgoingMessageId answer, LocalDate day) {
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
