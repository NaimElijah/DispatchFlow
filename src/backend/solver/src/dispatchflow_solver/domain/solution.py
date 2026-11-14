"""The routes and drops a solver proposes, before the validator judges them.

A `Solution` checks only that its fields have the right types and that its indices are not
negative, so a test can build any content, feasible or not (NFR-169). `Problem.validate` checks
the indices against the problem and every rule. A solution carries no drop reasons:
`Problem.explain_drop` gives them (ADR-044).
"""

import math
from dataclasses import dataclass


class InvalidSolutionError(ValueError):
    """A solution of the wrong shape: a bad type, a negative index or an index past the problem."""


@dataclass(frozen=True, slots=True)
class Route:
    """One vehicle's and one driver's ordered stops.

    Attributes:
        vehicle: The vehicle's index in the problem (BR-39).
        driver: The driver's index in the problem (BR-39).
        start: The location the route starts at (BR-40).
        end: The location the route ends at (BR-40).
        stops: The orders served, in order, by their index in the problem.
        planned_starts: The service starts the solver claims, one per stop, in seconds since the
            date's local midnight, or None when it claims none (BR-04).

    Raises:
        InvalidSolutionError: If a field has the wrong type, an index is negative, or the claimed
            starts are not one finite number per stop.
    """

    vehicle: int
    driver: int
    start: int
    end: int
    stops: tuple[int, ...]
    planned_starts: tuple[float, ...] | None = None

    def __post_init__(self) -> None:
        """Refuses a wrong type, a negative index or a claim that does not match the stops."""
        for name in ("vehicle", "driver", "start", "end"):
            _require_index(name, getattr(self, name))
        if not isinstance(self.stops, tuple):
            raise InvalidSolutionError(f"stops must be a tuple: {self.stops!r}")
        for stop in self.stops:
            _require_index("stop", stop)
        if self.planned_starts is not None:
            if not isinstance(self.planned_starts, tuple):
                raise InvalidSolutionError(
                    f"planned starts must be a tuple: {self.planned_starts!r}"
                )
            if len(self.planned_starts) != len(self.stops):
                raise InvalidSolutionError(
                    f"{len(self.planned_starts)} planned starts for {len(self.stops)} stops"
                )
            for claimed in self.planned_starts:
                if not (isinstance(claimed, int | float) and math.isfinite(claimed)):
                    raise InvalidSolutionError(
                        f"a planned start must be a finite number: {claimed!r}"
                    )


@dataclass(frozen=True, slots=True)
class Solution:
    """The routes and the dropped orders a solver proposes (ADR-044).

    Attributes:
        routes: The routes, in the order the solver gives them.
        dropped: The orders the solver leaves unserved. Orders the problem excluded before the
            search (no location, no road route) count as dropped whether or not they are listed.

    Raises:
        InvalidSolutionError: If a field has the wrong type or an order index is negative.
    """

    routes: tuple[Route, ...]
    dropped: frozenset[int]

    def __post_init__(self) -> None:
        """Refuses a wrong type or a negative order index."""
        if not isinstance(self.routes, tuple) or not all(
            isinstance(route, Route) for route in self.routes
        ):
            raise InvalidSolutionError(f"routes must be a tuple of Route: {self.routes!r}")
        if not isinstance(self.dropped, frozenset):
            raise InvalidSolutionError(f"dropped must be a frozenset: {self.dropped!r}")
        for order in self.dropped:
            _require_index("dropped order", order)

    def served(self) -> frozenset[int]:
        """The orders that appear on at least one route."""
        return frozenset(order for route in self.routes for order in route.stops)


def _require_index(name: str, value: object) -> None:
    # bool is an int subclass, but True as an index is a bug, not an index.
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise InvalidSolutionError(f"{name} must be a non-negative int: {value!r}")
