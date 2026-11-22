"""explain_drop in ADR-053's order: each case is an order that would also fail a later test, so
the expected reason is the first that applies.

The day: a depot at point 0 and a vehicle carrying 10 kg and 10 l, its driver on shift 0 to 1,000.
"""

from collections.abc import Callable

import pytest

from dispatchflow_optimizer.domain.drops import (
    Capacity,
    DepotHours,
    Displaced,
    MissingLocation,
    NoVehicleAvailable,
    PinNotHonoured,
    RemovedByDispatcher,
    Shift,
    TimeWindow,
    UnreachableLocation,
)
from dispatchflow_optimizer.domain.problem import Pin
from dispatchflow_optimizer.domain.solution import Route, Solution
from dispatchflow_optimizer.domain.travel_time import TravelTimeModel
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

    with pytest.raises(ValueError, match=r"^order 0 is served, not dropped$"):
        day.build().explain_drop(0, Solution(routes=(route,), dropped=frozenset()))


def test_given_an_order_past_the_problem_when_explained_then_refused() -> None:
    day = a_day()
    day.order(100)

    with pytest.raises(ValueError, match="order 1"):
        day.build().explain_drop(1, NOTHING_SERVED)


# --- boundaries: exactly on a limit fits alone (NFR-168), as the validator says ---


@pytest.mark.parametrize(
    "case",
    [
        pytest.param(lambda day: day.order(100, weight=10, volume=10), id="capacity"),
        pytest.param(lambda day: day.order(100, window=(0, 100)), id="window end"),
        pytest.param(lambda day: day.order(500), id="shift end"),
    ],
)
def test_given_an_order_exactly_on_a_limit_when_explained_then_displaced(
    case: object,
) -> None:
    day = a_day()
    case(day)  # type: ignore[operator]

    assert day.build().explain_drop(0, NOTHING_SERVED) == Displaced()


def test_given_buffered_travel_when_explained_then_the_reason_follows_the_validators_times() -> (
    None
):
    # Static travel reaches the order at 100, inside its window; a 10% buffer makes it 110.
    static = a_day()
    static.order(100, window=(0, 105))
    buffered = a_day()
    buffered.travel = TravelTimeModel(buckets=(), off_peak_factor=1.0, buffer=0.1)
    buffered.order(100, window=(0, 105))

    assert static.build().explain_drop(0, NOTHING_SERVED) == Displaced()
    assert buffered.build().explain_drop(0, NOTHING_SERVED) == TimeWindow(
        window_end=105, earliest_arrival=110
    )


def test_given_the_second_vehicle_arrives_first_when_explained_then_its_arrival_is_the_fact() -> (
    None
):
    day = a_day(shift=(30, 1_000))
    day.vehicle(shift=(0, 1_000))
    day.order(100, window=TOO_LATE)

    assert day.build().explain_drop(0, NOTHING_SERVED) == TimeWindow(
        window_end=50, earliest_arrival=100
    )


def test_given_the_second_vehicle_comes_closest_when_explained_then_its_facts_are_given() -> None:
    day = a_day(shift=(0, 150))
    day.vehicle(shift=(0, 190))
    day.order(100)

    assert day.build().explain_drop(0, NOTHING_SERVED) == Shift(
        latest_return=190, earliest_return=200
    )


def test_given_depot_closing_equal_to_the_shift_end_when_explained_then_shift() -> None:
    day = a_day(shift=(0, 199))
    day.depot_hours(closes=199)
    day.order(100)

    assert day.build().explain_drop(0, NOTHING_SERVED) == Shift(
        latest_return=199, earliest_return=200
    )


@pytest.mark.parametrize("depot_first", [True, False], ids=["depot vehicle first", "shift first"])
def test_given_two_vehicles_missing_by_equal_seconds_when_explained_then_shift_in_either_order(
    depot_first: bool,
) -> None:
    # One vehicle's end depot closes at 199, the other's driver's shift ends at 199; both return
    # at 200. The reason must not depend on which vehicle comes first.
    day = DayBuilder()
    closing = day.depot(0, closes=199)

    def depot_vehicle() -> None:
        day.vehicle(end_depot=closing)

    def shift_vehicle() -> None:
        day.vehicle(shift=(0, 199))

    for add in (depot_vehicle, shift_vehicle) if depot_first else (shift_vehicle, depot_vehicle):
        add()
    day.order(100)

    assert day.build().explain_drop(0, NOTHING_SERVED) == Shift(
        latest_return=199, earliest_return=200
    )


def test_given_a_depot_opening_late_when_explained_then_the_route_leaves_at_opening() -> None:
    # The shared route check leaves at 900, when the depot opens, so the round trip ends at 1,100.
    day = a_day()
    day.depot_hours(opens=900)
    day.order(100)

    assert day.build().explain_drop(0, NOTHING_SERVED) == Shift(
        latest_return=1_000, earliest_return=1_100
    )


def test_given_a_door_order_after_a_shift_ending_the_date_when_explained_then_shift_on_return() -> (
    None
):
    # The shift ends with the date at 150 (FR-190), so missing the date is missing the shift, and
    # the fact is the return to the depot.
    day = DayBuilder(day_length=150)
    day.vehicle(shift=(0, 150))
    day.order(200, door=True)

    assert day.build().explain_drop(0, NOTHING_SERVED) == Shift(
        latest_return=150, earliest_return=400
    )


def test_given_an_arrival_after_the_dates_end_when_explained_then_the_fact_passes_it() -> None:
    # ADR-113: a fact's time is never capped at the date's end, 86,400.
    day = DayBuilder()
    day.vehicle(shift=(80_000, 86_400))
    day.order(7_000, window=(0, 85_000))

    assert day.build().explain_drop(0, NOTHING_SERVED) == TimeWindow(
        window_end=85_000, earliest_arrival=87_000
    )


def test_given_an_order_only_the_second_depot_reaches_when_explained_then_judged_from_it() -> None:
    # Vehicle 0 has no road to the order, so only vehicle 1's arrival counts.
    day = a_day()
    other = day.depot(1_000)
    day.vehicle(start_depot=other, end_depot=other)
    order = day.order(950, window=(0, 10))
    day.no_road(DEPOT_LOCATION, day.location_of(order))

    assert day.build().explain_drop(0, NOTHING_SERVED) == TimeWindow(
        window_end=10, earliest_arrival=50
    )


def test_given_a_pin_to_a_vehicle_with_no_road_to_the_order_when_explained_then_pin() -> None:
    day = a_day()
    other = day.depot(1_000)
    day.vehicle(start_depot=other, end_depot=other)
    order = day.order(950, pin=Pin(vehicle=0))
    day.no_road(DEPOT_LOCATION, day.location_of(order))

    assert day.build().explain_drop(0, NOTHING_SERVED) == PinNotHonoured(Pin(vehicle=0), False)


# --- a date with no usable vehicle (FR-197, FR-223, ADR-100) ---


def a_day_with_no_usable_vehicle() -> DayBuilder:
    day = DayBuilder()
    nowhere = day.depot(None)
    day.vehicle(start_depot=nowhere, end_depot=nowhere)
    return day


def test_given_no_usable_vehicle_and_no_location_when_explained_then_missing_location() -> None:
    # A missing location stays the order's own fault: adding a vehicle would not serve it.
    day = a_day_with_no_usable_vehicle()
    day.order(None)

    assert day.build().explain_drop(0, NOTHING_SERVED) == MissingLocation()


@pytest.mark.parametrize(
    "add_order",
    [
        pytest.param(lambda day: day.order(100, weight=2, volume=3), id="located"),
        pytest.param(lambda day: day.order(100, weight=1_000), id="heavier than any"),
        # The factory judges no reachability on a date with no usable vehicle, so this order is
        # routable and the case equals "located": the order between *no vehicle available* and
        # *unreachable location* cannot be seen at this boundary.
        pytest.param(lambda day: day.order(100, reachable=False), id="no road"),
        pytest.param(lambda day: day.order(100, pin=Pin(vehicle=0)), id="pinned to a vehicle"),
        pytest.param(lambda day: day.order(100, pin=Pin(driver=0)), id="pinned to a driver"),
    ],
)
def test_given_no_usable_vehicle_and_a_located_order_when_explained_then_no_vehicle_available(
    add_order: Callable[[DayBuilder], object],
) -> None:
    # A fact about the whole date comes before every per-vehicle test: never unreachable,
    # capacity or pin.
    day = a_day_with_no_usable_vehicle()
    add_order(day)

    assert day.build().explain_drop(0, NOTHING_SERVED) == NoVehicleAvailable()


def test_given_no_usable_vehicle_and_a_removed_order_when_explained_then_no_vehicle_available() -> (
    None
):
    day = a_day_with_no_usable_vehicle()
    day.order(100)
    day.removed_by_dispatcher.add(0)

    assert day.build().explain_drop(0, NOTHING_SERVED) == NoVehicleAvailable()


def test_given_no_vehicle_at_all_when_explained_then_no_vehicle_available() -> None:
    day = DayBuilder()
    day.order(100)

    assert day.build().explain_drop(0, NOTHING_SERVED) == NoVehicleAvailable()


def test_given_vehicles_with_no_road_between_their_depots_when_explained_then_no_vehicle() -> None:
    day = DayBuilder()
    island = day.depot(5_000, reachable=False)
    day.vehicle(start_depot=0, end_depot=island)
    day.order(100)

    assert day.build().explain_drop(0, NOTHING_SERVED) == NoVehicleAvailable()
