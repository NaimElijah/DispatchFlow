package com.dispatchflow.messaging.domain;

import static com.dispatchflow.messaging.domain.MessagingFixtures.MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.STOP;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.MessagingFixtures.conditions;
import static com.dispatchflow.messaging.domain.MessagingFixtures.on;
import static com.dispatchflow.messaging.domain.RatingRequestBuilder.LOADED;
import static com.dispatchflow.messaging.domain.RatingRequestBuilder.REQUEST;
import static com.dispatchflow.messaging.domain.RatingRequestBuilder.aRatingRequest;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.messaging.domain.Answer.Counted;
import com.dispatchflow.messaging.domain.MessagingError.RetentionNotOver;
import com.dispatchflow.messaging.domain.RatingRequest.OneTimeReply;
import com.dispatchflow.messaging.domain.RatingRequest.Rated;
import com.dispatchflow.messaging.domain.RatingRequest.Status;
import com.dispatchflow.messaging.events.RatingReceived;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A rating request (DL §4.6): ratings, comments, the one thank-you and reminder, the 48 hours, and
 * the removal of its personal data (FR-357 to FR-361, FR-371, FR-376, FR-379, NFR-184, NFR-193).
 */
class RatingRequestTest {

    private static final Instant HOUR_LATER = TEN.plusSeconds(3_600);
    private static final Instant WINDOW_END = TEN.plusSeconds(48 * 3_600);

    /** 90 days after 10:00 on 15 June 2027, in Israel time: 13 September, still summer time. */
    private static final Instant RETENTION_END = Instant.parse("2027-09-13T07:00:00Z");

    private static Answer answer(RatingRequest request, String reply, Instant at) {
        return request.answer(Reply.parse(reply), on(), at);
    }

    private static Optional<Rated> ratedAt(int value, Instant at) {
        return Optional.of(new Rated(Rating.of(value), at));
    }

    private static void assertUnchanged(RatingRequest request, Runnable call) {
        RatingRequestSnapshot before = request.snapshot();

        call.run();

        assertThat(request.snapshot()).isEqualTo(before);
        assertThat(request.pendingEvents()).isEmpty();
    }

    // --- opening (DL §6.2)

    @Test
    void GivenAnAcceptedRatingQuestion_WhenARequestIsOpened_ThenItIsOpenWithNoReply() {
        RatingRequest request = RatingRequest.open(REQUEST, new RatingQuestion(STOP, MOBILE, TEN));

        assertThat(request.snapshot())
                .isEqualTo(new RatingRequestSnapshot(
                        REQUEST,
                        Version.NEW,
                        STOP,
                        Optional.of(MOBILE),
                        TEN,
                        Optional.empty(),
                        List.of(),
                        OneTimeReply.NOT_YET,
                        OneTimeReply.NOT_YET,
                        Optional.empty()));
        assertThat(request.statusAt(TEN)).isEqualTo(Status.OPEN);
        assertThat(request.pendingEvents()).isEmpty();
    }

    // --- ratings (FR-357, FR-358, FR-361)

    @Test
    void GivenAnOpenRequest_WhenABareRatingArrives_ThenItRatesTheDeliveryGetsTheThankYouAndRaisesRatingReceived() {
        RatingRequest request = aRatingRequest().build();

        Answer answer = answer(request, "3", HOUR_LATER);

        assertThat(answer).isEqualTo(new Answer(Counted.RATING, Optional.of(ReplyMessage.THANK_YOU)));
        assertThat(request.snapshot())
                .isEqualTo(aRatingRequest().rated(3, HOUR_LATER).snapshot());
        assertThat(request.pendingEvents())
                .containsExactly(new RatingReceived(
                        new EventId("RatingReceived", REQUEST.value(), LOADED.next(), 0),
                        HOUR_LATER,
                        REQUEST,
                        STOP,
                        3));
        assertThat(request.statusAt(HOUR_LATER)).isEqualTo(Status.RATED);
    }

    @Test
    void GivenAnOpenRequest_WhenARatingWithWordsArrives_ThenItIsBothTheRatingAndAComment() {
        RatingRequest request = aRatingRequest().build();

        Answer answer = answer(request, "8 thanks", HOUR_LATER);

        assertThat(answer).isEqualTo(new Answer(Counted.RATING_AND_COMMENT, Optional.of(ReplyMessage.THANK_YOU)));
        assertThat(request.snapshot())
                .isEqualTo(aRatingRequest()
                        .rated(8, HOUR_LATER)
                        .withComment("8 thanks", HOUR_LATER)
                        .snapshot());
    }

    @Test
    void GivenARatedRequest_WhenABareRatingArrivesAgain_ThenItCountsForNothingAndGetsNoAnswer() {
        RatingRequest request = aRatingRequest().rated(3, TEN).build();

        assertUnchanged(
                request,
                () -> assertThat(answer(request, "10", HOUR_LATER))
                        .isEqualTo(new Answer(Counted.NOTHING, Optional.empty())));
    }

    @Test
    void GivenARatedRequest_WhenARatingWithWordsArrives_ThenOnlyTheCommentIsKept() {
        RatingRequest request = aRatingRequest().rated(3, TEN).build();

        assertThat(answer(request, "9 sorry, it was fine", HOUR_LATER))
                .isEqualTo(new Answer(Counted.COMMENT, Optional.empty()));
        assertThat(request.snapshot())
                .isEqualTo(aRatingRequest()
                        .rated(3, TEN)
                        .withComment("9 sorry, it was fine", HOUR_LATER)
                        .snapshot());
        assertThat(request.pendingEvents()).isEmpty();
    }

    // --- comments and the format reminder (FR-359, FR-360)

    @Test
    void GivenAnOpenRequest_WhenTheFirstReplyIsNotARating_ThenItIsKeptAndGetsTheOneReminder() {
        RatingRequest request = aRatingRequest().build();

        assertThat(answer(request, "great driver", HOUR_LATER))
                .isEqualTo(new Answer(Counted.COMMENT, Optional.of(ReplyMessage.FORMAT_REMINDER)));
        assertThat(answer(request, "really", HOUR_LATER.plusSeconds(60)))
                .isEqualTo(new Answer(Counted.COMMENT, Optional.empty()));

        assertThat(request.snapshot())
                .isEqualTo(aRatingRequest()
                        .withComment("great driver", HOUR_LATER)
                        .withComment("really", HOUR_LATER.plusSeconds(60))
                        .withReminder(OneTimeReply.SENT)
                        .snapshot());
    }

    @Test
    void GivenAnOpenRequest_WhenTheFirstReplyIsBlank_ThenNothingIsKeptAndTheReminderIsSent() {
        RatingRequest request = aRatingRequest().build();

        assertThat(answer(request, "  ", HOUR_LATER))
                .isEqualTo(new Answer(Counted.NOTHING, Optional.of(ReplyMessage.FORMAT_REMINDER)));
        assertThat(request.snapshot())
                .isEqualTo(aRatingRequest().withReminder(OneTimeReply.SENT).snapshot());
    }

    @Test
    void GivenARatedRequest_WhenACommentArrives_ThenItIsKeptWithNoReminder() {
        RatingRequest request = aRatingRequest().rated(9, TEN).build();

        assertThat(answer(request, "thanks again", HOUR_LATER))
                .isEqualTo(new Answer(Counted.COMMENT, Optional.empty()));
        assertThat(request.snapshot().reminder()).isEqualTo(OneTimeReply.NOT_YET);
    }

    @Test
    void GivenAnOpenRequest_WhenAnOptOutWordArrives_ThenTheRequestIgnoresIt() {
        RatingRequest request = aRatingRequest().build();

        assertUnchanged(
                request,
                () -> assertThat(answer(request, "STOP", HOUR_LATER))
                        .isEqualTo(new Answer(Counted.NOTHING, Optional.empty())));
    }

    // --- when messages can't go (FR-350, FR-363)

    @Test
    void GivenMessagesOff_WhenTheFirstRatingArrives_ThenItCountsAndTheThankYouIsSkippedForGood() {
        RatingRequest request = aRatingRequest().build();
        SendingConditions off = conditions(Switch.OFF, Switch.ON, PhoneConsent.MAY_BE_MESSAGED);

        assertThat(request.answer(Reply.parse("7"), off, HOUR_LATER))
                .isEqualTo(new Answer(Counted.RATING, Optional.empty()));
        assertThat(request.snapshot().thankYou()).isEqualTo(OneTimeReply.SKIPPED);
        assertThat(request.snapshot().rating()).isEqualTo(ratedAt(7, HOUR_LATER));
        assertThat(request.pendingEvents()).hasSize(1);
    }

    @Test
    void GivenAnOptedOutPhone_WhenTheFirstCommentArrives_ThenTheReminderIsSkippedAndNotSentLater() {
        RatingRequest request = aRatingRequest().build();
        SendingConditions optedOut = conditions(Switch.ON, Switch.ON, PhoneConsent.OPTED_OUT);

        assertThat(request.answer(Reply.parse("hello"), optedOut, HOUR_LATER))
                .isEqualTo(new Answer(Counted.COMMENT, Optional.empty()));
        assertThat(answer(request, "hello again", HOUR_LATER.plusSeconds(60)))
                .isEqualTo(new Answer(Counted.COMMENT, Optional.empty()));
        assertThat(request.snapshot().reminder()).isEqualTo(OneTimeReply.SKIPPED);
    }

    // --- the 48 hours (FR-357, FR-379)

    @Test
    void GivenAReplyExactly48HoursAfterTheQuestion_WhenItArrives_ThenItStillRates() {
        RatingRequest request = aRatingRequest().build();

        assertThat(answer(request, "5", WINDOW_END).counted()).isEqualTo(Counted.RATING);
        assertThat(request.statusAt(WINDOW_END)).isEqualTo(Status.RATED);
    }

    @Test
    void GivenAReplyASecondPast48HoursOrBeforeTheQuestion_WhenItArrives_ThenItIsDiscardedAndNothingChanges() {
        RatingRequest request = aRatingRequest().build();

        assertUnchanged(
                request,
                () -> assertThat(answer(request, "5 lovely", WINDOW_END.plusSeconds(1)))
                        .isEqualTo(new Answer(Counted.DISCARDED, Optional.empty())));
        assertUnchanged(
                request,
                () -> assertThat(answer(request, "5", TEN.minusSeconds(1)))
                        .isEqualTo(new Answer(Counted.DISCARDED, Optional.empty())));
        assertThat(request.statusAt(WINDOW_END.plusSeconds(1))).isEqualTo(Status.CLOSED);
    }

    // --- removing personal data (NFR-184, NFR-193)

    @Test
    void GivenAQuestionAndACommentBoth90DaysOld_WhenPersonalDataIsRemoved_ThenThePhoneAndCommentGoAndTheRatingStays() {
        RatingRequest request =
                aRatingRequest().rated(4, TEN).withComment("late", TEN).build();

        request.removePersonalData(RETENTION_END);

        assertThat(request.snapshot())
                .isEqualTo(aRatingRequest().rated(4, TEN).removed(RETENTION_END).snapshot());
        assertThat(request.pendingEvents()).isEmpty();
    }

    @Test
    void GivenCommentsReceivedLater_WhenRetentionRuns_ThenThePhoneAndEachCommentGo90DaysAfterTheirOwnTime() {
        Instant secondEnd = Instant.parse("2027-09-15T07:00:00Z");
        RatingRequest request = aRatingRequest()
                .withComment("first", HOUR_LATER)
                .withComment("second", WINDOW_END)
                .build();

        request.removePersonalData(RETENTION_END);
        assertThat(request.snapshot())
                .isEqualTo(aRatingRequest()
                        .withComment("first", HOUR_LATER)
                        .withComment("second", WINDOW_END)
                        .phoneRemoved(RETENTION_END)
                        .snapshot());

        request.removePersonalData(RETENTION_END.plusSeconds(3_600));
        assertThat(request.snapshot())
                .isEqualTo(aRatingRequest()
                        .withComment("second", WINDOW_END)
                        .phoneRemoved(RETENTION_END)
                        .snapshot());

        request.removePersonalData(secondEnd);
        assertThat(request.snapshot())
                .isEqualTo(aRatingRequest().phoneRemoved(RETENTION_END).snapshot());
    }

    @Test
    void GivenNothingDueYet_WhenPersonalDataIsRemoved_ThenRetentionNotOver() {
        RatingRequest request = aRatingRequest().withComment("hi", HOUR_LATER).build();
        RatingRequestSnapshot before = request.snapshot();

        assertRefused(() -> request.removePersonalData(RETENTION_END.minusSeconds(1)), new RetentionNotOver());

        assertThat(request.snapshot()).isEqualTo(before);
        assertThat(request.pendingEvents()).isEmpty();
    }

    @Test
    void GivenThePhoneRemovedAndACommentNotYetDue_WhenRetentionRunsAgain_ThenRetentionNotOver() {
        RatingRequest request = aRatingRequest()
                .withComment("second", WINDOW_END)
                .phoneRemoved(RETENTION_END)
                .build();

        assertRefused(() -> request.removePersonalData(RETENTION_END.plusSeconds(3_600)), new RetentionNotOver());
    }

    @Test
    void GivenARemovedRequest_WhenRemovedAgainOrAnswered_ThenNothingChanges() {
        RatingRequest request =
                aRatingRequest().rated(4, TEN).removed(RETENTION_END).build();

        assertUnchanged(request, () -> request.removePersonalData(RETENTION_END.plusSeconds(86_400)));
        assertUnchanged(
                request,
                () -> assertThat(answer(request, "9", HOUR_LATER).counted()).isEqualTo(Counted.DISCARDED));
    }

    // --- the comment cap (ADR-108)

    @Test
    void GivenARequestKeeping19Comments_WhenThe20thArrives_ThenItIsKept() {
        RatingRequestBuilder nineteen = aRatingRequest().withReminder(OneTimeReply.SENT);
        for (int i = 1; i <= 19; i++) {
            nineteen.withComment("comment " + i, HOUR_LATER);
        }
        RatingRequest request = nineteen.build();

        assertThat(answer(request, "comment 20", HOUR_LATER)).isEqualTo(new Answer(Counted.COMMENT, Optional.empty()));
        assertThat(request.snapshot().comments()).hasSize(20);
    }

    @Test
    void GivenARequestKeeping20Comments_WhenA21stReplyArrives_ThenItIsIgnoredWithNoAnswer() {
        RatingRequest request = twentyComments().build();

        assertUnchanged(
                request,
                () -> assertThat(answer(request, "one more", HOUR_LATER))
                        .isEqualTo(new Answer(Counted.NOTHING, Optional.empty())));
        assertUnchanged(
                request,
                () -> assertThat(answer(request, "8 now", HOUR_LATER))
                        .isEqualTo(new Answer(Counted.NOTHING, Optional.empty())));
    }

    private static RatingRequestBuilder twentyComments() {
        RatingRequestBuilder builder = aRatingRequest().withReminder(OneTimeReply.SENT);
        for (int i = 1; i <= 20; i++) {
            builder.withComment("comment " + i, HOUR_LATER);
        }
        return builder;
    }

    // --- the lifecycle: every state against every kind of reply

    enum State {
        OPEN,
        RATED,
        CLOSED,
        REMOVED
    }

    private static RatingRequestBuilder builderFor(State state) {
        return switch (state) {
            case OPEN, CLOSED -> aRatingRequest();
            case RATED -> aRatingRequest().rated(2, TEN);
            case REMOVED -> aRatingRequest().rated(2, TEN).removed(RETENTION_END);
        };
    }

    private static RatingReceived ratingReceived(int rating) {
        return new RatingReceived(
                new EventId("RatingReceived", REQUEST.value(), LOADED.next(), 0), HOUR_LATER, REQUEST, STOP, rating);
    }

    static Stream<Arguments> lifecycle() {
        Answer nothing = new Answer(Counted.NOTHING, Optional.empty());
        Answer discarded = new Answer(Counted.DISCARDED, Optional.empty());
        List<RatingReceived> none = List.of();
        Stream<Arguments> open = Stream.of(
                Arguments.of(
                        State.OPEN,
                        "8",
                        new Answer(Counted.RATING, Optional.of(ReplyMessage.THANK_YOU)),
                        aRatingRequest().rated(8, HOUR_LATER),
                        List.of(ratingReceived(8))),
                Arguments.of(
                        State.OPEN,
                        "8 ok",
                        new Answer(Counted.RATING_AND_COMMENT, Optional.of(ReplyMessage.THANK_YOU)),
                        aRatingRequest().rated(8, HOUR_LATER).withComment("8 ok", HOUR_LATER),
                        List.of(ratingReceived(8))),
                Arguments.of(
                        State.OPEN,
                        "ok",
                        new Answer(Counted.COMMENT, Optional.of(ReplyMessage.FORMAT_REMINDER)),
                        aRatingRequest().withComment("ok", HOUR_LATER).withReminder(OneTimeReply.SENT),
                        none),
                Arguments.of(State.OPEN, "STOP", nothing, aRatingRequest(), none),
                Arguments.of(
                        State.OPEN,
                        "  ",
                        new Answer(Counted.NOTHING, Optional.of(ReplyMessage.FORMAT_REMINDER)),
                        aRatingRequest().withReminder(OneTimeReply.SENT),
                        none));
        Stream<Arguments> rated = Stream.of(
                Arguments.of(State.RATED, "8", nothing, builderFor(State.RATED), none),
                Arguments.of(
                        State.RATED,
                        "8 ok",
                        new Answer(Counted.COMMENT, Optional.empty()),
                        builderFor(State.RATED).withComment("8 ok", HOUR_LATER),
                        none),
                Arguments.of(
                        State.RATED,
                        "ok",
                        new Answer(Counted.COMMENT, Optional.empty()),
                        builderFor(State.RATED).withComment("ok", HOUR_LATER),
                        none),
                Arguments.of(State.RATED, "STOP", nothing, builderFor(State.RATED), none),
                Arguments.of(State.RATED, "  ", nothing, builderFor(State.RATED), none));
        Stream<Arguments> ended = Stream.of(State.CLOSED, State.REMOVED)
                .flatMap(state -> Stream.of("8", "8 ok", "ok", "STOP", "  ")
                        .map(reply -> Arguments.of(state, reply, discarded, builderFor(state), none)));
        return Stream.of(open, rated, ended).flatMap(cells -> cells);
    }

    @ParameterizedTest(name = "{0} request, reply \"{1}\"")
    @MethodSource("lifecycle")
    void GivenARequestInEachState_WhenEachKindOfReplyArrives_ThenItAnswersChangesAndRaisesWhatTheRulesSay(
            State state, String reply, Answer expected, RatingRequestBuilder after, List<RatingReceived> events) {
        RatingRequest request = builderFor(state).build();
        Instant at = state == State.CLOSED ? WINDOW_END.plusSeconds(1) : HOUR_LATER;

        assertThat(answer(request, reply, at)).isEqualTo(expected);
        assertThat(request.snapshot()).isEqualTo(after.snapshot());
        assertThat(request.pendingEvents()).containsExactlyElementsOf(events);
    }

    // --- restore

    static Stream<RatingRequestBuilder> builderStates() {
        return Stream.of(
                aRatingRequest(),
                aRatingRequest().withComment("hi", TEN).withReminder(OneTimeReply.SKIPPED),
                aRatingRequest().rated(10, WINDOW_END).withComment("10 wow", WINDOW_END),
                aRatingRequest().rated(1, TEN).withThankYou(OneTimeReply.SKIPPED),
                aRatingRequest().rated(4, TEN).removed(RETENTION_END),
                aRatingRequest().withComment("second", WINDOW_END).phoneRemoved(RETENTION_END),
                twentyComments());
    }

    @ParameterizedTest
    @MethodSource("builderStates")
    void GivenEachBuilderState_WhenItsSnapshotIsRestored_ThenTheSnapshotRoundTrips(RatingRequestBuilder builder) {
        RatingRequest request = builder.build();

        assertThat(RatingRequest.restore(request.snapshot()).snapshot()).isEqualTo(builder.snapshot());
        assertThat(request.version()).isEqualTo(LOADED);
    }

    static Stream<Arguments> damagedSnapshots() {
        String phoneRule = "a rating request has its phone exactly until it is removed";
        String windowRule = "a rating and comments are received within the 48 hours (FR-357, FR-360)";
        String thankYouRule = "a rating request's thank-you is due exactly when it is rated (FR-358)";
        return Stream.of(
                Arguments.of("no phone, not removed", aRatingRequest().withPhone(Optional.empty()), phoneRule),
                Arguments.of(
                        "removed, phone kept", aRatingRequest().withRemovedAt(Optional.of(RETENTION_END)), phoneRule),
                Arguments.of(
                        "21 comments",
                        twentyComments().withComment("comment 21", HOUR_LATER),
                        "a rating request keeps at most 20 comments"),
                Arguments.of("rated after 48 hours", aRatingRequest().rated(5, WINDOW_END.plusSeconds(1)), windowRule),
                Arguments.of(
                        "a comment before the question",
                        aRatingRequest().withComment("hi", TEN.minusSeconds(1)),
                        windowRule),
                Arguments.of(
                        "rated with no thank-you due",
                        aRatingRequest().rated(5, TEN).withThankYou(OneTimeReply.NOT_YET),
                        thankYouRule),
                Arguments.of(
                        "a thank-you with no rating", aRatingRequest().withThankYou(OneTimeReply.SENT), thankYouRule));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedSnapshots")
    void GivenASnapshotBreakingAnInvariantAtRest_WhenRestored_ThenStoredStateInvalid(
            String broken, RatingRequestBuilder builder, String detail) {
        assertThatThrownBy(builder::build).isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
            assertThat(invalid.subject()).isEqualTo("RatingRequest");
            assertThat(invalid.detail()).isEqualTo(detail);
        });
    }

    @Test
    void GivenARequestWithAComment_WhenItAndItsEventArePrinted_ThenNeitherShowsThePhoneOrTheComment() {
        RatingRequest request = aRatingRequest().build();
        answer(request, "6 call me 0549998877", HOUR_LATER);

        assertThat(request.toString()).doesNotContain("345").doesNotContain("0549998877");
        assertThat(request.pendingEvents().toString()).doesNotContain("345").doesNotContain("0549998877");
    }
}
