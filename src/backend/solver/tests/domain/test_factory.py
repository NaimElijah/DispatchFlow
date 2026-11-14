import math

import numpy as np
import pytest

from dispatchflow_solver.domain.drops import MissingLocation, UnreachableLocation
from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.exclusions import OrderExclusion, VehicleExclusion
from dispatchflow_solver.domain.factory import (
    DepotSpec,
    DriverSpec,
    OrderSpec,
    ProblemFactory,
    VehicleSpec,
)
from dispatchflow_solver.domain.penalty import DEFAULT_DOOR_WINDOW_RATE
from dispatchflow_solver.domain.problem import Pin
from dispatchflow_solver.domain.solution import Solution
from dispatchflow_solver.domain.travel_time import TravelBucket, TravelTimeModel
from tests.domain.builders import DAY, DayBuilder

NOTHING_SERVED = Solution(routes=(), dropped=frozenset())
INT32_MAX = 2_147_483_647


def an_order(**fields: object) -> OrderSpec:
    values: dict[str, object] = {
        "location": 1,
        "weight_kg": 1,
        "volume_l": 1,
        "service_s": 0,
        "window_start": 0,
        "window_end": DAY,
        "priority": 3,
    }
    return OrderSpec(**{**values, **fields})  # type: ignore[arg-type]


def a_vehicle(**fields: object) -> VehicleSpec:
    values: dict[str, object] = {
        "start_depot": 0,
        "end_depot": 0,
        "weight_capacity_kg": 10,
        "volume_capacity_l": 10,
        "driver": 0,
    }
    return VehicleSpec(**{**values, **fields})  # type: ignore[arg-type]


def two_location_inputs(**overrides: object) -> dict[str, object]:
    inputs: dict[str, object] = {
        "orders": [an_order()],
        "depots": [DepotSpec(location=0)],
        "vehicles": [a_vehicle()],
        "drivers": [DriverSpec(0, DAY)],
        "durations": [[0, 100], [100, 0]],
        "distances": [[0, 1_000], [1_000, 0]],
        "travel": TravelTimeModel.static(),
        "day_length": DAY,
        "fixed_vehicle_cost": 0.0,
        "door_window_rate": DEFAULT_DOOR_WINDOW_RATE,
        "breaks_reserved": False,
    }
    return {**inputs, **overrides}


def build(**overrides: object) -> object:
    return ProblemFactory.daily(**two_location_inputs(**overrides))  # type: ignore[arg-type]


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
    nan = math.nan
    problem = ProblemFactory.daily(
        **two_location_inputs(  # type: ignore[arg-type]
            durations=[[0, 100], [nan, 0]], distances=[[0, 1_000], [nan, 0]]
        )
    )

    assert problem.orders.exclusion == (OrderExclusion.UNREACHABLE_LOCATION,)


def test_given_an_isolated_depot_beside_a_working_one_when_built_then_orders_stay_routable() -> (
    None
):
    # FR-18, FR-66: one depot the road network cannot reach must not sink the date.
    day = DayBuilder()
    day.vehicle()
    island = day.depot(5_000, reachable=False)
    day.vehicle(start_depot=island, end_depot=island)
    day.vehicle(start_depot=0, end_depot=island)
    day.order(100)
    day.order(200)

    problem = day.build()

    assert problem.orders.exclusion == (None, None)
    assert problem.vehicles.exclusion == (None, None, VehicleExclusion.NO_ROAD_BETWEEN_DEPOTS)
    assert problem.vehicles.usable() == (0, 1)


def test_given_an_order_reachable_only_from_the_second_depot_when_built_then_routable() -> None:
    day = DayBuilder()
    day.vehicle()
    other = day.depot(1_000)
    day.vehicle(start_depot=other, end_depot=other)
    order = day.order(900)
    day.no_road(day.location_of(order), 0)

    assert day.build().orders.exclusion == (None,)


def test_given_no_usable_vehicle_when_built_then_no_order_is_judged_unreachable() -> None:
    # With no depot to judge from, reachability is not decided. FR-223 has no reason for such a
    # date's drops; that waits on the owner.
    day = DayBuilder()
    nowhere = day.depot(None)
    day.vehicle(start_depot=nowhere, end_depot=nowhere)
    day.order(100, reachable=False)

    problem = day.build()

    assert problem.vehicles.usable() == ()
    assert problem.orders.exclusion == (None,)


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


def test_given_priorities_when_built_then_each_order_carries_its_drop_penalty() -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(100, priority=1)
    day.order(None, priority=5)

    problem = day.build()

    unit = problem.penalties.drop_unit
    assert list(problem.drop_penalties) == [5 * unit, unit]


def test_given_a_set_of_removed_orders_when_built_then_held_as_a_frozenset() -> None:
    problem = ProblemFactory.daily(
        **two_location_inputs(removed_by_dispatcher={0})  # type: ignore[arg-type]
    )

    assert problem.removed_by_dispatcher == frozenset({0})
    assert isinstance(problem.removed_by_dispatcher, frozenset)


def test_given_no_road_as_nan_when_built_then_held_as_nan() -> None:
    nan = math.nan
    problem = ProblemFactory.daily(
        **two_location_inputs(  # type: ignore[arg-type]
            durations=[[0, nan], [100, 0]], distances=[[0, nan], [1_000, 0]]
        )
    )

    assert math.isnan(problem.durations[0, 1])
    assert math.isnan(problem.distances[0, 1])


def test_given_a_built_problem_when_its_arrays_written_then_refused() -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(100)
    problem = day.build()

    arrays = (
        problem.durations,
        problem.orders.window_end,
        problem.vehicles.driver,
        problem.drop_penalties,
    )
    for array in arrays:
        with pytest.raises(ValueError, match="read-only"):
            array[0] = 1


def test_given_the_inputs_changed_after_building_when_read_then_the_problem_is_unchanged() -> None:
    durations = np.array([[0.0, 100.0], [100.0, 0.0]])
    problem = ProblemFactory.daily(**two_location_inputs(durations=durations))  # type: ignore[arg-type]

    durations[0, 1] = 5.0

    assert problem.durations[0, 1] == 100


def test_given_depot_hours_open_ended_when_built_then_accepted() -> None:
    depot = DepotSpec(location=0, opens=-math.inf, closes=math.inf)

    problem = ProblemFactory.daily(**two_location_inputs(depots=[depot]))  # type: ignore[arg-type]

    assert problem.vehicles.start_depot_opens[0] == -math.inf
    assert problem.vehicles.end_depot_closes[0] == math.inf


NAN = math.nan
REFUSED = [
    pytest.param({"durations": [[0, -1], [100, 0]]}, r"durations\[0, 1\]", id="negative entry"),
    pytest.param({"durations": [[0, math.inf], [100, 0]]}, r"durations\[0, 1\]", id="inf entry"),
    pytest.param(
        {"durations": [[0, INT32_MAX + 1], [100, 0]]}, r"durations\[0, 1\]", id="past int32"
    ),
    pytest.param({"durations": [[0, "x"], [100, 0]]}, "durations", id="not numbers"),
    pytest.param({"durations": [[0, 100, 1], [100, 0, 1]]}, "durations.*square", id="not square"),
    pytest.param(
        {"durations": [[0, 100], [100, 0], [1, 1]]}, "durations.*square", id="tall matrix"
    ),
    pytest.param(
        {"distances": [[0, 100, 1], [100, 0, 1], [1, 1, 0]]}, "distances", id="sizes differ"
    ),
    pytest.param({"durations": [[0, NAN], [100, 0]]}, r"no road.*\[0, 1\]", id="roads disagree"),
    pytest.param(
        {"orders": [an_order(priority=2.7)]}, r"priority\[0\].*int.*2\.7", id="priority 2.7"
    ),
    pytest.param(
        {"orders": [an_order(priority="3")]}, r"priority\[0\].*int.*'3'", id="priority '3'"
    ),
    pytest.param(
        {"orders": [an_order(priority=True)]}, r"priority\[0\].*int.*True", id="priority bool"
    ),
    pytest.param(
        {"orders": [an_order(location=1.9)]}, r"orders\[0\]\.location.*int", id="location 1.9"
    ),
    pytest.param(
        {"orders": [an_order(location=True)]}, r"orders\[0\]\.location.*int", id="location bool"
    ),
    pytest.param(
        {"depots": [DepotSpec(location=0.0)]},  # type: ignore[arg-type]
        r"depots\[0\]\.location.*int",
        id="depot location float",
    ),
    pytest.param(
        {"vehicles": [a_vehicle(start_depot=0.0)]},
        r"vehicles\[0\]\.start_depot.*int",
        id="depot index float",
    ),
    pytest.param(
        {"vehicles": [a_vehicle(end_depot=False)]},
        r"vehicles\[0\]\.end_depot.*int",
        id="depot index bool",
    ),
    pytest.param(
        {"vehicles": [a_vehicle(driver=0.0)]}, r"driver\[0\].*int", id="driver index float"
    ),
    pytest.param(
        {"orders": [an_order(pin=Pin(vehicle=0.0))]},  # type: ignore[arg-type]
        r"pinned_vehicle\[0\].*int",
        id="pin index float",
    ),
    pytest.param({"orders": [an_order(priority=0)]}, r"priority\[0\]", id="priority 0"),
    pytest.param({"orders": [an_order(priority=6)]}, r"priority\[0\]", id="priority 6"),
    pytest.param({"orders": [an_order(priority=2**64)]}, "priority", id="priority overflow"),
    pytest.param(
        {"orders": [an_order(window_start=500, window_end=100)]}, r"window\w*\[0\]", id="window"
    ),
    pytest.param({"orders": [an_order(weight_kg=-1)]}, r"weight_kg\[0\]", id="negative weight"),
    pytest.param({"orders": [an_order(weight_kg=10**400)]}, "weight_kg", id="weight overflow"),
    pytest.param({"orders": [an_order(service_s=-5)]}, r"service_s\[0\]", id="negative service"),
    pytest.param({"orders": [an_order(location=7)]}, r"location.*7", id="order location"),
    pytest.param({"orders": [an_order(location=2**64)]}, "location", id="location overflow"),
    pytest.param(
        {"orders": [an_order(pin=Pin(vehicle=3))]}, r"pinned_vehicle\[0\]", id="pin vehicle"
    ),
    pytest.param({"orders": [an_order(pin=Pin(driver=3))]}, r"pinned_driver\[0\]", id="pin driver"),
    pytest.param({"depots": [DepotSpec(location=5)]}, r"depots\[0\].*5", id="depot location"),
    pytest.param(
        {"depots": [DepotSpec(location=0, opens=NAN)]}, r"depots\[0\].*opens", id="opens NaN"
    ),
    pytest.param(
        {"depots": [DepotSpec(location=0, opens=math.inf)]}, r"depots\[0\].*opens", id="opens inf"
    ),
    pytest.param(
        {"depots": [DepotSpec(location=0, closes=-math.inf)]},
        r"depots\[0\].*closes",
        id="closes -inf",
    ),
    pytest.param(
        {"depots": [DepotSpec(location=0, opens=500, closes=100)]},
        r"depots\[0\].*opens.*after",
        id="opens after closing",
    ),
    pytest.param({"vehicles": [a_vehicle(start_depot=4)]}, r"vehicles\[0\].*4", id="depot index"),
    pytest.param({"vehicles": [a_vehicle(driver=2)]}, r"driver\[0\]", id="driver index"),
    pytest.param(
        {"vehicles": [a_vehicle(weight_capacity_kg=-1)]},
        r"weight_capacity_kg\[0\]",
        id="negative weight capacity",
    ),
    pytest.param(
        {"vehicles": [a_vehicle(weight_capacity_kg=math.inf)]},
        r"weight_capacity_kg\[0\]",
        id="infinite weight capacity",
    ),
    pytest.param(
        {"vehicles": [a_vehicle(volume_capacity_l=-1)]},
        r"volume_capacity_l\[0\]",
        id="negative volume capacity",
    ),
    pytest.param(
        {"vehicles": [a_vehicle(volume_capacity_l=math.inf)]},
        r"volume_capacity_l\[0\]",
        id="infinite volume capacity",
    ),
    pytest.param(
        {"vehicles": [a_vehicle(), a_vehicle()]},
        r"driver 0 is rostered to vehicles 0 and 1",
        id="driver on two vehicles",
    ),
    pytest.param({"drivers": [DriverSpec(500, 100)]}, r"shift\w*\[0\]", id="shift backwards"),
    pytest.param({"drivers": [DriverSpec(100, 100)]}, r"shift\w*\[0\]", id="zero shift"),
    pytest.param({"drivers": [DriverSpec(-1, 100)]}, r"shift_start\[0\]", id="before the date"),
    pytest.param({"drivers": [DriverSpec(0, DAY + 1)]}, r"shift_end\[0\]", id="after the date"),
    pytest.param({"day_length": 0.0}, "day_length", id="empty date"),
    pytest.param({"fixed_vehicle_cost": -1.0}, "fixed_vehicle_cost", id="negative fixed cost"),
    pytest.param({"fixed_vehicle_cost": NAN}, "fixed_vehicle_cost", id="NaN fixed cost"),
    pytest.param({"door_window_rate": -1.0}, "door_window_rate", id="negative door rate"),
    pytest.param({"door_window_rate": math.inf}, "door_window_rate", id="infinite door rate"),
    pytest.param(
        {"removed_by_dispatcher": frozenset({4})}, r"removed_by_dispatcher.*4", id="removed order"
    ),
]


@pytest.mark.parametrize(("overrides", "match"), REFUSED)
def test_given_inputs_no_problem_can_hold_when_built_then_refused(
    overrides: dict[str, object], match: str
) -> None:
    with pytest.raises(InvalidProblemError, match=match):
        build(**overrides)


@pytest.mark.parametrize(
    "pin", [lambda: Pin(), lambda: Pin(vehicle=0, driver=0), lambda: Pin(vehicle=-1)]
)
def test_given_a_pin_to_neither_both_or_a_negative_index_when_built_then_refused(
    pin: object,
) -> None:
    with pytest.raises(InvalidProblemError, match="pin"):
        pin()  # type: ignore[operator]
