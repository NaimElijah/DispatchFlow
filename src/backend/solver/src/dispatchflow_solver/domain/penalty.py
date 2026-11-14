"""The penalty scale: the drop unit K and the rates the objective charges, in seconds.

K = 5L + 2T + F + 1 is meant to exceed the most that serving one order can cost directly, so no
order is dropped to save its detour, its window penalty or a vehicle (BR-08, BR-55, BR-121,
BR-126, ADR-049).

The guarantee holds only when every door order's window meets its driver's shift. A door order
whose window lies wholly outside its driver's shift is served outside its window for longer than
the shift lasts, so its window penalty can pass 5L, and serving it alone can cost more than K.
That case waits on the owner's decision (ADR-049's bound; BR-121).
"""

import math
from dataclasses import dataclass
from typing import Final

from dispatchflow_solver.domain.errors import InvalidProblemError

DEFAULT_DOOR_WINDOW_RATE: Final = 5.0
"""Seconds of objective per second a door stop is served outside its window: BR-120's starting
value, tuned before the study's measured runs."""

HIGHEST_PRIORITY: Final = 1
LOWEST_PRIORITY: Final = 5
_PRIORITY_CEILING: Final = LOWEST_PRIORITY + 1
"""BR-54 charges K x (6 - priority), so the lowest priority still pays K."""


@dataclass(frozen=True, slots=True)
class PenaltyScale:
    """What a drop, a second outside a door window and a used vehicle cost (BR-09).

    Attributes:
        drop_unit: K, in seconds (BR-126).
        door_window_rate: Seconds charged per second outside a door window (BR-120).
        fixed_vehicle_cost: Seconds charged per vehicle used, 0 when disabled (FR-21).

    Raises:
        InvalidProblemError: If a value is negative or not finite.
    """

    drop_unit: float
    door_window_rate: float
    fixed_vehicle_cost: float

    def __post_init__(self) -> None:
        """Refuses a negative or non-finite value."""
        for name in ("drop_unit", "door_window_rate", "fixed_vehicle_cost"):
            _require_non_negative(name, getattr(self, name))

    @classmethod
    def for_date(
        cls,
        *,
        longest_shift: float,
        largest_travel: float,
        fixed_vehicle_cost: float,
        door_window_rate: float,
    ) -> PenaltyScale:
        """Computes K for one delivery date (BR-126, ADR-049).

        The 5 in 5L is the door-window rate, so K follows the rate when it is tuned (ADR-049).

        Args:
            longest_shift: L, the longest shift on the date, in seconds.
            largest_travel: T, the largest unbuffered travel time at the highest factor.
            fixed_vehicle_cost: F, the fixed cost per vehicle used, 0 when disabled.
            door_window_rate: The door-window rate (BR-120).

        Returns:
            The scale with K = rate x L + 2T + F + 1.

        Raises:
            InvalidProblemError: If a term is negative or not finite.
        """
        _require_non_negative("longest_shift", longest_shift)
        _require_non_negative("largest_travel", largest_travel)
        _require_non_negative("fixed_vehicle_cost", fixed_vehicle_cost)
        _require_non_negative("door_window_rate", door_window_rate)
        drop_unit = door_window_rate * longest_shift + 2 * largest_travel + fixed_vehicle_cost + 1
        return cls(
            drop_unit=drop_unit,
            door_window_rate=door_window_rate,
            fixed_vehicle_cost=fixed_vehicle_cost,
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


def _require_non_negative(name: str, value: float) -> None:
    if not (math.isfinite(value) and value >= 0):
        raise InvalidProblemError(f"{name} must be finite and zero or more: {value}")
