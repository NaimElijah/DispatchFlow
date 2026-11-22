"""Properties of re-optimizations over generated live days (NFR-82), from a recorded seed."""

from dataclasses import replace

import numpy as np
from hypothesis import assume, event, given, seed
from hypothesis import strategies as st

from dispatchflow_optimizer.domain.drops import DropReason, TimeWindow
from dispatchflow_optimizer.domain.exclusions import VehicleExclusion
from dispatchflow_optimizer.domain.factory import Report, StopFact, VehicleState
from dispatchflow_optimizer.domain.problem import Problem
from dispatchflow_optimizer.domain.rules import RuleId
from dispatchflow_optimizer.domain.solution import Route, Solution
from tests.domain.builders import DAY, DEPOT_LOCATION, DayBuilder, LiveDay, position
from tests.domain.properties import PROPERTY_SEED, PROPERTY_SETTINGS, points, travel_models


@st.composite
def published_plans(draw: st.DrawFn) -> tuple[DayBuilder, Solution, float]:
    # A plan the validator accepts: every limit is set to what its routes reach, as in the daily
    # properties, from shifts and depot hours that open at different times. `now` lies before any
    # route leaves, so nothing has happened yet.
    day = DayBuilder(travel=draw(travel_models()), breaks_reserved=draw(st.booleans()))
    if draw(st.booleans()):
        day.depot_hours(opens=draw(st.floats(min_value=0, max_value=20_000, allow_nan=False)))
    vehicle_count = draw(st.integers(min_value=1, max_value=3))
    for _ in range(vehicle_count):
        shift_start = draw(st.floats(min_value=0, max_value=20_000, allow_nan=False))
        day.vehicle(weight=1_000, volume=1_000, shift=(shift_start, DAY))
    for _ in range(draw(st.integers(min_value=0, max_value=6))):
        day.order(
            draw(points),
            weight=draw(st.floats(min_value=1, max_value=10, allow_nan=False)),
            volume=draw(st.floats(min_value=1, max_value=10, allow_nan=False)),
            service=draw(st.floats(min_value=0, max_value=600, allow_nan=False)),
            door=draw(st.booleans()),
            priority=draw(st.integers(min_value=1, max_value=5)),
        )
    choice = [draw(st.integers(min_value=-1, max_value=vehicle_count - 1)) for _ in day.orders]
    routes = tuple(
        Route(
            vehicle=vehicle,
            driver=vehicle,
            start=DEPOT_LOCATION,
            end=DEPOT_LOCATION,
            stops=tuple(order for order, chosen in enumerate(choice) if chosen == vehicle),
        )
        for vehicle in range(vehicle_count)
    )
    plan = Solution(
        routes=routes,
        dropped=frozenset(order for order, chosen in enumerate(choice) if chosen < 0),
    )
    timings = day.build().validate(plan).timings
    for route, timing in zip(routes, timings, strict=True):
        if not route.stops:
            continue
        driver = day.drivers[route.vehicle]
        day.drivers[route.vehicle] = replace(driver, shift_end=max(timing.returns_at, DAY / 2))
        for stop, order in enumerate(route.stops):
            if not day.orders[order].leave_at_door:
                day.orders[order] = replace(day.orders[order], window_end=float(timing.start[stop]))
    built = day.build()
    first_departure = min(
        max(float(built.drivers.shift_start[v]), float(built.vehicles.start_depot_opens[v]))
        for v in range(vehicle_count)
    )
    now = draw(st.floats(min_value=0, max_value=first_departure, allow_nan=False))
    return day, plan, now


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(published_plans())
def test_given_a_published_plan_and_no_new_facts_when_reoptimized_then_it_is_the_warm_start(
    case: tuple[DayBuilder, Solution, float],
) -> None:
    day, plan, now = case
    daily = day.build()
    daily_report = daily.validate(plan)
    assume(daily_report.accepted)
    live = LiveDay(day, now=now)
    for route in plan.routes:
        live.route(route.vehicle, *route.stops)

    problem = live.build()
    assert problem.reoptimization is not None
    start = problem.reoptimization.warm_start
    report = problem.validate(start)

    assert start == plan
    assert report.accepted
    assert report.late_stops == ()
    for daily_timing, timing in zip(daily_report.timings, report.timings, strict=True):
        assert np.array_equal(daily_timing.start, timing.start)
        assert daily_timing.returns_at == timing.returns_at or (
            np.isnan(daily_timing.returns_at) and np.isnan(timing.returns_at)
        )


@st.composite
def one_order_served_alone_mid_day(draw: st.DrawFn) -> tuple[Problem, Solution]:
    # A hard window that ended before the vehicle can reach it, which BR-107 serves late at the
    # lateness rate, or a door window wholly before or after the shift. Half the vehicles are
    # already on the road at their position, perhaps before their shift starts. K must still
    # exceed what serving the order costs (ADR-105).
    shift_start = draw(st.floats(min_value=0, max_value=46_000, allow_nan=False))
    shift_end = shift_start + draw(st.floats(min_value=10_000, max_value=40_000, allow_nan=False))
    placement = draw(st.sampled_from(["late", "door before", "door after"]))
    if placement == "door after":
        window_start = draw(st.floats(min_value=shift_end, max_value=DAY, allow_nan=False))
        window_end = draw(st.floats(min_value=window_start, max_value=DAY, allow_nan=False))
    else:
        window_end = draw(st.floats(min_value=0, max_value=shift_start, allow_nan=False))
        window_start = draw(st.floats(min_value=0, max_value=window_end, allow_nan=False))
    day = DayBuilder(
        travel=draw(travel_models()),
        fixed_vehicle_cost=draw(st.floats(min_value=0, max_value=50_000, allow_nan=False)),
    )
    day.vehicle(shift=(shift_start, shift_end))
    day.order(
        draw(points),
        service=draw(st.floats(min_value=0, max_value=600, allow_nan=False)),
        window=(window_start, window_end),
        priority=5,
        door=placement != "late",
    )
    live = LiveDay(
        day,
        now=draw(st.floats(min_value=0, max_value=shift_end, allow_nan=False)),
        lateness_rate=draw(st.floats(min_value=0, max_value=10, allow_nan=False)),
    )
    live.route(0, 0)
    on_the_road = draw(st.booleans())
    if on_the_road:
        live.position_at(0, draw(points), position(live.now))
    event(f"{placement}, {'on the road' if on_the_road else 'at the depot'}")
    problem = live.build()
    route = Route(
        vehicle=0,
        driver=0,
        start=int(problem.vehicles.start_location[0]),
        end=DEPOT_LOCATION,
        stops=(0,),
    )
    return problem, Solution(routes=(route,), dropped=frozenset())


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(one_order_served_alone_mid_day())
def test_given_any_one_order_served_alone_mid_day_when_costed_then_k_exceeds_the_serving_cost(
    case: tuple[Problem, Solution],
) -> None:
    problem, solution = case
    report = problem.validate(solution)
    assume(report.accepted)
    if report.timings[0].outside_window[0] > problem.drivers.shift_length(0):
        event("outside its window for longer than the shift lasts")

    cost = problem.cost(solution, report.timings)

    # Its cheapest drop, priority 5, costs K plus the moved-stop cost (BR-55, BR-115, BR-121).
    serving = cost.travel + cost.lateness + cost.door_window + cost.fixed_vehicles
    assert serving < problem.penalties.drop_unit


REPORTS = [None, None, Report.ARRIVED, Report.DELIVERED, Report.FAILED]
# Most vehicles are active, so the per-vehicle reasons are reached, not only the date's: about
# one date in eight has none.
STATES = [VehicleState.ACTIVE] * 12 + list(VehicleState)
CONVENTION_RULES = frozenset(
    {RuleId.BR_10, RuleId.BR_51, RuleId.BR_52, RuleId.BR_106, RuleId.BR_108}
)


@st.composite
def any_live_day(draw: st.DrawFn) -> LiveDay:
    day = DayBuilder(travel=draw(travel_models()), breaks_reserved=draw(st.booleans()))
    vehicle_count = draw(st.integers(min_value=1, max_value=3))
    for _ in range(vehicle_count):
        shift_start = draw(st.floats(min_value=0, max_value=30_000, allow_nan=False))
        shift_length = draw(st.floats(min_value=1, max_value=40_000, allow_nan=False))
        day.vehicle(
            weight=draw(st.floats(min_value=1, max_value=50, allow_nan=False)),
            shift=(shift_start, shift_start + shift_length),
        )
    for _ in range(draw(st.integers(min_value=1, max_value=7))):
        window_start = draw(st.floats(min_value=0, max_value=DAY, allow_nan=False))
        day.order(
            None if draw(st.integers(min_value=0, max_value=9)) == 0 else draw(points),
            weight=draw(st.floats(min_value=0, max_value=40, allow_nan=False)),
            service=draw(st.floats(min_value=0, max_value=600, allow_nan=False)),
            window=(
                window_start,
                draw(st.floats(min_value=window_start, max_value=DAY, allow_nan=False)),
            ),
            priority=draw(st.integers(min_value=1, max_value=5)),
            door=draw(st.booleans()),
        )
    live = LiveDay(day, now=draw(st.floats(min_value=0, max_value=DAY, allow_nan=False)))
    owner = [draw(st.integers(min_value=-1, max_value=vehicle_count - 1)) for _ in day.orders]
    for vehicle in range(vehicle_count):
        stops = []
        for order in (o for o, chosen in enumerate(owner) if chosen == vehicle):
            report = draw(st.sampled_from(REPORTS))
            if day.orders[order].location is None:
                report = None
            at = None if report is None else draw(st.floats(0, live.now, allow_nan=False))
            stops.append(StopFact(order, report, at))
            if report is Report.FAILED and draw(st.booleans()):
                live.put_back.add(order)
        state = draw(st.sampled_from(STATES))
        if state is VehicleState.WITHDRAWN and not any(stop.report for stop in stops):
            state = VehicleState.UNAVAILABLE
        live.states[vehicle] = state
        if state is VehicleState.NO_DRIVER:
            live.without_driver.add(vehicle)
        live.route(vehicle, *stops)
        if draw(st.booleans()):
            # BR-80: an age around its 5 minutes, sometimes outside the area, and sometimes a
            # position before it, whose distance may imply more than 200 km/h.
            received_at = live.now - draw(st.floats(min_value=0, max_value=600, allow_nan=False))
            latest = position(
                received_at,
                latitude=32 + draw(st.floats(min_value=0, max_value=0.05, allow_nan=False)),
                in_area=draw(st.integers(min_value=0, max_value=9)) > 0,
            )
            previous = None
            if draw(st.booleans()):
                previous = position(
                    received_at - draw(st.floats(min_value=0, max_value=120, allow_nan=False))
                )
            live.position_at(vehicle, draw(points), latest, previous)
    return live


def note(problem: Problem) -> None:
    context = problem.reoptimization
    assert context is not None
    vehicles = problem.vehicles
    event(f"{len(vehicles.usable())} active")
    if context.locked.any():
        event("has locked stops")
    if context.orphaned.any():
        event("has orphans")
    if VehicleExclusion.WITHDRAWN in vehicles.exclusion:
        event("has a withdrawn vehicle")
    if len(context.no_usable_position) < len(vehicles):
        event("has a usable position")


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(any_live_day())
def test_given_any_live_day_when_the_warm_start_is_validated_then_no_br10_br51_br52_br106_br108(
    live: LiveDay,
) -> None:
    problem = live.build()
    note(problem)
    assert problem.reoptimization is not None

    report = problem.validate(problem.reoptimization.warm_start)

    # The factory builds the warm start under its own conventions, so it can break a time or a
    # load the facts impose, never a convention.
    assert not report.rules() & CONVENTION_RULES


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(any_live_day(), st.booleans())
def test_given_any_live_day_when_drops_explained_then_every_dropped_order_has_a_reason(
    live: LiveDay, from_the_warm_start: bool
) -> None:
    problem = live.build()
    note(problem)
    assert problem.reoptimization is not None
    solution = problem.reoptimization.warm_start
    if not from_the_warm_start:
        solution = Solution(routes=(), dropped=frozenset(problem.unserved(solution)))

    for order in problem.unserved(solution):
        reason = problem.explain_drop(order, solution)
        event(type(reason).__name__)

        assert isinstance(reason, DropReason.__value__)
        # A late stop is kept, never dropped for its window (BR-107).
        assert not isinstance(reason, TimeWindow)
