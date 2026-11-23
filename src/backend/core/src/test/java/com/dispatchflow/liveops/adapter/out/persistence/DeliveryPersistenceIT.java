package com.dispatchflow.liveops.adapter.out.persistence;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ABSENT;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DISPATCHER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_B;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_C;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.FIRST;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.MOVED_TO_A_WITH_CONTACT;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NO_HOLD;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ONLINE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ON_ROUTE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ON_ROUTE_B;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ON_ROUTE_C;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.PHOTO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_B;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_C;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SECOND;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SERVING;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SIGNATURE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.TYPO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0800;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0900;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byA;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byB;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.TestPersistenceConfiguration.RecordedEvents;
import com.dispatchflow.Transactions;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.domain.CancellationKind;
import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.liveops.domain.DriverReport;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.delivery.ArrivalRecord;
import com.dispatchflow.liveops.domain.delivery.Delivery;
import com.dispatchflow.liveops.domain.delivery.DeliveryDescription;
import com.dispatchflow.liveops.domain.delivery.DeliveryStatus;
import com.dispatchflow.liveops.domain.delivery.HoldCheck;
import com.dispatchflow.liveops.domain.delivery.LaterAttempt;
import com.dispatchflow.liveops.domain.delivery.LeaveAtDoor;
import com.dispatchflow.liveops.domain.delivery.StopWhereabouts;
import com.dispatchflow.liveops.domain.outcome.DispatcherOutcome;
import com.dispatchflow.liveops.domain.outcome.FailureReason;
import com.dispatchflow.liveops.domain.outcome.HeldReport;
import com.dispatchflow.liveops.domain.outcome.HeldState;
import com.dispatchflow.liveops.domain.outcome.Origin;
import com.dispatchflow.liveops.domain.outcome.Outcome;
import com.dispatchflow.liveops.domain.outcome.OutcomeRecord;
import com.dispatchflow.liveops.domain.proof.Proof;
import com.dispatchflow.liveops.events.OutcomeRecorded;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link DeliveryRepository} against a real PostgreSQL (ADR-126, ADR-127): what a delivery stores
 * and loads as, the version, the lookups by stops and by attempt, the stops whose personal data is
 * due for erasure, and the stored states the load check refuses.
 *
 * <p>The database is shared and never cleaned, and the retention lookup reads every delivery. So
 * test {@code n} keys its own stops ({@code stop(n)}, {@code stop(100 + n)}, …), orders and held
 * reports, and the retention test checks only its own stops. Held report {@code k} of stop {@code s}
 * is {@code held(s, k)}. Each getter is read outside the transaction that loaded the delivery, which
 * shows the delivery was loaded whole.
 */
@IntegrationTest
class DeliveryPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_6c64_6c01L;

    /** 90 Israel days after {@code NOW}, across the spring clock change: its cutoffs are {@code NOW}. */
    private static final Instant NINETY_DAYS_AFTER_NOW = Instant.parse("2027-05-30T09:00:00Z");

    @Autowired
    private DeliveryRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordedEvents events;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // --- round trips (ADR-127 decision 1)

    /** The states a delivery stores and loads in, reached through commands. */
    enum Scenario {
        FRESH,
        ARRIVED,
        DELIVERED,
        DELIVERED_AT_THE_DOOR_WITH_A_NOTE,
        FAILED,
        FAILED_FOR_ANOTHER_REASON,
        HELD,
        HELD_AFTER_ARRIVAL,
        HELD_OVER_A_CANCELLATION,
        PENDING_AFTER_REFUSAL,
        FAILED_THEN_CORRECTED,
        CONFIRMED_OVER_ANOTHER,
        OVERTAKEN_ON_THE_CURRENT_ROUTE,
        ON_BEHALF,
        SERVED_AFTER_CANCELLATION,
        ERASED,
        DELIVERED_THEN_CORRECTED_TO_THE_DOOR,
        OTHER_REASON_CORRECTED_TO_ANOTHER_TEXT,
        REASONS_ERASED
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenADelivery_WhenAddedAndLoadedInAnotherTransaction_ThenEveryPartComesBackAsAdded(Scenario scenario) {
        int n = 50 + scenario.ordinal();
        Delivery added = transactions.inTransaction(() -> {
            Delivery delivery = switch (scenario) {
                case FRESH -> fresh(n);
                case ARRIVED -> arrived(n);
                case DELIVERED -> delivered(n);
                case DELIVERED_AT_THE_DOOR_WITH_A_NOTE -> deliveredAtTheDoor(n);
                case FAILED -> failed(n);
                case FAILED_FOR_ANOTHER_REASON -> failedForAnotherReason(n, ONLINE);
                case HELD -> held(n);
                case HELD_AFTER_ARRIVAL -> {
                    Delivery arrived = arrived(n);
                    holdLate(arrived, n, 1);
                    yield arrived;
                }
                case HELD_OVER_A_CANCELLATION -> heldOverACancellation(n);
                case PENDING_AFTER_REFUSAL -> {
                    Delivery refused = held(n);
                    refused.refuseHeld(held(n, 1), DISPATCHER, LATER);
                    yield refused;
                }
                case FAILED_THEN_CORRECTED -> failedThenCorrected(n);
                case CONFIRMED_OVER_ANOTHER -> confirmedOverAnother(n);
                case OVERTAKEN_ON_THE_CURRENT_ROUTE -> overtaken(n);
                case ON_BEHALF -> onBehalf(n, NOW);
                case SERVED_AFTER_CANCELLATION -> servedAfterCancellation(n);
                case ERASED -> {
                    Delivery erased = confirmedOverAnother(n);
                    erased.eraseExpired(NINETY_DAYS_AFTER_NOW);
                    yield erased;
                }
                case DELIVERED_THEN_CORRECTED_TO_THE_DOOR -> {
                    Delivery corrected = delivered(n);
                    corrected.correct(
                            new DispatcherOutcome.Delivered(true),
                            TYPO,
                            Optional.empty(),
                            DISPATCHER,
                            new LaterAttempt.None(),
                            LeaveAtDoor.ALLOWED,
                            LATER);
                    yield corrected;
                }
                case OTHER_REASON_CORRECTED_TO_ANOTHER_TEXT -> correctedToAnotherText(n);
                case REASONS_ERASED -> {
                    Delivery erased = correctedToAnotherText(n);
                    erased.eraseExpired(NINETY_DAYS_AFTER_NOW.plus(Duration.ofHours(1)));
                    yield erased;
                }
            };
            repository.add(delivery);
            return delivery;
        });

        Delivery loaded =
                transactions.inTransaction(() -> repository.get(stop(n)).orElseThrow());

        assertThat(DeliveryDescription.of(loaded)).isEqualTo(DeliveryDescription.of(added));
        assertThat(loaded.stop()).isEqualTo(stop(n));
        assertThat(loaded.status()).isEqualTo(added.status());
        assertThat(loaded.effectiveOutcome()).isEqualTo(added.effectiveOutcome());
        assertThat(loaded.outcomeTime()).isEqualTo(added.outcomeTime());
        assertThat(loaded.hasArrival()).isEqualTo(added.hasArrival());
        assertThat(loaded.arrivedAt()).isEqualTo(added.arrivedAt());
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    /**
     * Deliveries built by commands, each with the records it must load as, written out by hand:
     * comparing with the added instance alone would miss a part both lose on the way to the columns.
     * Together they hold every stored part at least once.
     */
    static Stream<Arguments> recordsWrittenOut() {
        Outcome signed = Outcome.Delivered.withProof(SIGNATURE, false);
        Origin.Reported reportedOnline = new Origin.Reported(ROUTE_A, REV_1, ONLINE, Optional.empty());
        return Stream.of(
                Arguments.of(
                        "arrived, then left at the door with a photo and a note",
                        17,
                        (Builder) s -> {
                            Delivery delivery = arrived(s);
                            delivery.deliver(
                                    byA(ONLINE),
                                    Optional.of(PHOTO),
                                    true,
                                    LeaveAtDoor.ALLOWED,
                                    ON_ROUTE,
                                    NO_HOLD,
                                    held(s, 1),
                                    NOW);
                            return delivery;
                        },
                        Optional.of(new ArrivalRecord(DRIVER_A, ROUTE_A, ONLINE)),
                        List.of(new OutcomeRecord(
                                Outcome.Delivered.withProof(PHOTO, true), DRIVER_A, T_0800, reportedOnline)),
                        List.of()),
                Arguments.of(
                        "failed for another reason, then corrected to a stated time",
                        18,
                        (Builder) s -> {
                            Delivery delivery = failedForAnotherReason(s, ONLINE);
                            delivery.correct(
                                    new DispatcherOutcome.Delivered(false),
                                    TYPO,
                                    Optional.of(T_0900),
                                    DISPATCHER,
                                    new LaterAttempt.None(),
                                    LeaveAtDoor.NOT_ALLOWED,
                                    NOW);
                            return delivery;
                        },
                        Optional.empty(),
                        List.of(
                                new OutcomeRecord(
                                        new Outcome.Failed(FailureReason.other("gate locked")),
                                        DRIVER_A,
                                        T_0800,
                                        reportedOnline),
                                new OutcomeRecord(
                                        Outcome.Delivered.withoutProof(false),
                                        DISPATCHER,
                                        T_0900,
                                        new Origin.Correction(TYPO, NOW))),
                        List.of()),
                Arguments.of(
                        "served after its order's cancellation",
                        19,
                        (Builder) DeliveryPersistenceIT::servedAfterCancellation,
                        Optional.empty(),
                        List.of(new OutcomeRecord(
                                signed,
                                DRIVER_A,
                                T_0800,
                                new Origin.Reported(
                                        ROUTE_A, REV_1, ONLINE, Optional.of(CancellationKind.ORDER_CANCELLED)))),
                        List.of()),
                Arguments.of(
                        "held over its order's cancellation",
                        20,
                        (Builder) DeliveryPersistenceIT::heldOverACancellation,
                        Optional.empty(),
                        List.of(),
                        List.of(new HeldReport(
                                held(20, 1),
                                signed,
                                DRIVER_A,
                                ROUTE_A,
                                REV_1,
                                LATE,
                                Optional.of(CancellationKind.ORDER_CANCELLED),
                                new HeldState.Open()))),
                Arguments.of(
                        "one held report confirmed over another",
                        21,
                        (Builder) DeliveryPersistenceIT::confirmedOverAnother,
                        Optional.empty(),
                        List.of(new OutcomeRecord(signed, DRIVER_B, T_0800, new Origin.ConfirmedHeld(held(21, 1)))),
                        List.of(
                                new HeldReport(
                                        held(21, 1),
                                        signed,
                                        DRIVER_B,
                                        ROUTE_B,
                                        REV_1,
                                        LATE,
                                        Optional.empty(),
                                        new HeldState.Confirmed(DISPATCHER, LATER)),
                                new HeldReport(
                                        held(21, 2),
                                        new Outcome.Failed(ABSENT),
                                        DRIVER_C,
                                        ROUTE_C,
                                        REV_1,
                                        LATE,
                                        Optional.empty(),
                                        new HeldState.Overtaken(LATER)))),
                Arguments.of(
                        "held, then refused by a dispatcher",
                        22,
                        (Builder) s -> {
                            Delivery delivery = held(s);
                            delivery.refuseHeld(held(s, 1), DISPATCHER, LATER);
                            return delivery;
                        },
                        Optional.empty(),
                        List.of(),
                        List.of(new HeldReport(
                                held(22, 1),
                                signed,
                                DRIVER_B,
                                ROUTE_B,
                                REV_1,
                                LATE,
                                Optional.empty(),
                                new HeldState.RefusedByDispatcher(DISPATCHER, LATER)))),
                Arguments.of(
                        "recorded on the driver's behalf",
                        23,
                        (Builder) s -> onBehalf(s, NOW),
                        Optional.empty(),
                        List.of(new OutcomeRecord(
                                new Outcome.Failed(FailureReason.other("no one home")),
                                DISPATCHER,
                                NOW,
                                new Origin.OnBehalf(ROUTE_A))),
                        List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordsWrittenOut")
    void GivenADeliveryBuiltByCommands_WhenAddedAndLoaded_ThenItHoldsTheRecordsWrittenOut(
            String state,
            int n,
            Builder builder,
            Optional<ArrivalRecord> arrival,
            List<OutcomeRecord> outcomes,
            List<HeldReport> heldReports) {
        transactions.inTransaction(() -> repository.add(builder.build(n)));

        Delivery loaded =
                transactions.inTransaction(() -> repository.get(stop(n)).orElseThrow());

        assertThat(DeliveryDescription.of(loaded))
                .isEqualTo(new DeliveryDescription(stop(n), order(n), FIRST, arrival, outcomes, heldReports));
        assertThat(loaded.version()).isEqualTo(Version.of(0));
    }

    /** Builds a delivery for stop {@code s} through its commands. */
    @FunctionalInterface
    interface Builder {
        Delivery build(int s);
    }

    @Test
    void GivenADeliveredReport_WhenItsDeliveryIsAdded_ThenItsOutcomeRecordedIsCommittedAtVersionZero() {
        int n = 29;

        transactions.inTransaction(() -> repository.add(delivered(n)));

        assertThat(events.committed())
                .filteredOn(event -> event.eventId().aggregateId().equals(stop(n).value()))
                .singleElement()
                .isInstanceOfSatisfying(
                        OutcomeRecorded.class,
                        recorded -> assertThat(recorded.eventId().version()).isEqualTo(Version.of(0)));
    }

    /**
     * PostgreSQL keeps an instant to the microsecond, and the driver rounds each one on its own, so
     * a corrected time computed from nanoseconds would not be the one its stored parts give back.
     */
    @Test
    void GivenAReportWhoseDeviceTimesHaveNanoseconds_WhenItsDeliveryIsAddedAndLoaded_ThenItLoadsWithItsCorrectedTime() {
        int n = 42;
        CorrectedTime time = CorrectedTime.of(
                T_0800.minusSeconds(5).plusNanos(600), T_0800.minusSeconds(4).plusNanos(400), T_0800);

        transactions.inTransaction(() -> repository.add(failedForAnotherReason(n, time)));
        Delivery loaded =
                transactions.inTransaction(() -> repository.get(stop(n))).orElseThrow();

        assertThat(loaded.outcomes().getFirst().factTime()).isEqualTo(time.corrected());
    }

    @Test
    void GivenNoFactForAStop_WhenItsDeliveryIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(stop(30)))).isEmpty();
    }

    // --- versions and conflicts

    @Test
    void GivenAHeldReport_WhenItIsRefusedAndThenTheStopDelivered_ThenTheVersionIsZeroThenOneThenTwo() {
        int n = 31;
        transactions.inTransaction(() -> repository.add(held(n)));
        long added = storedVersion(n);

        // Only the held report's row changes, so the save forces the version up.
        transactions.inTransaction(() -> {
            Delivery delivery = repository.get(stop(n)).orElseThrow();
            delivery.refuseHeld(held(n, 1), DISPATCHER, LATER);
            repository.save(delivery);
        });
        long refused = storedVersion(n);
        transactions.inTransaction(() -> {
            Delivery delivery = repository.get(stop(n)).orElseThrow();
            deliverOnRouteA(delivery, n);
            repository.save(delivery);
        });

        assertThat(List.of(added, refused, storedVersion(n))).containsExactly(0L, 1L, 2L);
    }

    @Test
    void GivenTwoTransactionsOnOneStop_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        int n = 32;
        transactions.inTransaction(() -> repository.add(arrived(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Delivery mine = repository.get(stop(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        Delivery theirs = repository.get(stop(n)).orElseThrow();
                        theirs.fail(byA(ONLINE), ABSENT, ON_ROUTE, NO_HOLD, held(n, 1), NOW);
                        repository.save(theirs);
                    });
                    deliverOnRouteA(mine, n);
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Delivery");
                    assertThat(stale.id()).isEqualTo(stop(n).value());
                });

        assertThat(storedVersion(n)).isEqualTo(1L);
        assertThat(transactions
                        .inTransaction(() -> repository.get(stop(n)).orElseThrow())
                        .status())
                .isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void GivenAnotherWriterStoredTheStopsDeliveryFirst_WhenItIsAdded_ThenStaleVersionAndTheFirstStands() {
        int n = 33;

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Delivery mine = arrived(n);
                    transactions.inANewTransaction(() -> repository.add(failed(n)));
                    repository.add(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Delivery");
                    assertThat(stale.id()).isEqualTo(stop(n).value());
                });

        Delivery stored =
                transactions.inTransaction(() -> repository.get(stop(n)).orElseThrow());
        assertThat(stored.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(stored.hasArrival()).isFalse();
    }

    // --- the lookups

    @Test
    void GivenSomeStopsWithDeliveriesAndOneWithout_WhenTheirDeliveriesAreRead_ThenEachStoredOneComesOnceAndWhole() {
        int n = 34;
        transactions.inTransaction(() -> {
            repository.add(held(n));
            repository.add(failedThenCorrected(100 + n));
        });

        List<Delivery> found =
                transactions.inTransaction(() -> repository.ofStops(List.of(stop(n), stop(100 + n), stop(200 + n))));

        assertThat(found).extracting(Delivery::stop).containsExactlyInAnyOrder(stop(n), stop(100 + n));
        Delivery heldOne = found.stream()
                .filter(delivery -> delivery.stop().equals(stop(n)))
                .findFirst()
                .orElseThrow();
        Delivery corrected = found.stream()
                .filter(delivery -> delivery.stop().equals(stop(100 + n)))
                .findFirst()
                .orElseThrow();
        assertThat(heldOne.heldReports()).hasSize(1);
        assertThat(corrected.outcomes()).hasSize(2);
    }

    /**
     * The load check reads each delivery's held reports while the deliveries load, so without batch
     * fetching every delivery read would cost a statement of its own (ADR-126, loading whole).
     */
    @Test
    void GivenTwiceAsManyDeliveries_WhenTheirStopsAreRead_ThenTheSameNumberOfStatementsRuns() {
        List<StopId> two = List.of(stop(43), stop(44));
        List<StopId> four = List.of(stop(45), stop(46), stop(47), stop(48));
        transactions.inTransaction(() -> IntStream.rangeClosed(43, 48).forEach(s -> repository.add(held(s))));

        assertThat(statementsToRead(four)).isEqualTo(statementsToRead(two));
    }

    @Test
    void GivenNoStops_WhenTheirDeliveriesAreRead_ThenThereAreNone() {
        assertThat(transactions.inTransaction(() -> repository.ofStops(List.of())))
                .isEmpty();
    }

    @Test
    void GivenADeliveryOfAnOrdersFirstAttempt_WhenEachAttemptIsAskedFor_ThenOnlyTheFirstHasOne() {
        int n = 35;
        transactions.inTransaction(() -> repository.add(held(n)));

        Optional<Delivery> first = transactions.inTransaction(() -> repository.ofAttempt(order(n), FIRST));
        Optional<Delivery> second = transactions.inTransaction(() -> repository.ofAttempt(order(n), SECOND));
        Optional<Delivery> otherOrder = transactions.inTransaction(() -> repository.ofAttempt(order(100 + n), FIRST));

        assertThat(first).map(Delivery::stop).contains(stop(n));
        assertThat(first.orElseThrow().heldReports()).hasSize(1);
        assertThat(second).isEmpty();
        assertThat(otherOrder).isEmpty();
    }

    // --- retention (NFR-31, NFR-184, ADR-107)

    @Test
    void GivenDeliveriesWithPersonalDataOfEveryOrigin_WhenTheExpiredOnesAreListed_ThenExactlyThoseDueAreListed() {
        int n = 36;
        Instant aMinuteLate = NOW.plus(Duration.ofMinutes(1));
        CorrectedTime receivedAMinuteLate = CorrectedTime.of(aMinuteLate, aMinuteLate, aMinuteLate);
        transactions.inTransaction(() -> {
            repository.add(delivered(n));
            repository.add(failedForAnotherReason(100 + n, ONLINE));
            repository.add(failedThenCorrected(200 + n));
            repository.add(confirmedOverAnother(300 + n));
            repository.add(onBehalf(400 + n, NOW));
            repository.add(failedForAnotherReason(500 + n, receivedAMinuteLate));
            repository.add(onBehalf(600 + n, aMinuteLate));
            repository.add(failed(700 + n));
            Delivery erased = delivered(800 + n);
            erased.eraseExpired(NINETY_DAYS_AFTER_NOW);
            repository.add(erased);
        });
        Set<StopId> due = Set.of(stop(n), stop(100 + n), stop(200 + n), stop(300 + n), stop(400 + n));
        Set<StopId> notDue = Set.of(stop(500 + n), stop(600 + n), stop(700 + n), stop(800 + n));

        List<StopId> listed = transactions.inTransaction(() -> withExpiredDataInAUtcSession(NINETY_DAYS_AFTER_NOW));
        List<StopId> aSecondEarlier =
                transactions.inTransaction(() -> withExpiredDataInAUtcSession(NINETY_DAYS_AFTER_NOW.minusSeconds(1)));

        assertThat(listed).containsAll(due).doesNotContainAnyElementsOf(notDue).doesNotHaveDuplicates();
        // The correction, the held reports and the record on behalf were made at NOW, a second past
        // that cutoff; the first delivery's report arrived two hours before it.
        assertThat(aSecondEarlier)
                .doesNotContain(stop(200 + n), stop(300 + n), stop(400 + n))
                .contains(stop(n), stop(100 + n));
    }

    @Test
    void GivenAListedDelivery_WhenItIsErasedAndSaved_ThenItIsNoLongerListed() {
        int n = 37;
        transactions.inTransaction(() -> repository.add(confirmedOverAnother(n)));
        assertThat(transactions.inTransaction(() -> repository.withExpiredData(NINETY_DAYS_AFTER_NOW)))
                .contains(stop(n));

        transactions.inTransaction(() -> {
            Delivery delivery = repository.get(stop(n)).orElseThrow();
            delivery.eraseExpired(NINETY_DAYS_AFTER_NOW);
            repository.save(delivery);
        });

        assertThat(transactions.inTransaction(() -> repository.withExpiredData(NINETY_DAYS_AFTER_NOW)))
                .doesNotContain(stop(n));
        Outcome.Delivered stated = (Outcome.Delivered) transactions
                .inTransaction(() -> repository.get(stop(n)).orElseThrow())
                .effectiveOutcome()
                .orElseThrow();
        assertThat(stated.proof()).map(Proof::isErased).contains(true);
    }

    // --- the load check (ADR-127 decision 1): one case per invariant at rest

    static Stream<Arguments> damagedRows() {
        String original = "the first outcome is the original and every later one a correction (FR-333)";
        String confirmedOnce = "a confirmed held report is exactly the original outcome's (BR-132)";
        String confirmedAsHeld = "a confirmed outcome is the one its held report states (BR-132)";
        String proof = "a driver's delivered has a proof, and a photo when left at the door (FR-367, FR-368)";
        String dispatcher = "'" + DISPATCHER.value() + "'";
        return Stream.of(
                Arguments.of(
                        "a correction with no original",
                        1_001,
                        Base.FAILED_THEN_CORRECTED,
                        (Damage) rows -> {
                            rows.deleteOutcome(0);
                            rows.outcome(1, "position = 0");
                        },
                        original),
                Arguments.of("two originals", 1_002, Base.FAILED, (Damage) rows -> rows.copyOutcome(0, 1), original),
                Arguments.of(
                        "a correction that changes nothing",
                        1_003,
                        Base.FAILED_THEN_CORRECTED,
                        (Damage) rows -> rows.outcome(
                                1, "outcome = 'FAILED', left_at_door = NULL, failure_reason = 'CUSTOMER_ABSENT'"),
                        "a correction changes the outcome it corrects (FR-333)"),
                Arguments.of(
                        "an open held report beside an outcome",
                        1_004,
                        Base.OVERTAKEN,
                        (Damage) rows -> rows.held(1, "state = 'OPEN', state_at = NULL"),
                        "no report stays held once the stop has an outcome (BR-134, BR-18)"),
                Arguments.of(
                        "a confirmed held report the outcome does not cite",
                        1_006,
                        Base.CONFIRMED,
                        (Damage) rows -> rows.outcome(
                                0, "confirmed_report = '" + held(1_006, 9).value() + "'"),
                        confirmedOnce),
                Arguments.of(
                        "a confirmed outcome that differs from its held report",
                        1_007,
                        Base.CONFIRMED,
                        (Damage) rows -> rows.outcome(
                                0,
                                "outcome = 'FAILED', left_at_door = NULL, proof_kind = NULL, proof_file = NULL,"
                                        + " failure_reason = 'CUSTOMER_ABSENT'"),
                        confirmedAsHeld),
                Arguments.of(
                        "two confirmed held reports",
                        1_008,
                        Base.CONFIRMED,
                        (Damage) rows -> rows.held(2, "state = 'CONFIRMED', state_by = " + dispatcher),
                        confirmedOnce),
                Arguments.of(
                        "a confirmed outcome by another account",
                        1_009,
                        Base.CONFIRMED,
                        (Damage) rows -> rows.outcome(0, "recorded_by = " + dispatcher),
                        confirmedAsHeld),
                Arguments.of(
                        "a confirmed outcome at another time",
                        1_010,
                        Base.CONFIRMED,
                        (Damage) rows -> rows.outcome(0, "fact_time = fact_time + interval '1 hour'"),
                        confirmedAsHeld),
                Arguments.of(
                        "a reported outcome that does not count from its report",
                        1_011,
                        Base.FAILED,
                        (Damage) rows -> rows.outcome(0, "fact_time = fact_time + interval '1 hour'"),
                        "a reported outcome counts from its report's corrected time (BR-114)"),
                Arguments.of(
                        "a driver's delivered with no proof",
                        1_012,
                        Base.DELIVERED,
                        (Damage) rows -> rows.outcome(0, "proof_kind = NULL, proof_file = NULL"),
                        proof),
                Arguments.of(
                        "a held door delivery with a signature",
                        1_013,
                        Base.HELD,
                        (Damage) rows -> rows.held(1, "left_at_door = true"),
                        proof),
                Arguments.of(
                        "an overtaken held report and no outcome",
                        1_014,
                        Base.HELD,
                        (Damage) rows -> rows.held(1, "state = 'OVERTAKEN', state_at = received_at"),
                        "a held report is overtaken only by an outcome (BR-134, BR-18)"),
                Arguments.of(
                        "outcomes whose positions start past the first",
                        1_021,
                        Base.FAILED_THEN_CORRECTED,
                        (Damage) Rows::shiftOutcomePositions,
                        StoredLists.GAP));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredDeliveryBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Base base, Damage damage, String detail) {
        transactions.inTransaction(() -> repository.add(base.build(n)));

        damage.apply(new Rows(jdbc, n));

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(stop(n))))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("Delivery");
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("two held reports with one ID", 1_005, Base.CONFIRMED, (Damage)
                        rows -> rows.held(2, "id = '" + held(1_005, 1).value() + "'")),
                Arguments.of("a proof note without its file", 1_015, Base.DELIVERED_AT_THE_DOOR, (Damage)
                        rows -> rows.outcome(0, "proof_file = NULL")),
                Arguments.of("a report without its receipt time", 1_016, Base.FAILED, (Damage)
                        rows -> rows.outcome(0, "report_received_at = NULL")),
                Arguments.of("an arrival without its route", 1_017, Base.ARRIVED, (Damage)
                        rows -> rows.delivery("arrival_route = NULL")),
                Arguments.of("a refused held report that names no dispatcher", 1_018, Base.CONFIRMED, (Damage)
                        rows -> rows.held(1, "state = 'REFUSED_BY_DISPATCHER', state_by = NULL")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredDelivery_WhenARowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Base base, Damage damage) {
        transactions.inTransaction(() -> repository.add(base.build(n)));
        Rows rows = new Rows(jdbc, n);

        assertThatThrownBy(() -> damage.apply(rows)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(stop(n)))).isPresent();
    }

    static Stream<Arguments> damagedValues() {
        return Stream.of(
                Arguments.of(
                        "a revision below 1",
                        1_019,
                        (Damage) rows -> rows.outcome(0, "revision = 0"),
                        "RevisionNo",
                        "a stored value is refused with INVALID_REVISION"),
                Arguments.of(
                        "a device time past the year 9999",
                        1_020,
                        (Damage) rows -> rows.outcome(0, "report_device_time = '10000-01-01 00:00:00+00'"),
                        "CorrectedTime",
                        "a stored value is refused with DEVICE_TIME_OUT_OF_RANGE"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedValues")
    void GivenAStoredValueItsTypeRefuses_WhenTheDeliveryIsLoaded_ThenStoredStateInvalidNamesTheValue(
            String broken, int n, Damage damage, String subject, String detail) {
        transactions.inTransaction(() -> repository.add(Base.FAILED.build(n)));

        damage.apply(new Rows(jdbc, n));

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(stop(n))))
                .satisfies(thrown -> assertThat(storedStateInvalidIn(thrown)).satisfies(invalid -> {
                    assertThat(invalid.subject()).isEqualTo(subject);
                    assertThat(invalid.detail()).isEqualTo(detail);
                }));
    }

    /** The valid states the damage cases start from. */
    enum Base {
        ARRIVED,
        DELIVERED,
        DELIVERED_AT_THE_DOOR,
        FAILED,
        FAILED_THEN_CORRECTED,
        HELD,
        OVERTAKEN,
        CONFIRMED;

        Delivery build(int s) {
            return switch (this) {
                case ARRIVED -> arrived(s);
                case DELIVERED -> delivered(s);
                case DELIVERED_AT_THE_DOOR -> deliveredAtTheDoor(s);
                case FAILED -> failed(s);
                case FAILED_THEN_CORRECTED -> failedThenCorrected(s);
                case HELD -> held(s);
                case OVERTAKEN -> overtaken(s);
                case CONFIRMED -> confirmedOverAnother(s);
            };
        }
    }

    /** One damage done with SQL to a stored delivery. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /** The SQL the damage cases run on stop {@code s}'s delivery; assignments are the test's own SQL. */
    record Rows(JdbcClient jdbc, int s) {

        /** Sets columns of the delivery's own row. */
        void delivery(String assignments) {
            jdbc.sql("UPDATE liveops.delivery SET " + assignments + " WHERE id = ?")
                    .param(stop(s).value())
                    .update();
        }

        /** Sets columns of the outcome record at a position. */
        void outcome(int position, String assignments) {
            jdbc.sql("UPDATE liveops.delivery_outcome SET " + assignments + " WHERE delivery_id = ? AND position = ?")
                    .params(stop(s).value(), position)
                    .update();
        }

        /** Sets columns of held report {@code k}. */
        void held(int k, String assignments) {
            jdbc.sql("UPDATE liveops.held_report SET " + assignments + " WHERE id = ?")
                    .param(DeliveryPersistenceIT.held(s, k).value())
                    .update();
        }

        /** Moves every outcome record one position on, so no record holds the first. */
        void shiftOutcomePositions() {
            jdbc.sql("UPDATE liveops.delivery_outcome SET position = position + 1 WHERE delivery_id = ?")
                    .param(stop(s).value())
                    .update();
        }

        /** Deletes the outcome record at a position. */
        void deleteOutcome(int position) {
            jdbc.sql("DELETE FROM liveops.delivery_outcome WHERE delivery_id = ? AND position = ?")
                    .params(stop(s).value(), position)
                    .update();
        }

        /** Adds a copy of the outcome record at one position at another. */
        void copyOutcome(int from, int to) {
            jdbc.sql("""
                            INSERT INTO liveops.delivery_outcome
                                (delivery_id, position, outcome, left_at_door, proof_kind, proof_file, proof_note,
                                 failure_reason, failure_text, recorded_by, fact_time, origin, route, revision,
                                 report_device_time, report_device_clock_at_send, report_received_at, served_after,
                                 confirmed_report, correction_reason, corrected_at)
                            SELECT delivery_id, ?, outcome, left_at_door, proof_kind, proof_file, proof_note,
                                   failure_reason, failure_text, recorded_by, fact_time, origin, route, revision,
                                   report_device_time, report_device_clock_at_send, report_received_at, served_after,
                                   confirmed_report, correction_reason, corrected_at
                            FROM liveops.delivery_outcome WHERE delivery_id = ? AND position = ?
                            """).params(to, stop(s).value(), from).update();
        }
    }

    // --- states, reached through the domain's commands

    private static Delivery fresh(int s) {
        return Delivery.firstFact(stop(s), order(s), FIRST);
    }

    private static Delivery arrived(int s) {
        Delivery delivery = fresh(s);
        delivery.arrive(byA(ONLINE), ON_ROUTE, SERVING);
        return delivery;
    }

    /** The JDBC statements one transaction prepares to read the stops' deliveries. */
    private long statementsToRead(List<StopId> stops) {
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        statistics.setStatisticsEnabled(true);
        try {
            transactions.inTransaction(() -> repository.ofStops(stops));
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private static Delivery delivered(int s) {
        Delivery delivery = fresh(s);
        deliverOnRouteA(delivery, s);
        return delivery;
    }

    /** Left at the door with a photo and its note, by an order that allows it (FR-366, FR-368). */
    private static Delivery deliveredAtTheDoor(int s) {
        Delivery delivery = fresh(s);
        delivery.deliver(
                byA(ONLINE), Optional.of(PHOTO), true, LeaveAtDoor.ALLOWED, ON_ROUTE, NO_HOLD, held(s, 1), NOW);
        return delivery;
    }

    private static Delivery failed(int s) {
        Delivery delivery = fresh(s);
        delivery.fail(byA(ONLINE), ABSENT, ON_ROUTE, NO_HOLD, held(s, 1), NOW);
        return delivery;
    }

    private static Delivery failedForAnotherReason(int s, CorrectedTime time) {
        Delivery delivery = fresh(s);
        delivery.fail(byA(time), FailureReason.other("gate locked"), ON_ROUTE, NO_HOLD, held(s, 1), NOW);
        return delivery;
    }

    /** Failed for another reason, then corrected to another text of it (FR-333). */
    private static Delivery correctedToAnotherText(int s) {
        Delivery delivery = failedForAnotherReason(s, ONLINE);
        delivery.correct(
                new DispatcherOutcome.Failed(FailureReason.other("dog in the yard")),
                TYPO,
                Optional.empty(),
                DISPATCHER,
                new LaterAttempt.None(),
                LeaveAtDoor.NOT_ALLOWED,
                NOW);
        return delivery;
    }

    /** Driver B's late delivered for a stop now on A's route, held as {@code held(s, 1)}. */
    private static Delivery held(int s) {
        Delivery delivery = fresh(s);
        holdLate(delivery, s, 1);
        return delivery;
    }

    /** Driver A's late delivered, made before the order was cancelled at 09:00, held (BR-132). */
    private static Delivery heldOverACancellation(int s) {
        Delivery delivery = fresh(s);
        HoldCheck cancelledAtNine =
                HoldCheck.of(new StopWhereabouts.Cancelled(CancellationKind.ORDER_CANCELLED, T_0900), List.of(T_0900));
        delivery.deliver(
                byA(LATE),
                Optional.of(SIGNATURE),
                false,
                LeaveAtDoor.NOT_ALLOWED,
                ON_ROUTE,
                cancelledAtNine,
                held(s, 1),
                NOW);
        return delivery;
    }

    private static Delivery failedThenCorrected(int s) {
        Delivery delivery = failed(s);
        delivery.correct(
                new DispatcherOutcome.Delivered(false),
                TYPO,
                Optional.empty(),
                DISPATCHER,
                new LaterAttempt.None(),
                LeaveAtDoor.NOT_ALLOWED,
                NOW);
        return delivery;
    }

    /** Held reports from B and from C, then B's confirmed, which overtakes C's (BR-132). */
    private static Delivery confirmedOverAnother(int s) {
        Delivery delivery = held(s);
        delivery.fail(byC(LATE), ABSENT, ON_ROUTE_C, MOVED_TO_A_WITH_CONTACT, held(s, 2), NOW);
        delivery.confirmHeld(held(s, 1), DISPATCHER, LATER);
        return delivery;
    }

    /** B's held report, then A's delivered on the stop's current route, which overtakes it (BR-134). */
    private static Delivery overtaken(int s) {
        Delivery delivery = held(s);
        deliverOnRouteA(delivery, s);
        return delivery;
    }

    private static Delivery onBehalf(int s, Instant at) {
        Delivery delivery = fresh(s);
        delivery.recordOnBehalf(
                DISPATCHER,
                ROUTE_A,
                new DispatcherOutcome.Failed(FailureReason.other("no one home")),
                LeaveAtDoor.NOT_ALLOWED,
                at);
        return delivery;
    }

    /** Driver A's delivered, sent online before the order was cancelled at 09:00 (BR-130). */
    private static Delivery servedAfterCancellation(int s) {
        Delivery delivery = fresh(s);
        HoldCheck cancelledAtNine =
                HoldCheck.of(new StopWhereabouts.Cancelled(CancellationKind.ORDER_CANCELLED, T_0900), List.of());
        delivery.deliver(
                byA(ONLINE),
                Optional.of(SIGNATURE),
                false,
                LeaveAtDoor.NOT_ALLOWED,
                ON_ROUTE,
                cancelledAtNine,
                held(s, 1),
                NOW);
        return delivery;
    }

    private static void holdLate(Delivery delivery, int s, int k) {
        DriverReport late = byB(LATE);
        delivery.deliver(
                late,
                Optional.of(SIGNATURE),
                false,
                LeaveAtDoor.NOT_ALLOWED,
                ON_ROUTE_B,
                MOVED_TO_A_WITH_CONTACT,
                held(s, k),
                NOW);
    }

    private static void deliverOnRouteA(Delivery delivery, int s) {
        delivery.deliver(
                byA(ONLINE),
                Optional.of(SIGNATURE),
                false,
                LeaveAtDoor.NOT_ALLOWED,
                ON_ROUTE,
                NO_HOLD,
                held(s, 8),
                NOW);
    }

    // --- keys and stored values

    private static StopId stop(int s) {
        return StopId.of(new UUID(KEY_PREFIX, s));
    }

    private static OrderId order(int s) {
        return OrderId.of(new UUID(KEY_PREFIX + 1, s));
    }

    private static HeldReportId held(int s, int k) {
        return HeldReportId.of(new UUID(KEY_PREFIX + 2, 100L * s + k));
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

    /**
     * Lists the stops with expired data in a session whose zone is UTC, not the JVM's, so the result
     * shows the query depends on no session zone.
     */
    private List<StopId> withExpiredDataInAUtcSession(Instant now) {
        jdbc.sql("SET LOCAL TimeZone = 'UTC'").update();
        return repository.withExpiredData(now);
    }

    private long storedVersion(int s) {
        return jdbc.sql("SELECT version FROM liveops.delivery WHERE id = ?")
                .param(stop(s).value())
                .query(Long.class)
                .single();
    }
}
