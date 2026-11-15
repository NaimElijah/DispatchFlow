"""ProblemFactory.reoptimization's six conventions (ADR-050, ADR-105), and how the problem's
validate, cost and explain_drop apply them.

The live day: a depot at point 0, vehicle 0 with driver 0 on shift 0 to 1,000, and orders at 100
and 200 with 10 s of service. `now` is 150 unless a test says otherwise.
"""

import math
from dataclasses import replace

import numpy as np
import pytest

from dispatchflow_solver.domain.drops import (
    Capacity,
    Displaced,
    NoVehicleAvailable,
    UnreachableLocation,
)
from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.exclusions import VehicleExclusion
from dispatchflow_solver.domain.factory import (
    EARTH_RADIUS_M,
    Report,
    StopFact,
    VehiclePosition,
    VehicleState,
)
from dispatchflow_solver.domain.feasibility import LateStop
from dispatchflow_solver.domain.indices import NO_INDEX
from dispatchflow_solver.domain.penalty import DEFAULT_LATENESS_RATE, DEFAULT_MOVED_STOP_COST
from dispatchflow_solver.domain.problem import Pin, Problem, ReoptContext
from dispatchflow_solver.domain.rules import RuleId
from dispatchflow_solver.domain.solution import Route, Solution
from tests.domain.builders import (
    DEPOT_LOCATION,
    DayBuilder,
    LiveDay,
    arrived,
    delivered,
    failed,
    position,
)

SHIFT = (0.0, 1_000.0)
NOW = 150.0
SERVICE = 10.0
OUT_STATES = [VehicleState.UNAVAILABLE, VehicleState.NO_DRIVER, VehicleState.OUT_OF_SHIFT]


def a_live_day(*, now: float = NOW, orders: int = 2) -> LiveDay:
    day = DayBuilder()
    day.vehicle(shift=SHIFT)
    for order in range(orders):
        day.order(100.0 * (order + 1), service=SERVICE)
    return LiveDay(day, now=now)


def second_vehicle(live: LiveDay, state: VehicleState, *stops: StopFact | int) -> int:
    vehicle = live.day.vehicle(shift=SHIFT)
    live.states[vehicle] = state
    if state is VehicleState.NO_DRIVER:
        live.without_driver.add(vehicle)
    live.route(vehicle, *stops)
    return vehicle


def context_of(problem: Problem) -> ReoptContext:
    assert problem.reoptimization is not None
    return problem.reoptimization


def nothing_served(problem: Problem) -> Solution:
    open_orders = frozenset(o for o in range(len(problem.orders)) if not problem.is_locked(o))
    return Solution(routes=(), dropped=open_orders)


# --- Step 1: fixes what happened (BR-10, BR-91) ---


def test_given_delivered_failed_and_pending_stops_when_built_then_reported_ones_are_locked() -> (
    None
):
    live = a_live_day(orders=3)
    live.route(0, delivered(0, 100), failed(1, 120), 2)

    context = context_of(live.build())

    assert list(context.locked) == [True, True, False]
    assert list(context.stays_on) == [NO_INDEX, NO_INDEX, 0]


@pytest.mark.parametrize(
    ("now", "arrival", "expected"),
    [
        pytest.param(150.0, 100.0, 150.0, id="its service ended before now"),
        pytest.param(105.0, 100.0, 110.0, id="its service ends after now"),
    ],
)
def test_given_an_arrived_stop_when_built_then_locked_and_the_vehicle_starts_there_after_service(
    now: float, arrival: float, expected: float
) -> None:
    live = a_live_day(now=now)
    live.route(0, arrived(0, arrival), 1)
    live.position_at(0, 500, position(now))

    problem = live.build()

    assert problem.is_locked(0)
    assert problem.vehicles.start_location[0] == live.day.location_of(0)
    assert context_of(problem).start_time[0] == expected


def test_given_two_reports_at_the_same_time_when_built_then_the_later_planned_places_it() -> None:
    # BR-91 orders reports by time; a tie keeps the planned order, so order 1 is the last stop.
    live = a_live_day(orders=3)
    live.route(0, delivered(0, 100), delivered(1, 100), 2)

    problem = live.build()

    assert problem.vehicles.start_location[0] == live.day.location_of(1)


@pytest.mark.parametrize("now", [0.0, 86_400.0], ids=["the date's start", "the date's end"])
def test_given_now_at_either_end_of_the_date_when_built_then_accepted(now: float) -> None:
    live = a_live_day(now=now)

    assert context_of(live.build()).now == now


def test_given_a_report_timed_exactly_at_now_when_built_then_accepted() -> None:
    live = a_live_day()
    live.route(0, delivered(0, NOW), 1)

    problem = live.build()

    assert problem.is_locked(0)
    assert context_of(problem).start_time[0] == NOW


def test_given_stops_reported_out_of_order_when_built_then_the_last_reported_places_it() -> None:
    # BR-91: order 1 was reported before order 0, so order 0 is the last stop the vehicle made.
    live = a_live_day(orders=3)
    live.route(0, delivered(0, 120), delivered(1, 100), 2)

    problem = live.build()

    assert problem.vehicles.start_location[0] == live.day.location_of(0)
    assert context_of(problem).start_time[0] == NOW


def test_given_an_arrival_reported_before_a_later_outcome_when_built_then_not_in_progress() -> None:
    # The vehicle moved on: the arrival stays locked, and the later delivery places it at now.
    live = a_live_day(orders=3)
    live.route(0, arrived(0, 100), delivered(1, 130), 2)

    problem = live.build()

    assert problem.is_locked(0)
    assert problem.vehicles.start_location[0] == live.day.location_of(1)
    assert context_of(problem).start_time[0] == NOW


def test_given_a_failed_stop_put_back_when_built_then_it_stays_on_its_vehicle_dropped_for_now() -> (
    None
):
    # BR-117: a failed stop put back stays with its vehicle; it has no place in the sequence yet.
    live = a_live_day()
    live.route(0, failed(0, 100), 1)
    live.put_back.add(0)

    problem = live.build()
    context = context_of(problem)

    assert not problem.is_locked(0)
    assert context.stays_on[0] == 0
    assert context.warm_start.routes[0].stops == (1,)
    assert context.warm_start.dropped == frozenset({0})


def test_given_a_locked_order_when_explained_then_refused_as_served_by_its_report() -> None:
    live = a_live_day()
    live.route(0, delivered(0, 100), 1)
    problem = live.build()

    with pytest.raises(ValueError, match="order 0 is locked by its report"):
        problem.explain_drop(0, nothing_served(problem))


def test_given_a_locked_order_when_costed_then_it_pays_no_drop() -> None:
    live = a_live_day()
    live.route(0, delivered(0, 100), 1)
    problem = live.build()
    solution = nothing_served(problem)

    cost = problem.cost(solution, problem.validate(solution).timings)

    assert problem.unserved(solution) == (1,)
    assert (cost.drops, cost.moved_stops) == (problem.penalties.drop_unit * 3, 300)


# --- Step 2: places each vehicle (BR-24, BR-51, BR-79, BR-80, BR-81) ---


def test_given_a_usable_position_when_built_then_the_route_starts_there_now() -> None:
    live = a_live_day()
    live.route(0, delivered(0, 100), 1)
    where = live.position_at(0, 150, position(NOW - 10))

    problem = live.build()

    assert problem.vehicles.start_location[0] == where
    assert context_of(problem).start_time[0] == NOW
    assert context_of(problem).no_usable_position == frozenset()


@pytest.mark.parametrize(
    ("age", "usable"),
    [
        pytest.param(300.0, True, id="exactly 5 minutes old"),
        pytest.param(300.0 + 5e-7, True, id="within the tolerance"),
        pytest.param(300.0 + 2e-6, False, id="past the tolerance"),
        pytest.param(301.0, False, id="older than 5 minutes"),
    ],
)
def test_given_a_position_of_some_age_when_built_then_usable_up_to_five_minutes(
    age: float, usable: bool
) -> None:
    live = a_live_day(now=400)
    live.route(0, delivered(0, 100), 1)
    where = live.position_at(0, 150, position(400 - age))

    problem = live.build()

    expected = where if usable else live.day.location_of(0)
    assert problem.vehicles.start_location[0] == expected
    assert (0 in context_of(problem).no_usable_position) == (not usable)


def test_given_a_position_outside_the_service_area_when_built_then_the_last_stop_places_it() -> (
    None
):
    live = a_live_day()
    live.route(0, delivered(0, 100), 1)
    live.position_at(0, 150, position(NOW, in_area=False))

    problem = live.build()

    assert problem.vehicles.start_location[0] == live.day.location_of(0)
    assert context_of(problem).no_usable_position == frozenset({0})


KILOMETRE_NORTH = math.degrees(1_000 / EARTH_RADIUS_M)
"""Degrees of latitude that make 1 km along a meridian, which 200 km/h covers in 18 s."""


@pytest.mark.parametrize(
    ("seconds", "usable"),
    [
        pytest.param(18.0, True, id="exactly 200 km/h"),
        pytest.param(18.0 - 5e-7, True, id="above 200 km/h within the tolerance"),
        pytest.param(17.0, False, id="above 200 km/h"),
    ],
)
def test_given_a_kilometre_between_two_positions_when_built_then_usable_up_to_200_kmh(
    seconds: float, usable: bool
) -> None:
    live = a_live_day()
    live.route(0, delivered(0, 100), 1)
    before = position(NOW - seconds, latitude=0.0, longitude=0.0)
    where = live.position_at(0, 150, position(NOW, latitude=KILOMETRE_NORTH, longitude=0.0), before)

    problem = live.build()

    expected = where if usable else live.day.location_of(0)
    assert problem.vehicles.start_location[0] == expected


@pytest.mark.parametrize(
    ("distance_degrees", "usable"),
    [pytest.param(0.0, True, id="same place"), pytest.param(1e-3, False, id="another place")],
)
def test_given_two_positions_received_together_when_built_then_usable_only_at_one_place(
    distance_degrees: float, usable: bool
) -> None:
    live = a_live_day()
    before = position(NOW, latitude=0.0, longitude=0.0)
    where = live.position_at(
        0, 150, position(NOW, latitude=distance_degrees, longitude=0.0), before
    )

    problem = live.build()

    assert (problem.vehicles.start_location[0] == where) == usable


@pytest.mark.parametrize(
    ("now", "shift_start", "opens", "expected"),
    [
        pytest.param(150.0, 0.0, None, 150.0, id="now"),
        pytest.param(150.0, 300.0, None, 300.0, id="the shift starts later"),
        pytest.param(150.0, 0.0, 400.0, 400.0, id="the depot opens later"),
    ],
)
def test_given_no_position_and_no_report_when_built_then_the_start_depot_at_its_earliest(
    now: float, shift_start: float, opens: float | None, expected: float
) -> None:
    live = a_live_day(now=now)
    live.day.drivers[0] = replace(live.day.drivers[0], shift_start=shift_start)
    live.day.depot_hours(opens=opens)
    live.route(0, 0, 1)

    problem = live.build()

    assert problem.vehicles.start_location[0] == DEPOT_LOCATION
    assert context_of(problem).start_time[0] == expected
    assert context_of(problem).no_usable_position == frozenset({0})


def test_given_vehicles_with_and_without_usable_positions_when_built_then_br81_names_the_rest() -> (
    None
):
    live = a_live_day()
    live.day.vehicle(shift=SHIFT)
    live.day.vehicle(shift=SHIFT)
    live.position_at(0, 150, position(NOW))
    live.position_at(1, 150, position(NOW - 400))

    assert context_of(live.build()).no_usable_position == frozenset({1, 2})


def test_given_a_position_with_a_source_row_only_when_built_then_the_route_leaves_from_it() -> None:
    # The core adds a source row for a position (ADR-050): roads leave it, and none arrive.
    live = a_live_day()
    where = live.position_at(0, 150, position(NOW))
    for other in range(len(live.day.points)):
        if other != where:
            live.day.no_road(other, where)
    problem = live.build()
    route = Route(vehicle=0, driver=0, start=where, end=DEPOT_LOCATION, stops=(0, 1))

    report = problem.validate(Solution(routes=(route,), dropped=frozenset()))

    assert report.accepted
    assert list(report.timings[0].arrival) == [200, 310]


# --- Step 3: decides where each stop may go (BR-52, BR-106, BR-108, BR-117) ---


@pytest.mark.parametrize("state", OUT_STATES)
def test_given_a_vehicle_out_after_a_stop_when_built_then_withdrawn_ending_at_that_stop(
    state: VehicleState,
) -> None:
    live = a_live_day(orders=4)
    live.route(0, 0)
    vehicle = second_vehicle(live, state, delivered(1, 120), 2, 3)

    problem = live.build()
    context = context_of(problem)

    assert problem.vehicles.exclusion[vehicle] is VehicleExclusion.WITHDRAWN
    assert problem.vehicles.end_location[vehicle] == live.day.location_of(1)
    assert problem.vehicles.end_depot_closes[vehicle] == math.inf
    assert list(context.orphaned) == [False, False, True, True]
    assert list(context.stays_on) == [0, NO_INDEX, NO_INDEX, NO_INDEX]


@pytest.mark.parametrize("state", OUT_STATES)
def test_given_a_vehicle_out_before_a_stop_when_built_then_excluded_for_its_state(
    state: VehicleState,
) -> None:
    live = a_live_day()
    vehicle = second_vehicle(live, state, 0, 1)

    problem = live.build()

    assert problem.vehicles.exclusion[vehicle] == VehicleExclusion(state.value)
    assert list(context_of(problem).orphaned) == [True, True]


def test_given_a_failed_stop_put_back_on_a_vehicle_that_is_out_when_built_then_an_orphan() -> None:
    # BR-117 keeps a put-back stop with its vehicle, and BR-106 lets it leave one that is out.
    live = a_live_day()
    vehicle = second_vehicle(live, VehicleState.UNAVAILABLE, failed(0, 100), 1)
    live.put_back.add(0)

    problem = live.build()
    context = context_of(problem)

    assert problem.vehicles.exclusion[vehicle] is VehicleExclusion.WITHDRAWN
    assert not problem.is_locked(0)
    assert list(context.orphaned) == [True, True]
    assert list(context.stays_on) == [NO_INDEX, NO_INDEX]


def test_given_a_finished_route_with_stops_pending_when_built_then_they_are_orphans() -> None:
    # FR-329: a driver may finish with stops pending; BR-106 lets them leave the vehicle.
    live = a_live_day()
    vehicle = second_vehicle(live, VehicleState.FINISHED, delivered(0, 100), 1)

    problem = live.build()

    assert problem.vehicles.exclusion[vehicle] is VehicleExclusion.FINISHED
    assert list(context_of(problem).orphaned) == [False, True]


def test_given_a_withdrawn_vehicle_without_a_reported_stop_when_built_then_refused() -> None:
    live = a_live_day()
    second_vehicle(live, VehicleState.WITHDRAWN, 0)

    with pytest.raises(InvalidProblemError, match=r"withdrawn.*made a stop \(FR-267\)"):
        live.build()


def test_given_orders_on_no_route_when_built_then_they_may_go_to_any_active_vehicle() -> None:
    # New, dropped and moved orders count as new (BR-117).
    live = a_live_day()
    live.day.vehicle(shift=SHIFT)

    problem = live.build()
    context = context_of(problem)
    orphan_free = Solution(
        routes=(
            Route(vehicle=0, driver=0, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(0,)),
            Route(vehicle=1, driver=1, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(1,)),
        ),
        dropped=frozenset(),
    )

    assert list(context.stays_on) == [NO_INDEX, NO_INDEX]
    assert not context.orphaned.any()
    assert problem.validate(orphan_free).accepted


def test_given_an_orphan_on_an_active_vehicle_when_validated_then_accepted() -> None:
    live = a_live_day()
    live.route(0, 0)
    second_vehicle(live, VehicleState.UNAVAILABLE, 1)
    route = Route(vehicle=0, driver=0, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(0, 1))

    assert live.build().validate(Solution(routes=(route,), dropped=frozenset())).accepted


def test_given_an_active_vehicle_with_no_road_to_drive_when_built_then_its_stops_are_orphans() -> (
    None
):
    # It can drive no route, so it is treated as out: its stops have nowhere to stay (ADR-105).
    live = a_live_day()
    island = live.day.depot(5_000, reachable=False)
    live.day.vehicle(shift=SHIFT, start_depot=0, end_depot=island)
    live.route(1, 0)

    problem = live.build()

    assert problem.vehicles.exclusion[1] is VehicleExclusion.NO_ROAD_BETWEEN_DEPOTS
    assert list(context_of(problem).orphaned) == [True, False]


def test_given_a_withdrawn_route_its_driver_on_another_vehicle_when_validated_then_no_br39() -> (
    None
):
    # BR-39: a route withdrawn mid-route does not count, and its driver now drives vehicle 0.
    live = a_live_day(orders=3)
    vehicle = live.day.vehicle(shift=SHIFT, driver=0)
    live.states[vehicle] = VehicleState.UNAVAILABLE
    live.route(vehicle, delivered(2, 120))
    problem = live.build()
    last = live.day.location_of(2)
    routes = (
        Route(vehicle=0, driver=0, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(0, 1)),
        Route(vehicle=vehicle, driver=0, start=last, end=last, stops=()),
    )

    assert problem.validate(Solution(routes=routes, dropped=frozenset())).accepted


def test_given_a_stop_that_stays_neither_served_nor_dropped_when_validated_then_br07() -> None:
    live = a_live_day()
    live.route(0, 0, 1)
    route = Route(vehicle=0, driver=0, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(0,))

    report = live.build().validate(Solution(routes=(route,), dropped=frozenset()))

    assert report.rules() == {RuleId.BR_07}


# --- Step 4: relaxes windows (BR-107) ---


def late_day(*, door: bool = False) -> LiveDay:
    # Order 0's window ended at 120; reached at 250 from the depot at now, 130 seconds late.
    live = a_live_day(orders=0)
    live.day.order(100, service=SERVICE, window=(0, 120), door=door)
    live.route(0, 0)
    return live


def served_from_the_depot(*stops: int) -> Solution:
    route = Route(vehicle=0, driver=0, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=stops)
    return Solution(routes=(route,), dropped=frozenset())


def test_given_a_stop_past_its_window_end_when_validated_then_accepted_and_flagged_late() -> None:
    report = late_day().build().validate(served_from_the_depot(0))

    assert report.accepted
    assert report.late_stops == (LateStop(route=0, order=0, vehicle=0, seconds=130.0),)


def test_given_the_same_stop_in_a_daily_solve_when_validated_then_br03() -> None:
    day = DayBuilder()
    day.vehicle(shift=(NOW, 1_000))
    day.order(100, service=SERVICE, window=(0, 120))

    report = day.build().validate(served_from_the_depot(0))

    assert report.rules() == {RuleId.BR_03}
    assert report.late_stops == ()


def test_given_a_late_stop_whose_return_passes_the_shift_when_validated_then_br06() -> None:
    # BR-107 keeps a late stop within its driver's shift; the latest return stays hard.
    live = late_day()
    live.day.drivers[0] = replace(live.day.drivers[0], shift_end=300)

    assert live.build().validate(served_from_the_depot(0)).rules() == {RuleId.BR_06}


def test_given_an_early_arrival_in_a_reoptimization_when_timed_then_the_window_start_holds() -> (
    None
):
    live = a_live_day(orders=0)
    live.day.order(100, service=SERVICE, window=(400, 500))
    live.route(0, 0)

    report = live.build().validate(served_from_the_depot(0))

    assert report.accepted
    assert report.timings[0].start[0] == 400


def test_given_a_door_stop_outside_its_window_when_validated_then_charged_and_never_flagged() -> (
    None
):
    problem = late_day(door=True).build()
    solution = served_from_the_depot(0)

    report = problem.validate(solution)
    cost = problem.cost(solution, report.timings)

    assert report.accepted
    assert report.late_stops == ()
    assert cost.lateness == 0
    assert cost.door_window == problem.penalties.door_window_rate * 130


def test_given_a_late_stop_when_costed_then_five_seconds_per_second_late() -> None:
    problem = late_day().build()
    solution = served_from_the_depot(0)

    cost = problem.cost(solution, problem.validate(solution).timings)

    assert problem.penalties.lateness_rate == DEFAULT_LATENESS_RATE == 5
    assert cost.lateness == 5 * 130


def test_given_a_window_lost_longer_ago_than_the_shift_lasts_when_built_then_l_is_that_time() -> (
    None
):
    # ADR-049 counts a late stop's penalty in 5L (ADR-105). A 600-second shift ends at 1,000, and
    # the window ended at 100, so the stop can be up to 900 s late: L is 900, and T is 100.
    live = late_day()
    live.day.orders[0] = replace(live.day.orders[0], window_end=100)
    live.day.drivers[0] = replace(live.day.drivers[0], shift_start=400, shift_end=1_000)
    live.now = 400

    problem = live.build()

    assert problem.penalties.drop_unit == 5 * 900 + 2 * 100 + 1


def test_given_a_vehicle_on_the_road_before_its_shift_when_built_then_k_covers_an_early_door() -> (
    None
):
    # It is at its position at 100, before its 500-to-1,000 shift, so it can serve the door order
    # at 150, 750 s before its window opens at 900: L must be 800, not the shift's 500.
    live = a_live_day(now=100, orders=0)
    live.day.drivers[0] = replace(live.day.drivers[0], shift_start=500)
    live.day.order(50, window=(900, 950), door=True)
    live.position_at(0, 100, position(100))
    problem = live.build()
    route = Route(
        vehicle=0, driver=0, start=int(problem.vehicles.start_location[0]), end=0, stops=(0,)
    )
    solution = Solution(routes=(route,), dropped=frozenset())

    report = problem.validate(solution)
    cost = problem.cost(solution, report.timings)

    assert report.accepted
    assert cost.door_window == 5 * 750
    assert problem.penalties.drop_unit == 5 * 800 + 2 * 100 + 1
    assert cost.travel + cost.door_window < problem.penalties.drop_unit


def test_given_a_lateness_rate_above_the_door_rate_when_built_then_k_follows_the_larger() -> None:
    live = late_day()
    live.lateness_rate = 8.0

    problem = live.build()

    assert problem.penalties.drop_unit == 8 * 1_000 + 2 * 100 + 1


def test_given_a_locked_order_with_a_lost_window_when_built_then_it_does_not_widen_l() -> None:
    live = late_day()
    live.day.orders[0] = replace(live.day.orders[0], window_start=-60_000, window_end=-50_000)
    live.route(0, delivered(0, 100))

    problem = live.build()

    assert problem.penalties.drop_unit == 5 * 1_000 + 2 * 100 + 1


# --- Step 5: charges for instability (BR-115, FR-37, FR-256) ---


def test_given_stops_on_vehicles_when_built_then_those_that_stay_pay_300_more_to_drop() -> None:
    live = a_live_day(orders=3)
    live.route(0, 0)
    second_vehicle(live, VehicleState.UNAVAILABLE, 1)

    problem = live.build()
    unit = problem.penalties.drop_unit

    assert problem.penalties.moved_stop_cost == DEFAULT_MOVED_STOP_COST == 300
    assert list(problem.drop_penalties) == [unit * 3 + 300, unit * 3, unit * 3]


@pytest.mark.parametrize("cause", ["no location", "no road"])
def test_given_a_stop_that_stays_but_cannot_be_routed_when_built_then_it_pays_no_moved_cost(
    cause: str,
) -> None:
    # It is dropped whatever the solver does, so the moved-stop cost would steer nothing (ADR-105).
    live = a_live_day()
    if cause == "no location":
        live.day.order(None)
    else:
        live.day.order(300, reachable=False)
    live.route(0, 0, 2)

    problem = live.build()
    unit = problem.penalties.drop_unit

    assert not problem.orders.is_routable(2)
    assert context_of(problem).stays_on[2] == 0
    assert list(problem.drop_penalties) == [unit * 3 + 300, unit * 3, unit * 3]


def test_given_orphans_when_costed_then_each_pays_300_whatever_the_solution() -> None:
    live = a_live_day(orders=3)
    live.route(0, 0)
    second_vehicle(live, VehicleState.UNAVAILABLE, 1, 2)
    problem = live.build()
    solutions = [
        served_from_the_depot(0, 1, 2),
        Solution(routes=served_from_the_depot(0).routes, dropped=frozenset({1, 2})),
    ]

    costs = [problem.cost(s, problem.validate(s).timings) for s in solutions]

    assert [cost.orphaned_stops for cost in costs] == [600, 600]
    assert [cost.moved_stops for cost in costs] == [0, 0]


def test_given_a_stop_that_stays_dropped_when_costed_then_moved_stops_pays_300_apart() -> None:
    # FR-256 counts a dropped stop as one that leaves its vehicle, so its stability cost is a term
    # of its own, and `drops` holds only K x (6 - priority).
    live = a_live_day()
    live.route(0, 0, 1)
    problem = live.build()
    solution = Solution(routes=served_from_the_depot(0).routes, dropped=frozenset({1}))

    cost = problem.cost(solution, problem.validate(solution).timings)

    unit = problem.penalties.drop_unit
    assert (cost.drops, cost.moved_stops, cost.orphaned_stops) == (unit * 3, 300, 0)
    assert cost.total == cost.travel + unit * 3 + 300


def test_given_every_stop_that_stays_served_when_costed_then_no_moved_stops() -> None:
    live = a_live_day()
    live.route(0, 0, 1)
    problem = live.build()
    solution = served_from_the_depot(0, 1)

    cost = problem.cost(solution, problem.validate(solution).timings)

    assert (cost.drops, cost.moved_stops) == (0, 0)


@pytest.mark.parametrize("cause", ["no location", "no road"])
def test_given_a_stop_that_stays_but_cannot_be_routed_when_costed_then_no_moved_stops(
    cause: str,
) -> None:
    # ADR-105: it is dropped whatever the solver does, so it pays only its drop.
    live = a_live_day()
    if cause == "no location":
        live.day.order(None)
    else:
        live.day.order(300, reachable=False)
    live.route(0, 0, 2)
    problem = live.build()
    solution = Solution(routes=served_from_the_depot(0).routes, dropped=frozenset({1, 2}))

    cost = problem.cost(solution, problem.validate(solution).timings)

    assert (cost.drops, cost.moved_stops) == (problem.penalties.drop_unit * 6, 0)
    assert list(context_of(problem).pays_to_leave) == [True, False, False]


def test_given_a_daily_problem_with_drops_when_costed_then_no_moved_stops() -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(100)
    problem = day.build()
    solution = Solution(routes=(), dropped=frozenset({0}))

    cost = problem.cost(solution, problem.validate(solution).timings)

    assert (cost.drops, cost.moved_stops) == (problem.penalties.drop_unit * 3, 0)


@pytest.mark.parametrize(
    ("pays_to_leave", "match"),
    [
        pytest.param(
            [False, False],
            r"reoptimization\.pays_to_leave\[0\] is False, but the stop stays on vehicle 0, "
            r"which can serve it",
            id="a servable stop that stays, unflagged",
        ),
        pytest.param(
            [True, True],
            r"reoptimization\.pays_to_leave\[1\] is True, but the stop does not stay on a vehicle "
            r"that can serve it",
            id="a stop on no route, flagged",
        ),
    ],
)
def test_given_a_pays_to_leave_flag_against_the_roads_when_built_then_refused(
    pays_to_leave: list[bool], match: str
) -> None:
    live = a_live_day()
    live.route(0, 0)
    problem = live.build()

    with pytest.raises(InvalidProblemError, match=match):
        replace(
            problem,
            reoptimization=replace(context_of(problem), pays_to_leave=np.array(pays_to_leave)),
        )


def test_given_a_stop_that_pays_to_leave_below_the_moved_stop_cost_when_built_then_refused() -> (
    None
):
    live = a_live_day()
    live.route(0, 0)
    problem = live.build()

    with pytest.raises(
        InvalidProblemError,
        match=r"drop_penalties\[0\] is 299\.0, below the moved-stop cost 300\.0 its stop pays",
    ):
        replace(problem, drop_penalties=np.array([299.0, 0.0]))


def test_given_a_reoptimization_cost_when_totalled_then_the_sum_of_every_term() -> None:
    live = late_day()
    live.day.order(300, service=SERVICE)
    second_vehicle(live, VehicleState.UNAVAILABLE, 1)
    problem = live.build()
    solution = served_from_the_depot(0, 1)

    cost = problem.cost(solution, problem.validate(solution).timings)

    assert cost.orphaned_stops == 300
    assert cost.lateness == 5 * 130
    assert (
        cost.total == cost.travel + cost.drops + cost.door_window + cost.fixed_vehicles + 300 + 650
    )


def test_given_a_daily_problem_when_costed_then_lateness_and_orphans_cost_nothing() -> None:
    day = DayBuilder()
    day.vehicle(shift=(NOW, 1_000))
    day.order(100, service=SERVICE, window=(0, 120))
    problem = day.build()
    solution = served_from_the_depot(0)

    cost = problem.cost(solution, problem.validate(solution).timings)

    assert (cost.lateness, cost.orphaned_stops) == (0, 0)
    assert cost.total == cost.travel + cost.drops + cost.door_window + cost.fixed_vehicles


# --- Step 6: builds the warm start (ADR-044, FR-280) ---


def test_given_a_live_day_when_built_then_the_warm_start_is_the_remaining_sequences() -> None:
    live = a_live_day(orders=5)
    live.route(0, delivered(0, 100), 2, 1)
    second_vehicle(live, VehicleState.UNAVAILABLE, 3)
    problem = live.build()
    start = context_of(problem).warm_start

    assert start.routes == (
        Route(vehicle=0, driver=0, start=live.day.location_of(0), end=DEPOT_LOCATION, stops=(2, 1)),
    )
    assert start.dropped == frozenset({3, 4})
    assert problem.validate(start).accepted


def test_given_a_stop_that_lost_its_location_when_built_then_left_out_of_the_warm_start() -> None:
    live = a_live_day()
    live.day.order(None)
    live.route(0, 0, 2, 1)

    problem = live.build()
    start = context_of(problem).warm_start

    assert start.routes[0].stops == (0, 1)
    assert problem.validate(start).accepted


# --- Drop reasons in a re-optimization ---


def test_given_a_stop_too_heavy_for_its_vehicle_when_explained_then_capacity_of_that_vehicle() -> (
    None
):
    # Vehicle 1 could carry it, but the stop stays on vehicle 0 (BR-106).
    live = a_live_day(orders=0)
    live.day.vehicles[0] = replace(live.day.vehicles[0], weight_capacity_kg=5)
    live.day.order(100, weight=8)
    live.day.vehicle(weight=50, shift=SHIFT)
    live.route(0, 0)
    problem = live.build()

    reason = problem.explain_drop(0, nothing_served(problem))

    assert reason == Capacity(
        weight_kg=8, volume_l=1, largest_weight_capacity_kg=5, largest_volume_capacity_l=100
    )


def test_given_a_lost_window_when_explained_then_never_time_window() -> None:
    problem = late_day().build()

    assert problem.explain_drop(0, nothing_served(problem)) == Displaced()


def test_given_every_vehicle_out_when_explained_then_no_vehicle_available() -> None:
    live = a_live_day()
    live.states[0] = VehicleState.UNAVAILABLE
    live.route(0, 0, 1)
    problem = live.build()

    assert problem.explain_drop(1, nothing_served(problem)) == NoVehicleAvailable()


def test_given_a_stop_its_vehicle_cannot_reach_when_explained_then_unreachable_location() -> None:
    # Vehicle 1 reaches order 0 by road, so the order is routable; vehicle 0, which it stays on,
    # has no road back from it.
    live = a_live_day(orders=1)
    other = live.day.depot(150)
    live.day.vehicle(shift=SHIFT, start_depot=other, end_depot=other)
    live.day.no_road(live.day.location_of(0), DEPOT_LOCATION)
    live.route(0, 0)
    problem = live.build()

    assert problem.orders.is_routable(0)
    assert problem.explain_drop(0, nothing_served(problem)) == UnreachableLocation()
    # It can never be served where it stays, so it pays no moved-stop cost and starts dropped.
    assert problem.drop_penalties[0] == problem.penalties.drop_unit * 3
    start = context_of(problem).warm_start
    assert start.routes[0].stops == ()
    assert 0 in start.dropped
    assert problem.validate(start).accepted


# --- A vehicle with no driver (ADR-050's vehicle state, ADR-105) ---


def test_given_a_vehicle_without_a_driver_when_built_then_held_as_minus_one_and_out() -> None:
    live = a_live_day()
    vehicle = second_vehicle(live, VehicleState.NO_DRIVER, 0)

    problem = live.build()

    assert problem.vehicles.driver[vehicle] == NO_INDEX
    assert not problem.vehicles.has_driver(vehicle)
    assert not problem.vehicles.is_usable(vehicle)


def test_given_a_vehicle_out_with_its_driver_now_on_another_when_built_then_accepted() -> None:
    # FR-54 counts only the vehicles that can still take stops.
    live = a_live_day()
    vehicle = live.day.vehicle(shift=SHIFT, driver=0)
    live.states[vehicle] = VehicleState.UNAVAILABLE

    assert live.build().vehicles.driver[vehicle] == 0


def test_given_an_active_vehicle_without_a_driver_when_built_then_refused() -> None:
    live = a_live_day()
    live.without_driver.add(0)

    with pytest.raises(InvalidProblemError, match=r"vehicles\[0\] is active, so it needs a driver"):
        live.build()


def test_given_a_vehicle_out_for_want_of_a_driver_naming_one_when_built_then_refused() -> None:
    live = a_live_day()
    live.states[0] = VehicleState.NO_DRIVER

    with pytest.raises(InvalidProblemError, match=r"vehicles\[0\] has no driver by its state"):
        live.build()


def test_given_a_daily_vehicle_without_a_driver_when_built_then_refused() -> None:
    day = DayBuilder()
    day.vehicle()
    day.vehicles[0] = replace(day.vehicles[0], driver=None)

    with pytest.raises(InvalidProblemError, match=r"vehicles\.driver\[0\] must be an int: None"):
        day.build()


def daily_problem() -> Problem:
    day = DayBuilder()
    day.vehicle()
    day.order(100)
    return day.build()


def live_problem() -> Problem:
    live = a_live_day()
    live.route(0, delivered(0, 100), 1)
    second_vehicle(live, VehicleState.NO_DRIVER)
    return live.build()


WRONG_PARTS: list[object] = [
    pytest.param(
        lambda: replace(
            daily_problem(), vehicles=replace(daily_problem().vehicles, driver=np.array([-1]))
        ),
        r"vehicles\.driver\[0\] is none, but its exclusion is None",
        id="a usable vehicle without a driver",
    ),
    pytest.param(
        lambda: replace(
            daily_problem(),
            vehicles=replace(daily_problem().vehicles, exclusion=(VehicleExclusion.WITHDRAWN,)),
        ),
        r"vehicles\.exclusion\[0\] is withdrawn, which only a re-optimization",
        id="a re-optimization state in a daily solve",
    ),
    pytest.param(
        lambda: replace(
            live_problem(), vehicles=replace(live_problem().vehicles, driver=np.array([0, 0]))
        ),
        r"vehicles\.exclusion\[1\] is no_driver, but its driver is 0",
        id="out for want of a driver, naming one",
    ),
    pytest.param(
        lambda: replace(
            live_problem(),
            reoptimization=replace(context_of(live_problem()), stays_on=np.array([-1, 1])),
        ),
        r"stays_on\[1\] is vehicle 1, which can drive no route",
        id="a stop staying on a vehicle that is out",
    ),
    pytest.param(
        lambda: replace(
            live_problem(),
            reoptimization=replace(context_of(live_problem()), stays_on=np.array([0, 0])),
        ),
        r"reoptimization\.locked\[0\] is set, but the stop may still move or stay",
        id="a locked stop that stays",
    ),
    pytest.param(
        lambda: replace(
            live_problem(),
            reoptimization=replace(context_of(live_problem()), start_time=np.array([NOW - 1, NOW])),
        ),
        r"start_time\[0\] is before now",
        id="a start before now",
    ),
    pytest.param(
        lambda: replace(
            live_problem(), reoptimization=replace(context_of(live_problem()), now=-1.0)
        ),
        r"reoptimization\.now must lie on the date",
        id="now before the date",
    ),
    pytest.param(
        lambda: replace(
            live_problem(),
            reoptimization=replace(context_of(live_problem()), locked=np.array([True])),
        ),
        r"reoptimization\.stays_on has 2 entries, not 1",
        id="per-order arrays of different lengths",
    ),
    pytest.param(
        lambda: replace(
            live_problem(),
            reoptimization=replace(context_of(live_problem()), no_usable_position=frozenset({7})),
        ),
        r"no_usable_position holds vehicle 7, not one of 2",
        id="a vehicle past the problem",
    ),
    pytest.param(
        lambda: replace(
            live_problem(),
            reoptimization=replace(
                context_of(live_problem()),
                warm_start=Solution(routes=(), dropped=frozenset({9})),
            ),
        ),
        r"reoptimization\.warm_start: dropped order 9 is not one of 2",
        id="a warm start past the problem",
    ),
    pytest.param(
        lambda: replace(
            live_problem(),
            reoptimization=replace(context_of(live_problem()), orphaned=np.array([False, True])),
        ),
        r"reoptimization\.orphaned\[1\] is set, but the stop stays on a vehicle",
        id="an orphan that stays",
    ),
    pytest.param(
        lambda: replace(
            live_problem(),
            reoptimization=replace(
                context_of(live_problem()), start_time=np.array([math.nan, NOW])
            ),
        ),
        r"reoptimization\.start_time\[0\] must be finite: nan",
        id="a start time that is not finite",
    ),
    pytest.param(
        lambda: replace(
            live_problem(),
            reoptimization=replace(
                context_of(live_problem()),
                warm_start="routes",  # type: ignore[arg-type]
            ),
        ),
        r"reoptimization\.warm_start is a str, not Solution",
        id="a warm start that is no solution",
    ),
    pytest.param(
        lambda: replace(
            live_problem(),
            reoptimization=replace(context_of(live_problem()), start_time=np.array([NOW])),
        ),
        r"reoptimization\.start_time has 1 entries, not 2",
        id="start times for too few vehicles",
    ),
    pytest.param(
        lambda: replace(
            live_problem(),
            reoptimization=replace(
                context_of(live_problem()),
                no_usable_position=frozenset({"1"}),  # type: ignore[arg-type]
            ),
        ),
        r"reoptimization\.no_usable_position\[\] must be an int: '1'",
        id="a vehicle that is no int",
    ),
]


@pytest.mark.parametrize(("wrong", "match"), WRONG_PARTS)
def test_given_a_problem_with_one_wrong_reoptimization_part_when_built_then_refused(
    wrong: object, match: str
) -> None:
    with pytest.raises(InvalidProblemError, match=match):
        wrong()  # type: ignore[operator]


# --- Inputs no re-optimization can hold ---


def change_stop(live: LiveDay, stop: StopFact) -> None:
    live.route(0, stop, 1)


def change_order(live: LiveDay, order: int, **fields: object) -> None:
    live.day.orders[order] = replace(live.day.orders[order], **fields)  # type: ignore[arg-type]


def set_positions(
    live: LiveDay,
    latest: VehiclePosition | None,
    location: int | None,
    previous: VehiclePosition | None = None,
) -> None:
    live.positions[0] = (latest, location, previous)  # type: ignore[assignment]


REFUSED: list[object] = [
    pytest.param(
        lambda live: setattr(live, "now", -1.0), r"^now must lie on the date", id="now -1"
    ),
    pytest.param(lambda live: setattr(live, "now", math.nan), r"^now must lie", id="now NaN"),
    pytest.param(
        lambda live: setattr(live, "now", 86_401.0), r"^now must lie", id="now after the date"
    ),
    pytest.param(
        lambda live: change_order(live, 1, pin=Pin(vehicle=0)),
        r"orders\[1\] is pinned, but a pin applies only before a solve \(FR-91\)",
        id="a pinned order",
    ),
    pytest.param(
        lambda live: live.route(0, delivered(0, 100), 1, 1),
        r"orders\[1\] has stops on vehicles 0 and 0",
        id="an order with two stops",
    ),
    pytest.param(
        lambda live: change_stop(live, StopFact(0, Report.DELIVERED)),
        r"stops\[0\] has a report exactly when it has a report time",
        id="a report without its time",
    ),
    pytest.param(
        lambda live: change_stop(live, StopFact(0, None, 100.0)),
        r"stops\[0\] has a report exactly when it has a report time",
        id="a time without a report",
    ),
    pytest.param(
        lambda live: change_stop(live, delivered(0, NOW + 1)),
        r"stops\[0\]\.reported_at must be finite and no later than now",
        id="a report after now",
    ),
    pytest.param(
        lambda live: change_stop(live, StopFact(0, "lost", 100.0)),  # type: ignore[arg-type]
        r"stops\[0\]\.report is not a Report",
        id="a report of no kind",
    ),
    pytest.param(
        lambda live: live.route(0, delivered(0, 100), 7),
        r"stops\[1\]\.order 7 is not one of 2",
        id="a stop past the orders",
    ),
    pytest.param(
        lambda live: change_order(live, 0, location=None),
        r"stops\[0\] is reported, but orders\[0\] has no location",
        id="a reported stop without a location",
    ),
    pytest.param(
        lambda live: live.put_back.add(1),
        r"put_back holds order 1, whose stop is not failed",
        id="a put-back stop that did not fail",
    ),
    pytest.param(
        lambda live: live.put_back.add(5), r"put_back holds order 5, not one of 2", id="put back 5"
    ),
    pytest.param(
        lambda live: setattr(live, "states", {0: "flying"}),
        r"vehicles\[0\]\.state is not a VehicleState",
        id="a state of no kind",
    ),
    pytest.param(
        lambda live: live.position_at(0, 150, position(NOW + 1)),
        r"latest_position\.received_at must be finite and no later than now",
        id="a position received after now",
    ),
    pytest.param(
        lambda live: live.position_at(0, 150, position(NOW - 10), position(NOW - 5)),
        r"previous_position was received after the latest one",
        id="positions out of order",
    ),
    pytest.param(
        lambda live: live.position_at(0, 150, position(NOW, latitude=91)),
        r"latest_position\.latitude must lie from -90 to 90",
        id="latitude 91",
    ),
    pytest.param(
        lambda live: live.position_at(0, 150, position(NOW, longitude=-181)),
        r"latest_position\.longitude must lie from -180 to 180",
        id="longitude -181",
    ),
    pytest.param(
        lambda live: set_positions(live, None, None, position(NOW)),
        r"has a previous position but no latest",
        id="a previous position alone",
    ),
    pytest.param(
        lambda live: set_positions(live, position(NOW), None),
        r"has a position location exactly when it has a latest position",
        id="a position without its location",
    ),
    pytest.param(
        lambda live: set_positions(live, position(NOW), 99),
        r"position_location 99 is not one of 3 locations",
        id="a position past the matrix",
    ),
]


@pytest.mark.parametrize(
    ("latest", "match"),
    [
        pytest.param(
            position(NOW, latitude=91.25),
            r"^vehicles\[0\]\.latest_position\.latitude must lie from -90 to 90$",
            id="latitude 91.25",
        ),
        pytest.param(
            position(NOW, longitude=-181.5),
            r"^vehicles\[0\]\.latest_position\.longitude must lie from -180 to 180$",
            id="longitude -181.5",
        ),
        pytest.param(
            position(NOW + 1.5),
            r"^vehicles\[0\]\.latest_position\.received_at must be finite and no later than now$",
            id="received after now",
        ),
        pytest.param(
            replace(position(NOW), latitude="32.1"),  # type: ignore[arg-type]
            r"^vehicles\[0\]\.latest_position\.latitude must be a number$",
            id="latitude text",
        ),
    ],
)
def test_given_a_refused_position_when_built_then_the_error_names_the_field_not_the_value(
    latest: VehiclePosition, match: str
) -> None:
    # NFR-19: a driver's position is personal data, so the error never quotes it.
    live = a_live_day()
    live.position_at(0, 150, latest)

    with pytest.raises(InvalidProblemError, match=match):
        live.build()


def test_given_stop_orders_as_numpy_ints_when_built_then_held_as_ints() -> None:
    live = a_live_day(orders=3)
    live.route(0, delivered(np.int64(0), 100), StopFact(np.int64(1)), np.int64(2))  # type: ignore[arg-type]

    problem = live.build()

    assert problem.is_locked(0)
    assert context_of(problem).warm_start.routes[0].stops == (1, 2)


@pytest.mark.parametrize(("change", "match"), REFUSED)
def test_given_facts_no_reoptimization_can_hold_when_built_then_refused(
    change: object, match: str
) -> None:
    live = a_live_day()
    live.route(0, delivered(0, 100), 1)
    change(live)  # type: ignore[operator]

    with pytest.raises(InvalidProblemError, match=match):
        live.build()
