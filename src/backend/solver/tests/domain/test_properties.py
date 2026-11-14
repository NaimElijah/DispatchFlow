"""Properties over generated daily problems (NFR-82), from a recorded seed."""

from hypothesis import assume, given, seed
from hypothesis import strategies as st

from dispatchflow_solver.domain.drops import DropReason, TimeWindow
from dispatchflow_solver.domain.problem import Pin, Problem
from dispatchflow_solver.domain.solution import Route, Solution
from dispatchflow_solver.domain.travel_time import TravelBucket, TravelTimeModel
from tests.domain.builders import DAY, DEPOT_LOCATION, DayBuilder
from tests.domain.properties import PROPERTY_SEED, PROPERTY_SETTINGS

points = st.floats(min_value=1, max_value=1_000, allow_nan=False)
priorities = st.integers(min_value=1, max_value=5)


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
    vehicle_count = draw(st.integers(min_value=0, max_value=3))
    for _ in range(vehicle_count):
        shift_start = draw(st.floats(min_value=0, max_value=40_000, allow_nan=False))
        shift_length = draw(st.floats(min_value=0, max_value=40_000, allow_nan=False))
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
        day.order(
            draw(st.one_of(st.none(), points)),
            weight=draw(st.floats(min_value=0, max_value=60, allow_nan=False)),
            volume=draw(st.floats(min_value=0, max_value=60, allow_nan=False)),
            service=draw(st.floats(min_value=0, max_value=600, allow_nan=False)),
            window=(window_start, window_end),
            priority=draw(priorities),
            door=draw(st.booleans()),
            pin=pin,
            reachable=draw(st.booleans()),
        )
    routable = [order for order, spec in enumerate(day.orders) if spec.location not in day.cut_off]
    served = draw(st.lists(st.sampled_from(routable), unique=True)) if routable else []
    routes: list[Route] = []
    if vehicle_count:
        for vehicle in range(vehicle_count):
            stops = tuple(order for order in served if order % vehicle_count == vehicle)
            routes.append(
                Route(
                    vehicle=vehicle,
                    driver=vehicle,
                    start=DEPOT_LOCATION,
                    end=DEPOT_LOCATION,
                    stops=stops,
                )
            )
    day.removed_by_dispatcher = set(
        draw(st.lists(st.sampled_from(range(len(day.orders))), unique=True))
    )
    on_routes = {order for route in routes for order in route.stops}
    dropped = frozenset(order for order in range(len(day.orders)) if order not in on_routes)
    return day.build(), Solution(routes=tuple(routes), dropped=dropped)


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(any_day_and_solution())
def test_given_any_solution_when_drops_explained_then_every_dropped_order_has_a_reason(
    case: tuple[Problem, Solution],
) -> None:
    problem, solution = case

    for order in problem.unserved(solution):
        reason = problem.explain_drop(order, solution)

        assert isinstance(reason, DropReason.__value__)
        if problem.orders.leave_at_door[order]:
            assert not isinstance(reason, TimeWindow)


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
