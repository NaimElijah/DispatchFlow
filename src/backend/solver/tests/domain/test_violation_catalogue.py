"""The crafted-violation catalogue (NFR-169) and the limit cases (NFR-168, NFR-194).

Each case starts from one feasible day and breaks one rule one way, then names the exact set of
rules the validator must report. The day: a depot at point 0, a vehicle carrying 10 kg and 10 l
with a driver on shift 0 to 1,000, and two orders at points 100 and 200 with 10 s of service.
Served in order, the route arrives at 100 and 210 and returns at 420.
"""

from collections.abc import Callable
from dataclasses import replace

import pytest

from dispatchflow_solver.domain.problem import Pin, Problem
from dispatchflow_solver.domain.rules import RuleId
from dispatchflow_solver.domain.solution import InvalidSolutionError, Route, Solution
from tests.domain.builders import DEPOT_LOCATION, DayBuilder

SHIFT = (0.0, 1_000.0)
RETURN = 420.0
WITHIN_TOLERANCE = 5e-7
PAST_TOLERANCE = 2e-6


def a_day(
    *,
    first: dict[str, object] | None = None,
    second: dict[str, object] | None = None,
    shift: tuple[float, float] = SHIFT,
    weight: float = 10,
    volume: float = 10,
) -> DayBuilder:
    day = DayBuilder()
    day.vehicle(weight=weight, volume=volume, shift=shift)
    day.order(100, service=10, **(first or {}))  # type: ignore[arg-type]
    day.order(200, service=10, **(second or {}))  # type: ignore[arg-type]
    return day


def route_of(
    vehicle: int,
    driver: int,
    *stops: int,
    start: int = DEPOT_LOCATION,
    end: int = DEPOT_LOCATION,
    planned: tuple[float, ...] | None = None,
) -> Route:
    return Route(
        vehicle=vehicle, driver=driver, start=start, end=end, stops=stops, planned_starts=planned
    )


def served_in_order(*stops: int, planned: tuple[float, ...] | None = None) -> Solution:
    return Solution(routes=(route_of(0, 0, *stops, planned=planned),), dropped=frozenset())


def solution_of(*routes: Route, dropped: frozenset[int] = frozenset()) -> Solution:
    return Solution(routes=routes, dropped=dropped)


Case = Callable[[], tuple[Problem, Solution]]


def feasible() -> tuple[Problem, Solution]:
    return a_day().build(), served_in_order(0, 1)


def window_missed() -> tuple[Problem, Solution]:
    return a_day(second={"window": (0, 150)}).build(), served_in_order(0, 1)


def claimed_start_before_window_opens() -> tuple[Problem, Solution]:
    # A claim before a hard window opens breaks the window, not the service time.
    return a_day(first={"window": (50, 1_000)}).build(), served_in_order(0, 1, planned=(40, 210))


def claimed_start_ignores_service() -> tuple[Problem, Solution]:
    # The claim reaches order 1 at 200, as if order 0 took no service time.
    return a_day().build(), served_in_order(0, 1, planned=(100, 200))


def claimed_start_ignores_travel() -> tuple[Problem, Solution]:
    # The claim serves order 0 at 0, as if the depot were at its door.
    return a_day().build(), served_in_order(0, 1, planned=(0, 210))


def overweight() -> tuple[Problem, Solution]:
    return a_day(first={"weight": 6}, second={"weight": 5}).build(), served_in_order(0, 1)


def over_volume() -> tuple[Problem, Solution]:
    return a_day(first={"volume": 6}, second={"volume": 5}).build(), served_in_order(0, 1)


def back_after_shift() -> tuple[Problem, Solution]:
    return a_day(shift=(0, RETURN - 1)).build(), served_in_order(0, 1)


def served_twice() -> tuple[Problem, Solution]:
    return a_day().build(), served_in_order(0, 1, 0)


def served_and_dropped() -> tuple[Problem, Solution]:
    solution = served_in_order(0, 1)
    return a_day().build(), replace(solution, dropped=frozenset({0}))


def neither_served_nor_dropped() -> tuple[Problem, Solution]:
    return a_day().build(), served_in_order(1)


def no_location_served() -> tuple[Problem, Solution]:
    day = a_day()
    hidden = day.order(None)
    return day.build(), served_in_order(0, hidden, 1)


def unreachable_served() -> tuple[Problem, Solution]:
    day = a_day()
    island = day.order(150, reachable=False)
    return day.build(), served_in_order(0, island, 1)


def no_road_from_this_vehicles_depot() -> tuple[Problem, Solution]:
    # The order is routable through vehicle 0; vehicle 1's depot has no road to it.
    day = a_day()
    island = day.depot(5_000, reachable=False)
    day.vehicle(start_depot=island, end_depot=island)
    where = day.depots[island].location or 0
    other = route_of(1, 1, 1, start=where, end=where)
    return day.build(), solution_of(route_of(0, 0, 0), other)


def no_road_back_to_the_end_depot() -> tuple[Problem, Solution]:
    # Order 1 is routable through the second depot, but has no road back to depot 0.
    day = a_day()
    other = day.depot(500)
    day.vehicle(start_depot=other, end_depot=other)
    day.no_road(day.location_of(1), DEPOT_LOCATION)
    return day.build(), served_in_order(0, 1)


def driver_not_rostered_to_the_vehicle() -> tuple[Problem, Solution]:
    day = a_day()
    day.vehicle(weight=10, volume=10, shift=SHIFT)
    return day.build(), solution_of(route_of(0, 1, 0, 1))


def vehicle_drives_twice() -> tuple[Problem, Solution]:
    return a_day().build(), solution_of(route_of(0, 0, 0), route_of(0, 0, 1))


def driver_drives_twice() -> tuple[Problem, Solution]:
    # Two routes share driver 0, so the second drives vehicle 1 without its rostered driver.
    day = a_day()
    day.vehicle(weight=10, volume=10, shift=SHIFT)
    return day.build(), solution_of(route_of(0, 0, 0), route_of(1, 0, 1))


def starts_away_from_depot() -> tuple[Problem, Solution]:
    day = a_day()
    other = day.depot(0)
    start = day.depots[other].location or 0
    return day.build(), solution_of(route_of(0, 0, 0, 1, start=start))


def ends_away_from_depot() -> tuple[Problem, Solution]:
    day = a_day()
    other = day.depot(0)
    end = day.depots[other].location or 0
    return day.build(), solution_of(route_of(0, 0, 0, 1, end=end))


def vehicle_without_depot_location() -> tuple[Problem, Solution]:
    day = a_day()
    nowhere = day.depot(None)
    day.vehicle(weight=10, volume=10, shift=SHIFT, start_depot=nowhere, end_depot=nowhere)
    return day.build(), solution_of(route_of(1, 1, 0, 1))


def vehicle_without_road_between_depots() -> tuple[Problem, Solution]:
    day = a_day()
    island = day.depot(5_000, reachable=False)
    day.vehicle(start_depot=0, end_depot=island)
    end = day.depots[island].location or 0
    return day.build(), solution_of(route_of(1, 1, end=end), dropped=frozenset({0, 1}))


def back_after_depot_closes() -> tuple[Problem, Solution]:
    day = a_day()
    day.depot_hours(closes=RETURN - 1)
    return day.build(), served_in_order(0, 1)


def break_reserve_missed() -> tuple[Problem, Solution]:
    # A 6-hour shift: the route must return by 21,600 - 2,700 = 18,900, and returns at 18,902.
    day = DayBuilder(breaks_reserved=True)
    day.vehicle(weight=10, volume=10, shift=(0, 21_600))
    day.order(9_451)
    return day.build(), served_in_order(0)


def back_after_shift_with_breaks() -> tuple[Problem, Solution]:
    # Past the shift end, so past the break reserve too.
    day = DayBuilder(breaks_reserved=True)
    day.vehicle(weight=10, volume=10, shift=(0, 21_600))
    day.order(10_801)
    return day.build(), served_in_order(0)


def door_stop_after_shift() -> tuple[Problem, Solution]:
    # The door stop is reached at 100, after the shift ends at 99; so is the return.
    day = DayBuilder()
    day.vehicle(shift=(0, 99))
    day.order(100, door=True, window=(0, 50))
    return day.build(), served_in_order(0)


def door_stop_after_date() -> tuple[Problem, Solution]:
    # The shift ends with the date at 150 (FR-190), so the door stop misses both.
    day = DayBuilder(day_length=150)
    day.vehicle(shift=(0, 150))
    day.order(200, door=True, window=(0, 50))
    return day.build(), served_in_order(0)


def pinned_to_another_vehicle() -> tuple[Problem, Solution]:
    day = a_day(first={"pin": Pin(vehicle=1)})
    day.vehicle(weight=10, volume=10, shift=SHIFT)
    return day.build(), served_in_order(0, 1)


def pinned_to_another_driver() -> tuple[Problem, Solution]:
    day = a_day(first={"pin": Pin(driver=1)})
    day.vehicle(weight=10, volume=10, shift=SHIFT)
    return day.build(), served_in_order(0, 1)


R = RuleId
CATALOGUE: dict[str, tuple[Case, frozenset[RuleId]]] = {
    "BR-03 window missed": (window_missed, frozenset({R.BR_03})),
    "BR-03 claimed start before the window": (
        claimed_start_before_window_opens,
        frozenset({R.BR_03}),
    ),
    "BR-04 claimed start ignores service": (claimed_start_ignores_service, frozenset({R.BR_04})),
    "BR-04 claimed start ignores travel": (claimed_start_ignores_travel, frozenset({R.BR_04})),
    "BR-05 by weight": (overweight, frozenset({R.BR_05})),
    "BR-05 by volume": (over_volume, frozenset({R.BR_05})),
    "BR-06 back after shift": (back_after_shift, frozenset({R.BR_06})),
    "BR-06 and BR-105 back after shift": (
        back_after_shift_with_breaks,
        frozenset({R.BR_06, R.BR_105}),
    ),
    "BR-07 served twice": (served_twice, frozenset({R.BR_07})),
    "BR-07 served and dropped": (served_and_dropped, frozenset({R.BR_07})),
    "BR-07 neither served nor dropped": (neither_served_nor_dropped, frozenset({R.BR_07})),
    "BR-14 no location served": (no_location_served, frozenset({R.BR_14})),
    "BR-14 unreachable served": (unreachable_served, frozenset({R.BR_14})),
    "BR-14 no road from the depot": (no_road_from_this_vehicles_depot, frozenset({R.BR_14})),
    "BR-14 no road back to the depot": (no_road_back_to_the_end_depot, frozenset({R.BR_14})),
    "BR-20 driver not rostered": (driver_not_rostered_to_the_vehicle, frozenset({R.BR_20})),
    "BR-39 by vehicle": (vehicle_drives_twice, frozenset({R.BR_39})),
    "BR-39 by driver": (driver_drives_twice, frozenset({R.BR_20, R.BR_39})),
    "BR-40 by start": (starts_away_from_depot, frozenset({R.BR_40})),
    "BR-40 by end": (ends_away_from_depot, frozenset({R.BR_40})),
    "BR-40 depot without location": (vehicle_without_depot_location, frozenset({R.BR_40})),
    "BR-40 no road between depots": (vehicle_without_road_between_depots, frozenset({R.BR_40})),
    "BR-46 back after depot closes": (back_after_depot_closes, frozenset({R.BR_46})),
    "BR-105 break reserve missed": (break_reserve_missed, frozenset({R.BR_105})),
    "BR-119 door stop after shift": (door_stop_after_shift, frozenset({R.BR_119, R.BR_06})),
    "BR-119 door stop after date": (door_stop_after_date, frozenset({R.BR_119, R.BR_06})),
    "FR-91 pinned to another vehicle": (pinned_to_another_vehicle, frozenset({R.FR_91})),
    "FR-91 pinned to another driver": (pinned_to_another_driver, frozenset({R.FR_91})),
}


def test_given_the_baseline_day_when_validated_then_accepted() -> None:
    problem, solution = feasible()

    report = problem.validate(solution)

    assert report.violations == ()
    assert report.accepted


@pytest.mark.parametrize("name", sorted(CATALOGUE))
def test_given_a_crafted_violation_when_validated_then_exactly_its_rules_reported(
    name: str,
) -> None:
    case, expected = CATALOGUE[name]
    problem, solution = case()

    report = problem.validate(solution)

    assert report.rules() == expected
    assert not report.accepted


def test_given_the_catalogue_when_listed_then_every_daily_rule_has_a_case() -> None:
    covered = frozenset().union(*(expected for _, expected in CATALOGUE.values()))

    assert covered == frozenset(RuleId)


def test_given_two_broken_rules_when_validated_then_both_reported() -> None:
    problem = a_day(first={"weight": 6}, second={"weight": 5, "window": (0, 150)}).build()

    report = problem.validate(served_in_order(0, 1))

    assert report.rules() == {RuleId.BR_03, RuleId.BR_05}


def test_given_a_missed_window_when_validated_then_order_route_and_seconds_named() -> None:
    problem, solution = window_missed()

    (violation,) = problem.validate(solution).violations

    assert (violation.route, violation.order, violation.vehicle) == (0, 1, 0)
    assert violation.seconds == pytest.approx(60)


def test_given_too_much_weight_and_volume_when_validated_then_each_excess_reported() -> None:
    problem = a_day(first={"weight": 6, "volume": 8}, second={"weight": 5, "volume": 4}).build()

    violations = problem.validate(served_in_order(0, 1)).violations

    assert {(v.kilograms, v.litres) for v in violations} == {(1.0, None), (None, 2.0)}


def test_given_a_driver_not_rostered_when_validated_then_timed_on_the_rostered_shift() -> None:
    # Driver 1's shift would end before the return; the rostered driver 0's does not.
    day = a_day()
    day.vehicle(weight=10, volume=10, shift=(0, RETURN - 1))

    report = day.build().validate(solution_of(route_of(0, 1, 0, 1)))

    assert report.rules() == {RuleId.BR_20}


# --- NFR-168: a solution exactly on a limit is accepted, and ADR-098's tolerance ---


@pytest.mark.parametrize(
    "case",
    [
        pytest.param(lambda: a_day(second={"window": (0, 210)}), id="service starts at window end"),
        pytest.param(lambda: a_day(first={"weight": 5}, second={"weight": 5}), id="weight"),
        pytest.param(lambda: a_day(first={"volume": 5}, second={"volume": 5}), id="volume"),
        pytest.param(lambda: a_day(shift=(0, RETURN)), id="return at shift end"),
    ],
)
def test_given_a_route_exactly_on_a_limit_when_validated_then_accepted(
    case: Callable[[], DayBuilder],
) -> None:
    assert case().build().validate(served_in_order(0, 1)).accepted


def test_given_a_tenth_and_a_fifth_of_a_kilo_on_three_tenths_when_validated_then_accepted() -> None:
    # 0.1 + 0.2 is 0.30000000000000004 in float64.
    problem = a_day(first={"weight": 0.1}, second={"weight": 0.2}, weight=0.3).build()

    assert problem.validate(served_in_order(0, 1)).accepted


def door_at_shift_end(excess: float) -> tuple[Problem, Solution]:
    # The vehicle ends at a second depot at point 100, where the door order is, so the door stop
    # and the return both fall at 100, the shift end less the excess.
    day = DayBuilder()
    end = day.depot(100)
    day.vehicle(shift=(0, 100 - excess), end_depot=end)
    day.order(100, door=True, window=(0, 50))
    end_location = day.depots[end].location or 0
    return day.build(), solution_of(route_of(0, 0, 0, end=end_location))


def break_reserve_passed_by(excess: float) -> tuple[Problem, Solution]:
    day = DayBuilder(breaks_reserved=True)
    day.vehicle(shift=(0, 21_600))
    day.order(9_450 + excess / 2)
    return day.build(), served_in_order(0)


def depot_closing_passed_by(excess: float) -> tuple[Problem, Solution]:
    day = a_day()
    day.depot_hours(closes=RETURN - excess)
    return day.build(), served_in_order(0, 1)


TOLERANCE_SITES: dict[
    str, tuple[Callable[[float], tuple[Problem, Solution]], frozenset[RuleId]]
] = {
    "BR-03 window end": (
        lambda excess: (
            a_day(second={"window": (0, 210 - excess)}).build(),
            served_in_order(0, 1),
        ),
        frozenset({RuleId.BR_03}),
    ),
    "BR-04 claimed start": (
        lambda excess: (a_day().build(), served_in_order(0, 1, planned=(100, 210 - excess))),
        frozenset({RuleId.BR_04}),
    ),
    "BR-05 weight": (
        lambda excess: (
            a_day(first={"weight": 5}, second={"weight": 5 + excess}).build(),
            served_in_order(0, 1),
        ),
        frozenset({RuleId.BR_05}),
    ),
    "BR-05 volume": (
        lambda excess: (
            a_day(first={"volume": 5}, second={"volume": 5 + excess}).build(),
            served_in_order(0, 1),
        ),
        frozenset({RuleId.BR_05}),
    ),
    "BR-06 shift end": (
        lambda excess: (a_day(shift=(0, RETURN - excess)).build(), served_in_order(0, 1)),
        frozenset({RuleId.BR_06}),
    ),
    "BR-46 depot closing": (depot_closing_passed_by, frozenset({RuleId.BR_46})),
    "BR-105 break reserve": (break_reserve_passed_by, frozenset({RuleId.BR_105})),
    # A door stop past its shift end is followed by a return past it too.
    "BR-119 door stop at shift end": (door_at_shift_end, frozenset({RuleId.BR_119, RuleId.BR_06})),
}


@pytest.mark.parametrize("site", sorted(TOLERANCE_SITES))
def test_given_a_limit_passed_within_the_tolerance_when_validated_then_accepted(
    site: str,
) -> None:
    case, _ = TOLERANCE_SITES[site]
    problem, solution = case(WITHIN_TOLERANCE)

    assert problem.validate(solution).violations == ()


@pytest.mark.parametrize("site", sorted(TOLERANCE_SITES))
def test_given_a_limit_passed_beyond_the_tolerance_when_validated_then_exactly_its_rule(
    site: str,
) -> None:
    case, expected = TOLERANCE_SITES[site]
    problem, solution = case(PAST_TOLERANCE)

    assert problem.validate(solution).rules() == expected


@pytest.mark.parametrize("site", sorted(TOLERANCE_SITES))
def test_given_a_limit_met_exactly_when_validated_then_accepted(site: str) -> None:
    case, _ = TOLERANCE_SITES[site]
    problem, solution = case(0.0)

    assert problem.validate(solution).violations == ()


def test_given_a_shift_under_six_hours_when_return_in_last_45_minutes_then_accepted() -> None:
    day = DayBuilder(breaks_reserved=True)
    day.vehicle(shift=(0, 21_599))
    day.order(9_451)

    assert day.build().validate(served_in_order(0)).accepted


def test_given_breaks_not_reserved_when_return_in_last_45_minutes_then_accepted() -> None:
    day = DayBuilder(breaks_reserved=False)
    day.vehicle(shift=(0, 21_600))
    day.order(9_451)

    assert day.build().validate(served_in_order(0)).accepted


# --- BR-03, BR-04, BR-122: how the validator times a route ---


def test_given_an_early_arrival_at_a_hard_window_when_timed_then_the_vehicle_waits() -> None:
    problem = a_day(first={"window": (150, 1_000)}).build()

    report = problem.validate(served_in_order(0, 1))

    timing = report.timings[0]
    assert report.accepted
    assert list(timing.arrival) == [100, 260]
    assert list(timing.start) == [150, 260]
    assert list(timing.departure) == [160, 270]
    assert timing.returns_at == 470


def test_given_a_route_when_timed_then_legs_carry_buffered_seconds_and_metres() -> None:
    problem, solution = feasible()

    timing = problem.validate(solution).timings[0]

    assert timing.leaves_at == 0
    assert list(timing.leg_seconds) == [100, 100, 200]
    assert list(timing.leg_metres) == [1_000, 1_000, 2_000]
    assert list(timing.late) == [0, 0]
    assert list(timing.outside_window) == [0, 0]


def test_given_claimed_starts_on_or_after_the_validators_when_validated_then_accepted() -> None:
    problem = a_day().build()

    assert problem.validate(served_in_order(0, 1, planned=(100, 210))).accepted
    assert problem.validate(served_in_order(0, 1, planned=(100, 250))).accepted


def test_given_a_claim_before_the_window_and_the_validators_start_when_validated_then_br03() -> (
    None
):
    problem, solution = claimed_start_before_window_opens()

    (violation,) = problem.validate(solution).violations

    assert (violation.rule, violation.order, violation.seconds) == (RuleId.BR_03, 0, 10)


def test_given_a_door_stop_reached_early_when_timed_then_served_on_arrival_without_waiting() -> (
    None
):
    day = DayBuilder()
    day.vehicle()
    day.order(100, door=True, window=(500, 600))
    problem = day.build()

    report = problem.validate(served_in_order(0))

    assert report.accepted
    assert report.timings[0].start[0] == 100
    assert report.timings[0].outside_window[0] == 400


def test_given_a_solver_planned_wait_at_a_door_stop_when_validated_then_retimed_not_rejected() -> (
    None
):
    # FR-403: the solver may plan a wait at a door stop; the validator serves it on arrival.
    day = DayBuilder()
    day.vehicle()
    day.order(100, door=True, window=(500, 600))

    report = day.build().validate(served_in_order(0, planned=(500,)))

    assert report.accepted
    assert report.timings[0].start[0] == 100


def test_given_a_depot_opening_after_shift_start_when_timed_then_route_leaves_at_opening() -> None:
    day = DayBuilder()
    day.depot_hours(opens=50)
    day.vehicle()
    day.order(100)

    report = day.build().validate(served_in_order(0))

    assert report.accepted
    assert report.timings[0].leaves_at == 50


# --- NFR-194: leave-at-door stops, in an instance that also has binding windows ---


def door_day(*, door_window: tuple[float, float], shift: tuple[float, float] = SHIFT) -> Problem:
    day = DayBuilder()
    day.vehicle(shift=shift)
    day.order(100, service=10, window=(100, 110))
    day.order(200, door=True, window=door_window)
    return day.build()


def test_given_a_door_stop_late_within_shift_and_date_when_validated_then_accepted() -> None:
    report = door_day(door_window=(0, 50)).validate(served_in_order(0, 1))

    assert report.accepted
    assert report.timings[0].outside_window[1] == 210 - 50


def test_given_a_door_stop_early_within_shift_and_date_when_validated_then_accepted() -> None:
    report = door_day(door_window=(800, 900)).validate(served_in_order(0, 1))

    assert report.accepted
    assert report.timings[0].outside_window[1] == 800 - 210


def test_given_a_door_stop_outside_its_shift_when_validated_then_rejected() -> None:
    report = door_day(door_window=(0, 50), shift=(0, 200)).validate(served_in_order(0, 1))

    assert report.rules() == {RuleId.BR_119, RuleId.BR_06}


def test_given_a_hard_window_beside_door_stops_when_missed_then_still_rejected() -> None:
    report = door_day(door_window=(0, 50)).validate(served_in_order(1, 0))

    assert report.rules() == {RuleId.BR_03}


# --- what the validator accepts besides routes ---


def test_given_a_pinned_order_dropped_when_validated_then_accepted() -> None:
    problem = a_day(first={"pin": Pin(vehicle=0)}).build()

    solution = Solution(routes=served_in_order(1).routes, dropped=frozenset({0}))

    assert problem.validate(solution).accepted


def test_given_an_order_removed_by_a_dispatcher_served_again_when_validated_then_accepted() -> None:
    # BR-95: a new solve may serve an order the dispatcher took off the routes.
    day = a_day()
    day.removed_by_dispatcher.add(0)

    assert day.build().validate(served_in_order(0, 1)).accepted


def test_given_excluded_orders_left_off_the_dropped_list_when_validated_then_accepted() -> None:
    day = a_day()
    day.order(None)
    day.order(150, reachable=False)

    assert day.build().validate(served_in_order(0, 1)).accepted


def test_given_a_route_with_no_stops_when_validated_then_accepted_and_untimed() -> None:
    problem = a_day().build()
    solution = solution_of(route_of(0, 0), dropped=frozenset({0, 1}))

    report = problem.validate(solution)

    assert report.accepted
    assert len(report.timings[0].leg_seconds) == 0


def test_given_an_unroutable_stop_when_timed_then_skipped_and_later_stops_still_checked() -> None:
    day = a_day(second={"window": (0, 150)})
    hidden = day.order(None)

    report = day.build().validate(served_in_order(0, hidden, 1))

    assert report.rules() == {RuleId.BR_14, RuleId.BR_03}
    assert [v.order for v in report.violations if v.rule is RuleId.BR_14] == [hidden]


@pytest.mark.parametrize(
    ("route", "match"),
    [
        pytest.param(route_of(5, 0, 0), r"routes\[0\]\.vehicle 5 is not one of 1$", id="vehicle"),
        pytest.param(route_of(0, 5, 0), r"routes\[0\]\.driver 5 is not one of 1$", id="driver"),
        pytest.param(
            route_of(0, 0, 0, start=99), r"routes\[0\]\.start 99 is not one of 3$", id="start"
        ),
        pytest.param(route_of(0, 0, 0, end=99), r"routes\[0\]\.end 99 is not one of 3$", id="end"),
        pytest.param(route_of(0, 0, 9), r"routes\[0\]\.stops\[0\] 9 is not one of 2$", id="stop"),
    ],
)
def test_given_an_index_past_the_problem_when_validated_then_refused_naming_it(
    route: Route, match: str
) -> None:
    with pytest.raises(InvalidSolutionError, match=match):
        a_day().build().validate(Solution(routes=(route,), dropped=frozenset({1})))


def test_given_a_dropped_index_past_the_problem_when_validated_then_refused() -> None:
    with pytest.raises(InvalidSolutionError, match=r"dropped order 9 is not one of 2$"):
        a_day().build().validate(Solution(routes=(), dropped=frozenset({0, 1, 9})))
