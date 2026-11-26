package com.dispatchflow.liveops.adapter.out.persistence;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ONLINE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_2;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0800;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.DatabaseOwner;
import com.dispatchflow.IntegrationTest;
import com.dispatchflow.TestPersistenceConfiguration.RecordedEvents;
import com.dispatchflow.Transactions;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.liveops.domain.routerun.RouteRun;
import com.dispatchflow.liveops.domain.routerun.RouteRunDescription;
import com.dispatchflow.liveops.domain.routerun.RunStatus;
import com.dispatchflow.liveops.events.RouteStarted;
import com.dispatchflow.liveops.events.RunStartCause;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link RouteRunRepository} against a real PostgreSQL (ADR-126, ADR-127): what a run stores and
 * loads as, the version, the delivery date kept beside it and the runs of a date, and the stored
 * state the load check refuses.
 *
 * <p>The database is shared and never cleaned. So test {@code n} keys its own routes ({@code
 * route(n)}, {@code route(100 + n)}, …) and stops, and keeps its runs on its own delivery date,
 * {@code dateOf(n)}. Each getter is read outside the transaction that loaded the run, which shows
 * the run was loaded whole.
 */
@IntegrationTest
class RouteRunPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_7275_6e01L;
    private static final LocalDate FIRST_DAY = LocalDate.of(2032, 1, 1);
    private static final Instant FINISHED_AT = Instant.parse("2027-03-01T13:00:00Z");
    private static final CorrectedTime FINISH_1300 = CorrectedTime.of(FINISHED_AT, FINISHED_AT, FINISHED_AT);

    @Autowired
    private RouteRunRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    @DatabaseOwner
    private JdbcClient owner;

    @Autowired
    private RecordedEvents events;

    // --- round trips (ADR-127 decision 1)

    /** The states a run stores and loads in, reached through commands. */
    enum Scenario {
        STARTED,
        CONFIRMED,
        FINISHED_WITH_PENDING_STOPS,
        FINISHED_WITH_NONE_PENDING
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenARun_WhenAddedAndLoadedInAnotherTransaction_ThenEveryPartComesBackAsAdded(Scenario scenario) {
        int n = 1 + scenario.ordinal();
        RouteRun added = transactions.inTransaction(() -> {
            RouteRun run = switch (scenario) {
                case STARTED -> started(n);
                case CONFIRMED -> {
                    RouteRun confirmed = started(n);
                    confirmed.acknowledge(DRIVER_A, DRIVER_A, REV_2, REV_2);
                    yield confirmed;
                }
                case FINISHED_WITH_PENDING_STOPS -> finished(n, List.of(stop(n, 1), stop(n, 2)));
                case FINISHED_WITH_NONE_PENDING -> finished(n, List.of());
            };
            repository.add(run, dateOf(n));
            return run;
        });

        RouteRun loaded =
                transactions.inTransaction(() -> repository.get(route(n)).orElseThrow());

        // Written out by hand, since the added instance keeps whatever its columns kept.
        RunStatus status = switch (scenario) {
            case STARTED, CONFIRMED -> new RunStatus.Started();
            case FINISHED_WITH_PENDING_STOPS ->
                new RunStatus.Finished(FINISHED_AT, DRIVER_A, List.of(stop(n, 1), stop(n, 2)));
            case FINISHED_WITH_NONE_PENDING -> new RunStatus.Finished(FINISHED_AT, DRIVER_A, List.of());
        };
        Optional<RevisionNo> confirmed = scenario == Scenario.CONFIRMED ? Optional.of(REV_2) : Optional.empty();
        assertThat(RouteRunDescription.of(loaded))
                .isEqualTo(new RouteRunDescription(
                        route(n), DRIVER_A, RunStartCause.DRIVER_STARTED, T_0800, status, confirmed));
        assertThat(RouteRunDescription.of(loaded)).isEqualTo(RouteRunDescription.of(added));
        assertThat(loaded.route()).isEqualTo(route(n));
        assertThat(loaded.isFinished()).isEqualTo(added.isFinished());
        assertThat(loaded.confirmed()).isEqualTo(added.confirmed());
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenANewRun_WhenAdded_ThenItsRouteStartedIsCommittedAndItsDateKeptBesideIt() {
        int n = 5;
        RouteRun run = started(n);

        transactions.inTransaction(() -> repository.add(run, dateOf(n)));

        assertThat(events.committed())
                .filteredOn(event -> event.eventId().aggregateId().equals(route(n).value()))
                .singleElement()
                .isInstanceOfSatisfying(
                        RouteStarted.class,
                        started -> assertThat(started.eventId().version()).isEqualTo(Version.of(0)));
        assertThat(run.pendingEvents()).isEmpty();
        assertThat(storedDate(n)).isEqualTo(dateOf(n).value());
    }

    @Test
    void GivenARouteThatHasNotStarted_WhenItsRunIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(route(6)))).isEmpty();
    }

    // --- versions and conflicts

    @Test
    void GivenAddedRun_WhenAChangeIsConfirmedAndThenTheRouteFinished_ThenTheVersionIsZeroThenOneThenTwo() {
        int n = 7;
        transactions.inTransaction(() -> repository.add(started(n), dateOf(n)));
        long added = storedVersion(n);

        transactions.inTransaction(() -> {
            RouteRun run = repository.get(route(n)).orElseThrow();
            run.acknowledge(DRIVER_A, DRIVER_A, REV_1, REV_2);
            repository.save(run);
        });
        long confirmed = storedVersion(n);
        transactions.inTransaction(() -> {
            RouteRun run = repository.get(route(n)).orElseThrow();
            run.finish(byA(FINISH_1300), DRIVER_A, DRIVER_A, List.of(stop(n, 1)));
            repository.save(run);
        });

        assertThat(List.of(added, confirmed, storedVersion(n))).containsExactly(0L, 1L, 2L);
    }

    @Test
    void GivenTwoTransactionsOnOneRun_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        int n = 8;
        transactions.inTransaction(() -> repository.add(started(n), dateOf(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    RouteRun mine = repository.get(route(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        RouteRun theirs = repository.get(route(n)).orElseThrow();
                        theirs.acknowledge(DRIVER_A, DRIVER_A, REV_2, REV_2);
                        repository.save(theirs);
                    });
                    mine.finish(byA(FINISH_1300), DRIVER_A, DRIVER_A, List.of());
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("RouteRun");
                    assertThat(stale.id()).isEqualTo(route(n).value());
                });

        RouteRun stored =
                transactions.inTransaction(() -> repository.get(route(n)).orElseThrow());
        assertThat(storedVersion(n)).isEqualTo(1L);
        assertThat(stored.isFinished()).isFalse();
        assertThat(stored.confirmed()).contains(REV_2);
    }

    @Test
    void GivenTwoFirstReportsAtOnce_WhenTheLaterRunIsAdded_ThenStaleVersionAndTheFirstStands() {
        int n = 9;

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    RouteRun mine = started(n, RunStartCause.DRIVER_STARTED);
                    transactions.inANewTransaction(
                            () -> repository.add(started(n, RunStartCause.FIRST_REPORT), dateOf(n)));
                    repository.add(mine, dateOf(n));
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("RouteRun");
                    assertThat(stale.id()).isEqualTo(route(n).value());
                });

        assertThat(transactions
                        .inTransaction(() -> repository.get(route(n)).orElseThrow())
                        .cause())
                .isEqualTo(RunStartCause.FIRST_REPORT);
        assertThat(storedDate(n)).isEqualTo(dateOf(n).value());
    }

    // --- the runs of a date (FR-244)

    @Test
    void GivenRunsOnADateAndAnother_WhenTheDatesRunsAreRead_ThenEachOfItsRunsComesOnceAndWhole() {
        int n = 10;
        transactions.inTransaction(() -> {
            repository.add(started(n), dateOf(n));
            repository.add(finished(100 + n, List.of(stop(100 + n, 1))), dateOf(n));
            repository.add(started(200 + n), dateOf(100 + n));
        });

        List<RouteRun> runs = transactions.inTransaction(() -> repository.startedOn(dateOf(n)));

        assertThat(runs).extracting(RouteRun::route).containsExactlyInAnyOrder(route(n), route(100 + n));
        assertThat(runs)
                .filteredOn(RouteRun::isFinished)
                .singleElement()
                .satisfies(finished -> assertThat(finished.status())
                        .isEqualTo(new RunStatus.Finished(FINISHED_AT, DRIVER_A, List.of(stop(100 + n, 1)))));
    }

    @Test
    void GivenADateWithNoRun_WhenItsRunsAreRead_ThenThereAreNone() {
        assertThat(transactions.inTransaction(() -> repository.startedOn(dateOf(11))))
                .isEmpty();
    }

    @Test
    void GivenAStoredRun_WhenItIsAddedAgain_ThenAssertionErrorAndItsDateStays() {
        int n = 16;
        transactions.inTransaction(() -> repository.add(started(n), dateOf(n)));

        assertThatThrownBy(() -> transactions.inTransaction(
                        () -> repository.add(repository.get(route(n)).orElseThrow(), dateOf(100 + n))))
                .isInstanceOf(AssertionError.class)
                .hasMessage("a route run is added once, and its date kept with it");
        assertThat(storedDate(n)).isEqualTo(dateOf(n).value());
    }

    /** A save would store the run with no date, so {@code startedOn} would never find it (ADR-127 decision 12). */
    @Test
    void GivenARunNeverStored_WhenItIsSavedInsteadOfAdded_ThenAssertionErrorAndNothingIsStored() {
        int n = 17;

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.save(started(n))))
                .isInstanceOf(AssertionError.class)
                .hasMessage("a new route run is added with its date, never saved");
        assertThat(transactions.inTransaction(() -> repository.get(route(n)))).isEmpty();
    }

    // --- the load check (ADR-127 decision 1)

    @Test
    void GivenAFinishedRunNamingAPendingStopTwice_WhenLoaded_ThenStoredStateInvalid() {
        int n = 12;
        transactions.inTransaction(() -> repository.add(finished(n, List.of(stop(n, 1), stop(n, 2))), dateOf(n)));

        owner.sql("UPDATE liveops.route_run_pending_stop SET stop = ? WHERE route_run_id = ? AND position = 1")
                .params(stop(n, 1).value(), route(n).value())
                .update();

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(route(n))))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("RouteRun");
                    assertThat(invalid.detail()).isEqualTo("a finished run names each pending stop once (FR-329)");
                });
    }

    @Test
    void GivenAFinishedRunWhosePendingStopsStartPastTheFirstPosition_WhenLoaded_ThenStoredStateInvalid() {
        int n = 18;
        transactions.inTransaction(() -> repository.add(finished(n, List.of(stop(n, 1), stop(n, 2))), dateOf(n)));

        owner.sql("UPDATE liveops.route_run_pending_stop SET position = position + 1 WHERE route_run_id = ?")
                .param(route(n).value())
                .update();

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(route(n))))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("RouteRun");
                    assertThat(invalid.detail()).isEqualTo(StoredLists.GAP);
                });
    }

    @Test
    void GivenAStartedRunWithAPendingStop_WhenLoaded_ThenStoredStateInvalid() {
        int n = 15;
        transactions.inTransaction(() -> repository.add(started(n), dateOf(n)));

        owner.sql("INSERT INTO liveops.route_run_pending_stop (route_run_id, position, stop) VALUES (?, 0, ?)")
                .params(route(n).value(), stop(n, 1).value())
                .update();

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(route(n))))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("RouteRun");
                    assertThat(invalid.detail()).isEqualTo("only a finished run has pending stops (FR-329)");
                });
    }

    @Test
    void GivenAStoredRun_WhenItIsMarkedFinishedWithNoFinish_ThenTheDatabaseRefusesTheWrite() {
        int n = 13;
        transactions.inTransaction(() -> repository.add(started(n), dateOf(n)));

        assertThatThrownBy(() -> owner.sql("UPDATE liveops.route_run SET status = 'FINISHED' WHERE id = ?")
                        .param(route(n).value())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(route(n)))).isPresent();
    }

    @Test
    void GivenAStoredConfirmedRevisionBelowOne_WhenTheRunIsLoaded_ThenStoredStateInvalidNamesTheRevision() {
        int n = 14;
        RouteRun confirmed = started(n);
        confirmed.acknowledge(DRIVER_A, DRIVER_A, REV_1, REV_1);
        transactions.inTransaction(() -> repository.add(confirmed, dateOf(n)));

        owner.sql("UPDATE liveops.route_run SET confirmed = 0 WHERE id = ?")
                .param(route(n).value())
                .update();

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(route(n))))
                .satisfies(thrown -> assertThat(storedStateInvalidIn(thrown)).satisfies(invalid -> {
                    assertThat(invalid.subject()).isEqualTo(RevisionNo.class.getSimpleName());
                    assertThat(invalid.detail()).isEqualTo("a stored value is refused with INVALID_REVISION");
                }));
    }

    // --- states, reached through the domain's commands

    private static RouteRun started(int r) {
        return started(r, RunStartCause.DRIVER_STARTED);
    }

    private static RouteRun started(int r, RunStartCause cause) {
        return RouteRun.start(route(r), byA(ONLINE), DRIVER_A, DRIVER_A, cause, NOW);
    }

    private static RouteRun finished(int r, List<StopId> pending) {
        RouteRun run = started(r);
        run.finish(byA(FINISH_1300), DRIVER_A, DRIVER_A, pending);
        return run;
    }

    // --- keys, dates and stored values

    private static RouteId route(int r) {
        return RouteId.of(new UUID(KEY_PREFIX, r));
    }

    private static StopId stop(int r, int s) {
        return StopId.of(new UUID(KEY_PREFIX + 1, 100L * r + s));
    }

    private static DeliveryDate dateOf(int n) {
        return DeliveryDate.of(FIRST_DAY.plusDays(2L * n));
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

    private long storedVersion(int r) {
        return jdbc.sql("SELECT version FROM liveops.route_run WHERE id = ?")
                .param(route(r).value())
                .query(Long.class)
                .single();
    }

    private LocalDate storedDate(int r) {
        return jdbc.sql("SELECT delivery_date FROM liveops.route_run WHERE id = ?")
                .param(route(r).value())
                .query(LocalDate.class)
                .single();
    }
}
