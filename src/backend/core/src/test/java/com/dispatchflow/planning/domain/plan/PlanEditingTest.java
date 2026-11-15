package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertPlanRefused;
import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertUnchanged;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DATE;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O5;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O6;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PLAN;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TOMORROW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.ordersOn;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.stopId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.uuid;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.planning.domain.PlanningError.DateInPast;
import com.dispatchflow.planning.domain.PlanningError.NothingToRedo;
import com.dispatchflow.planning.domain.PlanningError.NothingToUndo;
import com.dispatchflow.planning.domain.PlanningError.OrderNotDropped;
import com.dispatchflow.planning.domain.PlanningError.OrderNotPinned;
import com.dispatchflow.planning.domain.PlanningError.OrderNotRoutable;
import com.dispatchflow.planning.domain.PlanningError.PlanNotDraftOrReady;
import com.dispatchflow.planning.domain.PlanningError.PlanNotReady;
import com.dispatchflow.planning.domain.PlanningError.PositionOutOfRange;
import com.dispatchflow.planning.domain.PlanningError.RouteLocked;
import com.dispatchflow.planning.domain.PlanningError.RouteNotInPlan;
import com.dispatchflow.planning.domain.PlanningError.RouteNotLocked;
import com.dispatchflow.planning.domain.PlanningError.StaleEvaluation;
import com.dispatchflow.planning.domain.PlanningError.StopNotInPlan;
import com.dispatchflow.planning.domain.PlanningError.ValidatorUnavailable;
import com.dispatchflow.planning.domain.solution.DropReason;
import com.dispatchflow.planning.domain.solution.DroppedOrder;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.planning.domain.solution.PinTarget;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanEditingTest {

    private static final StopId NEW_STOP = StopId.of(uuid(777));
    private static final RouteId UNKNOWN_ROUTE = RouteId.of(uuid(778));

    @Test
    void GivenAReadyPlan_WhenAStopMovesToAnotherRoute_ThenItKeepsItsIdTakesTheNewTimesAndTheEditIsJournaled() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);
        Violation late = violation("BR-03", v2);
        Evaluation evaluation =
                evaluationOf(plan).moving(o1, v2, 1).withViolation(late).build();

        plan.moveStop(o1, v2, 1, evaluation, TODAY);

        assertThat(ordersOn(plan, V2)).containsExactly(O3, O1);
        assertThat(stopId(plan, O1)).isEqualTo(o1);
        assertThat(routeOf(plan, V2).stops().get(1).plan()).isPresent();
        assertThat(plan.snapshot().violations()).containsExactly(late);
        assertThat(plan.snapshot().undo()).hasSize(1);
        assertThat(plan.status()).isEqualTo(new Plan.Status.Ready());
        assertThat(plan.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAReadyPlan_WhenAStopMovesWithinItsRoute_ThenTheOrderOfItsStopsChanges() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v1 = routeId(plan, V1);

        plan.moveStop(o1, v1, 1, evaluationOf(plan).moving(o1, v1, 1).build(), TODAY);

        assertThat(ordersOn(plan, V1)).containsExactly(O2, O1);
    }

    @Test
    void GivenADraft_WhenAStopIsMoved_ThenPlanNotReady() {
        Plan plan = aPlan().draft();

        assertPlanRefused(
                plan,
                () -> plan.moveStop(
                        NEW_STOP, UNKNOWN_ROUTE, 0, evaluationOf(plan).build(), TODAY),
                new PlanNotReady(PLAN));
    }

    @Test
    void GivenAPlanWhoseDateHasPassed_WhenAStopIsMoved_ThenDateInPast() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(
                plan,
                () -> plan.moveStop(
                        o1, v2, 0, evaluationOf(plan).moving(o1, v2, 0).build(), TOMORROW),
                new DateInPast(DATE));
    }

    @Test
    void GivenAnEvaluationReadForAnotherVersion_WhenAStopIsMoved_ThenStaleEvaluation() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);
        Evaluation old = Evaluation.checked(Version.NEW, List.of(), List.of());

        assertPlanRefused(
                plan, () -> plan.moveStop(o1, v2, 0, old, TODAY), new StaleEvaluation(Version.NEW, plan.version()));
    }

    @Test
    void GivenAnEvaluationOfAnotherEdit_WhenAStopIsMoved_ThenStaleEvaluation() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);
        Evaluation ofTheUnchangedPlan = evaluationOf(plan).build();

        assertPlanRefused(
                plan,
                () -> plan.moveStop(o1, v2, 0, ofTheUnchangedPlan, TODAY),
                new StaleEvaluation(plan.version(), plan.version()));
    }

    @Test
    void GivenAnEvaluationWhoseViolationNamesAnUnknownRoute_WhenAStopIsMoved_ThenStaleEvaluation() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);
        Evaluation strange = evaluationOf(plan)
                .moving(o1, v2, 0)
                .withViolation(violation("BR-05", UNKNOWN_ROUTE))
                .build();

        assertPlanRefused(
                plan,
                () -> plan.moveStop(o1, v2, 0, strange, TODAY),
                new StaleEvaluation(plan.version(), plan.version()));
    }

    @Test
    void GivenTheOptimizerIsDown_WhenAStopIsMoved_ThenValidatorUnavailable() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(
                plan,
                () -> plan.moveStop(o1, v2, 0, evaluationOf(plan).unavailable(), TODAY),
                new ValidatorUnavailable());
    }

    @Test
    void GivenAStopThePlanDoesNotHold_WhenItIsMoved_ThenStopNotInPlan() {
        Plan plan = aPlan().ready();
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(
                plan,
                () -> plan.moveStop(NEW_STOP, v2, 0, evaluationOf(plan).build(), TODAY),
                new StopNotInPlan(NEW_STOP));
    }

    @Test
    void GivenARouteThePlanDoesNotHave_WhenAStopMovesToIt_ThenRouteNotInPlan() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);

        assertPlanRefused(
                plan,
                () -> plan.moveStop(o1, UNKNOWN_ROUTE, 0, evaluationOf(plan).build(), TODAY),
                new RouteNotInPlan(UNKNOWN_ROUTE));
    }

    @Test
    void GivenAPositionPastTheRoutesEnd_WhenAStopMovesThere_ThenPositionOutOfRange() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(
                plan, () -> plan.moveStop(o1, v2, 2, evaluationOf(plan).build(), TODAY), new PositionOutOfRange(v2, 2));
    }

    @Test
    void GivenALastPositionWithinTheSameRoute_WhenTheStopMovesOnePast_ThenPositionOutOfRange() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v1 = routeId(plan, V1);

        assertPlanRefused(
                plan, () -> plan.moveStop(o1, v1, 2, evaluationOf(plan).build(), TODAY), new PositionOutOfRange(v1, 2));
    }

    @Test
    void GivenALockedTargetRoute_WhenAStopMovesOntoIt_ThenRouteLocked() {
        Plan plan = lockedReady(V2, RouteLock.STOP_SEQUENCE);
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(
                plan,
                () -> plan.moveStop(
                        o1, v2, 0, evaluationOf(plan).moving(o1, v2, 0).build(), TODAY),
                new RouteLocked(v2));
    }

    @Test
    void GivenALockedSourceRoute_WhenAStopMovesOffIt_ThenRouteLocked() {
        Plan plan = lockedReady(V1, RouteLock.WHOLE_ROUTE);
        StopId o1 = stopId(plan, O1);
        RouteId v1 = routeId(plan, V1);
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(
                plan,
                () -> plan.moveStop(
                        o1, v2, 0, evaluationOf(plan).moving(o1, v2, 0).build(), TODAY),
                new RouteLocked(v1));
    }

    @Test
    void GivenAReadyPlan_WhenAStopIsTakenOff_ThenItsOrderIsDroppedAsRemovedByTheDispatcher() {
        Plan plan = aPlan().ready();
        StopId o3 = stopId(plan, O3);

        plan.takeOffRoute(o3, evaluationOf(plan).removing(o3).build(), TODAY);

        assertThat(ordersOn(plan, V2)).isEmpty();
        assertThat(plan.snapshot().dropped())
                .contains(new DroppedOrder(O3, FIRST, new DropReason.RemovedByDispatcher()));
    }

    @Test
    void GivenAStopThePlanDoesNotHold_WhenItIsTakenOff_ThenStopNotInPlan() {
        Plan plan = aPlan().ready();

        assertPlanRefused(
                plan,
                () -> plan.takeOffRoute(NEW_STOP, evaluationOf(plan).build(), TODAY),
                new StopNotInPlan(NEW_STOP));
    }

    @Test
    void GivenALockedRoute_WhenAStopIsTakenOffIt_ThenRouteLocked() {
        Plan plan = lockedReady(V2, RouteLock.WHOLE_ROUTE);
        StopId o3 = stopId(plan, O3);

        assertPlanRefused(
                plan,
                () -> plan.takeOffRoute(o3, evaluationOf(plan).removing(o3).build(), TODAY),
                new RouteLocked(routeId(plan, V2)));
    }

    @Test
    void GivenADisplacedOrder_WhenItIsPlacedOnARoute_ThenItIsServedThereAsANewStop() {
        Plan plan = aPlan().ready();
        RouteId v2 = routeId(plan, V2);

        plan.placeDropped(
                O4,
                FIRST,
                v2,
                1,
                NEW_STOP,
                evaluationOf(plan).inserting(v2, 1, NEW_STOP).build(),
                TODAY);

        assertThat(ordersOn(plan, V2)).containsExactly(O3, O4);
        assertThat(stopId(plan, O4)).isEqualTo(NEW_STOP);
        assertThat(plan.snapshot().dropped()).extracting(DroppedOrder::order).containsExactly(O5);
    }

    @Test
    void GivenAnOrderThatIsNotDropped_WhenItIsPlaced_ThenOrderNotDropped() {
        Plan plan = aPlan().ready();
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(
                plan,
                () -> plan.placeDropped(
                        O6, FIRST, v2, 0, NEW_STOP, evaluationOf(plan).build(), TODAY),
                new OrderNotDropped(O6, FIRST));
    }

    @Test
    void GivenAnOrderDroppedForItsLocation_WhenItIsPlaced_ThenOrderNotRoutable() {
        Plan plan = aPlan().ready();
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(
                plan,
                () -> plan.placeDropped(
                        O5,
                        FIRST,
                        v2,
                        0,
                        NEW_STOP,
                        evaluationOf(plan).inserting(v2, 0, NEW_STOP).build(),
                        TODAY),
                new OrderNotRoutable(O5));
    }

    @Test
    void GivenAReadyPlan_WhenAnOrderIsPinnedToADriver_ThenThePinIsSetAndJournaled() {
        Plan plan = aPlan().ready();
        PinTarget toD1 = new PinTarget.ToDriver(D1);

        plan.pin(O4, toD1, evaluationOf(plan).build(), TODAY);

        assertThat(plan.snapshot().pins()).containsExactly(new Pin(O4, toD1));
        assertThat(plan.snapshot().undo()).containsExactly(new Edit.Pinned(O4, Optional.empty(), toD1));
    }

    @Test
    void GivenADraft_WhenAnOrderIsPinnedToAVehicle_ThenThePinIsSetWithoutAJournalEntry() {
        Plan plan = aPlan().draft();
        PinTarget toV1 = new PinTarget.ToVehicle(V1);

        plan.pin(O1, toV1, evaluationOf(plan).build(), TODAY);

        assertThat(plan.snapshot().pins()).containsExactly(new Pin(O1, toV1));
        assertThat(plan.snapshot().undo()).isEmpty();
    }

    @Test
    void GivenAPinnedOrder_WhenItIsPinnedAgain_ThenTheNewPinReplacesTheOld() {
        Plan plan = aPlan().draft();
        plan.pin(O1, new PinTarget.ToVehicle(V1), evaluationOf(plan).build(), TODAY);

        plan.pin(O1, new PinTarget.ToDriver(D1), evaluationOf(plan).build(), TODAY);

        assertThat(plan.snapshot().pins()).containsExactly(new Pin(O1, new PinTarget.ToDriver(D1)));
    }

    @Test
    void GivenAPublishedPlan_WhenAnOrderIsPinned_ThenPlanNotDraftOrReady() {
        Plan plan = aPlan().published();

        assertPlanRefused(
                plan,
                () -> plan.pin(
                        O1, new PinTarget.ToVehicle(V1), evaluationOf(plan).build(), TODAY),
                new PlanNotDraftOrReady(PLAN));
    }

    @Test
    void GivenAPinnedOrder_WhenItIsUnpinned_ThenThePinIsGone() {
        Plan plan = aPlan().draft();
        plan.pin(O1, new PinTarget.ToVehicle(V1), evaluationOf(plan).build(), TODAY);

        plan.unpin(O1, evaluationOf(plan).build(), TODAY);

        assertThat(plan.snapshot().pins()).isEmpty();
    }

    @Test
    void GivenAnOrderWithNoPin_WhenItIsUnpinned_ThenOrderNotPinned() {
        Plan plan = aPlan().ready();

        assertPlanRefused(plan, () -> plan.unpin(O1, evaluationOf(plan).build(), TODAY), new OrderNotPinned(O1));
    }

    @Test
    void GivenAReadyPlan_WhenARouteIsLocked_ThenItCarriesTheLockAndTheEditIsJournaled() {
        Plan plan = aPlan().ready();
        RouteId v1 = routeId(plan, V1);

        plan.lockRoute(v1, RouteLock.WHOLE_ROUTE, TODAY);

        assertThat(routeOf(plan, V1).lock()).contains(RouteLock.WHOLE_ROUTE);
        assertThat(plan.snapshot().undo())
                .containsExactly(new Edit.Locked(v1, Optional.empty(), RouteLock.WHOLE_ROUTE));
    }

    @Test
    void GivenALockedRoute_WhenItIsLockedAgainWithTheSameLock_ThenNothingChangesAndNothingIsJournaled() {
        Plan plan = lockedReady(V1, RouteLock.WHOLE_ROUTE);
        PlanSnapshot before = plan.snapshot();

        plan.lockRoute(routeId(plan, V1), RouteLock.WHOLE_ROUTE, TODAY);

        assertUnchanged(plan, before);
        assertThat(plan.snapshot().undo()).hasSize(1);
    }

    @Test
    void GivenALockedRoute_WhenItIsLockedWithTheOtherKind_ThenTheKindChangesAndIsJournaled() {
        Plan plan = lockedReady(V1, RouteLock.WHOLE_ROUTE);
        RouteId v1 = routeId(plan, V1);

        plan.lockRoute(v1, RouteLock.STOP_SEQUENCE, TODAY);

        assertThat(routeOf(plan, V1).lock()).contains(RouteLock.STOP_SEQUENCE);
        assertThat(plan.snapshot().undo())
                .last()
                .isEqualTo(new Edit.Locked(v1, Optional.of(RouteLock.WHOLE_ROUTE), RouteLock.STOP_SEQUENCE));
    }

    @Test
    void GivenOneHundredEditsInTheJournal_WhenAnotherIsMade_ThenTheOldestIsForgottenAndTheJournalHoldsOneHundred() {
        Plan plan = aPlan().ready();
        RouteId v1 = routeId(plan, V1);
        for (int edit = 0; edit < 50; edit++) {
            plan.lockRoute(v1, RouteLock.WHOLE_ROUTE, TODAY);
            plan = reload(plan);
            plan.unlockRoute(v1, TODAY);
            plan = reload(plan);
        }
        assertThat(plan.snapshot().undo()).hasSize(100);

        plan.lockRoute(v1, RouteLock.STOP_SEQUENCE, TODAY);

        assertThat(plan.snapshot().undo()).hasSize(100);
        assertThat(plan.snapshot().undo().get(0)).isEqualTo(new Edit.Unlocked(v1, RouteLock.WHOLE_ROUTE));
        assertThat(plan.snapshot().undo())
                .last()
                .isEqualTo(new Edit.Locked(v1, Optional.empty(), RouteLock.STOP_SEQUENCE));
        assertThat(reload(plan).snapshot().undo()).hasSize(100);
    }

    @Test
    void GivenARouteThePlanDoesNotHave_WhenItIsLocked_ThenRouteNotInPlan() {
        Plan plan = aPlan().ready();

        assertPlanRefused(
                plan,
                () -> plan.lockRoute(UNKNOWN_ROUTE, RouteLock.WHOLE_ROUTE, TODAY),
                new RouteNotInPlan(UNKNOWN_ROUTE));
    }

    @Test
    void GivenAnUnlockedRoute_WhenItIsUnlocked_ThenRouteNotLocked() {
        Plan plan = aPlan().ready();
        RouteId v1 = routeId(plan, V1);

        assertPlanRefused(plan, () -> plan.unlockRoute(v1, TODAY), new RouteNotLocked(v1));
    }

    @Test
    void GivenAMovedStop_WhenTheMoveIsUndone_ThenTheStopIsBackAndTheEditCanBeRedone() {
        Plan ready = aPlan().ready();
        StopId o1 = stopId(ready, O1);
        RouteId v2 = routeId(ready, V2);
        ready.moveStop(o1, v2, 0, evaluationOf(ready).moving(o1, v2, 0).build(), TODAY);
        Plan plan = reload(ready);
        RouteId v1 = routeId(plan, V1);

        plan.undo(evaluationOf(plan).moving(o1, v1, 0).build(), TODAY);

        assertThat(ordersOn(plan, V1)).containsExactly(O1, O2);
        assertThat(plan.snapshot().undo()).isEmpty();
        assertThat(plan.snapshot().redo()).hasSize(1);

        Plan undone = reload(plan);
        undone.redo(evaluationOf(undone).moving(o1, v2, 0).build(), TODAY);

        assertThat(ordersOn(undone, V2)).containsExactly(O1, O3);
        assertThat(undone.snapshot().redo()).isEmpty();
        assertThat(undone.snapshot().undo()).hasSize(1);
    }

    @Test
    void GivenATakenOffStop_WhenItIsUndone_ThenTheSameStopIsBackWhereItWas() {
        Plan ready = aPlan().ready();
        StopId o1 = stopId(ready, O1);
        ready.takeOffRoute(o1, evaluationOf(ready).removing(o1).build(), TODAY);
        Plan plan = reload(ready);
        RouteId v1 = routeId(plan, V1);

        plan.undo(evaluationOf(plan).inserting(v1, 0, o1).build(), TODAY);

        assertThat(stopId(plan, O1)).isEqualTo(o1);
        assertThat(ordersOn(plan, V1)).containsExactly(O1, O2);
        assertThat(plan.snapshot().dropped()).extracting(DroppedOrder::order).containsExactlyInAnyOrder(O4, O5);
    }

    @Test
    void GivenAPlacedOrder_WhenThePlacementIsUndone_ThenItIsDroppedForItsFormerReason() {
        Plan ready = aPlan().ready();
        RouteId v2 = routeId(ready, V2);
        ready.placeDropped(
                O4,
                FIRST,
                v2,
                0,
                NEW_STOP,
                evaluationOf(ready).inserting(v2, 0, NEW_STOP).build(),
                TODAY);
        Plan plan = reload(ready);

        plan.undo(evaluationOf(plan).removing(NEW_STOP).build(), TODAY);

        assertThat(plan.snapshot().dropped()).contains(new DroppedOrder(O4, FIRST, new DropReason.Displaced()));
    }

    @Test
    void GivenALockAndAPin_WhenBothAreUndone_ThenThePlanHasNeither() {
        Plan ready = aPlan().ready();
        RouteId v1 = routeId(ready, V1);
        ready.pin(O4, new PinTarget.ToVehicle(V1), evaluationOf(ready).build(), TODAY);
        Plan pinned = reload(ready);
        pinned.lockRoute(v1, RouteLock.WHOLE_ROUTE, TODAY);
        Plan locked = reload(pinned);
        locked.undo(evaluationOf(locked).build(), TODAY);
        Plan plan = reload(locked);

        plan.undo(evaluationOf(plan).build(), TODAY);

        assertThat(routeOf(plan, V1).lock()).isEmpty();
        assertThat(plan.snapshot().pins()).isEmpty();
        assertThat(plan.snapshot().redo()).hasSize(2);
        Plan undone = reload(plan);
        undone.redo(evaluationOf(undone).build(), TODAY);
        assertThat(undone.snapshot().pins()).containsExactly(new Pin(O4, new PinTarget.ToVehicle(V1)));
        assertThat(routeOf(undone, V1).lock()).isEmpty();
    }

    @Test
    void GivenAnUndoneEdit_WhenANewEditIsMade_ThenRedoIsCleared() {
        Plan ready = aPlan().ready();
        RouteId v1 = routeId(ready, V1);
        ready.lockRoute(v1, RouteLock.WHOLE_ROUTE, TODAY);
        Plan locked = reload(ready);
        locked.undo(evaluationOf(locked).build(), TODAY);
        Plan plan = reload(locked);

        plan.lockRoute(v1, RouteLock.STOP_SEQUENCE, TODAY);

        assertThat(plan.snapshot().redo()).isEmpty();
    }

    @Test
    void GivenNoEdits_WhenUndoIsAsked_ThenNothingToUndo() {
        Plan plan = aPlan().ready();

        assertPlanRefused(plan, () -> plan.undo(evaluationOf(plan).build(), TODAY), new NothingToUndo());
    }

    @Test
    void GivenNothingUndone_WhenRedoIsAsked_ThenNothingToRedo() {
        Plan plan = aPlan().ready();

        assertPlanRefused(plan, () -> plan.redo(evaluationOf(plan).build(), TODAY), new NothingToRedo());
    }

    @Test
    void GivenAnEditToUndo_WhenTheEvaluationDoesNotDescribeTheUndo_ThenStaleEvaluation() {
        Plan ready = aPlan().ready();
        StopId o1 = stopId(ready, O1);
        RouteId v2 = routeId(ready, V2);
        ready.moveStop(o1, v2, 0, evaluationOf(ready).moving(o1, v2, 0).build(), TODAY);
        Plan plan = reload(ready);

        assertPlanRefused(
                plan,
                () -> plan.undo(evaluationOf(plan).build(), TODAY),
                new StaleEvaluation(plan.version(), plan.version()));
    }

    private static Plan lockedReady(VehicleId vehicle, RouteLock lock) {
        Plan plan = aPlan().ready();
        plan.lockRoute(routeId(plan, vehicle), lock, TODAY);
        return reload(plan);
    }
}
