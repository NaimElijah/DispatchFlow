package com.dispatchflow.liveops.domain;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DISPATCHER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_B;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.PLAN;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_2;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_B;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.STOP;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.uuid;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.dispatchflow.liveops.domain.LiveOpsError.AlertAlreadyAcknowledged;
import com.dispatchflow.liveops.events.OutcomeKind;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.Version;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * An alert's lifecycle (FR-157, FR-260) and each kind's dedupe key, which makes it once (DL §5,
 * ADR-107, ADR-114).
 */
class AlertTest {

    private static final AlertId ALERT = AlertId.of(uuid(60));
    private static final AlertId OTHER_ALERT = AlertId.of(uuid(61));
    private static final RatingRequestId RATING = RatingRequestId.of(uuid(70));
    private static final String S = "0190a5e2-0000-7000-a000-000000000001";
    private static final String A = "0190a5e2-0000-7000-a000-000000000020";

    static Stream<Arguments> keys() {
        return Stream.of(
                arguments(new AlertSubject.StopAtRisk(PLAN, ROUTE_A, STOP, REV_2), "STOP_AT_RISK:" + S + ":2"),
                arguments(
                        new AlertSubject.DriverProblemReported(PLAN, ROUTE_A, DRIVER_A, DriverProblem.BREAKDOWN),
                        "DRIVER_PROBLEM:0190a5e2-0000-7000-a000-000000000060"),
                arguments(new AlertSubject.RouteNotStarted(PLAN, ROUTE_A), "ROUTE_NOT_STARTED:" + A),
                arguments(new AlertSubject.StopFailed(PLAN, ROUTE_A, STOP), "STOP_FAILED:" + S),
                arguments(
                        new AlertSubject.RouteChangeUnconfirmed(PLAN, ROUTE_A, REV_2),
                        "ROUTE_CHANGE_UNCONFIRMED:" + A + ":2"),
                arguments(new AlertSubject.LateReport(PLAN, ROUTE_A, STOP, Optional.of(ROUTE_B)), "LATE_REPORT:" + S),
                arguments(
                        new AlertSubject.ReportHeld(PLAN, ROUTE_A, STOP, HELD_1),
                        "REPORT_HELD:0190a5e2-0000-7000-a000-000000000040"),
                arguments(
                        new AlertSubject.ReportRefused(PLAN, ROUTE_A, STOP, DRIVER_A, OutcomeKind.DELIVERED),
                        "REPORT_REFUSED:" + S + ":" + A + ":0190a5e2-0000-7000-a000-000000000010:DELIVERED"),
                arguments(
                        new AlertSubject.RouteFinishedWithPendingStops(PLAN, ROUTE_A),
                        "ROUTE_FINISHED_WITH_PENDING_STOPS:" + A),
                arguments(
                        new AlertSubject.LowRating(PLAN, STOP, RATING),
                        "LOW_RATING:0190a5e2-0000-7000-a000-000000000070"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("keys")
    void GivenEachKindsSubject_WhenRaised_ThenItIsOpenAndKeyedOnItsKindAndSubjectIds(AlertSubject subject, String key) {
        Alert alert = Alert.raise(ALERT, subject, NOW);

        assertThat(alert.isOpen()).isTrue();
        assertThat(alert.kind()).isEqualTo(subject.kind());
        assertThat(alert.dedupeKey()).isEqualTo(new DedupeKey(key));
        assertThat(alert.pendingEvents()).isEmpty();
    }

    @Test
    void GivenEveryKind_WhenItsSubjectsAreListed_ThenEachKindHasOne() {
        assertThat(keys().map(arguments -> ((AlertSubject) arguments.get()[0]).kind()))
                .containsExactlyInAnyOrder(AlertKind.values());
    }

    @Test
    void GivenTheSameStopAtRiskUnderTwoRevisions_WhenKeyed_ThenTheKeysDiffer() {
        assertThat(new AlertSubject.StopAtRisk(PLAN, ROUTE_A, STOP, REV_1).dedupeKey(ALERT))
                .isNotEqualTo(new AlertSubject.StopAtRisk(PLAN, ROUTE_A, STOP, REV_2).dedupeKey(ALERT));
    }

    @Test
    void GivenOneRouteNotStartedRaisedTwice_WhenKeyed_ThenTheKeysMatch() {
        AlertSubject subject = new AlertSubject.RouteNotStarted(PLAN, ROUTE_A);

        assertThat(Alert.raise(ALERT, subject, NOW).dedupeKey())
                .isEqualTo(Alert.raise(OTHER_ALERT, subject, LATER).dedupeKey());
    }

    @Test
    void GivenOneRefusedReportRetried_WhenKeyed_ThenTheKeysMatch() {
        AlertSubject subject = new AlertSubject.ReportRefused(PLAN, ROUTE_A, STOP, DRIVER_A, OutcomeKind.FAILED);

        assertThat(Alert.raise(ALERT, subject, NOW).dedupeKey())
                .isEqualTo(Alert.raise(OTHER_ALERT, subject, LATER).dedupeKey());
    }

    @Test
    void GivenRefusalsFromTwoDriversOnOneRouteAndStop_WhenKeyed_ThenEachAlerts() {
        AlertSubject first = new AlertSubject.ReportRefused(PLAN, ROUTE_A, STOP, DRIVER_A, OutcomeKind.FAILED);
        AlertSubject second = new AlertSubject.ReportRefused(PLAN, ROUTE_A, STOP, DRIVER_B, OutcomeKind.FAILED);

        assertThat(first.dedupeKey(ALERT)).isNotEqualTo(second.dedupeKey(OTHER_ALERT));
    }

    @Test
    void GivenRefusalsOfTwoOutcomesFromOneDriver_WhenKeyed_ThenEachAlerts() {
        AlertSubject delivered = new AlertSubject.ReportRefused(PLAN, ROUTE_A, STOP, DRIVER_A, OutcomeKind.DELIVERED);
        AlertSubject failed = new AlertSubject.ReportRefused(PLAN, ROUTE_A, STOP, DRIVER_A, OutcomeKind.FAILED);

        assertThat(delivered.dedupeKey(ALERT)).isNotEqualTo(failed.dedupeKey(OTHER_ALERT));
    }

    @Test
    void GivenTwoProblemReports_WhenKeyed_ThenEachAlerts() {
        AlertSubject subject =
                new AlertSubject.DriverProblemReported(PLAN, ROUTE_A, DRIVER_A, DriverProblem.VEHICLE_FULL);

        assertThat(Alert.raise(ALERT, subject, NOW).dedupeKey())
                .isNotEqualTo(Alert.raise(OTHER_ALERT, subject, LATER).dedupeKey());
    }

    @Test
    void GivenAnOpenAlert_WhenAcknowledged_ThenItRecordsWhoAndWhen() {
        Alert alert = Alert.raise(ALERT, new AlertSubject.StopFailed(PLAN, ROUTE_A, STOP), NOW);

        alert.acknowledge(DISPATCHER, LATER);

        assertThat(alert.isOpen()).isFalse();
        assertThat(alert.snapshot().state()).isEqualTo(new AlertState.Acknowledged(DISPATCHER, LATER));
    }

    @Test
    void GivenAnAcknowledgedAlert_WhenAcknowledgedAgain_ThenAlertAlreadyAcknowledged() {
        Alert alert = Alert.raise(ALERT, new AlertSubject.StopFailed(PLAN, ROUTE_A, STOP), NOW);
        alert.acknowledge(DISPATCHER, LATER);
        Alert reloaded = Alert.restore(new AlertSnapshot(
                ALERT,
                Version.of(1),
                alert.snapshot().subject(),
                NOW,
                alert.snapshot().state()));
        AlertSnapshot before = reloaded.snapshot();

        assertRefused(() -> reloaded.acknowledge(DISPATCHER, LATER), new AlertAlreadyAcknowledged(ALERT));
        assertThat(reloaded.snapshot()).isEqualTo(before);
    }

    @Test
    void GivenAnOpenAndAnAcknowledgedAlert_WhenRestoredFromTheirSnapshots_ThenTheyRoundTrip() {
        Alert open = Alert.raise(ALERT, new AlertSubject.RouteNotStarted(PLAN, ROUTE_A), NOW);
        Alert acknowledged = Alert.raise(OTHER_ALERT, new AlertSubject.RouteNotStarted(PLAN, ROUTE_B), NOW);
        acknowledged.acknowledge(DISPATCHER, LATER);

        assertThat(Alert.restore(open.snapshot()).snapshot()).isEqualTo(open.snapshot());
        assertThat(Alert.restore(acknowledged.snapshot()).snapshot()).isEqualTo(acknowledged.snapshot());
    }
}
