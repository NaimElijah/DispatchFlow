import math
from dataclasses import replace

import numpy as np
import pytest

from dispatchflow_optimizer.domain.problem import Problem
from dispatchflow_optimizer.domain.solution import Route, Solution
from dispatchflow_optimizer.domain.travel_time import TravelBucket, TravelTimeModel
from tests.domain.builders import DEPOT_LOCATION, DayBuilder


def route(vehicle: int, *stops: int) -> Route:
    return Route(
        vehicle=vehicle, driver=vehicle, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=stops
    )


def costed(problem: Problem, solution: Solution) -> tuple[float, float, float, float]:
    cost = problem.cost(solution, problem.validate(solution).timings)
    return cost.travel, cost.drops, cost.door_window, cost.fixed_vehicles


def test_given_a_peak_and_a_buffer_when_costed_then_travel_is_per_bucket_without_the_buffer() -> (
    None
):
    # Out at 0 in a 2x bucket, buffered to ceil(100 x 2 x 1.5) = 300, so back at 300 off-peak.
    # The objective charges ceil(100 x 2) = 200 out and 100 back (FR-402).
    peak = TravelBucket(start=0, end=150, factor=2.0)
    day = DayBuilder(travel=TravelTimeModel(buckets=(peak,), off_peak_factor=1.0, buffer=0.5))
    day.vehicle()
    day.order(100)
    problem = day.build()
    solution = Solution(routes=(route(0, 0),), dropped=frozenset())

    report = problem.validate(solution)

    assert list(report.timings[0].leg_seconds) == [300, 150]
    assert costed(problem, solution) == (300, 0, 0, 0)


def test_given_dropped_orders_when_costed_then_each_pays_k_times_six_minus_priority() -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(100, priority=1)
    day.order(200, priority=5)
    problem = day.build()
    unit = problem.penalties.drop_unit

    drops = costed(problem, Solution(routes=(), dropped=frozenset({0, 1})))[1]

    assert drops == 5 * unit + 1 * unit


def test_given_an_order_excluded_before_the_search_when_costed_then_it_pays_its_drop() -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(None, priority=2)
    problem = day.build()

    assert costed(problem, Solution(routes=(), dropped=frozenset()))[1] == (
        4 * problem.penalties.drop_unit
    )


def test_given_a_door_stop_outside_its_window_when_costed_then_five_per_second_outside() -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(100, door=True, window=(500, 600))
    problem = day.build()

    door = costed(problem, Solution(routes=(route(0, 0),), dropped=frozenset()))[2]

    assert door == 5 * 400


def test_given_a_hard_window_stop_waiting_when_costed_then_no_window_penalty() -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(100, window=(500, 600))
    problem = day.build()

    assert costed(problem, Solution(routes=(route(0, 0),), dropped=frozenset()))[2] == 0


def test_given_a_hard_window_stop_served_late_when_costed_then_no_window_penalty() -> None:
    # BR-120 prices door stops only; a late hard-window stop is a violation, not a cost.
    day = DayBuilder()
    day.vehicle()
    day.order(100, window=(0, 50))
    problem = day.build()

    assert costed(problem, Solution(routes=(route(0, 0),), dropped=frozenset()))[2] == 0


def test_given_a_fixed_cost_when_costed_then_paid_per_vehicle_that_serves_a_stop() -> None:
    day = DayBuilder(fixed_vehicle_cost=1_000)
    for _ in range(3):
        day.vehicle()
    day.order(100)
    day.order(200)
    problem = day.build()
    solution = Solution(routes=(route(0, 0), route(1, 1), route(2)), dropped=frozenset())

    assert costed(problem, solution)[3] == 2_000


def test_given_a_cost_when_totalled_then_the_sum_of_its_terms() -> None:
    day = DayBuilder(fixed_vehicle_cost=1_000)
    day.vehicle()
    day.order(100, door=True, window=(500, 600))
    day.order(300, priority=5)
    problem = day.build()
    solution = Solution(routes=(route(0, 0),), dropped=frozenset({1}))

    cost = problem.cost(solution, problem.validate(solution).timings)

    assert cost.total == 200 + problem.penalties.drop_unit + 2_000 + 1_000


@pytest.mark.parametrize("excluded", ["no location", "unreachable"])
def test_given_an_excluded_stop_off_peak_at_1_2_when_costed_then_its_legs_are_skipped(
    excluded: str,
) -> None:
    # The validator skips the excluded stop and goes on from order 0, so the travel is 0 to 100
    # to 200 to 0, each leg at ceil(base x 1.2): 120 + 120 + 240.
    day = DayBuilder(travel=TravelTimeModel(buckets=(), off_peak_factor=1.2, buffer=0.0))
    day.vehicle()
    day.order(100)
    stray = day.order(None) if excluded == "no location" else day.order(5_000, reachable=False)
    last = day.order(200)
    problem = day.build()
    solution = Solution(routes=(route(0, 0, stray, last),), dropped=frozenset())

    travel = costed(problem, solution)[0]

    assert travel == 480
    assert not math.isnan(travel)


def test_given_drop_penalties_in_the_problem_when_costed_then_those_are_charged() -> None:
    # The objective reads each order's penalty as the factory wrote it, not its priority.
    day = DayBuilder()
    day.vehicle()
    day.order(100, priority=1)
    day.order(200, priority=1)
    problem = replace(day.build(), drop_penalties=np.array([7.0, 11.0]))

    assert costed(problem, Solution(routes=(), dropped=frozenset({0, 1})))[1] == 18


def test_given_timings_of_another_solution_when_costed_then_refused() -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(100)
    day.order(200)
    problem = day.build()
    one = Solution(routes=(route(0, 0),), dropped=frozenset({1}))
    other = Solution(routes=(route(0, 0, 1),), dropped=frozenset())

    with pytest.raises(ValueError, match=r"^the timings do not belong to this solution$"):
        problem.cost(other, problem.validate(one).timings)
