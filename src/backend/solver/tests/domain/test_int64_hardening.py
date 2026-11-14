"""Whole numbers past int64 are refused by name, never formatted (CPython's 4,300-digit limit)."""

from dataclasses import replace

import pytest

from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.problem import Pin
from tests.domain.builders import DayBuilder

HUGE = 10**5000


@pytest.mark.parametrize(
    "pin",
    [
        pytest.param(lambda: Pin(vehicle=-HUGE), id="vehicle of minus 5,001 digits"),
        pytest.param(lambda: Pin(vehicle=HUGE), id="vehicle of 5,001 digits"),
        pytest.param(lambda: Pin(driver=HUGE), id="driver of 5,001 digits"),
        pytest.param(lambda: Pin(driver=-HUGE), id="driver of minus 5,001 digits"),
    ],
)
def test_given_a_pin_index_outside_int64_when_built_then_refused_without_formatting_it(
    pin: object,
) -> None:
    with pytest.raises(InvalidProblemError, match=r"^a pin's \w+ is outside the int64 range$"):
        pin()  # type: ignore[operator]


@pytest.mark.parametrize("order", [HUGE, -HUGE], ids=["5,001 digits", "minus 5,001 digits"])
def test_given_a_removed_order_outside_int64_when_built_then_refused_without_formatting_it(
    order: int,
) -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(100)
    day.removed_by_dispatcher.add(order)

    with pytest.raises(
        InvalidProblemError, match=r"^removed_by_dispatcher\[\] is outside the int64 range$"
    ):
        day.build()


def test_given_a_removed_order_outside_int64_in_a_built_problem_when_replaced_then_refused() -> (
    None
):
    day = DayBuilder()
    day.vehicle()
    day.order(100)
    problem = day.build()

    with pytest.raises(InvalidProblemError, match="outside the int64 range"):
        replace(problem, removed_by_dispatcher=frozenset({HUGE}))


@pytest.mark.parametrize("order", [True, 0.0, "0"], ids=["bool", "float", "text"])
def test_given_a_removed_order_that_is_no_int_when_built_then_refused(order: object) -> None:
    day = DayBuilder()
    day.vehicle()
    day.order(100)
    day.removed_by_dispatcher.add(order)  # type: ignore[arg-type]

    with pytest.raises(InvalidProblemError, match=r"removed_by_dispatcher\[\] must be an int"):
        day.build()
