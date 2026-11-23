package com.dispatchflow.planning.adapter.out.persistence.plan;

import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.stopId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.TestPersistenceConfiguration.RecordedEvents;
import com.dispatchflow.Transactions;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.planning.domain.PlanningError.PlanAlreadyExists;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.plan.Edit;
import com.dispatchflow.planning.domain.plan.LaneCapacity;
import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.planning.domain.plan.PlanDescription;
import com.dispatchflow.planning.domain.plan.PlanningFixtures;
import com.dispatchflow.planning.domain.plan.PlanningFixtures.PlanBuilder;
import com.dispatchflow.planning.domain.plan.PlansOnDate;
import com.dispatchflow.planning.domain.plan.RemovalCause;
import com.dispatchflow.planning.domain.plan.RouteLock;
import com.dispatchflow.planning.domain.plan.Seed;
import com.dispatchflow.planning.domain.plan.SolveJob;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.PlanPublished;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainAssertions;
import com.dispatchflow.shared.FreshIds;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link PlanRepository} against a real PostgreSQL (ADR-126, ADR-127): what a plan stores and loads
 * as in every state its builder reaches, a stop that changes routes, the version, the revisions a
 * save appends, the journal at its limit, one uncancelled plan per date (BR-34), the reads by date
 * and by route check, and the stored states the load check refuses.
 *
 * <p>The database is shared and never cleaned, so test {@code n} keys its plan, routes and stops on
 * a prefix of its own and opens its plan for a date of its own. The builder drives every command
 * through the repository: each command loads the plan in a transaction, changes it and saves it,
 * and the plan is loaded again in another. The read of the plans with a route not yet checked sees
 * every plan, so its test checks only its own.
 */
@IntegrationTest
class PlanPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_706c_0000L;
    private static final LocalDate FIRST_DATE = LocalDate.of(2031, 1, 1);
    private static final Instant SOLVED_AGAIN = Instant.parse("2027-06-15T05:00:00Z");
    private static final String DRAFT_HOLDS_NOTHING =
            "a draft plan has no routes, drops, excluded vehicles, solve, violations or journal";
    private static final String FACTS_AFTER_PUBLISHING =
            "withdrawals, late outcomes and confirmed returns belong to a published plan";
    private static final String JOURNAL_BEFORE_PUBLISHING = "only a ready or solving plan keeps a journal";
    private static final String PUBLISHED_WAS_SOLVED = "a published plan has an applied solve";
    private static final String ROUTES_OF_THE_PLAN = "violations and edits name only routes of the plan";
    private static final String JOURNAL_TOPS = "the last journal entries name stops where they left them";
    private static final String REFUSED = "a stored value is refused with ";
    private static final String NO_APPLIED_SOLVE =
            "applied_job_id = NULL, applied_requested_at = NULL, applied_at = NULL";
    private static final String FIRST_ROUTE = "(SELECT id FROM planning.route WHERE plan_id = :plan AND position = 0)";
    private static final String SECOND_ROUTE = "(SELECT id FROM planning.route WHERE plan_id = :plan AND position = 1)";
    private static final String FIRST_STOP =
            "(SELECT id FROM planning.planned_stop WHERE route_id = " + FIRST_ROUTE + " AND position = 0)";

    @Autowired
    private PlanRepository repository;

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

    // --- round trips (ADR-127 decision 1)

    /**
     * The ten states the domain's restore tests round-tripped, the two whose last edit the damage
     * cases need, and a redo after a save, each reached through the builder's commands.
     */
    enum State {
        DRAFT(PlanBuilder::draft),
        SOLVING(PlanBuilder::solving),
        READY(PlanBuilder::ready),
        READY_WITH_PINS_A_LOCK_A_VIOLATION_AND_BOTH_JOURNALS(PlanBuilder::readyEdited),
        SOLVING_AGAIN(PlanBuilder::solvingAgain),
        PUBLISHED(PlanBuilder::published),
        PUBLISHED_AT_REVISION_4_WITH_A_WITHDRAWN_ROUTE_A_LATE_OUTCOME_AND_A_CONFIRMED_RETURN(
                PlanBuilder::publishedWithFacts),
        COMPLETED(PlanBuilder::completed),
        CANCELLED(PlanBuilder::cancelled),
        ABANDONED(PlanBuilder::abandoned),
        /** Ready, its last edit moving O1 from the front of V1's route to V2's, after O3. */
        LAST_MOVED_O1_TO_V2((KeyedBuilder keyed) -> keyed.edited(keyed.builder().ready(), (plan, today) -> {
            StopId o1 = stopId(plan, O1);
            RouteId v2 = routeId(plan, V2);
            plan.moveStop(o1, v2, 1, evaluationOf(plan).moving(o1, v2, 1).build(), today);
        })),
        /** Ready, its last edit taking O3, V2's only stop, off its route. */
        LAST_TOOK_OFF_O3((KeyedBuilder keyed) -> keyed.edited(keyed.builder().ready(), (plan, today) -> {
            StopId o3 = stopId(plan, O3);
            plan.takeOffRoute(o3, evaluationOf(plan).removing(o3).build(), today);
        })),
        /** The edited ready plan, saved, then its undone pin redone. */
        REDONE_AFTER_A_SAVE((KeyedBuilder keyed) -> keyed.edited(
                keyed.builder().readyEdited(),
                (plan, today) -> plan.redo(
                        evaluationOf(plan)
                                .withViolation(violation("BR-03", routeId(plan, V2)))
                                .build(),
                        today)));

        private final Function<KeyedBuilder, Plan> build;

        State(Function<KeyedBuilder, Plan> build) {
            this.build = build;
        }

        State(BuilderStep step) {
            this.build = keyed -> step.of(keyed.builder());
        }

        Plan build(KeyedBuilder builder) {
            return build.apply(builder);
        }
    }

    @ParameterizedTest
    @EnumSource(State.class)
    void GivenAPlanInEachBuilderState_WhenSavedAndLoadedInAnotherTransaction_ThenItsWholeStateIsAsItsLastCommandLeftIt(
            State state) {
        KeyedBuilder builder = builder(1 + state.ordinal());

        Plan loaded = state.build(builder);

        builder.saves().hasStored(loaded);
        assertThat(loaded.pendingRevision()).isEmpty();
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenNoPlanWithAnId_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(PlanId.of(new UUID(keys(19), 1)))))
                .isEmpty();
    }

    // --- a stop that changes routes keeps its row and its ID (ADR-126 decision 11)

    @Test
    void GivenAStopMovedFromTheFirstRouteToTheSecond_WhenSavedAndLoaded_ThenItIsTheSameStopOnTheSecondRoute() {
        KeyedBuilder builder = builder(20);
        Plan ready = builder.builder().ready();
        StopId o1 = stopId(ready, O1);

        Plan moved = builder.edited(ready, (plan, today) -> {
            RouteId v2 = routeId(plan, V2);
            plan.moveStop(o1, v2, 0, evaluationOf(plan).moving(o1, v2, 0).build(), today);
        });

        builder.saves().hasStored(moved);
        assertThat(stopsOn(moved, V2)).containsExactly(o1, stopId(ready, O3));
        assertThat(storedStops(moved.id()))
                .containsExactlyInAnyOrder(stopId(ready, O1), stopId(ready, O2), stopId(ready, O3));
    }

    @Test
    void GivenAStopMovedFromTheSecondRouteToTheFirst_WhenSavedAndLoaded_ThenItIsTheSameStopOnTheFirstRoute() {
        KeyedBuilder builder = builder(21);
        Plan ready = builder.builder().ready();
        StopId o3 = stopId(ready, O3);

        Plan moved = builder.edited(ready, (plan, today) -> {
            RouteId v1 = routeId(plan, V1);
            plan.moveStop(o3, v1, 0, evaluationOf(plan).moving(o3, v1, 0).build(), today);
        });

        builder.saves().hasStored(moved);
        assertThat(stopsOn(moved, V1)).containsExactly(o3, stopId(ready, O1), stopId(ready, O2));
        assertThat(stopsOn(moved, V2)).isEmpty();
        assertThat(storedStops(moved.id()))
                .containsExactlyInAnyOrder(stopId(ready, O1), stopId(ready, O2), stopId(ready, O3));
    }

    @Test
    void GivenASolveThatLeavesTheSecondRouteOut_WhenApplied_ThenItsStopIsTheSameStopOnTheFirstRoute() {
        KeyedBuilder builder = builder(22);
        Plan ready = builder.builder().ready();

        Plan solved = solvedAgain(builder, ready, solved(V1, D1, O3, O1, O2));

        builder.saves().hasStored(solved);
        assertThat(stopsOn(solved, V1)).containsExactly(stopId(ready, O3), stopId(ready, O1), stopId(ready, O2));
        assertThat(storedRoutes(solved.id())).containsExactly(routeId(ready, V1));
        assertThat(storedStops(solved.id())).hasSize(3);
    }

    @Test
    void GivenASolveThatLeavesTheFirstRouteOut_WhenApplied_ThenItsStopsAreTheSameStopsOnTheSecondRoute() {
        KeyedBuilder builder = builder(23);
        Plan ready = builder.builder().ready();

        Plan solved = solvedAgain(builder, ready, solved(V2, D2, O1, O2, O3));

        builder.saves().hasStored(solved);
        assertThat(stopsOn(solved, V2)).containsExactly(stopId(ready, O1), stopId(ready, O2), stopId(ready, O3));
        assertThat(storedRoutes(solved.id())).containsExactly(routeId(ready, V2));
        assertThat(storedStops(solved.id())).hasSize(3);
    }

    // --- versions and conflicts

    @Test
    void GivenAnAddedPlan_WhenEachCommandIsSavedInItsOwnTransaction_ThenTheVersionMovesByOneAndTheEventCarriesIt() {
        KeyedBuilder builder = builder(30);

        Plan published = builder.builder().published();

        assertThat(builder.saves().storedVersions()).containsExactly(0L, 1L, 2L, 3L);
        assertThat(published.version()).isEqualTo(Version.of(3));
        assertThat(events.committed())
                .filteredOn(PlanPublished.class::isInstance)
                .singleElement()
                .satisfies(event -> assertThat(event.eventId().version()).isEqualTo(Version.of(3)));
    }

    @Test
    void GivenTwoTransactionsOnOnePlan_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        KeyedBuilder builder = builder(31);
        Plan ready = builder.builder().ready();
        PlanId id = ready.id();
        RouteId v1 = routeId(ready, V1);
        RouteId v2 = routeId(ready, V2);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Plan mine = repository.get(id).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        Plan theirs = repository.get(id).orElseThrow();
                        theirs.lockRoute(v1, RouteLock.WHOLE_ROUTE, builder.today());
                        repository.save(theirs);
                    });
                    mine.lockRoute(v2, RouteLock.STOP_SEQUENCE, builder.today());
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Plan");
                    assertThat(stale.id()).isEqualTo(id.value());
                });

        assertThat(storedVersion(id)).isEqualTo(3L);
        assertThat(PlanDescription.of(loaded(id)).undo())
                .containsExactly(new Edit.Locked(v1, Optional.empty(), RouteLock.WHOLE_ROUTE));
    }

    @Test
    void
            GivenTwoTransactionsRevisingOnePublishedPlan_WhenTheLaterOneSaves_ThenStaleVersionAndOnlyTheFirstRevisionIsStored() {
        KeyedBuilder builder = builder(72);
        Plan published = builder.builder().published();
        PlanId id = published.id();

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Plan mine = repository.get(id).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        Plan theirs = repository.get(id).orElseThrow();
                        theirs.removeStopOf(O1, Attempt.FIRST, RemovalCause.ORDER_CANCELLED, SOLVED_AGAIN);
                        repository.save(theirs);
                    });
                    mine.removeStopOf(O2, Attempt.FIRST, RemovalCause.ORDER_CANCELLED, SOLVED_AGAIN);
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(
                        StaleVersion.class, stale -> assertThat(stale.id()).isEqualTo(id.value()));

        assertThat(storedRevisions(id)).containsExactly("1 PUBLISHED by someone", "2 ORDER_CANCELLED");
    }

    // --- revisions (ADR-059)

    @Test
    void GivenAPlan_WhenPublishedRevisedAndChangedWithoutARevision_ThenEachSaveAppendsOnlyTheRevisionItsChangeMade() {
        KeyedBuilder builder = builder(40);
        Plan published = builder.builder().published();
        assertThat(storedRevisions(published.id())).containsExactly("1 PUBLISHED by someone");
        assertThat(revisionRoutes(published.id(), 1)).isEqualTo(routesInPlanOrder(published));
        assertThat(revisionStops(published.id(), 1)).isEqualTo(stopsInPlanOrder(published));

        Plan revised = builder.saves()
                .changed(
                        published,
                        plan -> plan.removeStopOf(O1, Attempt.FIRST, RemovalCause.ORDER_CANCELLED, SOLVED_AGAIN));
        Plan checked = builder.saves()
                .changed(
                        revised,
                        plan -> plan.recordRouteCheck(evaluationOf(plan).build()));
        Plan saved = builder.saves().changed(checked, plan -> {});

        assertThat(storedRevisions(saved.id())).containsExactly("1 PUBLISHED by someone", "2 ORDER_CANCELLED");
        assertThat(revisionRoutes(saved.id(), 2)).isEqualTo(routesInPlanOrder(revised));
        assertThat(revisionStops(saved.id(), 2))
                .isEqualTo(stopsInPlanOrder(revised))
                .hasSize(2);
        assertThat(builder.saves().storedVersions()).containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void GivenAnAcceptedReoptimization_WhenSaved_ThenItsRevisionCitesTheProposal() {
        KeyedBuilder builder = builder(41);

        Plan withFacts = builder.builder().publishedWithFacts();

        assertThat(storedRevisions(withFacts.id()))
                .containsExactly(
                        "1 PUBLISHED by someone",
                        "2 REOPTIMIZATION_ACCEPTED by someone citing a proposal",
                        "3 LATE_OUTCOME",
                        "4 HANDED_OVER by someone");
        assertThat(revisionStops(withFacts.id(), 4)).isEqualTo(stopsInPlanOrder(withFacts));
    }

    // --- the journal at its limit (FR-145, ADR-112)

    @Test
    void GivenAReadyPlanPinnedPastTheJournalsLimit_WhenSavedUndoneAndLoaded_ThenTheJournalsHoldTheLastHundredEdits() {
        KeyedBuilder builder = builder(50);
        Plan ready = builder.builder().ready();

        Plan pinned = builder.edited(
                ready,
                (plan, today) -> IntStream.range(0, 101)
                        .forEach(n -> plan.pin(
                                O1,
                                new PinTarget.ToVehicle(n % 2 == 0 ? V1 : V2),
                                evaluationOf(plan).build(),
                                today)));

        builder.saves().hasStored(pinned);
        assertThat(PlanDescription.of(pinned).undo()).hasSize(100);
        assertThat(PlanDescription.of(pinned).undo().getFirst())
                .isEqualTo(new Edit.Pinned(O1, Optional.of(new PinTarget.ToVehicle(V1)), new PinTarget.ToVehicle(V2)));

        Plan undone = builder.edited(
                pinned, (plan, today) -> plan.undo(evaluationOf(plan).build(), today));

        builder.saves().hasStored(undone);
        assertThat(journalRows(undone.id())).isEqualTo(Map.of("plan_undo", 99, "plan_redo", 1));
    }

    // --- one uncancelled plan per date (BR-34)

    @Test
    void GivenADatesPlan_WhenAnotherIsAddedPastTheRead_ThenPlanAlreadyExistsNamesTheFirst() {
        Plan first = builder(60).builder().draft();
        Plan second = opened(61, date(60));

        DomainAssertions.assertRefused(
                () -> transactions.inTransaction(() -> repository.add(second)),
                new PlanAlreadyExists(date(60), first.id()));

        assertThat(transactions.inTransaction(() -> repository.get(second.id())))
                .isEmpty();
    }

    @Test
    void GivenATransactionAddingADatesPlan_WhenAnotherTriesTheDatesLock_ThenItIsHeldUntilCommitAndOtherDatesAreFree() {
        Plan first = opened(66, date(66));

        transactions.inTransaction(() -> {
            repository.add(first);
            transactions.inANewTransaction(() -> {
                assertThat(triedDateLock(date(66))).isFalse();
                assertThat(triedDateLock(date(67))).isTrue();
            });
        });

        assertThat(transactions.inTransaction(() -> triedDateLock(date(66)))).isTrue();
    }

    @Test
    void GivenADatesCancelledPlan_WhenAnotherIsAdded_ThenItIsStoredAsTheDatesUncancelledPlan() {
        builder(62).builder().cancelled();
        Plan reopened = opened(63, date(62));

        transactions.inTransaction(() -> repository.add(reopened));

        assertThat(transactions.inTransaction(() -> repository.plansOn(date(62))))
                .isEqualTo(PlansOnDate.of(reopened.id()));
        assertThat(transactions.inTransaction(
                        () -> repository.uncancelledOn(date(62)).map(Plan::id)))
                .contains(reopened.id());
    }

    @Test
    void GivenAStoredPlan_WhenAPlanWithItsIdIsAddedForAnotherDate_ThenTheConstraintViolationPassesThrough() {
        Plan stored = builder(64).builder().draft();
        Plan sameKey = Plan.open(stored.id(), date(65), PlansOnDate.none(), date(65).value(), SOLVED_AGAIN);

        Throwable refused = catchThrowable(() -> transactions.inTransaction(() -> repository.add(sameKey)));

        assertThat(refused).isNotInstanceOf(RuleViolation.class);
        assertThat(Stream.iterate(refused, Objects::nonNull, Throwable::getCause))
                .anyMatch(cause -> cause instanceof ConstraintViolationException violation
                        && "plan_pkey".equals(violation.getConstraintName()));
    }

    // --- the reads by date and by route check

    @Test
    void GivenADateWithNoPlan_WhenItsPlansAreRead_ThenThereAreNone() {
        assertThat(transactions.inTransaction(() -> repository.plansOn(date(70))))
                .isEqualTo(PlansOnDate.none());
        assertThat(transactions.inTransaction(() -> repository.uncancelledOn(date(70))))
                .isEmpty();
        assertThat(transactions.inTransaction(() -> repository.unfinishedOn(date(70))))
                .isEmpty();
    }

    @Test
    void GivenADatesDraft_WhenItsUncancelledPlanIsRead_ThenItLoadsWhole() {
        Plan draft = builder(71).builder().draft();

        Plan loaded = transactions.inTransaction(
                () -> repository.uncancelledOn(date(71)).orElseThrow());

        assertThat(PlanDescription.of(loaded)).isEqualTo(PlanDescription.of(draft));
        assertThat(transactions.inTransaction(() -> repository.plansOn(date(71))))
                .isEqualTo(PlansOnDate.of(draft.id()));
    }

    @ParameterizedTest
    @EnumSource(
            value = State.class,
            names = {"DRAFT", "SOLVING", "READY", "PUBLISHED"})
    void GivenADatesPlanNotYetClosed_WhenTheDatesUnfinishedPlanIsRead_ThenItIsThatPlan(State state) {
        KeyedBuilder builder = builder(80 + state.ordinal());
        Plan plan = state.build(builder);

        assertThat(transactions.inTransaction(() -> repository.unfinishedOn(builder.date())))
                .contains(plan.id());
    }

    @ParameterizedTest
    @EnumSource(
            value = State.class,
            names = {"COMPLETED", "CANCELLED", "ABANDONED"})
    void GivenADatesClosedPlan_WhenTheDatesUnfinishedPlanIsRead_ThenThereIsNone(State state) {
        KeyedBuilder builder = builder(80 + state.ordinal());
        state.build(builder);

        assertThat(transactions.inTransaction(() -> repository.unfinishedOn(builder.date())))
                .isEmpty();
    }

    @Test
    void
            GivenPlansWithAndWithoutRoutesNotYetChecked_WhenThePlansToRecheckAreRead_ThenTheyAreTheReadyAndPublishedOnesEachOnce() {
        Plan checked = builder(100).builder().ready();
        KeyedBuilder ready = builder(101);
        Plan readyUnchecked = removed(ready, ready.builder().ready(), O1, O3);
        KeyedBuilder published = builder(102);
        Plan publishedUnchecked = removed(published, published.builder().published(), O1);
        KeyedBuilder completed = builder(103);
        Plan completedUnchecked = completed
                .saves()
                .changed(
                        removed(completed, completed.builder().published(), O1),
                        plan -> plan.complete(completed.today().plusDays(1)));

        List<PlanId> toRecheck = transactions.inTransaction(() -> repository.withUncheckedRoutes());

        assertThat(toRecheck).containsOnlyOnce(readyUnchecked.id(), publishedUnchecked.id());
        assertThat(toRecheck).doesNotContain(checked.id(), completedUnchecked.id());
    }

    // --- the load check (ADR-127 decision 1): one case for each damaged snapshot the domain tests
    // built, PlanRestoreTest's and PlanOptimizerOutputsTest's, then the stored values it rebuilds

    static Stream<Arguments> damagedRows() {
        String undoUnpinnedO1 = unpinnedO1("plan_undo", 1);
        String violationOnO1 = "INSERT INTO planning.plan_violation (plan_id, position, rule, order_id)"
                + " VALUES (:plan, 0, 'FR-91', '" + O1.value() + "')";
        return numbered(
                1_000,
                damaged("a draft with routes", State.DRAFT, rows -> rows.run("""
                                INSERT INTO planning.route (id, plan_id, position, route_index, vehicle_id, driver_id,
                                    check_state)
                                VALUES (gen_random_uuid(), :plan, 0, 1, gen_random_uuid(), gen_random_uuid(), 'CHECKED')
                                """), "Plan", DRAFT_HOLDS_NOTHING),
                damaged(
                        "a draft with an applied solve",
                        State.READY,
                        rows -> {
                            rows.run("DELETE FROM planning.planned_stop WHERE route_id IN"
                                    + " (SELECT id FROM planning.route WHERE plan_id = :plan)");
                            rows.run("DELETE FROM planning.route WHERE plan_id = :plan");
                            rows.run("DELETE FROM planning.plan_drop WHERE plan_id = :plan");
                            rows.plan("status = 'DRAFT'");
                        },
                        "Plan",
                        DRAFT_HOLDS_NOTHING),
                damaged(
                        "a solving plan with routes and no applied solve",
                        State.SOLVING_AGAIN,
                        rows -> rows.plan(NO_APPLIED_SOLVE),
                        "Plan",
                        "a solving plan has routes only from an applied solve"),
                damaged(
                        "a solving plan waiting for the job it applied",
                        State.READY,
                        rows -> rows.plan("status = 'SOLVING', solving_job_id = applied_job_id,"
                                + " solving_requested_at = applied_requested_at"),
                        "Plan",
                        "a solving plan waits for a job it has not applied"),
                damaged(
                        "a ready plan with no applied solve",
                        State.READY,
                        rows -> rows.plan(NO_APPLIED_SOLVE),
                        "Plan",
                        "a ready plan has an applied solve"),
                damaged(
                        "a published plan with a journal",
                        State.PUBLISHED,
                        rows -> rows.run(undoUnpinnedO1),
                        "Plan",
                        JOURNAL_BEFORE_PUBLISHING),
                damaged(
                        "a published plan with no applied solve",
                        State.PUBLISHED,
                        rows -> rows.plan(NO_APPLIED_SOLVE),
                        "Plan",
                        PUBLISHED_WAS_SOLVED),
                damaged(
                        "an abandoned plan with a journal",
                        State.ABANDONED,
                        rows -> rows.run(undoUnpinnedO1),
                        "Plan",
                        JOURNAL_BEFORE_PUBLISHING),
                damaged(
                        "a withdrawal before publishing",
                        State.READY,
                        rows -> rows.update("route", "withdrawal_cause = 'NO_DRIVER'", "position = 0"),
                        "Plan",
                        FACTS_AFTER_PUBLISHING),
                damaged(
                        "a late outcome before publishing",
                        State.READY,
                        rows -> rows.run("UPDATE planning.planned_stop SET late = 'SERVED_LATE' WHERE route_id = "
                                + FIRST_ROUTE),
                        "Plan",
                        FACTS_AFTER_PUBLISHING),
                damaged(
                        "an order both served and dropped",
                        State.READY,
                        rows -> rows.run(droppedO1(2)),
                        "Plan",
                        "an order and attempt is served or dropped at most once"),
                damaged(
                        "a vehicle with two routes",
                        State.READY,
                        rows -> rows.update("route", "vehicle_id = '" + V1.value() + "'", "position = 1"),
                        "Plan",
                        "a vehicle or driver drives at most one route"),
                damaged(
                        "a driver with two routes",
                        State.READY,
                        rows -> rows.update("route", "driver_id = '" + D1.value() + "'", "position = 1"),
                        "Plan",
                        "a vehicle or driver drives at most one route"),
                damaged(
                        "a violation on an unknown route",
                        State.READY,
                        rows -> rows.run("INSERT INTO planning.plan_violation (plan_id, position, rule, route_id)"
                                + " VALUES (:plan, 0, 'BR-03', gen_random_uuid())"),
                        "Plan",
                        ROUTES_OF_THE_PLAN),
                damaged(
                        "an edit of an unknown route",
                        State.READY,
                        rows -> rows.run(
                                "INSERT INTO planning.plan_undo (plan_id, position, kind, route_id, lock_before)"
                                        + " VALUES (:plan, 0, 'UNLOCKED', gen_random_uuid(), 'WHOLE_ROUTE')"),
                        "Plan",
                        ROUTES_OF_THE_PLAN),
                damaged(
                        "a solve applied before it was requested",
                        State.READY,
                        rows -> rows.plan("applied_at = applied_requested_at - interval '1 second'"),
                        "Plan",
                        "a solve is applied no earlier than it was requested"),
                damaged(
                        "a solving plan with a journal and no applied solve",
                        State.SOLVING,
                        rows -> rows.run(undoUnpinnedO1),
                        "Plan",
                        "a journal belongs to a plan solved before"),
                damaged(
                        "a confirmed return before publishing",
                        State.READY,
                        rows -> rows.update(
                                "route",
                                "confirmed_return_date = '2027-06-15', confirmed_return_seconds = 60000",
                                "position = 0"),
                        "Plan",
                        FACTS_AFTER_PUBLISHING),
                damaged(
                        "a completed plan with no applied solve",
                        State.COMPLETED,
                        rows -> rows.plan(NO_APPLIED_SOLVE),
                        "Plan",
                        PUBLISHED_WAS_SOLVED),
                damaged(
                        "a cancelled plan with no applied solve",
                        State.CANCELLED,
                        rows -> rows.plan(NO_APPLIED_SOLVE),
                        "Plan",
                        PUBLISHED_WAS_SOLVED),
                damaged(
                        "a completed plan with a journal",
                        State.COMPLETED,
                        rows -> rows.run(undoUnpinnedO1),
                        "Plan",
                        JOURNAL_BEFORE_PUBLISHING),
                damaged(
                        "a cancelled plan with a journal",
                        State.CANCELLED,
                        rows -> rows.run(undoUnpinnedO1),
                        "Plan",
                        JOURNAL_BEFORE_PUBLISHING),
                damaged(
                        "a published plan with a lock",
                        State.PUBLISHED,
                        rows -> rows.update("route", "route_lock = 'WHOLE_ROUTE'", "position = 0"),
                        "Plan",
                        "locks belong to a plan not yet published"),
                damaged(
                        "a ready plan with 101 journal entries",
                        State.READY,
                        rows -> {
                            rows.run(unpinnedO1("plan_undo", 51));
                            rows.run(unpinnedO1("plan_redo", 50));
                        },
                        "Plan",
                        "a journal holds at most 100 edits"),
                damaged(
                        "a draft with drops only",
                        State.DRAFT,
                        rows -> rows.run(droppedO1(0)),
                        "Plan",
                        DRAFT_HOLDS_NOTHING),
                damaged(
                        "a draft with violations only",
                        State.DRAFT,
                        rows -> rows.run(violationOnO1),
                        "Plan",
                        DRAFT_HOLDS_NOTHING),
                damaged(
                        "a solving plan never solved with violations",
                        State.SOLVING,
                        rows -> rows.run(violationOnO1),
                        "Plan",
                        "a plan never solved holds no violations"),
                damaged(
                        "a last move whose way back is past the route's end",
                        State.LAST_MOVED_O1_TO_V2,
                        rows -> rows.update("plan_undo", "from_position = 9"),
                        "Plan",
                        JOURNAL_TOPS),
                damaged(
                        "a last move whose stop isn't where it says",
                        State.LAST_MOVED_O1_TO_V2,
                        rows -> rows.update("plan_undo", "to_position = 0"),
                        "Plan",
                        JOURNAL_TOPS),
                damaged(
                        "a last removal whose way back is past the route's end",
                        State.LAST_TOOK_OFF_O3,
                        rows -> rows.update("plan_undo", "from_position = 9"),
                        "Plan",
                        JOURNAL_TOPS),
                damaged(
                        "a last taken-off stop that is still on a route",
                        State.READY,
                        rows -> rows.run("INSERT INTO planning.plan_undo (plan_id, position, kind, stop_id, order_id,"
                                + " attempt, from_route_id, from_position) VALUES (:plan, 0, 'TOOK_OFF', " + FIRST_STOP
                                + ", '" + O1.value() + "', 1, " + FIRST_ROUTE + ", 0)"),
                        "Plan",
                        JOURNAL_TOPS),
                damaged(
                        "a last move of a stop the plan doesn't hold",
                        State.READY,
                        rows -> rows.run(
                                "INSERT INTO planning.plan_undo (plan_id, position, kind, stop_id, from_route_id,"
                                        + " from_position, to_route_id, to_position) VALUES (:plan, 0, 'MOVED_STOP',"
                                        + " gen_random_uuid(), " + FIRST_ROUTE + ", 0, " + FIRST_ROUTE + ", 0)"),
                        "Plan",
                        JOURNAL_TOPS),
                damaged(
                        "a last undone placement whose stop is still on a route",
                        State.READY,
                        rows -> rows.run("INSERT INTO planning.plan_redo (plan_id, position, kind, stop_id, order_id,"
                                + " attempt, to_route_id, to_position, reason) VALUES (:plan, 0, 'PLACED', "
                                + FIRST_STOP
                                + ", '" + O1.value() + "', 1, " + FIRST_ROUTE + ", 0, 'DISPLACED')"),
                        "Plan",
                        JOURNAL_TOPS),
                damaged(
                        "a draft that names an excluded vehicle",
                        State.DRAFT,
                        rows -> rows.run("INSERT INTO planning.plan_excluded_vehicle (plan_id, position, vehicle_id,"
                                + " cause) VALUES (:plan, 0, gen_random_uuid(), 'DEPOT_WITHOUT_LOCATION')"),
                        "Plan",
                        DRAFT_HOLDS_NOTHING),
                damaged(
                        "a stop served before it is reached",
                        State.READY,
                        rows -> rows.run("UPDATE planning.planned_stop SET start_seconds = arrival_seconds - 1"
                                + " WHERE route_id = " + FIRST_ROUTE),
                        "StopPlan",
                        REFUSED + "INVALID_STOP_TIMES"),
                damaged(
                        "a route that returns before it leaves",
                        State.READY,
                        rows -> rows.update("route", "returns_at_seconds = leaves_at_seconds - 1", "position = 0"),
                        "RoutePlan",
                        REFUSED + "INVALID_ROUTE_TIMES"),
                damaged(
                        "a confirmed return before its day, on a route after a withdrawn one",
                        State.PUBLISHED_AT_REVISION_4_WITH_A_WITHDRAWN_ROUTE_A_LATE_OUTCOME_AND_A_CONFIRMED_RETURN,
                        rows -> rows.update(
                                "route", "confirmed_return_seconds = -1", "confirmed_return_seconds IS NOT NULL"),
                        "SolveTime",
                        REFUSED + "TIME_BEFORE_THE_DAY"),
                damaged(
                        "a taken-off stop's times out of order in the journal",
                        State.LAST_TOOK_OFF_O3,
                        rows -> rows.update("plan_undo", "start_seconds = arrival_seconds - 1"),
                        "StopPlan",
                        REFUSED + "INVALID_STOP_TIMES"),
                damaged(
                        "two drops out of drop order",
                        State.READY,
                        rows -> rows.update("plan_drop", "position = 1 - position"),
                        "Plan",
                        "drops are in drop order"),
                damaged(
                        "two pins out of pin order",
                        State.REDONE_AFTER_A_SAVE,
                        rows -> rows.update("plan_pin", "position = 1 - position"),
                        "Plan",
                        "pins are in pin order"),
                damaged(
                        "routes whose positions start past the first",
                        State.READY,
                        rows -> rows.update("route", "position = position + 1"),
                        "Plan",
                        StoredLists.GAP),
                damaged(
                        "a route's stops whose positions start past the first",
                        State.READY,
                        rows -> rows.run("UPDATE planning.planned_stop SET position = position + 1 WHERE route_id = "
                                + FIRST_ROUTE),
                        "Plan",
                        StoredLists.GAP));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredPlanBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, State state, Damage damage, String subject, String detail) {
        Plan stored = state.build(builder(n));

        damage.apply(new Rows(jdbc, stored.id()));

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(stored.id())))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo(subject);
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    static Stream<Arguments> damagedColumns() {
        return numbered(
                1_100,
                Arguments.of(
                        "a route index outside 1 to 30",
                        State.READY,
                        (Damage) rows -> rows.update("route", "route_index = 31", "position = 0"),
                        "RouteIndex"),
                Arguments.of(
                        "a revision number below 1",
                        State.PUBLISHED,
                        (Damage) rows -> rows.plan("revision = 0"),
                        "RevisionNo"),
                Arguments.of(
                        "a violation's rule code that is no row ID",
                        State.READY_WITH_PINS_A_LOCK_A_VIOLATION_AND_BOTH_JOURNALS,
                        (Damage) rows -> rows.update("plan_violation", "rule = 'not a rule'"),
                        "RuleCode"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedColumns")
    void GivenAStoredColumnItsValueRefuses_WhenThePlanIsLoaded_ThenItsConverterRaisesStoredStateInvalid(
            String broken, int n, State state, Damage damage, String subject) {
        Plan stored = state.build(builder(n));

        damage.apply(new Rows(jdbc, stored.id()));

        // Hibernate wraps a converter's exception, and Spring wraps Hibernate's.
        Throwable thrown = catchThrowable(() -> transactions.inTransaction(() -> repository.get(stored.id())));
        assertThat(storedStateInvalidIn(thrown).subject()).isEqualTo(subject);
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return numbered(
                1_200,
                forbidden(
                        "two routes with one ID",
                        State.READY,
                        rows -> rows.update("route", "id = " + FIRST_ROUTE, "position = 1")),
                forbidden(
                        "two stops with one ID",
                        State.READY,
                        rows -> rows.run("UPDATE planning.planned_stop SET id = " + FIRST_STOP + " WHERE route_id = "
                                + SECOND_ROUTE)),
                forbidden("a status no plan has", State.DRAFT, rows -> rows.plan("status = 'PAUSED'")),
                forbidden(
                        "a solving plan without its basis",
                        State.SOLVING,
                        rows -> rows.plan("solving_job_id = NULL, solving_requested_at = NULL")),
                forbidden(
                        "a basis on a ready plan",
                        State.READY,
                        rows -> rows.plan(
                                "solving_job_id = applied_job_id, solving_requested_at = applied_requested_at")),
                forbidden(
                        "a published plan without its revision", State.PUBLISHED, rows -> rows.plan("revision = NULL")),
                forbidden("a revision on a ready plan", State.READY, rows -> rows.plan("revision = 1")),
                forbidden(
                        "a cancelled plan without its time", State.CANCELLED, rows -> rows.plan("cancelled_at = NULL")),
                forbidden("an applied solve missing its time", State.READY, rows -> rows.plan("applied_at = NULL")),
                forbidden("a lock no route has", State.READY, rows -> rows.update("route", "route_lock = 'HALF'")),
                forbidden(
                        "a route plan missing its return",
                        State.READY,
                        rows -> rows.update("route", "returns_at_seconds = NULL", "position = 0")),
                forbidden(
                        "a stop plan missing its start",
                        State.READY,
                        rows -> rows.run("UPDATE planning.planned_stop SET start_seconds = NULL WHERE route_id = "
                                + FIRST_ROUTE)),
                forbidden(
                        "two stops at one place on a route",
                        State.READY,
                        rows -> rows.run(
                                "UPDATE planning.planned_stop SET position = 0 WHERE route_id = " + FIRST_ROUTE)),
                forbidden(
                        "a confirmed return without its date",
                        State.PUBLISHED_AT_REVISION_4_WITH_A_WITHDRAWN_ROUTE_A_LATE_OUTCOME_AND_A_CONFIRMED_RETURN,
                        rows -> rows.update(
                                "route", "confirmed_return_date = NULL", "confirmed_return_seconds IS NOT NULL")),
                forbidden(
                        "a pin to a vehicle and a driver",
                        State.READY_WITH_PINS_A_LOCK_A_VIOLATION_AND_BOTH_JOURNALS,
                        rows -> rows.update("plan_pin", "driver_id = gen_random_uuid()")),
                forbidden(
                        "an edit of a kind no edit has",
                        State.READY_WITH_PINS_A_LOCK_A_VIOLATION_AND_BOTH_JOURNALS,
                        rows -> rows.update("plan_undo", "kind = 'SWAPPED'")),
                forbidden(
                        "a move without its destination",
                        State.LAST_MOVED_O1_TO_V2,
                        rows -> rows.update("plan_undo", "to_route_id = NULL, to_position = NULL")),
                forbidden(
                        "a lock without the lock it set",
                        State.READY_WITH_PINS_A_LOCK_A_VIOLATION_AND_BOTH_JOURNALS,
                        rows -> rows.update("plan_undo", "lock_after = NULL", "kind = 'LOCKED'")),
                forbidden(
                        "a removal's times on a move",
                        State.LAST_MOVED_O1_TO_V2,
                        rows -> rows.update(
                                "plan_undo",
                                "times_date = '2027-06-15', arrival_seconds = 1, start_seconds = 1,"
                                        + " departure_seconds = 1, leg_in_travel = 1, leg_in_metres = 1")),
                forbidden(
                        "a drop with a reason no drop has",
                        State.READY,
                        rows -> rows.update("plan_drop", "reason = 'LOST'")),
                forbidden(
                        "a cause no exclusion has",
                        State.DRAFT,
                        rows -> rows.run("INSERT INTO planning.plan_excluded_vehicle (plan_id, position, vehicle_id,"
                                + " cause) VALUES (:plan, 0, gen_random_uuid(), 'BORED')")),
                forbidden(
                        "two routes with one index",
                        State.READY,
                        rows -> rows.update("route", "route_index = 1", "position = 1")),
                forbidden(
                        "an order with two pins",
                        State.READY,
                        rows -> rows.run("INSERT INTO planning.plan_pin (plan_id, position, order_id, vehicle_id)"
                                + " VALUES (:plan, 0, '" + O1.value() + "', '" + V1.value() + "'),"
                                + " (:plan, 1, '" + O1.value() + "', '" + V2.value() + "')")),
                forbidden(
                        "two stops for one order and attempt",
                        State.READY,
                        rows -> rows.run("UPDATE planning.planned_stop SET order_id = '" + O1.value()
                                + "' WHERE route_id = " + SECOND_ROUTE)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredPlan_WhenARowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, State state, Damage damage) {
        Plan stored = state.build(builder(n));
        Rows rows = new Rows(jdbc, stored.id());

        assertThatThrownBy(() -> damage.apply(rows)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(stored.id())))
                .isPresent();
    }

    /**
     * The test stores the other plan itself and names the constraint, so the write can't be refused
     * for another reason, such as a plan that doesn't exist, while the stop's key goes untested.
     */
    @Test
    void GivenAStopMovedToAnotherStoredPlan_WhenItsRowIsWritten_ThenTheKeyToItsRoutesPlanRefusesIt() {
        Plan stored = State.READY.build(builder(1_300));
        Plan other = State.DRAFT.build(builder(1_301));

        Throwable refused = catchThrowable(
                () -> jdbc.sql("UPDATE planning.planned_stop SET plan_id = :other WHERE route_id = " + FIRST_ROUTE)
                        .param("plan", stored.id().value())
                        .param("other", other.id().value())
                        .update());

        assertThat(refused).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(refused).rootCause().hasMessageContaining("\"planned_stop_on_a_route_of_its_plan\"");
    }

    private static Arguments damaged(String broken, State state, Damage damage, String subject, String detail) {
        return Arguments.of(broken, state, damage, subject, detail);
    }

    private static Arguments forbidden(String broken, State state, Damage damage) {
        return Arguments.of(broken, state, damage);
    }

    /** The cases, each given the key number {@code first} plus its place, after its name. */
    private static Stream<Arguments> numbered(int first, Arguments... cases) {
        return IntStream.range(0, cases.length).mapToObj(place -> {
            Object[] parts = cases[place].get();
            Object[] keyed = new Object[parts.length + 1];
            keyed[0] = parts[0];
            keyed[1] = first + place;
            System.arraycopy(parts, 1, keyed, 2, parts.length - 1);
            return Arguments.of(keyed);
        });
    }

    /** An insert of {@code count} journal entries, each the removal of O1's pin to V1. */
    private static String unpinnedO1(String journal, int count) {
        return "INSERT INTO planning." + journal + " (plan_id, position, kind, order_id, pin_before_vehicle_id)"
                + " SELECT :plan, n, 'UNPINNED', '" + O1.value() + "', '" + V1.value() + "'"
                + " FROM generate_series(0, " + (count - 1) + ") n";
    }

    /** An insert of a drop of O1's first attempt at a position of the plan's drops. */
    private static String droppedO1(int position) {
        return "INSERT INTO planning.plan_drop (plan_id, position, order_id, attempt, reason)" + " VALUES (:plan, "
                + position + ", '" + O1.value() + "', 1, 'DISPLACED')";
    }

    /** One damage done with SQL to a stored plan's rows. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /**
     * The SQL the damage cases run on one plan's rows, with the plan's UUID bound to {@code :plan};
     * every statement is the test's own constant.
     */
    record Rows(JdbcClient jdbc, PlanId plan) {

        /** Sets columns of the plan's own row. */
        void plan(String assignments) {
            run("UPDATE planning.plan SET " + assignments + " WHERE id = :plan");
        }

        /** Sets columns of the plan's rows in one of its tables keyed by the plan. */
        void update(String table, String assignments) {
            update(table, assignments, "TRUE");
        }

        /** Sets columns of those of the plan's rows in one of its tables that meet a condition. */
        void update(String table, String assignments, String condition) {
            run("UPDATE planning." + table + " SET " + assignments + " WHERE plan_id = :plan AND (" + condition + ")");
        }

        /** Runs a statement whose one named parameter is the plan's UUID. */
        void run(String statement) {
            jdbc.sql(statement).param("plan", plan.value()).update();
        }
    }

    // --- building plans through the repository

    /** A builder state, as a method reference to the builder. */
    @FunctionalInterface
    interface BuilderStep {
        Plan of(PlanBuilder builder);
    }

    /** A command that also takes the date its builder's commands take as today. */
    @FunctionalInterface
    interface DatedCommand {
        void run(Plan plan, LocalDate today);
    }

    /** A builder keyed on a test's own prefix and date, which saves through the repository. */
    private record KeyedBuilder(PlanBuilder builder, ThroughTheRepository saves, DeliveryDate date) {

        LocalDate today() {
            return builder.today();
        }

        /** Runs a dated command on the plan as stored, and saves it. */
        Plan edited(Plan plan, DatedCommand command) {
            return saves.changed(plan, stored -> command.run(stored, today()));
        }
    }

    /**
     * Keeps a plan between its builder's commands through the repository: each command runs on the
     * plan as loaded in a transaction of its own, which saves it, and the plan is then loaded again
     * in another. It remembers the state the last command left, at the version its save stored, and
     * the version each save stored.
     */
    private final class ThroughTheRepository implements PlanningFixtures.Saves {

        private @Nullable PlanDescription lastSaved;
        private @Nullable Version lastVersion;
        private final List<Long> storedVersions = new ArrayList<>();

        @Override
        public Plan added(Plan opened) {
            lastSaved = PlanDescription.of(opened);
            lastVersion = Version.of(0);
            transactions.inTransaction(() -> repository.add(opened));
            storedVersions.add(storedVersion(opened.id()));
            return loaded(opened.id());
        }

        @Override
        public Plan changed(Plan plan, Consumer<Plan> command) {
            transactions.inTransaction(() -> {
                Plan stored = repository.get(plan.id()).orElseThrow();
                command.accept(stored);
                lastSaved = PlanDescription.of(stored);
                lastVersion = stored.version().next();
                repository.save(stored);
            });
            storedVersions.add(storedVersion(plan.id()));
            return loaded(plan.id());
        }

        /**
         * Checks a plan as loaded holds what the last command left, at the version its save stored.
         * Read outside the transaction, so a collection the load left unfetched fails here.
         */
        void hasStored(Plan loaded) {
            assertThat(PlanDescription.of(loaded)).isEqualTo(Objects.requireNonNull(lastSaved, "a plan was saved"));
            assertThat(loaded.version()).isEqualTo(lastVersion);
        }

        List<Long> storedVersions() {
            return List.copyOf(storedVersions);
        }
    }

    /**
     * Solves a ready plan again with the given routes, through the repository: the plan requests a
     * solve, whose job succeeds with them in memory, and then applies its result.
     */
    private Plan solvedAgain(KeyedBuilder builder, Plan ready, SolvedRoute... routes) {
        long keys = ready.id().value().getMostSignificantBits();
        JobId job = JobId.of(new UUID(keys, 50));
        List<SolveJob> requested = new ArrayList<>();
        Plan solving = builder.saves()
                .changed(
                        ready,
                        plan -> requested.add(plan.requestSolve(
                                job,
                                SolverName.OR_TOOLS,
                                SolveLimit.DEFAULT_DAILY,
                                Seed.of(9),
                                LaneCapacity.readUnderLock(Lane.DAILY, List.of(), List.of()),
                                builder.today(),
                                SOLVED_AGAIN)));
        SolveJob succeeded = requested.getFirst();
        succeeded.claim(SOLVED_AGAIN);
        succeeded.searchStarted(SOLVED_AGAIN);
        succeeded.succeed(
                ValidatedSolution.of(ValidationReport.of(List.of(), List.of(routes), List.of(), List.of())),
                SOLVED_AGAIN);
        FreshIds ids = FreshIds.of(
                IntStream.range(0, 10).mapToObj(i -> new UUID(keys, 20_000 + i)).toList());
        return builder.saves().changed(solving, plan -> plan.applySolveResult(succeeded, ids, SOLVED_AGAIN));
    }

    /** Takes orders' stops off a ready or published plan, which leaves their routes not yet checked. */
    private static Plan removed(KeyedBuilder builder, Plan plan, OrderId... orders) {
        Plan changed = plan;
        for (OrderId order : orders) {
            changed = builder.saves()
                    .changed(
                            changed,
                            stored -> stored.removeStopOf(
                                    order, Attempt.FIRST, RemovalCause.ORDER_CANCELLED, SOLVED_AGAIN));
        }
        return changed;
    }

    private static Plan opened(int n, DeliveryDate date) {
        return Plan.open(PlanId.of(new UUID(keys(n), 1)), date, PlansOnDate.none(), date.value(), SOLVED_AGAIN);
    }

    private Plan loaded(PlanId plan) {
        return transactions.inTransaction(() -> repository.get(plan).orElseThrow());
    }

    private static List<StopId> stopsOn(Plan plan, VehicleId vehicle) {
        return PlanDescription.of(plan).routes().stream()
                .filter(route -> route.vehicle().equals(vehicle))
                .findFirst()
                .orElseThrow()
                .stops()
                .stream()
                .map(PlanDescription.StopDescription::id)
                .toList();
    }

    /** Each route of the plan as a revision row stores it: its ID, vehicle and driver. */
    private static List<String> routesInPlanOrder(Plan plan) {
        return PlanDescription.of(plan).routes().stream()
                .map(route -> route.id().value() + " " + route.vehicle().value() + " "
                        + route.driver().value())
                .toList();
    }

    /** Each stop of the plan, route by route, as a revision row stores it: ID, order, planned arrival. */
    private static List<String> stopsInPlanOrder(Plan plan) {
        return PlanDescription.of(plan).routes().stream()
                .flatMap(route -> route.stops().stream())
                .map(stop -> stop.id().value() + " " + stop.order().value() + " "
                        + stop.plan()
                                .map(times ->
                                        String.valueOf(times.times().arrival().seconds()))
                                .orElse("none"))
                .toList();
    }

    // --- reading the stored rows

    private long storedVersion(PlanId plan) {
        return jdbc.sql("SELECT version FROM planning.plan WHERE id = ?")
                .param(plan.value())
                .query(Long.class)
                .single();
    }

    private List<StopId> storedStops(PlanId plan) {
        return jdbc.sql("""
                        SELECT s.id FROM planning.planned_stop s
                        JOIN planning.route r ON r.id = s.route_id
                        WHERE r.plan_id = ?
                        """).param(plan.value()).query(UUID.class).list().stream()
                .map(StopId::of)
                .toList();
    }

    private List<RouteId> storedRoutes(PlanId plan) {
        return jdbc
                .sql("SELECT id FROM planning.route WHERE plan_id = ? ORDER BY position")
                .param(plan.value())
                .query(UUID.class)
                .list()
                .stream()
                .map(RouteId::of)
                .toList();
    }

    /** Each stored revision as its number and cause, and whether a person made it and it cites a proposal. */
    private List<String> storedRevisions(PlanId plan) {
        return jdbc.sql("""
                        SELECT revision || ' ' || cause
                            || CASE WHEN made_by IS NULL THEN '' ELSE ' by someone' END
                            || CASE WHEN proposal_id IS NULL THEN '' ELSE ' citing a proposal' END
                        FROM planning.plan_revision WHERE plan_id = ? ORDER BY revision
                        """).param(plan.value()).query(String.class).list();
    }

    private List<String> revisionRoutes(PlanId plan, int revision) {
        return jdbc.sql("""
                        SELECT route_id || ' ' || vehicle_id || ' ' || driver_id
                        FROM planning.plan_revision_route
                        WHERE plan_id = ? AND revision = ?
                        ORDER BY position
                        """).params(plan.value(), revision).query(String.class).list();
    }

    private List<String> revisionStops(PlanId plan, int revision) {
        return jdbc.sql("""
                        SELECT stop_id || ' ' || order_id || ' ' || coalesce(planned_arrival_seconds::text, 'none')
                        FROM planning.plan_revision_stop
                        WHERE plan_id = ? AND revision = ?
                        ORDER BY route_position, stop_position
                        """).params(plan.value(), revision).query(String.class).list();
    }

    /** Tries the date's advisory lock under the key ADR-127 decision 9 names, without waiting. */
    private boolean triedDateLock(DeliveryDate date) {
        return jdbc.sql("SELECT pg_try_advisory_xact_lock(hashtext('planning:plan-date:' || ?))")
                .param(date.value().toString())
                .query(Boolean.class)
                .single();
    }

    private Map<String, Integer> journalRows(PlanId plan) {
        return Map.of("plan_undo", rowsOf("plan_undo", plan), "plan_redo", rowsOf("plan_redo", plan));
    }

    private int rowsOf(String journal, PlanId plan) {
        return jdbc.sql("SELECT count(*) FROM planning." + journal + " WHERE plan_id = ?")
                .param(plan.value())
                .query(Integer.class)
                .single();
    }

    private static StoredStateInvalid storedStateInvalidIn(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof StoredStateInvalid invalid) {
                return invalid;
            }
        }
        throw new AssertionError("no StoredStateInvalid in the cause chain of " + thrown);
    }

    // --- keys

    private KeyedBuilder builder(int n) {
        ThroughTheRepository saves = new ThroughTheRepository();
        return new KeyedBuilder(aPlan().keyed(keys(n), date(n)).savedBy(saves), saves, date(n));
    }

    private static long keys(int n) {
        return KEY_PREFIX + n;
    }

    private static DeliveryDate date(int n) {
        return DeliveryDate.of(FIRST_DATE.plusDays(n));
    }
}
