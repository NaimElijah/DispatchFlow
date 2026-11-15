package com.dispatchflow.liveops.domain.delivery;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ABSENT;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DATE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DISPATCHER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER_STOP;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.PHOTO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SECOND;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SIGNATURE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.TYPO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0800;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.aDelivery;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byA;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byB;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byC;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.reload;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.uuid;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.liveops.domain.CancellationKind;
import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.liveops.domain.DriverReport;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.delivery.DeliverySnapshot.HeldReport;
import com.dispatchflow.liveops.domain.delivery.DeliverySnapshot.HeldState;
import com.dispatchflow.liveops.domain.delivery.DeliverySnapshot.OutcomeRecord;
import com.dispatchflow.liveops.events.OutcomeKind;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Seeded sequences of commands on deliveries (DL §14), with random facts weighted towards late,
 * moved and in-contact reports so held reports occur. A fresh delivery starts every {@link
 * #FRESH_EVERY} steps, and after an outcome followed by {@link #REFUSALS_BEFORE_FRESH} refusals.
 * After every step the invariants are recomputed from the snapshot in test code: the first outcome
 * never changes once recorded (BR-18), no report stays held beside an outcome, at most one held
 * report is confirmed, the state follows its precedence, and {@code restore} takes the snapshot
 * back. An accepted step raises exactly the events its states before and after call for. A refused
 * step leaves the snapshot and the events as they were. Each seed must reach a held
 * report, and have one confirmation and one refusal accepted. The message names the seed and the
 * step, so a failure replays.
 */
class DeliverySequenceTest {

    private static final int STEPS = 200;
    private static final int FRESH_EVERY = 15;
    private static final int REFUSALS_BEFORE_FRESH = 3;
    private static final List<String> COMMANDS = List.of(
            "arrive",
            "deliver",
            "deliver",
            "deliver",
            "fail",
            "fail",
            "fail",
            "onBehalf",
            "correct",
            "confirm",
            "confirm",
            "refuse",
            "refuse",
            "erase");

    private static final class Counts {
        private boolean reachedHeld;
        private int confirmed;
        private int refused;
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 7, 42, 2027, 31_337, 99_991})
    void GivenASeededSequenceOfCommands_WhenEachStepRuns_ThenTheInvariantsHoldAfterEveryStep(long seed) {
        Random random = new Random(seed);
        Counts counts = new Counts();
        Delivery delivery = aDelivery().fresh();
        Optional<OutcomeRecord> firstOutcome = Optional.empty();
        int refusalsAfterOutcome = 0;
        for (int step = 0; step < STEPS; step++) {
            if (step % FRESH_EVERY == 0 || refusalsAfterOutcome >= REFUSALS_BEFORE_FRESH) {
                delivery = aDelivery().fresh();
                firstOutcome = Optional.empty();
                refusalsAfterOutcome = 0;
            }
            String command = COMMANDS.get(random.nextInt(COMMANDS.size()));
            String where = "seed " + seed + ", step " + step + ", " + command;
            DeliverySnapshot before = delivery.snapshot();
            List<DomainEvent> eventsBefore = delivery.pendingEvents();
            try {
                boolean putBackWithoutOutcome = run(command, delivery, random, step, counts);
                assertThat(delivery.pendingEvents().stream()
                                .map(event -> event.eventId().eventType())
                                .toList())
                        .as(where + ": events")
                        .isEqualTo(expectedEvents(before, delivery.snapshot(), putBackWithoutOutcome));
                delivery = reload(delivery);
            } catch (RuleViolation refused) {
                assertThat(delivery.snapshot()).as(where).isEqualTo(before);
                assertThat(delivery.pendingEvents()).as(where).isEqualTo(eventsBefore);
                if (!before.outcomes().isEmpty()) {
                    refusalsAfterOutcome++;
                }
            } catch (RuntimeException | AssertionError unexpected) {
                throw new AssertionError(where + ": " + unexpected, unexpected);
            }
            DeliverySnapshot s = delivery.snapshot();
            if (firstOutcome.isPresent()) {
                assertThat(s.outcomes().get(0).outcome().kind())
                        .as(where + ": the first outcome stays")
                        .isEqualTo(firstOutcome.get().outcome().kind());
                assertThat(s.outcomes().get(0).by())
                        .as(where)
                        .isEqualTo(firstOutcome.get().by());
            } else if (!s.outcomes().isEmpty()) {
                firstOutcome = Optional.of(s.outcomes().get(0));
            }
            counts.reachedHeld |= delivery.status() == DeliveryStatus.HELD;
            checkInvariants(delivery, s, where);
        }
        assertThat(counts.reachedHeld)
                .as("seed " + seed + " reached a held report")
                .isTrue();
        assertThat(counts.confirmed)
                .as("seed " + seed + " confirmed a held report")
                .isPositive();
        assertThat(counts.refused).as("seed " + seed + " refused a held report").isPositive();
    }

    /**
     * The event types an accepted step raises, derived from the states before and after it: {@code
     * OutcomeRecorded} exactly when the stop gets its first outcome, {@code PutBackSuperseded}
     * exactly when a failed stop is corrected to delivered while the put-back's stop has no outcome
     * (FR-404), and nothing otherwise.
     */
    private static List<String> expectedEvents(
            DeliverySnapshot before, DeliverySnapshot after, boolean putBackWithoutOutcome) {
        if (before.outcomes().isEmpty() && !after.outcomes().isEmpty()) {
            return List.of("OutcomeRecorded");
        }
        boolean corrected = after.outcomes().size() == before.outcomes().size() + 1;
        if (corrected
                && putBackWithoutOutcome
                && effectiveKind(before) == OutcomeKind.FAILED
                && effectiveKind(after) == OutcomeKind.DELIVERED) {
            return List.of("PutBackSuperseded");
        }
        return List.of();
    }

    private static OutcomeKind effectiveKind(DeliverySnapshot state) {
        return state.outcomes().get(state.outcomes().size() - 1).outcome().kind();
    }

    /**
     * Runs one command with random facts.
     *
     * @return whether the command was a correction told that the put-back's stop has no outcome
     */
    private static boolean run(String command, Delivery delivery, Random random, int step, Counts counts) {
        Instant now = NOW.plus(Duration.ofMinutes(step));
        int entitled = random.nextInt(10);
        int driver = random.nextInt(3);
        DriverReport report;
        CorrectedTime time =
                random.nextInt(10) < 7 ? CorrectedTime.of(T_0800, now, now) : CorrectedTime.of(T_0800, T_0800, T_0800);
        report = switch (driver) {
            case 0 -> byA(time);
            case 1 -> byB(time);
            default -> byC(time);
        };
        Entitlement entitlement = switch (entitled) {
            case 0 -> new Entitlement.NotOnRoute();
            case 1 -> new Entitlement.OnRoute(REV_1, DISPATCHER);
            default -> new Entitlement.OnRoute(REV_1, report.driver());
        };
        int place = random.nextInt(10);
        StopWhereabouts where;
        if (place < 5) {
            where = new StopWhereabouts.OnAnotherRoute(RouteId.of(uuid(900)));
        } else if (place < 7) {
            where = new StopWhereabouts.OnServingRoute();
        } else if (place < 8) {
            where = new StopWhereabouts.Dropped();
        } else {
            where = new StopWhereabouts.Cancelled(
                    CancellationKind.ORDER_CANCELLED, T_0800.plus(Duration.ofMinutes(random.nextInt(60) - 1)));
        }
        List<Instant> contacts = random.nextInt(10) < 7 ? List.of(T_0800.plus(Duration.ofMinutes(30))) : List.of();
        HoldCheck check = HoldCheck.of(where, contacts);
        HeldReportId next = HeldReportId.of(uuid(1_000 + step));
        switch (command) {
            case "arrive" -> delivery.arrive(report, entitlement, where);
            case "deliver" ->
                delivery.deliver(
                        report,
                        Optional.of(random.nextBoolean() ? SIGNATURE : PHOTO),
                        false,
                        LeaveAtDoor.ALLOWED,
                        entitlement,
                        check,
                        next,
                        now);
            case "fail" -> delivery.fail(report, ABSENT, entitlement, check, next, now);
            case "onBehalf" ->
                delivery.recordOnBehalf(
                        DISPATCHER,
                        ROUTE_A,
                        random.nextBoolean()
                                ? new DispatcherOutcome.Delivered(false)
                                : new DispatcherOutcome.Failed(ABSENT),
                        LeaveAtDoor.ALLOWED,
                        now);
            case "correct" -> {
                DispatcherOutcome corrected = random.nextBoolean()
                        ? new DispatcherOutcome.Delivered(false)
                        : new DispatcherOutcome.Failed(FailureReason.of(FailureReasonKind.DAMAGED));
                LaterAttempt later = switch (random.nextInt(3)) {
                    case 0 -> new LaterAttempt.None();
                    case 1 -> new LaterAttempt.WithOutcome(LATER_STOP, SECOND, OutcomeKind.FAILED);
                    default -> new LaterAttempt.WithoutOutcome(LATER_STOP, SECOND, DATE);
                };
                delivery.correct(corrected, TYPO, Optional.empty(), DISPATCHER, later, LeaveAtDoor.ALLOWED, now);
                return later instanceof LaterAttempt.WithoutOutcome;
            }
            case "confirm" -> {
                delivery.confirmHeld(pickHeld(delivery, random, next), DISPATCHER, now);
                counts.confirmed++;
            }
            case "refuse" -> {
                delivery.refuseHeld(pickHeld(delivery, random, next), DISPATCHER, now);
                counts.refused++;
            }
            case "erase" -> delivery.eraseExpired(now.plus(Duration.ofDays(random.nextInt(2) * 120L)));
            default -> throw new AssertionError("unknown command " + command);
        }
        return false;
    }

    /** Mostly a held report the delivery has, sometimes one it doesn't, so both answers occur. */
    private static HeldReportId pickHeld(Delivery delivery, Random random, HeldReportId unknown) {
        List<HeldReportId> ids =
                delivery.snapshot().heldReports().stream().map(HeldReport::id).toList();
        return ids.isEmpty() || random.nextInt(5) == 0 ? unknown : ids.get(random.nextInt(ids.size()));
    }

    private static void checkInvariants(Delivery delivery, DeliverySnapshot s, String where) {
        boolean anyOpen = s.heldReports().stream().anyMatch(HeldReport::isOpen);
        long confirmed = s.heldReports().stream()
                .filter(report -> report.state() instanceof HeldState.Confirmed)
                .count();
        assertThat(anyOpen && !s.outcomes().isEmpty())
                .as(where + ": held beside an outcome")
                .isFalse();
        assertThat(confirmed).as(where + ": confirmed reports").isLessThanOrEqualTo(1);
        DeliveryStatus expected;
        if (!s.outcomes().isEmpty()) {
            expected = s.outcomes().get(s.outcomes().size() - 1).outcome().kind() == OutcomeKind.DELIVERED
                    ? DeliveryStatus.DELIVERED
                    : DeliveryStatus.FAILED;
        } else if (anyOpen) {
            expected = DeliveryStatus.HELD;
        } else {
            expected = s.arrival().isPresent() ? DeliveryStatus.ARRIVED : DeliveryStatus.PENDING;
        }
        assertThat(delivery.status()).as(where).isEqualTo(expected);
        assertThat(Delivery.restore(s).snapshot()).as(where).isEqualTo(s);
    }
}
