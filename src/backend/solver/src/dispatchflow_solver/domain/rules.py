"""The rules the feasibility validator checks, and how it compares limits.

Each `RuleId` is named after the requirement row it enforces (BR-15, BR-16, NFR-169). BR-10, BR-51,
BR-52, BR-106 and BR-108 can break only in a re-optimization, whose conventions the problem carries
(ADR-050, ADR-105). Some rows have no member:

- BR-122: a door stop is always re-timed to be served on arrival, and a wait a solver plans there
  is replaced, never rejected (FR-403).
- BR-23, BR-24, BR-79, BR-80, BR-91 and BR-117: conventions the factory applies, which the
  validator then checks as BR-10, BR-51 and BR-106.
- BR-107: a late stop in a re-optimization is flagged, never refused, so it is a finding of the
  report, not a violation.
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
    """Every order is served by exactly one stop or dropped, never both and never twice. A locked
    order is served by its report, so BR-10 judges it instead."""
    BR_10 = "BR-10"
    """A re-optimization never moves a locked stop: one reported done or in progress appears on no
    route and is not dropped."""
    BR_14 = "BR-14"
    """A stop is an order with a location, reached and left by road (BR-14, FR-66)."""
    BR_20 = "BR-20"
    """The route's driver is the vehicle's rostered driver (BR-20, FR-54)."""
    BR_39 = "BR-39"
    """A vehicle drives at most one route, and so does a driver."""
    BR_40 = "BR-40"
    """A route starts at its vehicle's start depot and ends at its end depot, on a vehicle that can
    drive a route (FR-197). In a re-optimization BR-51 judges the start, and BR-52 a withdrawn
    route's end."""
    BR_51 = "BR-51"
    """In a re-optimization, a route starts where its vehicle is placed: its stop in progress, its
    usable position, its last reported stop or its start depot (BR-24, BR-79, BR-80)."""
    BR_52 = "BR-52"
    """A withdrawn route ends at its last reported stop."""
    BR_46 = "BR-46"
    """A route returns no later than its end depot closes."""
    BR_105 = "BR-105"
    """Where breaks are reserved, a route on a shift of 6 hours or more returns 45 minutes early."""
    BR_106 = "BR-106"
    """In a re-optimization, a stop stays on its vehicle, and a stop that may move goes only to an
    active vehicle; it never goes to one that is unavailable, without a driver or out of shift
    (BR-117)."""
    BR_108 = "BR-108"
    """A re-optimization adds no stop to a finished route, and a withdrawn route counts as
    finished."""
    BR_119 = "BR-119"
    """A leave-at-door stop is served within its driver's shift and on its date. Only the shift
    end is checked: it lies on the date (FR-190), and a daily route leaves no earlier than the
    shift starts. A re-optimization's route may leave before the shift starts, when its vehicle is
    already on the road, and a door stop served then is accepted (BR-23, ADR-105)."""
    FR_91 = "FR-91"
    """A pinned order is served by its pinned vehicle or driver, or dropped (FR-91, FR-228)."""
