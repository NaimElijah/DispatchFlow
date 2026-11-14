import math

import pytest

from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.penalty import DEFAULT_DOOR_WINDOW_RATE, PenaltyScale


def a_scale(**terms: float) -> PenaltyScale:
    values = {
        "longest_shift": 100.0,
        "longest_door_outside_window": 0.0,
        "largest_travel": 10.0,
        "fixed_vehicle_cost": 0.0,
        "door_window_rate": DEFAULT_DOOR_WINDOW_RATE,
    }
    return PenaltyScale.for_date(**{**values, **terms})


def test_given_adr_049s_example_when_scale_built_then_k_is_the_sum_plus_one() -> None:
    # ADR-049: L = 11 hours, T = 1 hour and F = 20,000 give K = 225,201.
    scale = a_scale(longest_shift=11 * 3600, largest_travel=3600, fixed_vehicle_cost=20_000)

    assert scale.drop_unit == 225_201
    assert scale.door_window_rate == DEFAULT_DOOR_WINDOW_RATE == 5


@pytest.mark.parametrize(("priority", "units"), [(1, 5), (3, 3), (5, 1)])
def test_given_a_priority_when_drop_penalty_read_then_k_times_six_minus_it(
    priority: int, units: int
) -> None:
    assert a_scale().drop_penalty(priority) == 521 * units


@pytest.mark.parametrize("priority", [0, 6])
def test_given_a_priority_off_the_scale_when_drop_penalty_read_then_refused(priority: int) -> None:
    with pytest.raises(InvalidProblemError, match=f"priority.*{priority}"):
        a_scale().drop_penalty(priority)


def test_given_a_tuned_door_rate_when_scale_built_then_k_follows_it() -> None:
    # ADR-049: the 5 in 5L is the window-penalty rate, so K moves with it.
    assert a_scale(door_window_rate=3).drop_unit == 3 * 100 + 2 * 10 + 1


def test_given_door_time_outside_longer_than_the_shift_when_scale_built_then_l_is_that_time() -> (
    None
):
    # ADR-100: L is the larger of the longest shift and the longest door time outside a window.
    assert a_scale(longest_door_outside_window=250).drop_unit == 5 * 250 + 2 * 10 + 1


def test_given_door_time_outside_shorter_than_the_shift_when_scale_built_then_l_is_the_shift() -> (
    None
):
    assert a_scale(longest_door_outside_window=60).drop_unit == 5 * 100 + 2 * 10 + 1


@pytest.mark.parametrize(
    ("terms", "match"),
    [
        pytest.param({"longest_shift": -1}, "longest_shift", id="negative shift"),
        pytest.param(
            {"longest_door_outside_window": -1},
            "longest_door_outside_window",
            id="negative door time outside",
        ),
        pytest.param(
            {"longest_door_outside_window": math.inf},
            "longest_door_outside_window",
            id="infinite door time outside",
        ),
        pytest.param({"largest_travel": -1}, "largest_travel", id="negative travel"),
        pytest.param({"largest_travel": math.inf}, "largest_travel", id="infinite travel"),
        pytest.param({"fixed_vehicle_cost": -1}, "fixed_vehicle_cost", id="negative fixed cost"),
        pytest.param({"fixed_vehicle_cost": math.nan}, "fixed_vehicle_cost", id="NaN fixed cost"),
        pytest.param({"door_window_rate": -1}, "door_window_rate", id="negative rate"),
        pytest.param({"door_window_rate": math.inf}, "door_window_rate", id="infinite rate"),
    ],
)
def test_given_a_bad_term_when_scale_built_then_refused(
    terms: dict[str, float], match: str
) -> None:
    with pytest.raises(InvalidProblemError, match=match):
        a_scale(**terms)
