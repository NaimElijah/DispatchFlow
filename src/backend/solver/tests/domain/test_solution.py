import math

import pytest

from dispatchflow_solver.domain.solution import InvalidSolutionError, Route, Solution


def test_given_routes_breaking_every_rule_when_built_then_accepted_as_content() -> None:
    # A solution checks shape only, so the violation catalogue can build anything (NFR-169).
    twice = Route(vehicle=0, driver=0, start=7, end=9, stops=(3, 3), planned_starts=(0.0, 0.0))

    solution = Solution(routes=(twice, twice), dropped=frozenset({3}))

    assert solution.served() == {3}


@pytest.mark.parametrize(
    "fields",
    [
        {"vehicle": -1},
        {"driver": -1},
        {"start": -1},
        {"end": -1},
        {"stops": (0, -1)},
        {"vehicle": True},
        {"vehicle": 1.0},
        {"stops": [0, 1]},
        {"planned_starts": (0.0,)},
        {"planned_starts": (0.0, math.inf)},
        {"planned_starts": (0.0, math.nan)},
        {"planned_starts": [0.0, 1.0]},
    ],
)
def test_given_a_badly_shaped_route_when_built_then_refused(fields: dict[str, object]) -> None:
    values: dict[str, object] = {"vehicle": 0, "driver": 0, "start": 0, "end": 0, "stops": (0, 1)}

    with pytest.raises(InvalidSolutionError):
        Route(**{**values, **fields})  # type: ignore[arg-type]


@pytest.mark.parametrize(
    ("routes", "dropped"),
    [
        ([], frozenset()),
        (("route",), frozenset()),
        ((), {0}),
        ((), frozenset({-1})),
    ],
)
def test_given_a_badly_shaped_solution_when_built_then_refused(
    routes: object, dropped: object
) -> None:
    with pytest.raises(InvalidSolutionError):
        Solution(routes=routes, dropped=dropped)  # type: ignore[arg-type]
