"""The penalty scale: the drop unit K and the rates the objective charges, in seconds.

K = 5L + 2T + F + 1 exceeds the most that serving one order can cost directly, so no order is
dropped to save its detour, its window penalty or a vehicle (BR-08, BR-55, BR-121, BR-126,
ADR-049). L is the larger of the date's longest shift and the longest a door stop can be served
outside its window within a shift, so a door window wholly outside its driver's shift is covered
too (ADR-100). In a re-optimization L also covers the longest a stop can be late within a shift,
because BR-115 charges lateness at a rate per second as BR-120 charges a door stop (ADR-105).
"""

import math
from dataclasses import dataclass
from typing import Final

from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.indices import require_float

DEFAULT_DOOR_WINDOW_RATE: Final = 5.0
"""Seconds of objective per second a door stop is served outside its window: BR-120's starting
value, tuned before the study's measured runs."""

DEFAULT_LATENESS_RATE: Final = 5.0
"""Seconds of objective per second a stop is served past its window end in a re-optimization: each
late minute costs 5 minutes, BR-115's starting value, tuned before the study's measured runs."""

DEFAULT_MOVED_STOP_COST: Final = 300.0
"""Seconds of objective for each stop that leaves its vehicle in a re-optimization: 5 minutes of
travel time, BR-115's starting value, tuned before the study's measured runs."""

HIGHEST_PRIORITY: Final = 1
LOWEST_PRIORITY: Final = 5
_PRIORITY_CEILING: Final = LOWEST_PRIORITY + 1
"""BR-54 charges K x (6 - priority), so the lowest priority still pays K."""


@dataclass(frozen=True, slots=True)
class PenaltyScale:
    """What a drop, a second outside a door window, a used vehicle and a moved stop cost (BR-09).

    Attributes:
        drop_unit: K, in seconds (BR-126).
        door_window_rate: Seconds charged per second outside a door window (BR-120).
        fixed_vehicle_cost: Seconds charged per vehicle used, 0 when disabled (FR-21).
        lateness_rate: Seconds charged per second a stop is served past its window end; 0 in a
            daily solve, which keeps hard windows (BR-03, BR-115).
        moved_stop_cost: Seconds charged for each stop that leaves its vehicle; 0 in a daily solve,
            where no stop is on a vehicle yet (BR-115, FR-37).

    Raises:
        InvalidProblemError: If a value is no number, negative or not finite.
    """

    drop_unit: float
    door_window_rate: float
    fixed_vehicle_cost: float
    lateness_rate: float = 0.0
    moved_stop_cost: float = 0.0

    def __post_init__(self) -> None:
        """Refuses a value that is no number, negative or not finite, and holds it as a float."""
        for name in (
            "drop_unit",
            "door_window_rate",
            "fixed_vehicle_cost",
            "lateness_rate",
            "moved_stop_cost",
        ):
            object.__setattr__(self, name, _require_non_negative(name, getattr(self, name)))

    @classmethod
    def for_date(
        cls,
        *,
        longest_shift: float,
        longest_door_outside_window: float,
        largest_travel: float,
        fixed_vehicle_cost: float,
        door_window_rate: float,
        longest_late: float = 0.0,
        lateness_rate: float = 0.0,
        moved_stop_cost: float = 0.0,
    ) -> PenaltyScale:
        """Computes K for one delivery date (BR-126, ADR-049, ADR-100, ADR-105).

        The 5 in 5L is the rate the order's own penalty is charged at: the door-window rate, or in
        a re-optimization the larger of it and the lateness rate. Both start at 5, so K follows
        either when it is tuned (ADR-049). A daily solve leaves the three re-optimization terms at
        0, and K is BR-126's.

        Args:
            longest_shift: The longest shift on the date, in seconds.
            longest_door_outside_window: The longest a leave-at-door stop can be served outside
                its window within a shift on the date, in seconds; 0 with no door order or no
                usable vehicle.
            largest_travel: T, the largest unbuffered travel time at the highest factor.
            fixed_vehicle_cost: F, the fixed cost per vehicle used, 0 when disabled.
            door_window_rate: The door-window rate (BR-120).
            longest_late: The longest a stop with a hard window can be served past its window end
                within a shift on the date, in seconds; 0 in a daily solve (BR-107).
            lateness_rate: The lateness rate (BR-115); 0 in a daily solve.
            moved_stop_cost: The cost of a stop that leaves its vehicle (BR-115); 0 in a daily
                solve.

        Returns:
            The scale with K = rate x L + 2T + F + 1, L being the larger of the longest shift,
            the longest door time outside a window and the longest lateness.

        Raises:
            InvalidProblemError: If a term is no number, negative or not finite.
        """
        longest_shift = _require_non_negative("longest_shift", longest_shift)
        longest_door_outside_window = _require_non_negative(
            "longest_door_outside_window", longest_door_outside_window
        )
        largest_travel = _require_non_negative("largest_travel", largest_travel)
        fixed_vehicle_cost = _require_non_negative("fixed_vehicle_cost", fixed_vehicle_cost)
        door_window_rate = _require_non_negative("door_window_rate", door_window_rate)
        longest_late = _require_non_negative("longest_late", longest_late)
        lateness_rate = _require_non_negative("lateness_rate", lateness_rate)
        moved_stop_cost = _require_non_negative("moved_stop_cost", moved_stop_cost)
        longest = max(longest_shift, longest_door_outside_window, longest_late)
        rate = max(door_window_rate, lateness_rate)
        drop_unit = rate * longest + 2 * largest_travel + fixed_vehicle_cost + 1
        return cls(
            drop_unit=drop_unit,
            door_window_rate=door_window_rate,
            fixed_vehicle_cost=fixed_vehicle_cost,
            lateness_rate=lateness_rate,
            moved_stop_cost=moved_stop_cost,
        )

    def drop_penalty(self, priority: int) -> float:
        """What dropping an order of a priority costs: K x (6 - priority) (BR-54).

        Args:
            priority: From 1, the highest, to 5 (BR-53).

        Returns:
            The penalty in seconds.

        Raises:
            InvalidProblemError: If the priority is off the 1 to 5 scale.
        """
        if not HIGHEST_PRIORITY <= priority <= LOWEST_PRIORITY:
            raise InvalidProblemError(f"a priority runs from 1 to 5: {priority}")
        return self.drop_unit * (_PRIORITY_CEILING - priority)


def _require_non_negative(name: str, value: object) -> float:
    number = require_float(name, value)
    if not (math.isfinite(number) and number >= 0):
        raise InvalidProblemError(f"{name} must be finite and zero or more: {number}")
    return number
