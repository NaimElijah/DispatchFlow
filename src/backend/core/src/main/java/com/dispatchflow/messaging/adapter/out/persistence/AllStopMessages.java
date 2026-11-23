package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.message.OutgoingMessage;
import com.dispatchflow.messaging.domain.message.StopMessages;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaStopMessagesRepository}: {@code save}, and the lookups
 * and native statements the port needs, those of the daily cap's {@code counted_answer} table
 * included.
 */
interface AllStopMessages extends Repository<StopMessages, UUID> {

    /**
     * Saves a stop's messages.
     *
     * @param messages the stop's messages
     * @return the managed aggregate
     */
    StopMessages save(StopMessages messages);

    /**
     * Loads a stop's messages whole.
     *
     * @param stop the stop's UUID
     * @return the aggregate, or empty
     */
    @EntityGraph(attributePaths = "messages")
    Optional<StopMessages> findById(UUID stop);

    /**
     * The stops with a message the sender may claim: pending, or of unknown outcome, a state a
     * message is left in only when it may be sent again (NFR-198).
     *
     * @return the stops' UUIDs, each once, ordered by the trigger of their oldest such message
     */
    @Query("""
            select s.id from StopMessages s join s.messages m
            where m.state in (PENDING, UNKNOWN)
            group by s.id
            order by min(m.triggeredAt), s.id
            """)
    List<UUID> withClaimableMessages();

    /**
     * The messages that reached their state in a span of time, whichever stop they are to.
     *
     * @param from the span's start, included
     * @param until the span's end, excluded
     * @return the messages, in no order
     */
    @Query("select m from OutgoingMessage m where m.stateSince >= :from and m.stateSince < :until")
    List<OutgoingMessage> reachingTheirStateBetween(Instant from, Instant until);

    /**
     * When the provider last accepted a message to a phone, compared in its stored E.164 form.
     *
     * @param phone the phone
     * @return the latest acceptance, or empty
     */
    @Query("select max(m.stateSince) from OutgoingMessage m where m.phone = :phone and m.state = ACCEPTED")
    Optional<Instant> lastAcceptedTo(PhoneNumber phone);

    /**
     * Takes an Israel day's cap lock for the caller's transaction, waiting for it (ADR-127 decision
     * 9). The day is bound as its ISO text, so the key doesn't depend on the session's date style.
     *
     * @param day the day, as {@code yyyy-MM-dd}
     * @return 1, always: the call is made for its lock
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext('messaging:daily-cap:' || :day))", nativeQuery = true)
    int lockDay(String day);

    /**
     * Whether a reply's answer was counted toward a cap, on any day.
     *
     * @param answer the answer's UUID
     * @return {@code true} when it was
     */
    @Query(
            value = "SELECT EXISTS (SELECT 1 FROM messaging.counted_answer WHERE answer_id = :answer)",
            nativeQuery = true)
    boolean isAnswerCounted(UUID answer);

    /**
     * How many reply answers were counted toward a day's cap.
     *
     * @param day the Israel day
     * @return the count
     */
    @Query(value = "SELECT count(*) FROM messaging.counted_answer WHERE day = :day", nativeQuery = true)
    long answersCountedOn(LocalDate day);

    /**
     * Counts a reply's answer toward a day's cap, unless it was counted already.
     *
     * @param answer the answer's UUID
     * @param day the Israel day
     * @return 1 when it was counted now, 0 when it had been
     */
    @Modifying
    @Query(value = """
            INSERT INTO messaging.counted_answer (answer_id, day) VALUES (:answer, :day)
            ON CONFLICT (answer_id) DO NOTHING
            """, nativeQuery = true)
    int countAnswer(UUID answer, LocalDate day);

    /**
     * Deletes every stop whose messages' retention has ended, its messages with it ({@code ON DELETE
     * CASCADE}). The end is the last state's time in Israel's calendar plus the retention period,
     * which PostgreSQL resolves as {@code MessagingRetention.endOf} does, except that a local time
     * the autumn clock change repeats is taken in standard time: an hour later, never earlier.
     *
     * @param now when the retention job runs
     * @param zone Israel's zone ID
     * @param retention the retention period, in ISO 8601 form
     * @return how many stops were deleted
     */
    @Modifying
    @Query(value = """
            DELETE FROM messaging.stop_messages
            WHERE id IN (
                SELECT stop_messages_id FROM messaging.outgoing_message
                GROUP BY stop_messages_id
                HAVING (max(state_since) AT TIME ZONE :zone + CAST(:retention AS interval)) AT TIME ZONE :zone <= :now)
            """, nativeQuery = true)
    int deleteExpired(Instant now, String zone, String retention);
}
