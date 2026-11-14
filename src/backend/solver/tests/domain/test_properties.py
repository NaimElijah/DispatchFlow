"""Properties over generated daily problems (NFR-82), from a recorded seed."""

from dataclasses import replace

from hypothesis import assume, event, given, seed
from hypothesis import strategies as st

from dispatchflow_solver.domain.drops import Displaced, DropReason, TimeWindow
from dispatchflow_solver.domain.problem import Pin, Problem
from dispatchflow_solver.domain.rules import RuleId
from dispatchflow_solver.domain.solution import Route, Solution
from dispatchflow_solver.domain.travel_time import TravelBucket, TravelTimeModel
from tests.domain.builders import DAY, DEPOT_LOCATION, DayBuilder
from tests.domain.properties import PROPERTY_SEED, PROPERTY_SETTINGS

points = st.floats(min_value=1, max_value=1_000, allow_nan=False)
priorities = st.integers(min_value=1, max_value=5)


def one_in_ten(draw: st.DrawFn) -> bool:
    return draw(st.integers(min_value=0, max_value=9)) == 0


@st.composite
def travel_models(draw: st.DrawFn) -> TravelTimeModel:
    start = draw(st.floats(min_value=0, max_value=40_000, allow_nan=False))
    length = draw(st.floats(min_value=1, max_value=20_000, allow_nan=False))
    factor = draw(st.floats(min_value=0.5, max_value=3, allow_nan=False))
    buckets = (TravelBucket(start, start + length, factor),) if draw(st.booleans()) else ()
    return TravelTimeModel(
        buckets=buckets,
        off_peak_factor=draw(st.floats(min_value=0.5, max_value=3, allow_nan=False)),
        buffer=draw(st.floats(min_value=0, max_value=0.5, allow_nan=False)),
    )


@st.composite
def one_order_served_alone(draw: st.DrawFn) -> tuple[Problem, Solution]:
    shift_start = draw(st.floats(min_value=0, max_value=20_000, allow_nan=False))
    shift_end = shift_start + draw(st.floats(min_value=10_000, max_value=40_000, allow_nan=False))
    door = draw(st.booleans())
    # The window meets the shift somewhere, as BR-121's bound assumes: a door stop served within
    # its shift is then outside its window for at most the shift's length.
    inside = draw(st.floats(min_value=shift_start, max_value=shift_end, allow_nan=False))
    window_start = draw(st.floats(min_value=0, max_value=inside, allow_nan=False))
    window_end = (
        draw(st.floats(min_value=inside, max_value=DAY, allow_nan=False)) if door else shift_end
    )
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
        door=door,
    )
    route = Route(vehicle=0, driver=0, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(0,))
    return day.build(), Solution(routes=(route,), dropped=frozenset())


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(one_order_served_alone())
def test_given_any_one_order_served_alone_when_costed_then_k_exceeds_the_serving_cost(
    case: tuple[Problem, Solution],
) -> None:
    problem, solution = case
    report = problem.validate(solution)
    assume(report.accepted)

    cost = problem.cost(solution, report.timings)

    # Its cheapest drop, priority 5, costs exactly K (BR-55, BR-121, BR-126).
    assert cost.travel + cost.door_window + cost.fixed_vehicles < problem.penalties.drop_unit


@st.composite
def any_day_and_solution(draw: st.DrawFn) -> tuple[Problem, Solution]:
    day = DayBuilder(travel=draw(travel_models()), breaks_reserved=draw(st.booleans()))
    if draw(st.integers(min_value=0, max_value=9)) < 3:
        opens = draw(st.floats(min_value=0, max_value=20_000, allow_nan=False))
        closes = opens + draw(st.floats(min_value=0, max_value=60_000, allow_nan=False))
        day.depot_hours(opens=opens, closes=closes)
    vehicle_count = draw(st.integers(min_value=0, max_value=3))
    for _ in range(vehicle_count):
        shift_start = draw(st.floats(min_value=0, max_value=40_000, allow_nan=False))
        shift_length = draw(st.floats(min_value=1, max_value=40_000, allow_nan=False))
        day.vehicle(
            weight=draw(st.floats(min_value=1, max_value=50, allow_nan=False)),
            volume=draw(st.floats(min_value=1, max_value=50, allow_nan=False)),
            shift=(shift_start, shift_start + shift_length),
        )
    for _ in range(draw(st.integers(min_value=1, max_value=6))):
        window_start = draw(st.floats(min_value=0, max_value=DAY, allow_nan=False))
        window_end = draw(st.floats(min_value=window_start, max_value=DAY, allow_nan=False))
        pin = None
        if vehicle_count and draw(st.booleans()):
            target = draw(st.integers(min_value=0, max_value=vehicle_count - 1))
            pin = Pin(vehicle=target) if draw(st.booleans()) else Pin(driver=target)
        order = day.order(
            None if one_in_ten(draw) else draw(points),
            weight=draw(st.floats(min_value=0, max_value=60, allow_nan=False)),
            volume=draw(st.floats(min_value=0, max_value=60, allow_nan=False)),
            service=draw(st.floats(min_value=0, max_value=600, allow_nan=False)),
            window=(window_start, window_end),
            priority=draw(priorities),
            door=draw(st.booleans()),
            pin=pin,
            reachable=not one_in_ten(draw),
        )
        if one_in_ten(draw):
            day.removed_by_dispatcher.add(order)
    built = day.build()
    routable = [order for order in range(len(built.orders)) if built.orders.is_routable(order)]
    served = draw(st.lists(st.sampled_from(routable), unique=True)) if routable else []
    routes = tuple(
        Route(
            vehicle=vehicle,
            driver=vehicle,
            start=DEPOT_LOCATION,
            end=DEPOT_LOCATION,
            stops=tuple(order for order in served if order % vehicle_count == vehicle),
        )
        for vehicle in range(vehicle_count)
    )
    on_routes = {order for route in routes for order in route.stops}
    dropped = frozenset(order for order in range(len(built.orders)) if order not in on_routes)
    return built, Solution(routes=routes, dropped=dropped)


def serves_alone(problem: Problem, order: int) -> bool:
    """The oracle: some usable vehicle that honours the order's pin serves it alone, validly."""
    pin = problem.orders.pin(order)
    vehicles = problem.vehicles
    everything_else = frozenset(range(len(problem.orders))) - {order}
    for vehicle in vehicles.usable():
        driver = int(vehicles.driver[vehicle])
        if pin is not None and pin not in (Pin(vehicle=vehicle), Pin(driver=driver)):
            continue
        route = Route(
            vehicle=vehicle,
            driver=driver,
            start=int(vehicles.start_location[vehicle]),
            end=int(vehicles.end_location[vehicle]),
            stops=(order,),
        )
        if problem.validate(Solution(routes=(route,), dropped=everything_else)).accepted:
            return True
    return False


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(any_day_and_solution())
def test_given_any_solution_when_drops_explained_then_every_dropped_order_has_a_reason(
    case: tuple[Problem, Solution],
) -> None:
    problem, solution = case

    for order in problem.unserved(solution):
        reason = problem.explain_drop(order, solution)
        event(type(reason).__name__)

        assert isinstance(reason, DropReason.__value__)
        if problem.orders.leave_at_door[order]:
            assert not isinstance(reason, TimeWindow)
        judged = problem.orders.is_routable(order) and order not in problem.removed_by_dispatcher
        assert isinstance(reason, Displaced) == (judged and serves_alone(problem, order))


@st.composite
def trivially_feasible(draw: st.DrawFn) -> tuple[Problem, Solution]:
    # Whole-day shifts and windows, room for every load, and routes far shorter than a day, so
    # every route the generator builds meets every rule.
    day = DayBuilder(travel=draw(travel_models()))
    vehicle_count = draw(st.integers(min_value=1, max_value=4))
    order_count = draw(st.integers(min_value=0, max_value=8))
    for _ in range(vehicle_count):
        day.vehicle(weight=order_count * 10 + 1, volume=order_count * 10 + 1, shift=(0, DAY))
    for _ in range(order_count):
        day.order(
            draw(points),
            weight=draw(st.floats(min_value=0, max_value=10, allow_nan=False)),
            volume=draw(st.floats(min_value=0, max_value=10, allow_nan=False)),
            service=draw(st.floats(min_value=0, max_value=600, allow_nan=False)),
            door=draw(st.booleans()),
            priority=draw(priorities),
        )
    assignment = [draw(st.integers(min_value=-1, max_value=vehicle_count - 1)) for _ in day.orders]
    routes = tuple(
        Route(
            vehicle=vehicle,
            driver=vehicle,
            start=DEPOT_LOCATION,
            end=DEPOT_LOCATION,
            stops=tuple(order for order, chosen in enumerate(assignment) if chosen == vehicle),
        )
        for vehicle in range(vehicle_count)
    )
    dropped = frozenset(order for order, chosen in enumerate(assignment) if chosen < 0)
    return day.build(), Solution(routes=routes, dropped=dropped)


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(trivially_feasible())
def test_given_a_trivially_feasible_solution_when_validated_then_no_violation(
    case: tuple[Problem, Solution],
) -> None:
    problem, solution = case

    assert problem.validate(solution).violations == ()


@st.composite
def a_day_with_loaded_routes(draw: st.DrawFn) -> tuple[DayBuilder, Solution]:
    # Hard windows open at 0 and every load is at least 1 kg and 1 l, so every limit can be set
    # to what the route reaches and then lowered by 1 without becoming invalid.
    day = DayBuilder(travel=draw(travel_models()))
    vehicle_count = draw(st.integers(min_value=1, max_value=3))
    for _ in range(vehicle_count):
        day.vehicle(weight=1_000, volume=1_000, shift=(0, DAY))
    for _ in range(draw(st.integers(min_value=1, max_value=6))):
        day.order(
            draw(points),
            weight=draw(st.floats(min_value=1, max_value=10, allow_nan=False)),
            volume=draw(st.floats(min_value=1, max_value=10, allow_nan=False)),
            service=draw(st.floats(min_value=0, max_value=600, allow_nan=False)),
            door=draw(st.booleans()),
        )
    stops: list[list[int]] = [[] for _ in range(vehicle_count)]
    for order in range(len(day.orders)):
        stops[draw(st.integers(min_value=0, max_value=vehicle_count - 1))].append(order)
    routes = tuple(
        Route(
            vehicle=vehicle,
            driver=vehicle,
            start=DEPOT_LOCATION,
            end=DEPOT_LOCATION,
            stops=tuple(stops[vehicle]),
        )
        for vehicle in range(vehicle_count)
    )
    return day, Solution(routes=routes, dropped=frozenset())


def set_every_limit_to_the_route(day: DayBuilder, solution: Solution) -> None:
    timings = day.build().validate(solution).timings
    for route, timing in zip(solution.routes, timings, strict=True):
        if not route.stops:
            continue
        vehicle = route.vehicle
        day.drivers[vehicle] = replace(day.drivers[vehicle], shift_end=timing.returns_at)
        spec = day.vehicles[vehicle]
        day.vehicles[vehicle] = replace(
            spec,
            weight_capacity_kg=sum(day.orders[order].weight_kg for order in route.stops),
            volume_capacity_l=sum(day.orders[order].volume_l for order in route.stops),
        )
        for position, order in enumerate(route.stops):
            if not day.orders[order].leave_at_door:
                end = float(timing.start[position])
                day.orders[order] = replace(day.orders[order], window_end=end)


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(a_day_with_loaded_routes(), st.data())
def test_given_limits_at_what_routes_reach_when_validated_then_accepted_and_one_less_breaks(
    case: tuple[DayBuilder, Solution], data: st.DataObject
) -> None:
    day, solution = case
    set_every_limit_to_the_route(day, solution)
    assert day.build().validate(solution).violations == ()

    route = data.draw(st.sampled_from([route for route in solution.routes if route.stops]))
    vehicle = route.vehicle
    hard = [order for order in route.stops if not day.orders[order].leave_at_door]

    shift = replace(day, drivers=list(day.drivers))
    shift.drivers[vehicle] = replace(
        shift.drivers[vehicle], shift_end=shift.drivers[vehicle].shift_end - 1
    )
    assert shift.build().validate(solution).rules() == {RuleId.BR_06}

    load = replace(day, vehicles=list(day.vehicles))
    load.vehicles[vehicle] = replace(
        load.vehicles[vehicle], weight_capacity_kg=load.vehicles[vehicle].weight_capacity_kg - 1
    )
    assert load.build().validate(solution).rules() == {RuleId.BR_05}

    if hard:
        order = data.draw(st.sampled_from(hard))
        window = replace(day, orders=list(day.orders))
        window.orders[order] = replace(
            window.orders[order], window_end=window.orders[order].window_end - 1
        )
        assert window.build().validate(solution).rules() == {RuleId.BR_03}
