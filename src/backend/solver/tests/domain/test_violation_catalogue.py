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


def a_day(
    *,
    first: dict[str, object] | None = None,
    second: dict[str, object] | None = None,
    shift: tuple[float, float] = SHIFT,
) -> DayBuilder:
    day = DayBuilder()
    day.vehicle(weight=10, volume=10, shift=shift)
    day.order(100, service=10, **(first or {}))  # type: ignore[arg-type]
    day.order(200, service=10, **(second or {}))  # type: ignore[arg-type]
    return day


def served_in_order(*stops: int, planned: tuple[float, ...] | None = None) -> Solution:
    route = Route(
        vehicle=0,
        driver=0,
        start=DEPOT_LOCATION,
        end=DEPOT_LOCATION,
        stops=stops,
        planned_starts=planned,
    )
    return Solution(routes=(route,), dropped=frozenset())


Case = Callable[[], tuple[Problem, Solution]]


def feasible() -> tuple[Problem, Solution]:
    return a_day().build(), served_in_order(0, 1)


def window_missed() -> tuple[Problem, Solution]:
    return a_day(second={"window": (0, 150)}).build(), served_in_order(0, 1)


def claimed_start_ignores_service() -> tuple[Problem, Solution]:
    # The claim reaches order 1 at 200, as if order 0 took no service time.
    return a_day().build(), served_in_order(0, 1, planned=(100, 200))


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


def vehicle_drives_twice() -> tuple[Problem, Solution]:
    day = a_day()
    day.vehicle(weight=10, volume=10, shift=SHIFT)
    routes = (
        Route(vehicle=0, driver=0, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(0,)),
        Route(vehicle=0, driver=1, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(1,)),
    )
    return day.build(), Solution(routes=routes, dropped=frozenset())


def driver_drives_twice() -> tuple[Problem, Solution]:
    day = a_day()
    day.vehicle(weight=10, volume=10, shift=SHIFT)
    routes = (
        Route(vehicle=0, driver=0, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(0,)),
        Route(vehicle=1, driver=0, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(1,)),
    )
    return day.build(), Solution(routes=routes, dropped=frozenset())


def starts_away_from_depot() -> tuple[Problem, Solution]:
    day = a_day()
    other = day.depot(0)
    route = Route(
        vehicle=0, driver=0, start=day.depots[other].location or 0, end=DEPOT_LOCATION, stops=(0, 1)
    )
    return day.build(), Solution(routes=(route,), dropped=frozenset())


def ends_away_from_depot() -> tuple[Problem, Solution]:
    day = a_day()
    other = day.depot(0)
    route = Route(
        vehicle=0, driver=0, start=DEPOT_LOCATION, end=day.depots[other].location or 0, stops=(0, 1)
    )
    return day.build(), Solution(routes=(route,), dropped=frozenset())


def vehicle_without_depot_location() -> tuple[Problem, Solution]:
    day = a_day()
    nowhere = day.depot(None)
    day.vehicle(weight=10, volume=10, shift=SHIFT, start_depot=nowhere, end_depot=nowhere)
    route = Route(vehicle=1, driver=1, start=DEPOT_LOCATION, end=DEPOT_LOCATION, stops=(0, 1))
    return day.build(), Solution(routes=(route,), dropped=frozenset())


def back_after_depot_closes() -> tuple[Problem, Solution]:
    day = DayBuilder()
    day.depot_hours(closes=RETURN - 1)
    day.vehicle(weight=10, volume=10, shift=SHIFT)
    day.order(100, service=10)
    day.order(200, service=10)
    return day.build(), served_in_order(0, 1)


def break_reserve_missed() -> tuple[Problem, Solution]:
    # A 6-hour shift: the route must return by 21,600 - 2,700 = 18,900, and returns at 18,902.
    day = DayBuilder(breaks_reserved=True)
    day.vehicle(weight=10, volume=10, shift=(0, 21_600))
    day.order(9_451)
    return day.build(), served_in_order(0)


def door_stop_after_shift() -> tuple[Problem, Solution]:
    # The door stop is reached at 100, after the shift ends at 99; so is the return.
    day = DayBuilder()
    day.vehicle(shift=(0, 99))
    day.order(100, door=True, window=(0, 50))
    return day.build(), served_in_order(0)


def door_stop_after_date() -> tuple[Problem, Solution]:
    # The shift runs past the date's end at 150, so only the date is missed.
    day = DayBuilder(day_length=150)
    day.vehicle(shift=(0, 2_000))
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


CATALOGUE: dict[str, tuple[Case, frozenset[RuleId]]] = {
    "BR-03 window missed": (window_missed, frozenset({RuleId.BR_03})),
    "BR-04 claimed start ignores service": (
        claimed_start_ignores_service,
        frozenset({RuleId.BR_04}),
    ),
    "BR-05 by weight": (overweight, frozenset({RuleId.BR_05})),
    "BR-05 by volume": (over_volume, frozenset({RuleId.BR_05})),
    "BR-06 back after shift": (back_after_shift, frozenset({RuleId.BR_06})),
    "BR-07 served twice": (served_twice, frozenset({RuleId.BR_07})),
    "BR-07 served and dropped": (served_and_dropped, frozenset({RuleId.BR_07})),
    "BR-07 neither served nor dropped": (neither_served_nor_dropped, frozenset({RuleId.BR_07})),
    "BR-14 no location served": (no_location_served, frozenset({RuleId.BR_14})),
    "BR-14 unreachable served": (unreachable_served, frozenset({RuleId.BR_14})),
    "BR-39 by vehicle": (vehicle_drives_twice, frozenset({RuleId.BR_39})),
    "BR-39 by driver": (driver_drives_twice, frozenset({RuleId.BR_39})),
    "BR-40 by start": (starts_away_from_depot, frozenset({RuleId.BR_40})),
    "BR-40 by end": (ends_away_from_depot, frozenset({RuleId.BR_40})),
    "BR-40 depot without location": (vehicle_without_depot_location, frozenset({RuleId.BR_40})),
    "BR-46 back after depot closes": (back_after_depot_closes, frozenset({RuleId.BR_46})),
    "BR-105 break reserve missed": (break_reserve_missed, frozenset({RuleId.BR_105})),
    "BR-119 door stop after shift": (
        door_stop_after_shift,
        frozenset({RuleId.BR_119, RuleId.BR_06}),
    ),
    "BR-119 door stop after date": (door_stop_after_date, frozenset({RuleId.BR_119})),
    "FR-91 pinned to another vehicle": (pinned_to_another_vehicle, frozenset({RuleId.FR_91})),
    "FR-91 pinned to another driver": (pinned_to_another_driver, frozenset({RuleId.FR_91})),
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


# --- NFR-168: a solution exactly on a limit is accepted ---


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


def test_given_a_return_exactly_at_depot_closing_when_validated_then_accepted() -> None:
    day = DayBuilder()
    day.depot_hours(closes=RETURN)
    day.vehicle(weight=10, volume=10, shift=SHIFT)
    day.order(100, service=10)
    day.order(200, service=10)

    assert day.build().validate(served_in_order(0, 1)).accepted


def test_given_a_return_exactly_at_the_break_reserve_when_validated_then_accepted() -> None:
    day = DayBuilder(breaks_reserved=True)
    day.vehicle(shift=(0, 21_600))
    day.order(9_450)

    assert day.build().validate(served_in_order(0)).accepted


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


def test_given_a_limit_passed_by_rounding_noise_when_validated_then_accepted() -> None:
    # ADR-098: a limit compares within 1e-6, so float64 noise never rejects a solution.
    problem = a_day(second={"window": (0, 210 - 5e-7)}).build()

    assert problem.validate(served_in_order(0, 1)).accepted


def test_given_a_limit_passed_by_more_than_the_tolerance_when_validated_then_rejected() -> None:
    problem = a_day(second={"window": (0, 210 - 2e-6)}).build()

    assert problem.validate(served_in_order(0, 1)).rules() == {RuleId.BR_03}


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

    assert day.build().validate(served_in_order(0)).timings[0].leaves_at == 50


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

    assert RuleId.BR_119 in report.rules()


def test_given_a_hard_window_beside_door_stops_when_missed_then_still_rejected() -> None:
    report = door_day(door_window=(0, 50)).validate(served_in_order(1, 0))

    assert report.rules() == {RuleId.BR_03}


# --- what the validator accepts besides routes ---


def test_given_a_pinned_order_dropped_when_validated_then_accepted() -> None:
    problem = a_day(first={"pin": Pin(vehicle=0)}).build()

    solution = Solution(routes=served_in_order(1).routes, dropped=frozenset({0}))

    assert problem.validate(solution).accepted


def test_given_excluded_orders_left_off_the_dropped_list_when_validated_then_accepted() -> None:
    day = a_day()
    day.order(None)
    day.order(150, reachable=False)

    assert day.build().validate(served_in_order(0, 1)).accepted


def test_given_a_route_with_no_stops_when_validated_then_accepted_and_untimed() -> None:
    problem = a_day().build()
    solution = Solution(
        routes=(Route(vehicle=0, driver=0, start=0, end=0, stops=()),),
        dropped=frozenset({0, 1}),
    )

    report = problem.validate(solution)

    assert report.accepted
    assert len(report.timings[0].leg_seconds) == 0


def test_given_an_unroutable_stop_when_timed_then_skipped_and_later_stops_still_checked() -> None:
    day = a_day(second={"window": (0, 150)})
    hidden = day.order(None)

    report = day.build().validate(served_in_order(0, hidden, 1))

    assert report.rules() == {RuleId.BR_14, RuleId.BR_03}


@pytest.mark.parametrize(
    "route",
    [
        Route(vehicle=5, driver=0, start=0, end=0, stops=(0,)),
        Route(vehicle=0, driver=5, start=0, end=0, stops=(0,)),
        Route(vehicle=0, driver=0, start=99, end=0, stops=(0,)),
        Route(vehicle=0, driver=0, start=0, end=99, stops=(0,)),
        Route(vehicle=0, driver=0, start=0, end=0, stops=(9,)),
    ],
)
def test_given_an_index_past_the_problem_when_validated_then_refused(route: Route) -> None:
    with pytest.raises(InvalidSolutionError):
        a_day().build().validate(Solution(routes=(route,), dropped=frozenset({1})))


def test_given_a_dropped_index_past_the_problem_when_validated_then_refused() -> None:
    with pytest.raises(InvalidSolutionError):
        a_day().build().validate(Solution(routes=(), dropped=frozenset({0, 1, 9})))
