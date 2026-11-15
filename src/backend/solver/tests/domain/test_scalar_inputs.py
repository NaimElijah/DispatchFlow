"""Scalar inputs that are no number, a bool, or a whole number past a float, refused by name."""

from collections.abc import Callable
from dataclasses import replace

import pytest

from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.factory import DepotSpec
from dispatchflow_solver.domain.problem import Pin, Problem
from tests.domain.builders import DEPOT_LOCATION, DayBuilder, LiveDay, delivered, position

PAST_A_FLOAT = 10**400
NOW = 150.0


def a_live_day() -> LiveDay:
    day = DayBuilder()
    day.vehicle(shift=(0.0, 1_000.0))
    day.order(100, service=10)
    day.order(200, service=10)
    live = LiveDay(day, now=NOW)
    live.route(0, delivered(0, 100), 1)
    return live


def a_daily_day() -> DayBuilder:
    day = DayBuilder()
    day.vehicle()
    day.order(100)
    return day


def daily_with(change: Callable[[DayBuilder], None]) -> Callable[[], Problem]:
    def build() -> Problem:
        day = a_daily_day()
        change(day)
        return day.build()

    return build


def live_with(change: Callable[[LiveDay], None]) -> Callable[[], Problem]:
    def build() -> Problem:
        live = a_live_day()
        change(live)
        return live.build()

    return build


def depot_hours(day: DayBuilder, **hours: object) -> None:
    day.depots[0] = DepotSpec(location=DEPOT_LOCATION, **hours)  # type: ignore[arg-type]


def volume(day: DayBuilder, value: object) -> None:
    day.vehicles[0] = replace(day.vehicles[0], volume_capacity_l=value)  # type: ignore[arg-type]


def positioned(live: LiveDay, **fields: object) -> None:
    live.position_at(0, 150, replace(position(NOW), **fields))  # type: ignore[arg-type]


def reported_at(live: LiveDay, at: object) -> None:
    live.route(0, delivered(0, at), 1)  # type: ignore[arg-type]


def context_now(value: object) -> Problem:
    problem = a_live_day().build()
    assert problem.reoptimization is not None
    return replace(problem, reoptimization=replace(problem.reoptimization, now=value))  # type: ignore[arg-type]


REFUSED: list[object] = [
    pytest.param(
        live_with(lambda live: setattr(live, "now", "150")), r"^now must be a number", id="now text"
    ),
    pytest.param(
        live_with(lambda live: setattr(live, "now", True)), r"^now must be a number", id="now bool"
    ),
    pytest.param(
        live_with(lambda live: setattr(live, "now", PAST_A_FLOAT)),
        r"^now must be a number that fits a float$",
        id="now past a float",
    ),
    pytest.param(
        live_with(lambda live: reported_at(live, "100")),
        r"stops\[0\]\.reported_at must be a number",
        id="reported_at text",
    ),
    pytest.param(
        live_with(lambda live: reported_at(live, PAST_A_FLOAT)),
        r"stops\[0\]\.reported_at must be a number that fits a float",
        id="reported_at past a float",
    ),
    pytest.param(
        live_with(lambda live: positioned(live, latitude="32")),
        r"latest_position\.latitude must be a number",
        id="latitude text",
    ),
    pytest.param(
        live_with(lambda live: positioned(live, longitude=True)),
        r"latest_position\.longitude must be a number",
        id="longitude bool",
    ),
    pytest.param(
        live_with(lambda live: positioned(live, received_at=PAST_A_FLOAT)),
        r"latest_position\.received_at must be a number that fits a float",
        id="received_at past a float",
    ),
    pytest.param(
        live_with(lambda live: positioned(live, in_service_area=1)),
        r"latest_position\.in_service_area must be a bool$",
        id="in_service_area 1",
    ),
    pytest.param(
        live_with(lambda live: positioned(live, in_service_area="inside")),
        r"latest_position\.in_service_area must be a bool$",
        id="in_service_area text",
    ),
    pytest.param(
        live_with(lambda live: setattr(live, "lateness_rate", "5")),
        r"lateness_rate must be a number",
        id="lateness_rate text",
    ),
    pytest.param(
        live_with(lambda live: setattr(live, "moved_stop_cost", True)),
        r"moved_stop_cost must be a number",
        id="moved_stop_cost bool",
    ),
    pytest.param(
        live_with(lambda live: setattr(live.day, "day_length", "86400")),
        r"day_length must be a number",
        id="re-optimization day_length text",
    ),
    pytest.param(
        lambda: context_now("150"), r"reoptimization\.now must be a number", id="context now"
    ),
    pytest.param(
        daily_with(lambda day: setattr(day, "day_length", "86400")),
        r"day_length must be a number",
        id="daily day_length text",
    ),
    pytest.param(
        daily_with(lambda day: setattr(day, "day_length", PAST_A_FLOAT)),
        r"day_length must be a number that fits a float",
        id="daily day_length past a float",
    ),
    pytest.param(
        daily_with(lambda day: depot_hours(day, opens="08:00")),
        r"depots\[0\]\.opens must be a number",
        id="opens text",
    ),
    pytest.param(
        daily_with(lambda day: depot_hours(day, closes=PAST_A_FLOAT)),
        r"depots\[0\]\.closes must be a number that fits a float",
        id="closes past a float",
    ),
    pytest.param(
        daily_with(lambda day: volume(day, "10")),
        r"vehicles\.volume_capacity_l\[0\] must be a number: '10'",
        id="daily volume capacity text",
    ),
    pytest.param(
        live_with(lambda live: volume(live.day, PAST_A_FLOAT)),
        r"vehicles\.volume_capacity_l\[0\] must be a number that fits a float",
        id="re-optimization volume capacity past a float",
    ),
    pytest.param(
        daily_with(lambda day: setattr(day, "fixed_vehicle_cost", "0")),
        r"fixed_vehicle_cost must be a number",
        id="fixed_vehicle_cost text",
    ),
]


@pytest.mark.parametrize(("build", "match"), REFUSED)
def test_given_a_scalar_that_is_no_number_when_built_then_refused_by_name(
    build: Callable[[], Problem], match: str
) -> None:
    with pytest.raises(InvalidProblemError, match=match):
        build()


@pytest.mark.parametrize("target", ["vehicle", "driver"])
def test_given_a_pin_to_text_when_built_then_refused_by_the_pin(target: str) -> None:
    with pytest.raises(InvalidProblemError, match=rf"^a pin's {target} must be a number: 'x'$"):
        Pin(**{target: "x"})  # type: ignore[arg-type]
