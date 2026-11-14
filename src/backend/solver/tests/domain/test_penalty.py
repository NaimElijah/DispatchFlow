import pytest

from dispatchflow_solver.domain.penalty import DEFAULT_DOOR_WINDOW_RATE, PenaltyScale


def test_given_adr_049s_example_when_scale_built_then_k_is_the_sum_plus_one() -> None:
    # ADR-049: L = 11 hours, T = 1 hour and F = 20,000 give K = 225,201.
    scale = PenaltyScale.for_date(
        longest_shift=11 * 3600, largest_travel=3600, fixed_vehicle_cost=20_000
    )

    assert scale.drop_unit == 225_201
    assert scale.door_window_rate == DEFAULT_DOOR_WINDOW_RATE == 5


@pytest.mark.parametrize(("priority", "units"), [(1, 5), (3, 3), (5, 1)])
def test_given_a_priority_when_drop_penalty_read_then_k_times_six_minus_it(
    priority: int, units: int
) -> None:
    scale = PenaltyScale.for_date(longest_shift=100, largest_travel=10, fixed_vehicle_cost=0)

    assert scale.drop_penalty(priority) == 521 * units


@pytest.mark.parametrize("priority", [0, 6])
def test_given_a_priority_off_the_scale_when_drop_penalty_read_then_refused(priority: int) -> None:
    scale = PenaltyScale.for_date(longest_shift=100, largest_travel=10, fixed_vehicle_cost=0)

    with pytest.raises(ValueError):
        scale.drop_penalty(priority)


def test_given_a_tuned_door_rate_when_scale_built_then_k_follows_it() -> None:
    # ADR-049: the 5 in 5L is the window-penalty rate, so K moves with it.
    scale = PenaltyScale.for_date(
        longest_shift=100, largest_travel=10, fixed_vehicle_cost=0, door_window_rate=3
    )

    assert scale.drop_unit == 3 * 100 + 2 * 10 + 1


@pytest.mark.parametrize(("shift", "travel", "fixed"), [(-1, 0, 0), (0, -1, 0), (0, 0, -1)])
def test_given_a_negative_term_when_scale_built_then_refused(
    shift: float, travel: float, fixed: float
) -> None:
    with pytest.raises(ValueError):
        PenaltyScale.for_date(longest_shift=shift, largest_travel=travel, fixed_vehicle_cost=fixed)
