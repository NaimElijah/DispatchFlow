package com.dispatchflow.liveops.domain.routerun;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_B;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ONLINE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_2;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.STOP;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0800;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byA;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.reload;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.liveops.domain.DriverReport;
import com.dispatchflow.liveops.domain.LiveOpsError.NotYourRoute;
import com.dispatchflow.liveops.domain.LiveOpsError.RouteAlreadyFinished;
import com.dispatchflow.liveops.domain.LiveOpsError.RunAlreadyStarted;
import com.dispatchflow.liveops.domain.LiveOpsError.TrackingClosed;
import com.dispatchflow.liveops.domain.LiveOpsError.UnknownRevision;
import com.dispatchflow.liveops.domain.TrackingRefusal;
import com.dispatchflow.liveops.events.RouteStarted;
import com.dispatchflow.liveops.events.RunStartCause;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A route run's lifecycle (FR-68, FR-244, FR-327, FR-329), its tracking window (FR-328, FR-394,
 * NFR-30), the driver's *Seen* (FR-97, FR-250), the unconfirmed-change rule (ADR-107) and who may
 * start or finish it (BR-02, ADR-114). Times are on 1 March 2027, UTC+2.
 */
class RouteRunTest {

    /** 16:00 local, so the cut-off is 17:00 local, 15:00Z. */
    private static final IsraelDateTime SHIFT_END_A = IsraelDateTime.of(LocalDateTime.of(2027, 3, 1, 16, 0));

    /** 18:00 local, the shift end of the driver a route is handed to: cut-off 19:00 local, 17:00Z. */
    private static final IsraelDateTime SHIFT_END_B = IsraelDateTime.of(LocalDateTime.of(2027, 3, 1, 18, 0));

    private static final TrackingStanding TRACKED = new TrackingStanding(true, true, SHIFT_END_A);
    private static final CorrectedTime FINISH_1300 = CorrectedTime.of(
            Instant.parse("2027-03-01T13:00:00Z"),
            Instant.parse("2027-03-01T13:00:00Z"),
            Instant.parse("2027-03-01T13:00:00Z"));

    private static RouteRun started() {
        return reload(RouteRun.start(ROUTE_A, byA(ONLINE), DRIVER_A, DRIVER_A, RunStartCause.DRIVER_STARTED, NOW));
    }

    private static RouteRun finished() {
        RouteRun run = started();
        run.finish(byA(FINISH_1300), DRIVER_A, DRIVER_A, List.of(STOP));
        return reload(run);
    }

    private static void assertRefusedUnchanged(RouteRun run, ThrowingCallable call, DomainError expected) {
        RouteRunSnapshot before = run.snapshot();

        assertRefused(call, expected);

        assertThat(run.snapshot()).isEqualTo(before);
        assertThat(run.pendingEvents()).isEmpty();
    }

    @Test
    void GivenNoRun_WhenTheFirstReportStartsTheRoute_ThenRouteStartedNamesItsCorrectedTime() {
        RouteRun run = RouteRun.start(ROUTE_A, byA(ONLINE), DRIVER_A, DRIVER_A, RunStartCause.FIRST_REPORT, NOW);

        assertThat(run.isFinished()).isFalse();
        assertThat(run.pendingEvents())
                .containsExactly(new RouteStarted(
                        new EventId("RouteStarted", ROUTE_A.value(), Version.of(0), 0),
                        NOW,
                        ROUTE_A,
                        DRIVER_A,
                        T_0800,
                        RunStartCause.FIRST_REPORT));
        assertThat(run.snapshot().startedAt()).isEqualTo(T_0800);
    }

    enum Command {
        START(run -> run.start(byA(ONLINE), DRIVER_A, DRIVER_A, NOW)),
        FINISH(run -> run.finish(byA(FINISH_1300), DRIVER_A, DRIVER_A, List.of()));

        private final Consumer<RouteRun> call;

        Command(Consumer<RouteRun> call) {
            this.call = call;
        }
    }

    static Stream<Arguments> refusedCells() {
        return Stream.of(
                arguments("started", Command.START, new RunAlreadyStarted(ROUTE_A)),
                arguments("finished", Command.START, new RouteAlreadyFinished(ROUTE_A)),
                arguments("finished", Command.FINISH, new RouteAlreadyFinished(ROUTE_A)));
    }

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @MethodSource("refusedCells")
    void GivenARun_WhenARefusedCommandRuns_ThenItIsRefusedAndNothingChanges(
            String state, Command command, DomainError expected) {
        RouteRun run = state.equals("started") ? started() : finished();

        assertRefusedUnchanged(run, () -> command.call.accept(run), expected);
    }

    @Test
    void GivenARouteDrivenByBAtTheStart_WhenAStartsIt_ThenNotYourRouteAndNoRunStarts() {
        assertRefused(
                () -> RouteRun.start(ROUTE_A, byA(ONLINE), DRIVER_B, DRIVER_B, RunStartCause.DRIVER_STARTED, NOW),
                new NotYourRoute(ROUTE_A));
    }

    @Test
    void GivenARunHandedToB_WhenAStartsItAgain_ThenNotYourRouteComesBeforeRunAlreadyStarted() {
        RouteRun run = started();

        assertRefusedUnchanged(run, () -> run.start(byA(ONLINE), DRIVER_B, DRIVER_B, NOW), new NotYourRoute(ROUTE_A));
    }

    @Test
    void GivenARouteHandedToBAt0810BeforeBStarts_WhenAsStartTimedAt0755Arrives_ThenNotYourRouteAndBStillStartsIt() {
        CorrectedTime beforeTheHandOver = CorrectedTime.of(
                Instant.parse("2027-03-01T07:55:00Z"),
                Instant.parse("2027-03-01T08:20:00Z"),
                Instant.parse("2027-03-01T08:20:00Z"));

        assertRefused(
                () -> RouteRun.start(
                        ROUTE_A, byA(beforeTheHandOver), DRIVER_A, DRIVER_B, RunStartCause.DRIVER_STARTED, NOW),
                new NotYourRoute(ROUTE_A));
        RouteRun run = RouteRun.start(
                ROUTE_A,
                new DriverReport(DRIVER_B, ROUTE_A, ONLINE),
                DRIVER_B,
                DRIVER_B,
                RunStartCause.DRIVER_STARTED,
                NOW);

        assertThat(run.snapshot().startedBy()).isEqualTo(DRIVER_B);
        assertThat(run.pendingEvents())
                .containsExactly(new RouteStarted(
                        new EventId("RouteStarted", ROUTE_A.value(), Version.of(0), 0),
                        NOW,
                        ROUTE_A,
                        DRIVER_B,
                        T_0800,
                        RunStartCause.DRIVER_STARTED));
    }

    @Test
    void GivenARouteHandedToBAndBackToA_WhenAStartsWithATimeInBsTurn_ThenNotYourRoute() {
        assertRefused(
                () -> RouteRun.start(ROUTE_A, byA(ONLINE), DRIVER_B, DRIVER_A, RunStartCause.DRIVER_STARTED, NOW),
                new NotYourRoute(ROUTE_A));
    }

    @Test
    void GivenARunHandedToB_WhenAsStartTimedBeforeTheHandOverArrives_ThenNotYourRouteComesBeforeRunAlreadyStarted() {
        RouteRun run = started();

        assertRefusedUnchanged(run, () -> run.start(byA(ONLINE), DRIVER_A, DRIVER_B, NOW), new NotYourRoute(ROUTE_A));
    }

    @Test
    void
            GivenAFinishedRunHandedToB_WhenAsStartTimedBeforeTheHandOverArrives_ThenNotYourRouteComesBeforeRouteAlreadyFinished() {
        RouteRun run = finished();

        assertRefusedUnchanged(run, () -> run.start(byA(ONLINE), DRIVER_A, DRIVER_B, NOW), new NotYourRoute(ROUTE_A));
    }

    @Test
    void GivenARunHandedToBAndBackToA_WhenAStartsAgainWithATimeInBsTurn_ThenNotYourRouteComesBeforeRunAlreadyStarted() {
        RouteRun run = started();

        assertRefusedUnchanged(run, () -> run.start(byA(ONLINE), DRIVER_B, DRIVER_A, NOW), new NotYourRoute(ROUTE_A));
    }

    @Test
    void GivenARunHandedToB_WhenAFinishesIt_ThenNotYourRoute() {
        RouteRun run = started();

        assertRefusedUnchanged(
                run, () -> run.finish(byA(FINISH_1300), DRIVER_B, DRIVER_B, List.of()), new NotYourRoute(ROUTE_A));
    }

    @Test
    void GivenAFinishedRunHandedToB_WhenAFinishesItAgain_ThenNotYourRouteComesBeforeRouteAlreadyFinished() {
        RouteRun run = finished();

        assertRefusedUnchanged(
                run, () -> run.finish(byA(FINISH_1300), DRIVER_B, DRIVER_B, List.of()), new NotYourRoute(ROUTE_A));
    }

    @Test
    void GivenARunHandedToBAt0900_WhenAsOfflineFinishTimedAt0850Arrives_ThenNotYourRouteAndBStillFinishesIt() {
        RouteRun run = started();
        CorrectedTime beforeTheHandOver = CorrectedTime.of(
                Instant.parse("2027-03-01T08:50:00Z"),
                Instant.parse("2027-03-01T09:30:00Z"),
                Instant.parse("2027-03-01T09:30:00Z"));

        assertRefusedUnchanged(
                run,
                () -> run.finish(byA(beforeTheHandOver), DRIVER_A, DRIVER_B, List.of(STOP)),
                new NotYourRoute(ROUTE_A));
        run.finish(new DriverReport(DRIVER_B, ROUTE_A, FINISH_1300), DRIVER_B, DRIVER_B, List.of());

        assertThat(run.snapshot().status())
                .isEqualTo(new RunStatus.Finished(Instant.parse("2027-03-01T13:00:00Z"), DRIVER_B, List.of()));
    }

    @Test
    void GivenARunHandedToBAndBackToA_WhenAFinishesWithATimeInBsTurn_ThenNotYourRoute() {
        RouteRun run = started();

        assertRefusedUnchanged(
                run, () -> run.finish(byA(FINISH_1300), DRIVER_B, DRIVER_A, List.of()), new NotYourRoute(ROUTE_A));
    }

    @Test
    void GivenARunHandedToB_WhenASeesARouteChange_ThenNotYourRouteAndBsChangeStaysUnconfirmed() {
        RouteRun run = started();

        assertRefusedUnchanged(run, () -> run.acknowledge(DRIVER_A, DRIVER_B, REV_2, REV_2), new NotYourRoute(ROUTE_A));
        assertThat(run.isConfirmationOverdue(REV_2, Instant.parse("2027-03-01T09:00:00Z"), NOW))
                .isTrue();
    }

    @Test
    void GivenARunHandedToB_WhenASeesARevisionPastTheCurrentOne_ThenNotYourRouteComesBeforeUnknownRevision() {
        RouteRun run = started();

        assertRefusedUnchanged(run, () -> run.acknowledge(DRIVER_A, DRIVER_B, REV_2, REV_1), new NotYourRoute(ROUTE_A));
    }

    @Test
    void GivenARunHandedToB_WhenBSeesTheRouteChange_ThenItIsStored() {
        RouteRun run = started();

        run.acknowledge(DRIVER_B, DRIVER_B, REV_2, REV_2);

        assertThat(run.confirmed()).contains(REV_2);
    }

    @Test
    void GivenARunHandedToB_WhenBFinishesIt_ThenItIsFinishedByB() {
        RouteRun run = started();

        run.finish(new DriverReport(DRIVER_B, ROUTE_A, FINISH_1300), DRIVER_B, DRIVER_B, List.of());

        assertThat(run.snapshot().status())
                .isEqualTo(new RunStatus.Finished(Instant.parse("2027-03-01T13:00:00Z"), DRIVER_B, List.of()));
    }

    @Test
    void GivenAStartedRun_WhenFinishedWithAStopPending_ThenItIsFinishedWithThatStop() {
        RouteRun run = started();

        run.finish(byA(FINISH_1300), DRIVER_A, DRIVER_A, List.of(STOP));

        assertThat(run.isFinished()).isTrue();
        assertThat(run.snapshot().status())
                .isEqualTo(new RunStatus.Finished(Instant.parse("2027-03-01T13:00:00Z"), DRIVER_A, List.of(STOP)));
        assertThat(run.pendingEvents()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"started", "finished"})
    void GivenARun_WhenTheDriverConfirmsTheCurrentRevision_ThenItIsStored(String state) {
        RouteRun run = state.equals("started") ? started() : finished();

        run.acknowledge(DRIVER_A, DRIVER_A, REV_2, REV_2);

        assertThat(run.confirmed()).contains(REV_2);
    }

    @Test
    void GivenRev1Confirmed_WhenRev2Seen_ThenRev2Stored() {
        RouteRun run = started();
        run.acknowledge(DRIVER_A, DRIVER_A, REV_1, REV_1);
        RouteRun reloaded = reload(run);

        reloaded.acknowledge(DRIVER_A, DRIVER_A, REV_2, REV_2);

        assertThat(reloaded.confirmed()).contains(REV_2);
    }

    @Test
    void GivenAStartedRun_WhenTheDriverStartsAgain_ThenRunAlreadyStarted() {
        RouteRun run = started();

        assertRefusedUnchanged(
                run, () -> run.start(byA(ONLINE), DRIVER_A, DRIVER_A, NOW), new RunAlreadyStarted(ROUTE_A));
    }

    @Test
    void GivenAFinishedRun_WhenFinishedAgain_ThenRouteAlreadyFinished() {
        RouteRun run = finished();

        assertRefusedUnchanged(
                run,
                () -> run.finish(byA(FINISH_1300), DRIVER_A, DRIVER_A, List.of()),
                new RouteAlreadyFinished(ROUTE_A));
    }

    @Test
    void GivenARevisionTheDriverConfirmed_WhenAnOlderOrTheSameOneArrives_ThenItIsIgnored() {
        RouteRun run = started();
        run.acknowledge(DRIVER_A, DRIVER_A, REV_2, REV_2);
        RouteRun reloaded = reload(run);
        RouteRunSnapshot before = reloaded.snapshot();

        reloaded.acknowledge(DRIVER_A, DRIVER_A, REV_1, REV_2);
        reloaded.acknowledge(DRIVER_A, DRIVER_A, REV_2, REV_2);

        assertThat(reloaded.confirmed()).contains(REV_2);
        assertThat(reloaded.snapshot()).isEqualTo(before);
    }

    @Test
    void GivenTheCurrentRevision_WhenTheDriverConfirmsALaterOne_ThenUnknownRevision() {
        RouteRun run = started();

        assertRefusedUnchanged(
                run,
                () -> run.acknowledge(DRIVER_A, DRIVER_A, REV_2, REV_1),
                new UnknownRevision(ROUTE_A, REV_2, REV_1));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
        "2027-03-01T07:59:59Z, false",
        "2027-03-01T08:00:00Z, true",
        "2027-03-01T14:59:59Z, true",
        "2027-03-01T15:00:00Z, false"
    })
    void GivenAStartedRun_WhenAPositionArrivesNearItsWindowsEnds_ThenTheStartCountsAndTheCutOffDoesNot(
            String at, boolean kept) {
        RouteRun run = started();

        assertThat(run.acceptsPositionAt(Instant.parse(at), TRACKED)).isEqualTo(kept);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({"2027-03-01T16:59:59Z, true", "2027-03-01T17:00:00Z, false"})
    void GivenARouteHandedToBWithALaterShift_WhenBsPositionArrives_ThenItIsKeptUntilBsShiftEndPlusAnHour(
            String at, boolean kept) {
        RouteRun run = started();
        TrackingStanding b = new TrackingStanding(true, true, SHIFT_END_B);

        assertThat(run.acceptsPositionAt(Instant.parse(at), b)).isEqualTo(kept);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({"2027-03-01T13:00:00Z, true", "2027-03-01T13:00:01Z, false"})
    void GivenAFinishedRun_WhenAPositionArrivesNearTheFinish_ThenTheFinishCounts(String at, boolean kept) {
        RouteRun run = finished();

        assertThat(run.acceptsPositionAt(Instant.parse(at), TRACKED)).isEqualTo(kept);
    }

    @Test
    void GivenAPositionAfterTheCutOff_WhenChecked_ThenTrackingClosed() {
        RouteRun run = started();

        assertRefusedUnchanged(
                run,
                () -> run.checkPositionAt(Instant.parse("2027-03-01T15:00:00Z"), TRACKED),
                new TrackingClosed(ROUTE_A, TrackingRefusal.OUTSIDE_WINDOW));
    }

    @Test
    void GivenTheOldDriverAfterAHandOver_WhenTheirPositionIsChecked_ThenTrackingClosedNamesThem() {
        RouteRun run = started();

        assertRefusedUnchanged(
                run,
                () -> run.checkPositionAt(NOW, new TrackingStanding(false, false, SHIFT_END_A)),
                new TrackingClosed(ROUTE_A, TrackingRefusal.NOT_THE_CURRENT_DRIVER));
    }

    @Test
    void GivenAnUnavailableDriverOutsideTheWindow_WhenTheirPositionIsChecked_ThenUnavailableComesFirst() {
        RouteRun run = started();
        TrackingStanding unavailable = new TrackingStanding(true, false, SHIFT_END_A);

        assertRefusedUnchanged(
                run,
                () -> run.checkPositionAt(Instant.parse("2027-03-01T15:00:00Z"), unavailable),
                new TrackingClosed(ROUTE_A, TrackingRefusal.DRIVER_UNAVAILABLE));
        assertThat(run.acceptsPositionAt(NOW, unavailable)).isFalse();
    }

    @ParameterizedTest(name = "now {0}: overdue {1}")
    @CsvSource({"2027-03-01T09:09:59Z, false", "2027-03-01T09:10:00Z, true"})
    void GivenARevisionAfterTheStartNotConfirmed_WhenTenMinutesPass_ThenTheChangeIsOverdueFromTheDeadline(
            String now, boolean overdue) {
        RouteRun run = started();

        assertThat(run.isConfirmationOverdue(REV_2, Instant.parse("2027-03-01T09:00:00Z"), Instant.parse(now)))
                .isEqualTo(overdue);
    }

    @Test
    void GivenRev1Confirmed_WhenRev2UnconfirmedTenMinutes_ThenOverdue() {
        RouteRun run = started();
        run.acknowledge(DRIVER_A, DRIVER_A, REV_1, REV_1);

        assertThat(run.isConfirmationOverdue(
                        REV_2, Instant.parse("2027-03-01T09:00:00Z"), Instant.parse("2027-03-01T09:10:00Z")))
                .isTrue();
    }

    @Test
    void GivenARevisionAtExactlyTheStart_WhenTenMinutesPass_ThenItIsOverdue() {
        RouteRun run = started();

        assertThat(run.isConfirmationOverdue(REV_2, T_0800, Instant.parse("2027-03-01T08:10:00Z")))
                .isTrue();
    }

    @Test
    void GivenARevisionBeforeTheStart_WhenJudged_ThenItCountsAsSeen() {
        RouteRun run = started();

        assertThat(run.isConfirmationOverdue(REV_2, Instant.parse("2027-03-01T07:59:59Z"), NOW))
                .isFalse();
    }

    @Test
    void GivenAConfirmedRevision_WhenJudged_ThenItIsNotOverdue() {
        RouteRun run = started();
        run.acknowledge(DRIVER_A, DRIVER_A, REV_2, REV_2);

        assertThat(run.isConfirmationOverdue(REV_2, Instant.parse("2027-03-01T09:00:00Z"), NOW))
                .isFalse();
    }

    @Test
    void GivenAFinishedRun_WhenJudged_ThenNoChangeIsOverdue() {
        RouteRun run = finished();

        assertThat(run.isConfirmationOverdue(
                        REV_2, Instant.parse("2027-03-01T13:30:00Z"), Instant.parse("2027-03-01T14:00:00Z")))
                .isFalse();
    }

    @Test
    void GivenAFinishedRunNamingAStopTwice_WhenRestored_ThenStoredStateInvalid() {
        RouteRunSnapshot s = finished().snapshot();
        RouteRunSnapshot twice = new RouteRunSnapshot(
                s.route(),
                s.version(),
                s.startedBy(),
                s.cause(),
                s.startedAt(),
                new RunStatus.Finished(Instant.parse("2027-03-01T13:00:00Z"), DRIVER_A, List.of(STOP, STOP)),
                Optional.empty());

        assertThatThrownBy(() -> RouteRun.restore(twice)).isInstanceOfSatisfying(StoredStateInvalid.class, refused -> {
            assertThat(refused.subject()).isEqualTo("RouteRun");
            assertThat(refused.detail()).isEqualTo("a finished run names each pending stop once (FR-329)");
        });
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"started", "finished"})
    void GivenEveryBuilderState_WhenRestoredFromItsSnapshot_ThenTheSnapshotRoundTrips(String state) {
        RouteRun run = state.equals("started") ? started() : finished();

        assertThat(RouteRun.restore(run.snapshot()).snapshot()).isEqualTo(run.snapshot());
    }

    @Test
    void GivenAMissingParameter_WhenEachCommandRuns_ThenItFailsLoudly() {
        RouteRun run = started();

        assertThatThrownBy(() -> run.finish(byA(FINISH_1300), DRIVER_A, DRIVER_A, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> run.finish(byA(FINISH_1300), null, DRIVER_A, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> run.finish(byA(FINISH_1300), DRIVER_A, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> run.acknowledge(DRIVER_A, DRIVER_A, REV_1, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> run.acknowledge(null, DRIVER_A, REV_1, REV_1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> run.acknowledge(DRIVER_A, null, REV_1, REV_1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> run.checkPositionAt(NOW, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TrackingStanding(true, true, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RouteRun.start(ROUTE_A, byA(ONLINE), DRIVER_A, DRIVER_A, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RouteRun.start(ROUTE_A, byA(ONLINE), DRIVER_A, null, RunStartCause.FIRST_REPORT, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> run.start(byA(ONLINE), DRIVER_A, null, NOW)).isInstanceOf(NullPointerException.class);
        assertThat(run.isFinished()).isFalse();
    }
}
