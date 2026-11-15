package com.dispatchflow.planning.domain;

import static com.dispatchflow.planning.domain.PlanAssertions.assertPlanRefused;
import static com.dispatchflow.planning.domain.PlanAssertions.assertUnchanged;
import static com.dispatchflow.planning.domain.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.PlanningFixtures.O5;
import static com.dispatchflow.planning.domain.PlanningFixtures.PLAN;
import static com.dispatchflow.planning.domain.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.PlanningFixtures.stopId;
import static com.dispatchflow.planning.domain.PlanningFixtures.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.dispatchflow.planning.domain.PlanningError.DriverAlreadyHasRoute;
import com.dispatchflow.planning.domain.PlanningError.NothingToRedo;
import com.dispatchflow.planning.domain.PlanningError.NothingToUndo;
import com.dispatchflow.planning.domain.PlanningError.OrderNotRoutable;
import com.dispatchflow.planning.domain.PlanningError.PlanNotPublished;
import com.dispatchflow.planning.domain.PlanningError.PlanNotReady;
import com.dispatchflow.planning.domain.PlanningError.PositionOutOfRange;
import com.dispatchflow.planning.domain.PlanningError.RouteLocked;
import com.dispatchflow.planning.domain.PlanningError.StopNotInPlan;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The plan's pure queries for the layout a change would leave (ADR-101 point 15, ADR-112): each
 * changes nothing, and its command then leaves exactly the layout it returned.
 */
class PlanQueriesTest {

    private static final StopId NEW_STOP = StopId.of(uuid(777));
    private static final DriverStanding D3_STANDS = new DriverStanding(D3, true, true);

    @Test
    void GivenAReadyPlan_WhenItsLayoutIsRead_ThenItHoldsItsRoutesAndDropsWithoutTimes() {
        Plan plan = aPlan().ready();

        PlanLayout layout = plan.layout();

        assertThat(layout.routes())
                .extracting(PlanLayout.LaidOutRoute::vehicle, PlanLayout.LaidOutRoute::driver)
                .containsExactly(tuple(V1, D1), tuple(V2, D2));
        assertThat(ordersIn(layout, V1)).containsExactly(O1, O2);
        assertThat(ordersIn(layout, V2)).containsExactly(O3);
        assertThat(layout.routes()).noneMatch(PlanLayout.LaidOutRoute::withdrawn);
        assertThat(layout.dropped()).extracting(DroppedOrder::order).containsExactly(O4, O5);
        assertThat(layout.pins()).isEmpty();
    }

    @Test
    void GivenAReadyPlan_WhenAMoveIsQueriedAndThenMade_ThenThePlanHoldsTheQueriedLayout() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);
        PlanSnapshot before = plan.snapshot();

        PlanLayout after = plan.afterMove(o1, v2, 1);

        assertUnchanged(plan, before);
        assertThat(ordersIn(after, V1)).containsExactly(O2);
        assertThat(ordersIn(after, V2)).containsExactly(O3, O1);
        plan.moveStop(o1, v2, 1, evaluationOf(plan, after).build(), TODAY);
        assertThat(plan.layout()).isEqualTo(after);
    }

    @Test
    void GivenAReadyPlan_WhenATakeOffIsQueriedAndThenMade_ThenThePlanHoldsTheQueriedLayout() {
        Plan plan = aPlan().ready();
        StopId o3 = stopId(plan, O3);
        PlanSnapshot before = plan.snapshot();

        PlanLayout after = plan.afterTakeOff(o3);

        assertUnchanged(plan, before);
        assertThat(ordersIn(after, V2)).isEmpty();
        assertThat(after.dropped()).contains(new DroppedOrder(O3, FIRST, new DropReason.RemovedByDispatcher()));
        plan.takeOffRoute(o3, evaluationOf(plan, after).build(), TODAY);
        assertThat(plan.layout()).isEqualTo(after);
    }

    @Test
    void GivenAReadyPlan_WhenAPlacementIsQueriedAndThenMade_ThenThePlanHoldsTheQueriedLayout() {
        Plan plan = aPlan().ready();
        RouteId v1 = routeId(plan, V1);
        PlanSnapshot before = plan.snapshot();

        PlanLayout after = plan.afterPlacing(O4, FIRST, v1, 0, NEW_STOP);

        assertUnchanged(plan, before);
        assertThat(ordersIn(after, V1)).containsExactly(O4, O1, O2);
        assertThat(after.routes().get(0).stops().get(0).stop()).isEqualTo(NEW_STOP);
        assertThat(after.dropped()).extracting(DroppedOrder::order).containsExactly(O5);
        plan.placeDropped(O4, FIRST, v1, 0, NEW_STOP, evaluationOf(plan, after).build(), TODAY);
        assertThat(plan.layout()).isEqualTo(after);
    }

    @Test
    void GivenAMovedStop_WhenAnUndoIsQueriedAndThenMade_ThenThePlanHoldsTheQueriedLayout() {
        Plan plan = movedO1ToV2();
        PlanSnapshot before = plan.snapshot();

        PlanLayout after = plan.afterUndo();

        assertUnchanged(plan, before);
        assertThat(ordersIn(after, V1)).containsExactly(O1, O2);
        assertThat(ordersIn(after, V2)).containsExactly(O3);
        plan.undo(evaluationOf(plan, after).build(), TODAY);
        assertThat(plan.layout()).isEqualTo(after);
    }

    @Test
    void GivenAnUndoneMove_WhenARedoIsQueriedAndThenMade_ThenThePlanHoldsTheQueriedLayout() {
        Plan plan = movedO1ToV2();
        plan.undo(evaluationOf(plan, plan.afterUndo()).build(), TODAY);
        plan = reload(plan);
        PlanSnapshot before = plan.snapshot();

        PlanLayout after = plan.afterRedo();

        assertUnchanged(plan, before);
        assertThat(ordersIn(after, V2)).containsExactly(O3, O1);
        plan.redo(evaluationOf(plan, after).build(), TODAY);
        assertThat(plan.layout()).isEqualTo(after);
    }

    @Test
    void GivenAPublishedPlan_WhenAHandOverIsQueriedAndThenMade_ThenThePlanHoldsTheQueriedLayout() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        PlanSnapshot before = plan.snapshot();

        PlanLayout after = plan.afterHandOver(v1, D3);

        assertUnchanged(plan, before);
        assertThat(after.routes().get(0).driver()).isEqualTo(D3);
        plan.handOver(v1, D3_STANDS, Optional.of(evaluationOf(plan, after).build()), false, DISPATCHER, TODAY, LATER);
        assertThat(plan.layout()).isEqualTo(after);
    }

    @Test
    void GivenADraft_WhenAMoveIsQueried_ThenPlanNotReady() {
        Plan plan = aPlan().draft();

        assertPlanRefused(plan, () -> plan.afterMove(NEW_STOP, RouteId.of(uuid(778)), 0), new PlanNotReady(PLAN));
    }

    @Test
    void GivenAStopThePlanDoesntHold_WhenATakeOffIsQueried_ThenStopNotInPlan() {
        Plan plan = aPlan().ready();

        assertPlanRefused(plan, () -> plan.afterTakeOff(NEW_STOP), new StopNotInPlan(NEW_STOP));
    }

    @Test
    void GivenALockedRoute_WhenAMoveOntoItIsQueried_ThenRouteLocked() {
        Plan plan = aPlan().ready();
        RouteId v2 = routeId(plan, V2);
        plan.lockRoute(v2, RouteLock.WHOLE_ROUTE, TODAY);
        Plan locked = reload(plan);
        StopId o1 = stopId(locked, O1);

        assertPlanRefused(locked, () -> locked.afterMove(o1, v2, 0), new RouteLocked(v2));
    }

    @Test
    void GivenAPositionPastTheRoute_WhenAMoveIsQueried_ThenPositionOutOfRange() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(plan, () -> plan.afterMove(o1, v2, 2), new PositionOutOfRange(v2, 2));
    }

    @Test
    void GivenAnOrderDroppedForItsLocation_WhenAPlacementIsQueried_ThenOrderNotRoutable() {
        Plan plan = aPlan().ready();
        RouteId v1 = routeId(plan, V1);

        assertPlanRefused(plan, () -> plan.afterPlacing(O5, FIRST, v1, 0, NEW_STOP), new OrderNotRoutable(O5));
    }

    @Test
    void GivenAReadyPlanWithNoEdit_WhenAnUndoIsQueried_ThenNothingToUndo() {
        Plan plan = aPlan().ready();

        assertPlanRefused(plan, plan::afterUndo, new NothingToUndo());
    }

    @Test
    void GivenAReadyPlanWithNothingUndone_WhenARedoIsQueried_ThenNothingToRedo() {
        Plan plan = aPlan().ready();

        assertPlanRefused(plan, plan::afterRedo, new NothingToRedo());
    }

    @Test
    void GivenAReadyPlan_WhenAHandOverIsQueried_ThenPlanNotPublished() {
        Plan plan = aPlan().ready();
        RouteId v1 = routeId(plan, V1);

        assertPlanRefused(plan, () -> plan.afterHandOver(v1, D3), new PlanNotPublished(PLAN));
    }

    @Test
    void GivenADriverWhoDrivesARoute_WhenAHandOverToThemIsQueried_ThenDriverAlreadyHasRoute() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(plan, () -> plan.afterHandOver(v1, D2), new DriverAlreadyHasRoute(D2, v2));
    }

    private static Plan movedO1ToV2() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);
        plan.moveStop(o1, v2, 1, evaluationOf(plan, plan.afterMove(o1, v2, 1)).build(), TODAY);
        return reload(plan);
    }

    private static List<OrderId> ordersIn(PlanLayout layout, VehicleId vehicle) {
        return layout.routes().stream()
                .filter(route -> route.vehicle().equals(vehicle))
                .findFirst()
                .orElseThrow()
                .stops()
                .stream()
                .map(PlanLayout.LaidOutStop::order)
                .toList();
    }
}
