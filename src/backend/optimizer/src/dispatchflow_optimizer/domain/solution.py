"""The routes and drops a solver proposes, before the validator judges them.

A `Solution` checks only that its fields have the right types and that its indices are not
negative, so a test can build any content, feasible or not (NFR-169). `Problem.validate` checks
the indices against the problem and every rule. A solution carries no drop reasons:
`Problem.explain_drop` gives them (ADR-044).
"""

import math
from dataclasses import dataclass, field


class InvalidSolutionError(ValueError):
    """A solution of the wrong shape: a bad type, a negative index or an index past the problem.

    The message names the field and the offending index or value, never a whole route.
    """


@dataclass(frozen=True, slots=True)
class Route:
    """One vehicle's and one driver's ordered stops.

    Attributes:
        vehicle: The vehicle's index in the problem (BR-39).
        driver: The driver's index in the problem (BR-20, BR-39).
        start: The location the route starts at (BR-40).
        end: The location the route ends at (BR-40).
        stops: The orders served, in order, by their index in the problem.
        planned_starts: The service starts the solver claims, one per stop, in seconds since the
            date's local midnight, or None when it claims none (BR-03, BR-04).

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
        _require_tuple("stops", self.stops)
        for position, stop in enumerate(self.stops):
            _require_index(f"stops[{position}]", stop)
        if self.planned_starts is None:
            return
        _require_tuple("planned_starts", self.planned_starts)
        if len(self.planned_starts) != len(self.stops):
            raise InvalidSolutionError(
                f"{len(self.planned_starts)} planned starts for {len(self.stops)} stops"
            )
        for position, claimed in enumerate(self.planned_starts):
            if (
                isinstance(claimed, bool)
                or not isinstance(claimed, int | float)
                or not math.isfinite(claimed)
            ):
                raise InvalidSolutionError(
                    f"planned_starts[{position}] must be a finite number: {claimed!r}"
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
    _served: frozenset[int] = field(init=False, repr=False, compare=False)

    def __post_init__(self) -> None:
        """Refuses a wrong type or a negative order index, then notes the orders served."""
        _require_tuple("routes", self.routes)
        for index, route in enumerate(self.routes):
            if not isinstance(route, Route):
                raise InvalidSolutionError(
                    f"routes[{index}] is a {type(route).__name__}, not Route"
                )
        if not isinstance(self.dropped, frozenset):
            raise InvalidSolutionError(
                f"dropped must be a frozenset, not {type(self.dropped).__name__}"
            )
        for order in self.dropped:
            _require_index("dropped order", order)
        served = frozenset(order for route in self.routes for order in route.stops)
        object.__setattr__(self, "_served", served)

    def served(self) -> frozenset[int]:
        """The orders that appear on at least one route."""
        return self._served


def _require_index(name: str, value: object) -> None:
    # bool is an int subclass, but True as an index is a bug, not an index.
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise InvalidSolutionError(f"{name} must be a non-negative int: {value!r}")


def _require_tuple(name: str, value: object) -> None:
    if not isinstance(value, tuple):
        raise InvalidSolutionError(f"{name} must be a tuple, not {type(value).__name__}")
