import math

import numpy as np
import pytest

from dispatchflow_solver.domain.drops import MissingLocation, UnreachableLocation
from dispatchflow_solver.domain.factory import (
    DepotSpec,
    DriverSpec,
    OrderSpec,
    ProblemFactory,
    VehicleSpec,
)
from dispatchflow_solver.domain.problem import (
    InvalidProblemError,
    OrderExclusion,
    Pin,
    VehicleExclusion,
)
from dispatchflow_solver.domain.solution import Solution
from dispatchflow_solver.domain.travel_time import TravelBucket, TravelTimeModel
from tests.domain.builders import DAY, DayBuilder

NOTHING_SERVED = Solution(routes=(), dropped=frozenset())


def two_location_inputs(**overrides: object) -> dict[str, object]:
    inputs: dict[str, object] = {
        "orders": [OrderSpec(1, 1, 1, 0, 0, DAY, 3)],
        "depots": [DepotSpec(location=0)],
        "vehicles": [VehicleSpec(0, 0, 10, 10, driver=0)],
        "drivers": [DriverSpec(0, DAY)],
        "durations": [[0, 100], [100, 0]],
        "distances": [[0, 1_000], [1_000, 0]],
        "travel": TravelTimeModel.static(),
        "day_length": DAY,
    }
    return {**inputs, **overrides}


def test_given_orders_without_location_or_road_when_built_then_excluded_with_reasons() -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(100)
    day.order(None)
    day.order(200, reachable=False)

    problem = day.build()

    assert problem.orders.exclusion == (
        None,
        OrderExclusion.MISSING_LOCATION,
        OrderExclusion.UNREACHABLE_LOCATION,
    )
    assert problem.explain_drop(1, NOTHING_SERVED) == MissingLocation()
    assert problem.explain_drop(2, NOTHING_SERVED) == UnreachableLocation()


def test_given_a_location_with_no_road_back_to_the_depot_when_built_then_unreachable() -> None:
    inputs = two_location_inputs(durations=[[0, 100], [-1, 0]], distances=[[0, 1_000], [-1, 0]])

    problem = ProblemFactory.daily(**inputs)  # type: ignore[arg-type]

    assert problem.orders.exclusion == (OrderExclusion.UNREACHABLE_LOCATION,)


def test_given_a_depot_without_coordinates_when_built_then_its_vehicles_are_named_out() -> None:
    day = DayBuilder()
    day.vehicle()
    nowhere = day.depot(None)
    day.vehicle(start_depot=nowhere, end_depot=0)
    day.vehicle(start_depot=0, end_depot=nowhere)

    problem = day.build()

    assert problem.vehicles.exclusion == (
        None,
        VehicleExclusion.DEPOT_WITHOUT_LOCATION,
        VehicleExclusion.DEPOT_WITHOUT_LOCATION,
    )
    assert problem.vehicles.usable() == (0,)


def test_given_a_day_when_built_then_k_is_five_l_plus_two_t_plus_f_plus_one() -> None:
    # L: the longer shift, 500 s. T: the largest finite entry, 300 s, at the highest factor 1.5,
    # unbuffered: ceil(450) = 450. F: 7.
    peak = TravelBucket(start=0, end=10, factor=1.5)
    day = DayBuilder(
        travel=TravelTimeModel(buckets=(peak,), off_peak_factor=1.0, buffer=0.1),
        fixed_vehicle_cost=7,
    )
    day.vehicle(shift=(100, 300))
    day.vehicle(shift=(0, 500))
    day.order(100)
    day.order(300)
    day.order(5_000, reachable=False)

    problem = day.build()

    assert problem.penalties.drop_unit == 5 * 500 + 2 * 450 + 7 + 1


def test_given_contract_no_road_entries_when_built_then_held_as_nan() -> None:
    inputs = two_location_inputs(durations=[[0, -1], [100, 0]], distances=[[0, -1], [1_000, 0]])

    problem = ProblemFactory.daily(**inputs)  # type: ignore[arg-type]

    assert math.isnan(problem.durations[0, 1])
    assert math.isnan(problem.distances[0, 1])


@pytest.mark.parametrize("entry", [-2.0, math.nan])
def test_given_a_negative_or_nan_matrix_entry_when_built_then_refused(entry: float) -> None:
    inputs = two_location_inputs(durations=[[0, entry], [100, 0]])

    with pytest.raises(InvalidProblemError):
        ProblemFactory.daily(**inputs)  # type: ignore[arg-type]


def test_given_a_built_problem_when_its_arrays_written_then_refused() -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(100)
    problem = day.build()

    for array in (problem.durations, problem.orders.window_end, problem.vehicles.driver):
        with pytest.raises(ValueError):
            array[0] = 1


def test_given_the_inputs_changed_after_building_when_read_then_the_problem_is_unchanged() -> None:
    durations = np.array([[0.0, 100.0], [100.0, 0.0]])
    problem = ProblemFactory.daily(**two_location_inputs(durations=durations))  # type: ignore[arg-type]

    durations[0, 1] = 5.0

    assert problem.durations[0, 1] == 100


def test_given_two_routable_orders_with_no_road_between_them_when_built_then_refused() -> None:
    inputs = two_location_inputs(
        orders=[OrderSpec(1, 1, 1, 0, 0, DAY, 3), OrderSpec(2, 1, 1, 0, 0, DAY, 3)],
        durations=[[0, 1, 1], [1, 0, -1], [1, -1, 0]],
        distances=[[0, 1, 1], [1, 0, -1], [1, -1, 0]],
    )

    with pytest.raises(InvalidProblemError):
        ProblemFactory.daily(**inputs)  # type: ignore[arg-type]


@pytest.mark.parametrize(
    "overrides",
    [
        {"orders": [OrderSpec(1, 1, 1, 0, 0, DAY, 0)]},
        {"orders": [OrderSpec(1, 1, 1, 0, 0, DAY, 6)]},
        {"orders": [OrderSpec(1, 1, 1, 0, 500, 100, 3)]},
        {"orders": [OrderSpec(1, -1, 1, 0, 0, DAY, 3)]},
        {"orders": [OrderSpec(1, 1, 1, -5, 0, DAY, 3)]},
        {"orders": [OrderSpec(7, 1, 1, 0, 0, DAY, 3)]},
        {"orders": [OrderSpec(1, 1, 1, 0, 0, DAY, 3, pin=Pin(vehicle=3))]},
        {"orders": [OrderSpec(1, 1, 1, 0, 0, DAY, 3, pin=Pin(driver=3))]},
        {"vehicles": [VehicleSpec(0, 4, 10, 10, driver=0)]},
        {"vehicles": [VehicleSpec(0, 0, 10, 10, driver=2)]},
        {"vehicles": [VehicleSpec(0, 0, -1, 10, driver=0)]},
        {"drivers": [DriverSpec(500, 100)]},
        {"day_length": 0.0},
        {"durations": [[0, 100, 1], [100, 0, 1]]},
        {"durations": [[0, 100], [100, 0], [1, 1]]},
        {"distances": [[0, 100, 1], [100, 0, 1], [1, 1, 0]]},
        {"removed_by_dispatcher": frozenset({4})},
    ],
)
def test_given_inputs_no_problem_can_hold_when_built_then_refused(
    overrides: dict[str, object],
) -> None:
    with pytest.raises(InvalidProblemError):
        ProblemFactory.daily(**two_location_inputs(**overrides))  # type: ignore[arg-type]


@pytest.mark.parametrize(
    "pin", [lambda: Pin(), lambda: Pin(vehicle=0, driver=0), lambda: Pin(vehicle=-1)]
)
def test_given_a_pin_to_neither_both_or_a_negative_index_when_built_then_refused(
    pin: object,
) -> None:
    with pytest.raises(InvalidProblemError):
        pin()  # type: ignore[operator]
