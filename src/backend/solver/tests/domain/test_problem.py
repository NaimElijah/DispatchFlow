"""The refusals of `Problem`, `Vehicles` and `Orders` when one part of a built problem is wrong.

`ProblemFactory` never builds these; each test replaces one part of a valid problem, as a caller
building the values directly could.
"""

import math
from collections.abc import Callable
from dataclasses import replace

import numpy as np
import pytest

from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.exclusions import OrderExclusion, VehicleExclusion
from dispatchflow_solver.domain.problem import Problem
from tests.domain.builders import DayBuilder


def a_problem() -> Problem:
    # Order 0 is routable at point 100; order 1 has no location.
    day = DayBuilder()
    day.vehicle()
    day.order(100)
    day.order(None)
    return day.build()


def without_roads_to_order_0(problem: Problem) -> Problem:
    durations = np.array(problem.durations)
    durations[0, 1] = durations[1, 0] = math.nan
    return replace(problem, durations=durations, distances=durations * 10)


Wrong = Callable[[Problem], object]
WRONG_PARTS: list[object] = [
    pytest.param(
        lambda p: replace(
            p, vehicles=replace(p.vehicles, exclusion=(VehicleExclusion.NO_ROAD_BETWEEN_DEPOTS,))
        ),
        r"vehicles\.exclusion\[0\] is no_road_between_depots, but the road .* exists",
        id="vehicle excluded though its depots connect",
    ),
    pytest.param(
        lambda p: replace(
            p, vehicles=replace(p.vehicles, exclusion=(VehicleExclusion.DEPOT_WITHOUT_LOCATION,))
        ),
        r"vehicles\.exclusion\[0\] is depot_without_location",
        id="vehicle excluded for a location it has",
    ),
    pytest.param(
        lambda p: replace(p, vehicles=replace(p.vehicles, start_location=np.array([5]))),
        r"vehicles\.start_location\[0\] is 5, not one of 2",
        id="vehicle start past the matrix",
    ),
    pytest.param(
        lambda p: replace(p, vehicles=replace(p.vehicles, weight_capacity_kg=np.array([1.0, 2.0]))),
        r"vehicles\.weight_capacity_kg has 2 entries, not 1",
        id="vehicle column of the wrong length",
    ),
    pytest.param(
        lambda p: replace(
            p,
            orders=replace(
                p.orders,
                exclusion=(OrderExclusion.UNREACHABLE_LOCATION, OrderExclusion.MISSING_LOCATION),
            ),
        ),
        r"orders\.exclusion\[0\] is unreachable_location, but the matrices make it reachable",
        id="order excluded though reachable",
    ),
    pytest.param(
        lambda p: replace(
            p,
            orders=replace(
                p.orders,
                exclusion=(OrderExclusion.MISSING_LOCATION, OrderExclusion.MISSING_LOCATION),
            ),
        ),
        r"orders\.exclusion\[0\] is missing_location, but orders\.location\[0\] is 1",
        id="order excluded for a location it has",
    ),
    pytest.param(
        lambda p: replace(p, orders=replace(p.orders, weight_kg=np.array([1.0]))),
        r"orders\.weight_kg has 1 entries, not 2",
        id="order column of the wrong length",
    ),
    pytest.param(
        lambda p: replace(p, drop_penalties=np.array([1.0])),
        r"drop_penalties has 1 entries for 2 orders",
        id="short drop penalties",
    ),
    pytest.param(
        lambda p: replace(p, drop_penalties=np.array([1.0, -1.0])),
        r"drop_penalties\[1\] must be finite and zero or more: -1",
        id="negative drop penalty",
    ),
    pytest.param(
        without_roads_to_order_0,
        r"orders\.exclusion\[0\] is None, but the matrices make it unreachable",
        id="exclusions disagree with the roads",
    ),
    pytest.param(
        lambda p: replace(p, distances=np.array(p.distances)[:1, :1]),
        r"durations and distances must be the same size",
        id="matrices of different sizes",
    ),
    pytest.param(
        lambda p: replace(p, day_length=math.nan),
        r"day_length must be positive and finite: nan",
        id="NaN date length",
    ),
    pytest.param(
        lambda p: replace(p, removed_by_dispatcher=frozenset({2})),
        r"removed_by_dispatcher holds order 2, not one of 2",
        id="removed order past the problem",
    ),
]


@pytest.mark.parametrize(("wrong", "match"), WRONG_PARTS)
def test_given_a_problem_with_one_wrong_part_when_built_then_refused(
    wrong: Wrong, match: str
) -> None:
    problem = a_problem()

    with pytest.raises(InvalidProblemError, match=match):
        wrong(problem)


def test_given_a_valid_problem_when_rebuilt_unchanged_then_accepted() -> None:
    problem = a_problem()

    rebuilt = replace(problem)

    assert rebuilt.orders.exclusion == (None, OrderExclusion.MISSING_LOCATION)
