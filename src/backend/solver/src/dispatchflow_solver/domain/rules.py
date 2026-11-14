"""The rules the feasibility validator checks in a daily solve, and how it compares limits.

Each `RuleId` is named after the requirement row it enforces (BR-15, BR-16, NFR-169). The
re-optimization rules (BR-10, BR-23, BR-51, BR-52, BR-106 to BR-108) join with ADR-050's
conventions. BR-122 has no member: a door stop is always re-timed to be served on arrival, and
a wait a solver plans there is replaced, never rejected (FR-403).
"""

from enum import StrEnum
from typing import Final

LIMIT_TOLERANCE: Final = 1e-6
"""Seconds, kilograms or litres by which a value may pass a limit and still meet it (ADR-098).

Limits are inclusive (NFR-168), and travel times are float64, so an exact comparison would reject a
published Solomon solution over rounding noise (NFR-167).
"""


class RuleId(StrEnum):
    """A rule a solution can break, valued by its requirement row."""

    BR_03 = "BR-03"
    """Service at a stop with a hard window starts inside it; an early vehicle waits. A claimed
    start before the window opens breaks it too."""
    BR_04 = "BR-04"
    """A claimed service start is no earlier than the validator's own, for any reason other than
    the window: it ignores service time or travel, or assumes unbuffered or off-peak travel."""
    BR_05 = "BR-05"
    """A route's weight and volume stay within its vehicle's capacities."""
    BR_06 = "BR-06"
    """A route returns to its end depot no later than its driver's shift end."""
    BR_07 = "BR-07"
    """Every order is served by exactly one stop or dropped, never both and never twice."""
    BR_14 = "BR-14"
    """A stop is an order with a location, reached and left by road (BR-14, FR-66)."""
    BR_20 = "BR-20"
    """The route's driver is the vehicle's rostered driver (BR-20, FR-54)."""
    BR_39 = "BR-39"
    """A vehicle drives at most one route, and so does a driver."""
    BR_40 = "BR-40"
    """A route starts at its vehicle's start depot and ends at its end depot, on a vehicle that can
    drive a route (FR-197)."""
    BR_46 = "BR-46"
    """A route returns no later than its end depot closes."""
    BR_105 = "BR-105"
    """Where breaks are reserved, a route on a shift of 6 hours or more returns 45 minutes early."""
    BR_119 = "BR-119"
    """A leave-at-door stop is served within its driver's shift and on its date."""
    FR_91 = "FR-91"
    """A pinned order is served by its pinned vehicle or driver, or dropped (FR-91, FR-228)."""
