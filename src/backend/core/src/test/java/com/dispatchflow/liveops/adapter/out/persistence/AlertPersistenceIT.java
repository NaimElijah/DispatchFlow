package com.dispatchflow.liveops.adapter.out.persistence;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DISPATCHER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.alert.Alert;
import com.dispatchflow.liveops.domain.alert.AlertDescription;
import com.dispatchflow.liveops.domain.alert.AlertKind;
import com.dispatchflow.liveops.domain.alert.AlertState;
import com.dispatchflow.liveops.domain.alert.AlertSubject;
import com.dispatchflow.liveops.domain.alert.DriverProblem;
import com.dispatchflow.liveops.domain.alert.RefusedFact;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link AlertRepository} against a real PostgreSQL (ADR-126, ADR-127): what an alert stores and
 * loads as for every kind, the version, the dedupe key's refusal and its lock, the delivery date
 * kept beside each alert, and the stored values the load refuses.
 *
 * <p>The database is shared and never cleaned, and a dedupe key is unique across every alert. So
 * test {@code n} keys its own alerts ({@code alert(n)}, {@code alert(100 + n)}, …) and the IDs its
 * subjects name, and so its keys too.
 */
@IntegrationTest
class AlertPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_616c_7201L;
    private static final LocalDate FIRST_DAY = LocalDate.of(2033, 1, 1);
    private static final AlertState OPEN = new AlertState.Open();

    @Autowired
    private AlertRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    // --- round trips (ADR-127 decision 1)

    @ParameterizedTest
    @EnumSource(AlertKind.class)
    void GivenAnAlertOfEachKind_WhenAddedAndLoadedInAnotherTransaction_ThenEveryPartComesBackAsAdded(AlertKind kind) {
        int n = 1 + kind.ordinal();
        Alert added = Alert.raise(alert(n), subjectOf(kind, n), NOW);

        assertThat(transactions.inTransaction(() -> repository.addUnlessKeyTaken(added, dateOf(n))))
                .isTrue();
        Alert loaded = transactions.inTransaction(() -> repository.get(alert(n)).orElseThrow());

        // The subject raised, not the added instance's, which keeps whatever its columns kept.
        assertThat(AlertDescription.of(loaded)).isEqualTo(written(n, subjectOf(kind, n), OPEN));
        assertThat(loaded.id()).isEqualTo(alert(n));
        assertThat(loaded.kind()).isEqualTo(kind);
        assertThat(loaded.dedupeKey()).isEqualTo(subjectOf(kind, n).dedupeKey(alert(n)));
        assertThat(loaded.isOpen()).isTrue();
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(storedDate(n)).isEqualTo(dateOf(n).value());
    }

    @Test
    void GivenALateReportWithNoRouteLeft_WhenAddedAndLoaded_ThenItComesBackWithNone() {
        int n = 20;
        AlertSubject noRouteLeft = new AlertSubject.LateReport(plan(n), route(n), stop(n), Optional.empty());

        transactions.inTransaction(
                () -> repository.addUnlessKeyTaken(Alert.raise(alert(n), noRouteLeft, NOW), dateOf(n)));

        assertThat(AlertDescription.of(transactions.inTransaction(
                        () -> repository.get(alert(n)).orElseThrow())))
                .isEqualTo(written(n, noRouteLeft, OPEN));
    }

    @Test
    void GivenAnAcknowledgedAlert_WhenLoadedInAnotherTransaction_ThenItComesBackAcknowledged() {
        int n = 21;
        transactions.inTransaction(() -> repository.addUnlessKeyTaken(stopFailed(n, n), dateOf(n)));
        transactions.inTransaction(() -> {
            Alert alert = repository.get(alert(n)).orElseThrow();
            alert.acknowledge(DISPATCHER, LATER);
            repository.save(alert);
        });

        Alert loaded = transactions.inTransaction(() -> repository.get(alert(n)).orElseThrow());

        assertThat(AlertDescription.of(loaded))
                .isEqualTo(written(
                        n,
                        new AlertSubject.StopFailed(plan(n), route(n), stop(n)),
                        new AlertState.Acknowledged(DISPATCHER, LATER)));
        assertThat(loaded.version()).isEqualTo(Version.of(1));
        assertThat(loaded.isOpen()).isFalse();
    }

    @Test
    void GivenNoAlertWithAnId_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(alert(22)))).isEmpty();
    }

    // --- versions and conflicts

    @Test
    void GivenAnAddedAlert_WhenItIsAcknowledged_ThenTheVersionIsZeroThenOne() {
        int n = 23;
        transactions.inTransaction(() -> repository.addUnlessKeyTaken(stopFailed(n, n), dateOf(n)));
        long added = storedVersion(n);

        transactions.inTransaction(() -> {
            Alert alert = repository.get(alert(n)).orElseThrow();
            alert.acknowledge(DISPATCHER, LATER);
            repository.save(alert);
        });

        assertThat(List.of(added, storedVersion(n))).containsExactly(0L, 1L);
    }

    @Test
    void GivenTwoDispatchersOnOneAlert_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstAcknowledgementStands() {
        int n = 24;
        transactions.inTransaction(() -> repository.addUnlessKeyTaken(stopFailed(n, n), dateOf(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Alert mine = repository.get(alert(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        Alert theirs = repository.get(alert(n)).orElseThrow();
                        theirs.acknowledge(DISPATCHER, NOW);
                        repository.save(theirs);
                    });
                    mine.acknowledge(DRIVER_A, LATER);
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Alert");
                    assertThat(stale.id()).isEqualTo(alert(n).value());
                });

        assertThat(storedVersion(n)).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT acknowledged_by FROM liveops.alert WHERE id = ?")
                        .param(alert(n).value())
                        .query(UUID.class)
                        .single())
                .isEqualTo(DISPATCHER.value());
    }

    // --- one alert per dedupe key (DL §5, ADR-107)

    @Test
    void GivenAnAlertWithAKey_WhenAnotherWithTheKeyIsAdded_ThenItIsNotStoredAndTheCallersTransactionGoesOn() {
        int n = 25;
        transactions.inTransaction(() -> repository.addUnlessKeyTaken(stopFailed(n, n), dateOf(n)));
        Alert again = stopFailed(100 + n, n);

        boolean[] answers = transactions.inTransaction(() -> {
            boolean duplicate = repository.addUnlessKeyTaken(again, dateOf(n));
            boolean other = repository.addUnlessKeyTaken(stopFailed(200 + n, 200 + n), dateOf(n));
            return new boolean[] {duplicate, other};
        });

        assertThat(answers).containsExactly(false, true);
        assertThat(again.savedVersion()).isEmpty();
        assertThat(transactions.inTransaction(() -> repository.get(alert(100 + n))))
                .isEmpty();
        assertThat(transactions.inTransaction(() -> repository.get(alert(200 + n))))
                .isPresent();
        assertThat(alertsWithKey(again)).isEqualTo(1L);
    }

    @Test
    void GivenAnAcknowledgedAlert_WhenItsKeyIsRaisedAgain_ThenNothingIsStored() {
        int n = 26;
        transactions.inTransaction(() -> repository.addUnlessKeyTaken(stopFailed(n, n), dateOf(n)));
        transactions.inTransaction(() -> {
            Alert alert = repository.get(alert(n)).orElseThrow();
            alert.acknowledge(DISPATCHER, LATER);
            repository.save(alert);
        });

        assertThat(transactions.inTransaction(() -> repository.addUnlessKeyTaken(stopFailed(100 + n, n), dateOf(n))))
                .isFalse();
        assertThat(transactions.inTransaction(() -> repository.get(alert(100 + n))))
                .isEmpty();
    }

    @Test
    void GivenOneTransactionRaisingAKeyTwice_WhenBothAreAdded_ThenOnlyTheFirstIsStored() {
        int n = 27;

        boolean[] answers = transactions.inTransaction(() -> new boolean[] {
            repository.addUnlessKeyTaken(stopFailed(n, n), dateOf(n)),
            repository.addUnlessKeyTaken(stopFailed(100 + n, n), dateOf(n))
        });

        assertThat(answers).containsExactly(true, false);
        assertThat(transactions.inTransaction(() -> repository.get(alert(100 + n))))
                .isEmpty();
    }

    @Test
    void GivenATransactionAddingAKey_WhenAnotherAddsTheSameKey_ThenItCannotTakeTheKeysLock() {
        int n = 28;

        transactions.inTransaction(() -> {
            repository.addUnlessKeyTaken(stopFailed(n, n), dateOf(n));
            assertThatThrownBy(() -> transactions.inANewTransaction(() -> {
                        shortLockTimeout();
                        repository.addUnlessKeyTaken(stopFailed(100 + n, n), dateOf(n));
                    }))
                    .isInstanceOf(PessimisticLockingFailureException.class);
            transactions.inANewTransaction(() -> {
                shortLockTimeout();
                assertThat(repository.addUnlessKeyTaken(stopFailed(200 + n, 200 + n), dateOf(n)))
                        .isTrue();
            });
        });

        assertThat(transactions.inTransaction(() -> repository.get(alert(n)))).isPresent();
        assertThat(transactions.inTransaction(() -> repository.get(alert(100 + n))))
                .isEmpty();
    }

    @Test
    void GivenAStoredAlert_WhenARowWithItsKeyIsWrittenPastTheAdapter_ThenTheDatabaseRefusesIt() {
        int n = 29;
        Alert stored = stopFailed(n, n);
        transactions.inTransaction(() -> repository.addUnlessKeyTaken(stored, dateOf(n)));

        assertThatThrownBy(() -> jdbc.sql("""
                                INSERT INTO liveops.alert
                                    (id, version, kind, plan, route, stop, dedupe_key, raised_at, state)
                                SELECT ?, 0, kind, plan, route, stop, dedupe_key, raised_at, 'OPEN'
                                FROM liveops.alert WHERE id = ?
                                """)
                        .params(alert(100 + n).value(), alert(n).value())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- the stored values the load refuses

    @Test
    void GivenAStoredRevisionBelowOne_WhenTheAlertIsLoaded_ThenStoredStateInvalidNamesTheRevision() {
        int n = 30;
        transactions.inTransaction(() -> repository.addUnlessKeyTaken(
                Alert.raise(alert(n), subjectOf(AlertKind.STOP_AT_RISK, n), NOW), dateOf(n)));

        jdbc.sql("UPDATE liveops.alert SET revision = 0 WHERE id = ?")
                .param(alert(n).value())
                .update();

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(alert(n))))
                .satisfies(thrown -> assertThat(storedStateInvalidIn(thrown)).satisfies(invalid -> {
                    assertThat(invalid.subject()).isEqualTo("RevisionNo");
                    assertThat(invalid.detail()).isEqualTo("a stored value is refused with INVALID_REVISION");
                }));
    }

    @Test
    void GivenAStoredAlertAtRisk_WhenItsStopIsRemoved_ThenTheDatabaseRefusesTheWrite() {
        int n = 31;
        transactions.inTransaction(() -> repository.addUnlessKeyTaken(
                Alert.raise(alert(n), subjectOf(AlertKind.STOP_AT_RISK, n), NOW), dateOf(n)));

        assertThatThrownBy(() -> jdbc.sql("UPDATE liveops.alert SET stop = NULL WHERE id = ?")
                        .param(alert(n).value())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(alert(n)))).isPresent();
    }

    // --- subjects, keys and stored values

    /** One subject of each kind, naming test {@code n}'s own IDs. */
    private static AlertSubject subjectOf(AlertKind kind, int n) {
        return switch (kind) {
            case STOP_AT_RISK -> new AlertSubject.StopAtRisk(plan(n), route(n), stop(n), REV_2);
            case DRIVER_PROBLEM ->
                new AlertSubject.DriverProblemReported(plan(n), route(n), DRIVER_A, DriverProblem.VEHICLE_FULL);
            case ROUTE_NOT_STARTED -> new AlertSubject.RouteNotStarted(plan(n), route(n));
            case STOP_FAILED -> new AlertSubject.StopFailed(plan(n), route(n), stop(n));
            case ROUTE_CHANGE_UNCONFIRMED -> new AlertSubject.RouteChangeUnconfirmed(plan(n), route(n), REV_2);
            case LATE_REPORT -> new AlertSubject.LateReport(plan(n), route(n), stop(n), Optional.of(route(100 + n)));
            case REPORT_HELD -> new AlertSubject.ReportHeld(plan(n), route(n), stop(n), heldReport(n));
            case REPORT_REFUSED ->
                new AlertSubject.ReportRefused(plan(n), route(n), stop(n), DRIVER_A, RefusedFact.DELIVERED);
            case ROUTE_FINISHED_WITH_PENDING_STOPS -> new AlertSubject.RouteFinishedWithPendingStops(plan(n), route(n));
            case LOW_RATING -> new AlertSubject.LowRating(plan(n), stop(n), ratingRequest(n));
        };
    }

    /** What alert {@code a}, raised at {@code NOW}, must load as: written out, not read back. */
    private static AlertDescription written(int a, AlertSubject subject, AlertState state) {
        return new AlertDescription(alert(a), subject.kind(), subject, subject.dedupeKey(alert(a)), NOW, state);
    }

    /** Alert {@code a}, about stop {@code s} failing: its key is the stop's. */
    private static Alert stopFailed(int a, int s) {
        return Alert.raise(alert(a), new AlertSubject.StopFailed(plan(s), route(s), stop(s)), NOW);
    }

    private static AlertId alert(int a) {
        return AlertId.of(new UUID(KEY_PREFIX, a));
    }

    private static PlanId plan(int n) {
        return PlanId.of(new UUID(KEY_PREFIX + 1, n));
    }

    private static RouteId route(int n) {
        return RouteId.of(new UUID(KEY_PREFIX + 2, n));
    }

    private static StopId stop(int n) {
        return StopId.of(new UUID(KEY_PREFIX + 3, n));
    }

    private static HeldReportId heldReport(int n) {
        return HeldReportId.of(new UUID(KEY_PREFIX + 4, n));
    }

    private static RatingRequestId ratingRequest(int n) {
        return RatingRequestId.of(new UUID(KEY_PREFIX + 5, n));
    }

    private static DeliveryDate dateOf(int n) {
        return DeliveryDate.of(FIRST_DAY.plusDays(n));
    }

    /** The first {@link StoredStateInvalid} in a failure's cause chain, which Spring may wrap. */
    private static StoredStateInvalid storedStateInvalidIn(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof StoredStateInvalid invalid) {
                return invalid;
            }
        }
        throw new AssertionError("no StoredStateInvalid in the cause chain of " + thrown);
    }

    private void shortLockTimeout() {
        jdbc.sql("SET LOCAL lock_timeout = '200ms'").update();
    }

    private long storedVersion(int a) {
        return jdbc.sql("SELECT version FROM liveops.alert WHERE id = ?")
                .param(alert(a).value())
                .query(Long.class)
                .single();
    }

    private LocalDate storedDate(int a) {
        return jdbc.sql("SELECT delivery_date FROM liveops.alert WHERE id = ?")
                .param(alert(a).value())
                .query(LocalDate.class)
                .single();
    }

    private long alertsWithKey(Alert alert) {
        return jdbc.sql("SELECT count(*) FROM liveops.alert WHERE dedupe_key = ?")
                .param(alert.dedupeKey().value())
                .query(Long.class)
                .single();
    }
}
