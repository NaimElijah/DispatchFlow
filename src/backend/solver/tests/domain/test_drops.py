"""explain_drop in ADR-053's order: each case is an order that would also fail a later test, so
the expected reason is the first that applies.

The day: a depot at point 0 and a vehicle carrying 10 kg and 10 l, its driver on shift 0 to 1,000.
"""

import pytest

from dispatchflow_solver.domain.drops import (
    Capacity,
    DepotHours,
    Displaced,
    MissingLocation,
    PinNotHonoured,
    RemovedByDispatcher,
    Shift,
    TimeWindow,
    UnreachableLocation,
)
from dispatchflow_solver.domain.problem import Pin
from dispatchflow_solver.domain.solution import Route, Solution
from tests.domain.builders import DEPOT_LOCATION, DayBuilder

NOTHING_SERVED = Solution(routes=(), dropped=frozenset({0}))
TOO_HEAVY = 11.0
TOO_LATE = (0.0, 50.0)


def a_day(*, shift: tuple[float, float] = (0, 1_000)) -> DayBuilder:
    day = DayBuilder()
    day.vehicle(weight=10, volume=10, shift=shift)
    return day


def test_given_an_order_with_no_location_when_explained_then_missing_location() -> None:
    day = a_day()
    day.order(None, weight=TOO_HEAVY)

    assert day.build().explain_drop(0, NOTHING_SERVED) == MissingLocation()


def test_given_an_order_with_no_road_route_when_explained_then_unreachable_location() -> None:
    day = a_day()
    day.order(100, weight=TOO_HEAVY, reachable=False)

    assert day.build().explain_drop(0, NOTHING_SERVED) == UnreachableLocation()


def test_given_an_order_a_dispatcher_removed_when_explained_then_removed_by_dispatcher() -> None:
    day = a_day()
    day.order(100, weight=TOO_HEAVY, pin=Pin(vehicle=0))
    day.removed_by_dispatcher.add(0)

    assert day.build().explain_drop(0, NOTHING_SERVED) == RemovedByDispatcher()


def test_given_a_pin_to_a_vehicle_that_is_out_when_explained_then_pin_with_target_out() -> None:
    day = a_day()
    nowhere = day.depot(None)
    day.vehicle(start_depot=nowhere, end_depot=nowhere)
    day.order(100, weight=TOO_HEAVY, pin=Pin(vehicle=1))

    assert day.build().explain_drop(0, NOTHING_SERVED) == PinNotHonoured(Pin(vehicle=1), True)


def test_given_a_pin_to_a_driver_with_no_usable_vehicle_when_explained_then_target_out() -> None:
    day = a_day()
    idle = day.driver()
    day.order(100, pin=Pin(driver=idle))

    assert day.build().explain_drop(0, NOTHING_SERVED) == PinNotHonoured(Pin(driver=idle), True)


def test_given_a_pin_to_a_vehicle_that_cannot_carry_it_when_explained_then_pin_not_capacity() -> (
    None
):
    day = a_day()
    day.vehicle(weight=100, volume=100)
    day.order(100, weight=TOO_HEAVY, pin=Pin(vehicle=0))

    assert day.build().explain_drop(0, NOTHING_SERVED) == PinNotHonoured(Pin(vehicle=0), False)


def test_given_a_pin_to_a_driver_who_cannot_make_the_window_when_explained_then_pin() -> None:
    day = a_day()
    day.order(100, window=TOO_LATE, pin=Pin(driver=0))

    assert day.build().explain_drop(0, NOTHING_SERVED) == PinNotHonoured(Pin(driver=0), False)


def test_given_a_pin_its_target_can_serve_when_explained_then_displaced() -> None:
    day = a_day()
    day.order(100, pin=Pin(vehicle=0))

    assert day.build().explain_drop(0, NOTHING_SERVED) == Displaced()


def test_given_an_order_heavier_than_every_vehicle_when_explained_then_capacity_with_facts() -> (
    None
):
    day = a_day()
    day.vehicle(weight=8, volume=20)
    day.order(100, weight=TOO_HEAVY, volume=5, window=TOO_LATE)

    assert day.build().explain_drop(0, NOTHING_SERVED) == Capacity(
        weight_kg=TOO_HEAVY,
        volume_l=5,
        largest_weight_capacity_kg=10,
        largest_volume_capacity_l=20,
    )


def test_given_an_order_larger_than_every_vehicle_when_explained_then_capacity() -> None:
    day = a_day()
    day.order(100, volume=11)

    assert isinstance(day.build().explain_drop(0, NOTHING_SERVED), Capacity)


def test_given_each_vehicle_fits_only_one_measure_when_explained_then_capacity() -> None:
    day = DayBuilder()
    day.vehicle(weight=20, volume=5)
    day.vehicle(weight=5, volume=20)
    day.order(100, weight=10, volume=10)

    assert isinstance(day.build().explain_drop(0, NOTHING_SERVED), Capacity)


def test_given_a_window_ending_before_the_earliest_arrival_when_explained_then_time_window() -> (
    None
):
    day = a_day(shift=(0, 150))
    day.order(100, window=TOO_LATE)

    assert day.build().explain_drop(0, NOTHING_SERVED) == TimeWindow(
        window_end=50, earliest_arrival=100
    )


def test_given_the_earliest_arrival_from_any_vehicle_when_explained_then_it_is_the_fact() -> None:
    day = a_day()
    day.vehicle(shift=(30, 1_000))
    day.order(100, window=TOO_LATE)

    reason = day.build().explain_drop(0, NOTHING_SERVED)

    assert reason == TimeWindow(window_end=50, earliest_arrival=100)


def test_given_a_door_order_reached_after_its_window_when_explained_then_not_time_window() -> None:
    # BR-121: a door order is never dropped for its window.
    day = a_day()
    day.order(100, window=TOO_LATE, door=True)

    assert day.build().explain_drop(0, NOTHING_SERVED) == Displaced()


def test_given_a_round_trip_past_the_shift_when_explained_then_shift_with_facts() -> None:
    day = a_day(shift=(0, 199))
    day.order(100)

    assert day.build().explain_drop(0, NOTHING_SERVED) == Shift(
        latest_return=199, earliest_return=200
    )


def test_given_a_round_trip_into_the_break_reserve_when_explained_then_shift() -> None:
    day = DayBuilder(breaks_reserved=True)
    day.vehicle(shift=(0, 21_600))
    day.order(9_451)

    assert day.build().explain_drop(0, NOTHING_SERVED) == Shift(
        latest_return=18_900, earliest_return=18_902
    )


def test_given_a_door_order_reached_after_its_shift_when_explained_then_shift() -> None:
    day = a_day(shift=(0, 99))
    day.order(100, door=True)

    assert isinstance(day.build().explain_drop(0, NOTHING_SERVED), Shift)


def test_given_a_round_trip_past_depot_closing_when_explained_then_depot_hours() -> None:
    day = a_day()
    day.depot_hours(closes=199)
    day.order(100)

    assert day.build().explain_drop(0, NOTHING_SERVED) == DepotHours(
        depot_closes=199, earliest_return=200
    )


def test_given_an_order_that_fits_alone_when_explained_then_displaced() -> None:
    day = a_day()
    day.order(100)

    assert day.build().explain_drop(0, NOTHING_SERVED) == Displaced()


def test_given_a_served_order_when_explained_then_refused() -> None:
    day = a_day()
    day.order(100)
    route = Route(vehicle=0, driver=0, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(0,))

    with pytest.raises(ValueError):
        day.build().explain_drop(0, Solution(routes=(route,), dropped=frozenset()))


def test_given_an_order_past_the_problem_when_explained_then_refused() -> None:
    day = a_day()
    day.order(100)

    with pytest.raises(ValueError):
        day.build().explain_drop(1, NOTHING_SERVED)
