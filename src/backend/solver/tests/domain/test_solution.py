import math

import pytest

from dispatchflow_solver.domain.solution import InvalidSolutionError, Route, Solution


def test_given_routes_breaking_every_rule_when_built_then_accepted_as_content() -> None:
    # A solution checks shape only, so the violation catalogue can build anything (NFR-169).
    twice = Route(vehicle=0, driver=0, start=7, end=9, stops=(3, 3), planned_starts=(0.0, 0.0))

    solution = Solution(routes=(twice, twice), dropped=frozenset({3}))

    assert solution.served() == {3}


@pytest.mark.parametrize(
    ("fields", "match"),
    [
        pytest.param({"vehicle": -1}, r"vehicle must be a non-negative int: -1$", id="vehicle"),
        pytest.param({"driver": -1}, r"driver must .*: -1$", id="driver"),
        pytest.param({"start": -1}, r"start must .*: -1$", id="start"),
        pytest.param({"end": -1}, r"end must .*: -1$", id="end"),
        pytest.param({"stops": (0, -1)}, r"stops\[1\] must .*: -1$", id="stop"),
        pytest.param({"vehicle": True}, r"vehicle must .*: True$", id="bool index"),
        pytest.param({"vehicle": 1.0}, r"vehicle must .*: 1.0$", id="float index"),
        pytest.param({"stops": [0, 1]}, r"stops must be a tuple, not list$", id="stops list"),
        pytest.param({"planned_starts": (0.0,)}, r"1 planned starts for 2 stops$", id="count"),
        pytest.param(
            {"planned_starts": (0.0, math.inf)}, r"planned_starts\[1\] .*: inf$", id="infinite"
        ),
        pytest.param(
            {"planned_starts": (0.0, math.nan)}, r"planned_starts\[1\] .*: nan$", id="NaN"
        ),
        pytest.param(
            {"planned_starts": [0.0, 1.0]}, r"planned_starts must be a tuple, not list$", id="list"
        ),
    ],
)
def test_given_a_badly_shaped_route_when_built_then_refused_naming_the_field(
    fields: dict[str, object], match: str
) -> None:
    values: dict[str, object] = {"vehicle": 0, "driver": 0, "start": 0, "end": 0, "stops": (0, 1)}

    with pytest.raises(InvalidSolutionError, match=match):
        Route(**{**values, **fields})  # type: ignore[arg-type]


@pytest.mark.parametrize(
    ("routes", "dropped", "match"),
    [
        pytest.param([], frozenset(), r"routes must be a tuple, not list$", id="routes list"),
        pytest.param(("route",), frozenset(), r"routes\[0\] is a str", id="not a route"),
        pytest.param((), {0}, r"dropped must be a frozenset, not set$", id="dropped set"),
        pytest.param((), frozenset({-1}), r"dropped order must .*: -1$", id="negative drop"),
    ],
)
def test_given_a_badly_shaped_solution_when_built_then_refused_naming_the_field(
    routes: object, dropped: object, match: str
) -> None:
    with pytest.raises(InvalidSolutionError, match=match):
        Solution(routes=routes, dropped=dropped)  # type: ignore[arg-type]
