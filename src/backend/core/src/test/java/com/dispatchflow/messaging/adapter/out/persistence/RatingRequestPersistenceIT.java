package com.dispatchflow.messaging.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.TestPersistenceConfiguration.RecordedEvents;
import com.dispatchflow.Transactions;
import com.dispatchflow.messaging.application.port.out.RatingRequestRepository;
import com.dispatchflow.messaging.domain.CompanyName;
import com.dispatchflow.messaging.domain.DispatchPhone;
import com.dispatchflow.messaging.domain.MessageLanguage;
import com.dispatchflow.messaging.domain.MessagingRetention;
import com.dispatchflow.messaging.domain.PhoneConsent;
import com.dispatchflow.messaging.domain.RatingQuestion;
import com.dispatchflow.messaging.domain.SendingConditions;
import com.dispatchflow.messaging.domain.Switch;
import com.dispatchflow.messaging.domain.rating.RatingRequest;
import com.dispatchflow.messaging.domain.rating.Reply;
import com.dispatchflow.messaging.events.RatingReceived;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link RatingRequestRepository} against a real PostgreSQL (ADR-126, ADR-127): what a request
 * stores and loads as, the version, the request a reply answers, the requests whose personal data
 * is due, and the stored states the load check refuses.
 *
 * <p>The database is shared and never cleaned, and the retention query reads every request. So test
 * {@code n} keys its own requests ({@code request(n)}, {@code request(100 + n)}, …) and phones, and
 * its questions are accepted on its own day, years after the one test that reads retention.
 */
@IntegrationTest
class RatingRequestPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_7261_7465L;
    private static final LocalDate FIRST_DAY = LocalDate.of(2030, 1, 1);
    private static final int DAYS_APART = 3;
    private static final LocalTime TEN = LocalTime.of(10, 0);
    private static final Duration ANSWER_WINDOW = RatingRequest.ANSWER_WINDOW;
    private static final CompanyName COMPANY = CompanyName.of("Shefa Deliveries");
    private static final DispatchPhone DISPATCH = DispatchPhone.of(PhoneNumber.ofStored("+97235551234"));
    private static final SendingConditions MESSAGES_ON = conditions(Switch.ON);
    private static final SendingConditions MESSAGES_OFF = conditions(Switch.OFF);

    @Autowired
    private RatingRequestRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordedEvents events;

    @BeforeEach
    void forgetEarlierEvents() {
        events.clear();
    }

    // --- round trips, mirroring the domain's restore round trips

    /** The states the domain's restore round trip covers, reached through commands. */
    enum Scenario {
        OPEN,
        COMMENTED_WITH_THE_REMINDER_SKIPPED,
        RATED_AND_COMMENTED_AT_THE_WINDOWS_END,
        RATED_WITH_THE_THANK_YOU_SKIPPED,
        RATED_AND_REMOVED,
        COMMENTED_WITH_THE_PHONE_REMOVED,
        TWENTY_COMMENTS;

        void reach(RatingRequest request, Instant accepted) {
            Instant windowEnd = accepted.plus(ANSWER_WINDOW);
            switch (this) {
                case OPEN -> {}
                case COMMENTED_WITH_THE_REMINDER_SKIPPED -> answer(request, "hi", MESSAGES_OFF, accepted);
                case RATED_AND_COMMENTED_AT_THE_WINDOWS_END -> answer(request, "10 wow", MESSAGES_ON, windowEnd);
                case RATED_WITH_THE_THANK_YOU_SKIPPED -> answer(request, "1", MESSAGES_OFF, accepted);
                case RATED_AND_REMOVED -> {
                    answer(request, "4", MESSAGES_ON, accepted);
                    request.removePersonalData(MessagingRetention.endOf(accepted));
                }
                case COMMENTED_WITH_THE_PHONE_REMOVED -> {
                    answer(request, "second", MESSAGES_ON, windowEnd);
                    request.removePersonalData(MessagingRetention.endOf(accepted));
                }
                case TWENTY_COMMENTS -> commentTwentyTimes(request, accepted);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenARequest_WhenAddedAndLoadedInAnotherTransaction_ThenEveryPartComesBackAsAdded(Scenario scenario) {
        int n = 1 + scenario.ordinal();
        RatingRequest added = transactions.inTransaction(() -> {
            RatingRequest request = opened(n, tenOn(n), phone(n));
            scenario.reach(request, tenOn(n));
            repository.add(request);
            return request;
        });

        RatingRequest loaded =
                transactions.inTransaction(() -> repository.get(request(n)).orElseThrow());

        assertThat(loaded.snapshot()).isEqualTo(added.snapshot());
        assertThat(loaded.id()).isEqualTo(request(n));
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.statusAt(tenOn(n))).isEqualTo(added.statusAt(tenOn(n)));
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenNoRequestWithAnId_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(request(8)))).isEmpty();
    }

    // --- versions, events and conflicts

    @Test
    void GivenAnAddedRequest_WhenItIsRatedAndThenCommented_ThenTheVersionIsZeroThenOneThenTwo() {
        int n = 9;
        Instant ten = tenOn(n);
        transactions.inTransaction(() -> repository.add(opened(n, ten, phone(n))));
        long added = storedVersion(n);

        transactions.inTransaction(() -> {
            RatingRequest request = repository.get(request(n)).orElseThrow();
            answer(request, "9", MESSAGES_ON, ten.plusSeconds(60));
            repository.save(request);
        });
        long rated = storedVersion(n);
        transactions.inTransaction(() -> {
            RatingRequest request = repository.get(request(n)).orElseThrow();
            answer(request, "thanks", MESSAGES_ON, ten.plusSeconds(120));
            repository.save(request);
        });

        assertThat(List.of(added, rated, storedVersion(n))).containsExactly(0L, 1L, 2L);
        assertThat(events.committed())
                .containsExactly(new RatingReceived(
                        new EventId("RatingReceived", request(n).value(), Version.of(1), 0),
                        ten.plusSeconds(60),
                        request(n),
                        stop(n),
                        9));
    }

    @Test
    void GivenTwoTransactionsOnOneRequest_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstRatingStands() {
        int n = 10;
        Instant ten = tenOn(n);
        transactions.inTransaction(() -> repository.add(opened(n, ten, phone(n))));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    RatingRequest mine = repository.get(request(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        RatingRequest theirs = repository.get(request(n)).orElseThrow();
                        answer(theirs, "8", MESSAGES_ON, ten.plusSeconds(60));
                        repository.save(theirs);
                    });
                    answer(mine, "3", MESSAGES_ON, ten.plusSeconds(60));
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("RatingRequest");
                    assertThat(stale.id()).isEqualTo(request(n).value());
                });

        assertThat(storedVersion(n)).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT rating FROM messaging.rating_request WHERE id = ?")
                        .param(request(n).value())
                        .query(Integer.class)
                        .single())
                .isEqualTo(8);
    }

    // --- the request a reply answers (FR-357, FR-371, FR-379)

    @Test
    void GivenTwoRequestsToAPhoneAndOneToAnother_WhenAReplyComes_ThenItAnswersTheLatestToItsPhoneLoadedWhole() {
        int n = 11;
        Instant ten = tenOn(n);
        transactions.inTransaction(() -> {
            repository.add(opened(n, ten, phone(n)));
            RatingRequest later = opened(100 + n, ten.plusSeconds(3_600), phone(n));
            answer(later, "a comment", MESSAGES_ON, ten.plusSeconds(3_700));
            repository.add(later);
            repository.add(opened(200 + n, ten.plusSeconds(7_000), phone(200 + n)));
        });

        RatingRequest open = transactions.inTransaction(
                () -> repository.openFor(phone(n), ten.plusSeconds(7_200)).orElseThrow());

        assertThat(open.id()).isEqualTo(request(100 + n));
        assertThat(open.snapshot().comments()).hasSize(1);
    }

    @Test
    void GivenARequestAcceptedExactly48HoursBeforeAReply_WhenTheReplyComes_ThenItAnswersThatRequest() {
        int n = 12;
        Instant accepted = tenOn(n);
        transactions.inTransaction(() -> repository.add(opened(n, accepted, phone(n))));

        assertThat(transactions.inTransaction(() -> repository.openFor(phone(n), accepted.plus(ANSWER_WINDOW))))
                .map(RatingRequest::id)
                .contains(request(n));
        assertThat(transactions.inTransaction(() -> repository.openFor(phone(n), accepted)))
                .map(RatingRequest::id)
                .contains(request(n));
    }

    @Test
    void GivenARequestAcceptedMoreThan48HoursBeforeAReplyOrAfterIt_WhenTheReplyComes_ThenItAnswersNone() {
        int n = 13;
        Instant accepted = tenOn(n);
        transactions.inTransaction(() -> repository.add(opened(n, accepted, phone(n))));

        assertThat(transactions.inTransaction(() -> repository.openFor(
                        phone(n), accepted.plus(ANSWER_WINDOW).plusSeconds(1))))
                .isEmpty();
        assertThat(transactions.inTransaction(() -> repository.openFor(phone(n), accepted.minusSeconds(1))))
                .isEmpty();
        assertThat(transactions.inTransaction(() -> repository.openFor(phone(100 + n), accepted)))
                .isEmpty();
    }

    // --- personal data due (NFR-184, NFR-193)

    @Test
    void GivenRequestsWithPersonalDataDueAndNot_WhenTheDueOnesAreListed_ThenEachDueOneComesOnce() {
        int phoneDue = 14;
        int commentDue = 114;
        int bothDue = 214;
        int notYet = 314;
        int nothingLeft = 414;
        // In 2020, before every other test's day. 90 Israel days from 10 January cross the spring
        // clock change, so the retention ends an hour before 90 days of 24 hours have passed.
        Instant accepted = LocalDate.of(2020, 1, 10)
                .atTime(TEN)
                .atZone(IsraelDateTime.ZONE)
                .toInstant();
        Instant hourLater = accepted.plusSeconds(3_600);
        Instant end = MessagingRetention.endOf(hourLater);
        transactions.inTransaction(() -> {
            repository.add(opened(phoneDue, hourLater, phone(phoneDue)));
            RatingRequest comment = opened(commentDue, accepted, phone(commentDue));
            answer(comment, "late comment", MESSAGES_ON, hourLater);
            comment.removePersonalData(MessagingRetention.endOf(accepted));
            repository.add(comment);
            RatingRequest both = opened(bothDue, accepted, phone(bothDue));
            answer(both, "early comment", MESSAGES_ON, accepted);
            repository.add(both);
            repository.add(opened(notYet, hourLater.plusSeconds(1), phone(notYet)));
            RatingRequest removed = opened(nothingLeft, accepted, phone(nothingLeft));
            removed.removePersonalData(MessagingRetention.endOf(accepted));
            repository.add(removed);
        });
        Set<RatingRequestId> ours =
                Set.of(request(phoneDue), request(commentDue), request(bothDue), request(notYet), request(nothingLeft));

        List<RatingRequestId> beforeTheEnd = transactions.inTransaction(() -> dueInAUtcSession(end.minusSeconds(1)));
        List<RatingRequestId> atTheEnd = transactions.inTransaction(() -> dueInAUtcSession(end));

        assertThat(end).isEqualTo(hourLater.plus(Duration.ofDays(90)).minus(Duration.ofHours(1)));
        assertThat(beforeTheEnd).filteredOn(ours::contains).containsExactly(request(bothDue));
        assertThat(atTheEnd)
                .filteredOn(ours::contains)
                .containsExactlyInAnyOrder(request(phoneDue), request(commentDue), request(bothDue));
    }

    // --- the load check (ADR-127 decision 1), mirroring the domain's damaged snapshots

    static Stream<Arguments> damagedRows() {
        String phoneRule = "a rating request has its phone exactly until it is removed";
        String windowRule = "a rating and comments are received within the 48 hours (FR-357, FR-360)";
        String thankYouRule = "a rating request's thank-you is due exactly when it is rated (FR-358)";
        return Stream.of(
                Arguments.of(
                        "no phone, not removed",
                        1_001,
                        Scenario.OPEN,
                        "UPDATE messaging.rating_request SET phone = NULL WHERE id = ?",
                        phoneRule),
                Arguments.of(
                        "removed, phone kept",
                        1_002,
                        Scenario.OPEN,
                        "UPDATE messaging.rating_request SET removed_at = accepted_at + interval '90 days' WHERE id = ?",
                        phoneRule),
                Arguments.of(
                        "21 comments",
                        1_003,
                        Scenario.TWENTY_COMMENTS,
                        """
                        INSERT INTO messaging.rating_request_comment (rating_request_id, position, comment, received_at)
                        SELECT rating_request_id, 20, 'comment 21', received_at
                        FROM messaging.rating_request_comment WHERE rating_request_id = ? AND position = 19
                        """,
                        "a rating request keeps at most 20 comments"),
                Arguments.of("rated after 48 hours", 1_004, Scenario.RATED_WITH_THE_THANK_YOU_SKIPPED, """
                        UPDATE messaging.rating_request SET rated_at = accepted_at + interval '48 hours 1 second'
                        WHERE id = ?
                        """, windowRule),
                Arguments.of(
                        "a comment before the question",
                        1_005,
                        Scenario.COMMENTED_WITH_THE_REMINDER_SKIPPED,
                        """
                        UPDATE messaging.rating_request_comment SET received_at = received_at - interval '1 second'
                        WHERE rating_request_id = ?
                        """,
                        windowRule),
                Arguments.of(
                        "rated with no thank-you due",
                        1_006,
                        Scenario.RATED_WITH_THE_THANK_YOU_SKIPPED,
                        "UPDATE messaging.rating_request SET thank_you = 'NOT_YET' WHERE id = ?",
                        thankYouRule),
                Arguments.of(
                        "a thank-you with no rating",
                        1_007,
                        Scenario.OPEN,
                        "UPDATE messaging.rating_request SET thank_you = 'SENT' WHERE id = ?",
                        thankYouRule));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredRequestBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Scenario scenario, String damage, String detail) {
        transactions.inTransaction(() -> {
            RatingRequest request = opened(n, tenOn(n), phone(n));
            scenario.reach(request, tenOn(n));
            repository.add(request);
        });

        jdbc.sql(damage).param(request(n).value()).update();

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(request(n))))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("RatingRequest");
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    // --- states, reached through the domain's commands

    /** Request {@code r}, whose question went to {@code phone} for stop {@code r}. */
    private static RatingRequest opened(int r, Instant accepted, PhoneNumber phone) {
        return RatingRequest.open(request(r), new RatingQuestion(stop(r), phone, accepted));
    }

    private static void answer(RatingRequest request, String reply, SendingConditions conditions, Instant at) {
        request.answer(Reply.parse(reply), conditions, at);
    }

    private static void commentTwentyTimes(RatingRequest request, Instant accepted) {
        for (int comment = 1; comment <= RatingRequest.MOST_COMMENTS; comment++) {
            answer(request, "comment " + comment, MESSAGES_ON, accepted.plusSeconds(comment));
        }
    }

    /** The phone not opted out, the rating question on, and messages as given. */
    private static SendingConditions conditions(Switch messages) {
        return new SendingConditions(
                messages, Switch.ON, DISPATCH, COMPANY, MessageLanguage.HEBREW, PhoneConsent.MAY_BE_MESSAGED);
    }

    // --- keys, times and stored values

    private static RatingRequestId request(int r) {
        return RatingRequestId.of(new UUID(KEY_PREFIX, r));
    }

    private static StopId stop(int r) {
        return StopId.of(new UUID(KEY_PREFIX + 1, r));
    }

    /** An Israeli mobile number of its own for each {@code n}. */
    private static PhoneNumber phone(int n) {
        return PhoneNumber.ofStored("+97252%07d".formatted(4_000_000 + n));
    }

    /** 10:00 in Israel on test {@code n}'s day. */
    private static Instant tenOn(int n) {
        return FIRST_DAY
                .plusDays((long) DAYS_APART * n)
                .atTime(TEN)
                .atZone(IsraelDateTime.ZONE)
                .toInstant();
    }

    /**
     * The requests whose personal data is due, read in a session whose zone is UTC, not the JVM's,
     * so the result shows the SQL keeps Israel's calendar whatever the session's zone.
     */
    private List<RatingRequestId> dueInAUtcSession(Instant now) {
        jdbc.sql("SET LOCAL TimeZone = 'UTC'").update();
        return repository.withPersonalDataDue(now);
    }

    private long storedVersion(int r) {
        return jdbc.sql("SELECT version FROM messaging.rating_request WHERE id = ?")
                .param(request(r).value())
                .query(Long.class)
                .single();
    }
}
