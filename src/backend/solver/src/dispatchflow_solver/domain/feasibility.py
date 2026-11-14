"""The feasibility validator: the single definition of a feasible daily solution (BR-15, BR-42).

It re-times every route itself and reports every broken rule, never only the first, and repairs
nothing (BR-16). Times use buffered travel (FR-401), and limits are inclusive within ADR-098's
tolerance (NFR-168).
"""

import math
from collections import Counter
from dataclasses import dataclass
from typing import TYPE_CHECKING, Final

import numpy as np
import numpy.typing as npt

from dispatchflow_solver.domain._arrays import NO_INDEX, frozen_floats
from dispatchflow_solver.domain.rules import TOLERANCE, RuleId
from dispatchflow_solver.domain.solution import InvalidSolutionError, Route, Solution

if TYPE_CHECKING:
    from dispatchflow_solver.domain.problem import Problem

BREAK_SHIFT_THRESHOLD_S: Final = 6 * 3600.0
"""A shift this long or longer reserves time for a break, where breaks are reserved (BR-105)."""
BREAK_RESERVE_S: Final = 45 * 60.0
"""The time such a route keeps free before its shift ends (BR-105)."""


@dataclass(frozen=True, slots=True)
class Violation:
    """One broken rule, with what it concerns and by how much (BR-16, BR-57).

    Attributes:
        rule: The rule broken.
        route: The route's index in the solution, if one route is concerned.
        order: The order concerned, if one is.
        vehicle: The vehicle concerned, if one is.
        driver: The driver concerned, if one is.
        seconds: By how many seconds a time limit is passed, for a time rule.
        kilograms: By how many kilograms the weight capacity is passed (BR-05).
        litres: By how many litres the volume capacity is passed (BR-05).
    """

    rule: RuleId
    route: int | None = None
    order: int | None = None
    vehicle: int | None = None
    driver: int | None = None
    seconds: float | None = None
    kilograms: float | None = None
    litres: float | None = None


@dataclass(frozen=True, slots=True, eq=False)
class RouteTiming:
    """The validator's own times along one route; NaN where a stop could not be timed.

    Attributes:
        leaves_at: When the route leaves its start.
        arrival: When the vehicle reaches each stop.
        start: When service starts at each stop.
        departure: When the vehicle leaves each stop.
        late: Seconds each service starts after its window ends.
        outside_window: Seconds each service starts outside its window, early or late.
        leg_seconds: Buffered travel seconds of the leg into each stop, then of the leg into the
            end; none for a route with no stops. After a skipped stop, the leg runs from the last
            place the route reached.
        leg_metres: Metres of the same legs.
        returns_at: When the route reaches its end.
    """

    leaves_at: float
    arrival: npt.NDArray[np.float64]
    start: npt.NDArray[np.float64]
    departure: npt.NDArray[np.float64]
    late: npt.NDArray[np.float64]
    outside_window: npt.NDArray[np.float64]
    leg_seconds: npt.NDArray[np.float64]
    leg_metres: npt.NDArray[np.float64]
    returns_at: float

    def __post_init__(self) -> None:
        """Copies every array read-only."""
        for name in (
            "arrival",
            "start",
            "departure",
            "late",
            "outside_window",
            "leg_seconds",
            "leg_metres",
        ):
            object.__setattr__(self, name, frozen_floats(getattr(self, name)))


type Timings = tuple[RouteTiming, ...]
"""One `RouteTiming` per route of a solution, in the solution's order."""


@dataclass(frozen=True, slots=True)
class ValidationReport:
    """The validator's verdict on one solution.

    Attributes:
        violations: Every violation found, in the order found.
        timings: The validator's times for each route, which `Problem.cost` reads.
    """

    violations: tuple[Violation, ...]
    timings: Timings

    @property
    def accepted(self) -> bool:
        """Whether the solution breaks no rule (BR-15)."""
        return not self.violations

    def rules(self) -> frozenset[RuleId]:
        """The set of rules broken."""
        return frozenset(violation.rule for violation in self.violations)


def validate(problem: Problem, solution: Solution) -> ValidationReport:
    """Judges a solution; `Problem.validate` delegates here.

    Each route leaves at its earliest moment, the later of its driver's shift start and its start
    depot's opening (BR-06, BR-46). A stop with a hard window waits for the window to open
    (BR-03); a door stop is served on arrival, whatever its window (BR-122, FR-403). A stop whose
    leg has no road route cannot be timed: it is skipped, its times are NaN, and the route goes on
    from the last place it reached. That happens only at a stop BR-14 already reports, or after a
    start BR-40 already reports, because the problem guarantees a road between the locations a
    solve may use. A route with no stops is not driven, so it has no times.

    Args:
        problem: The problem.
        solution: Any solution whose indices fall inside the problem.

    Returns:
        Every violation, and one timing per route.

    Raises:
        InvalidSolutionError: If an index in the solution points past the problem.
    """
    _require_inside(problem, solution)
    violations: list[Violation] = []
    _check_single_assignment(problem, solution, violations)
    _check_one_route_each(solution, violations)
    timings = tuple(
        _RouteCheck(problem, index, route, violations).run()
        for index, route in enumerate(solution.routes)
    )
    return ValidationReport(violations=tuple(violations), timings=timings)


def earliest_departure(problem: Problem, vehicle: int, driver: int) -> float:
    """When a route may leave: the later of its driver's shift start and its depot's opening.

    Args:
        problem: The problem.
        vehicle: The route's vehicle.
        driver: The route's driver.

    Returns:
        The earliest departure (BR-06, BR-46).
    """
    return max(
        float(problem.drivers.shift_start[driver]),
        float(problem.vehicles.start_depot_opens[vehicle]),
    )


def shift_return_limit(problem: Problem, driver: int) -> tuple[float, RuleId]:
    """The latest return a driver's shift allows, and the rule that sets it.

    Where breaks are reserved, a shift of 6 hours or more must end with 45 minutes free (BR-105);
    otherwise the shift end is the limit (BR-06).

    Args:
        problem: The problem.
        driver: The driver.

    Returns:
        The latest return and the rule behind it.
    """
    shift_end = float(problem.drivers.shift_end[driver])
    if problem.breaks_reserved and (
        problem.drivers.shift_length(driver) >= BREAK_SHIFT_THRESHOLD_S - TOLERANCE
    ):
        return shift_end - BREAK_RESERVE_S, RuleId.BR_105
    return shift_end, RuleId.BR_06


def door_stop_outside(problem: Problem, driver: int, start: float) -> float:
    """How far a door stop's start lies outside its driver's shift or its date (BR-119).

    Args:
        problem: The problem.
        driver: The driver serving the stop.
        start: When service starts.

    Returns:
        The seconds by which the start misses the shift or the date; 0 or less within both.
    """
    earliest = max(float(problem.drivers.shift_start[driver]), 0.0)
    latest = min(float(problem.drivers.shift_end[driver]), problem.day_length)
    return max(earliest - start, start - latest)


def road(problem: Problem, origin: int, destination: int) -> float | None:
    """A leg's base duration, or None when there is no road route or no location.

    Args:
        problem: The problem.
        origin: The leg's first location, or -1 for none.
        destination: The leg's last location, or -1 for none.

    Returns:
        The base duration in seconds, or None.
    """
    if NO_INDEX in (origin, destination):
        return None
    base = float(problem.durations[origin, destination])
    return None if math.isnan(base) else base


def _require_inside(problem: Problem, solution: Solution) -> None:
    locations = problem.durations.shape[0]
    for route in solution.routes:
        if route.vehicle >= len(problem.vehicles) or route.driver >= len(problem.drivers):
            raise InvalidSolutionError(
                f"a route names a vehicle or driver past the problem: {route}"
            )
        if route.start >= locations or route.end >= locations:
            raise InvalidSolutionError(f"a route names a location past the problem: {route}")
        if any(order >= len(problem.orders) for order in route.stops):
            raise InvalidSolutionError(f"a route names an order past the problem: {route}")
    if any(order >= len(problem.orders) for order in solution.dropped):
        raise InvalidSolutionError("a dropped order is past the problem")


def _check_single_assignment(
    problem: Problem, solution: Solution, violations: list[Violation]
) -> None:
    # BR-07. An order the problem excluded counts as dropped even when the solution does not list
    # it, because no solver ever saw it.
    stops = Counter(order for route in solution.routes for order in route.stops)
    for order in range(len(problem.orders)):
        served = stops[order]
        listed = order in solution.dropped
        dropped = listed or not problem.orders.is_routable(order)
        if served > 1 or (served == 1 and listed) or (served == 0 and not dropped):
            violations.append(Violation(RuleId.BR_07, order=order))


def _check_one_route_each(solution: Solution, violations: list[Violation]) -> None:
    # BR-39: every route after a vehicle's or a driver's first is a violation of its own.
    vehicles: set[int] = set()
    drivers: set[int] = set()
    for index, route in enumerate(solution.routes):
        if route.vehicle in vehicles:
            violations.append(Violation(RuleId.BR_39, route=index, vehicle=route.vehicle))
        if route.driver in drivers:
            violations.append(Violation(RuleId.BR_39, route=index, driver=route.driver))
        vehicles.add(route.vehicle)
        drivers.add(route.driver)


@dataclass(slots=True)
class _RouteCheck:
    """Checks one route's own rules and times it, appending what it finds."""

    problem: Problem
    index: int
    route: Route
    violations: list[Violation]

    def run(self) -> RouteTiming:
        vehicles = self.problem.vehicles
        if self.route.start != vehicles.start_location[self.route.vehicle]:
            self._report(RuleId.BR_40)
        if self.route.end != vehicles.end_location[self.route.vehicle]:
            self._report(RuleId.BR_40)
        self._check_loads()
        self._check_stops_allowed()
        return self._time()

    def _report(
        self,
        rule: RuleId,
        *,
        order: int | None = None,
        driver: int | None = None,
        seconds: float | None = None,
        kilograms: float | None = None,
        litres: float | None = None,
    ) -> None:
        self.violations.append(
            Violation(
                rule,
                route=self.index,
                order=order,
                vehicle=self.route.vehicle,
                driver=driver,
                seconds=seconds,
                kilograms=kilograms,
                litres=litres,
            )
        )

    def _check_loads(self) -> None:
        orders, vehicles, vehicle = self.problem.orders, self.problem.vehicles, self.route.vehicle
        stops = list(self.route.stops)
        weight_excess = float(orders.weight_kg[stops].sum() - vehicles.weight_capacity_kg[vehicle])
        volume_excess = float(orders.volume_l[stops].sum() - vehicles.volume_capacity_l[vehicle])
        if weight_excess > TOLERANCE:
            self._report(RuleId.BR_05, kilograms=weight_excess)
        if volume_excess > TOLERANCE:
            self._report(RuleId.BR_05, litres=volume_excess)

    def _check_stops_allowed(self) -> None:
        orders = self.problem.orders
        for order in self.route.stops:
            if not orders.is_routable(order):
                self._report(RuleId.BR_14, order=order)
            pinned_vehicle = int(orders.pinned_vehicle[order])
            pinned_driver = int(orders.pinned_driver[order])
            if pinned_vehicle not in (NO_INDEX, self.route.vehicle) or pinned_driver not in (
                NO_INDEX,
                self.route.driver,
            ):
                self._report(RuleId.FR_91, order=order)

    def _time(self) -> RouteTiming:
        stops = self.route.stops
        if not stops:
            return _TimingDraft.of(0, legs=0).freeze(math.nan, math.nan)
        problem, route = self.problem, self.route
        draft = _TimingDraft.of(len(stops), legs=len(stops) + 1)
        leaves_at = earliest_departure(problem, route.vehicle, route.driver)
        clock, here = leaves_at, route.start
        for position, order in enumerate(stops):
            there = int(problem.orders.location[order])
            base = road(problem, here, there)
            if base is None:
                continue
            leg = problem.travel.buffered(base, clock)
            draft.legs[position] = (leg, float(problem.distances[here, there]))
            clock = self._serve(draft, position, order, arrival=clock + leg)
            here = there
        base = road(problem, here, route.end)
        if base is None:
            return draft.freeze(leaves_at, math.nan)
        leg = problem.travel.buffered(base, clock)
        draft.legs[len(stops)] = (leg, float(problem.distances[here, route.end]))
        self._check_return(clock + leg)
        return draft.freeze(leaves_at, clock + leg)

    def _serve(self, draft: _TimingDraft, position: int, order: int, *, arrival: float) -> float:
        orders = self.problem.orders
        window_start = float(orders.window_start[order])
        window_end = float(orders.window_end[order])
        door = bool(orders.leave_at_door[order])
        start = arrival if door else max(arrival, window_start)
        late = max(0.0, start - window_end)
        departure = start + float(orders.service_s[order])
        draft.arrival[position] = arrival
        draft.start[position] = start
        draft.departure[position] = departure
        draft.late[position] = late
        draft.outside[position] = max(late, window_start - start, 0.0)
        if door:
            outside = door_stop_outside(self.problem, self.route.driver, start)
            if outside > TOLERANCE:
                self._report(RuleId.BR_119, order=order, seconds=outside)
        elif late > TOLERANCE:
            self._report(RuleId.BR_03, order=order, seconds=late)
        if self.route.planned_starts is not None:
            # A claim earlier than the validator's own start leaves out time the vehicle spends,
            # such as the previous stop's service. A later claim is a planned wait, which the
            # validator's own times replace (FR-403).
            ahead = start - self.route.planned_starts[position]
            if ahead > TOLERANCE:
                self._report(RuleId.BR_04, order=order, seconds=ahead)
        return departure

    def _check_return(self, returns_at: float) -> None:
        problem, driver = self.problem, self.route.driver
        shift_end = float(problem.drivers.shift_end[driver])
        if returns_at - shift_end > TOLERANCE:
            self._report(RuleId.BR_06, driver=driver, seconds=returns_at - shift_end)
        limit, rule = shift_return_limit(problem, driver)
        if rule is RuleId.BR_105 and returns_at - limit > TOLERANCE:
            self._report(RuleId.BR_105, driver=driver, seconds=returns_at - limit)
        closes = float(problem.vehicles.end_depot_closes[self.route.vehicle])
        if returns_at - closes > TOLERANCE:
            self._report(RuleId.BR_46, seconds=returns_at - closes)


@dataclass(slots=True)
class _TimingDraft:
    """A route's times while they are computed; NaN until a stop is timed."""

    arrival: list[float]
    start: list[float]
    departure: list[float]
    late: list[float]
    outside: list[float]
    legs: list[tuple[float, float]]

    @classmethod
    def of(cls, stop_count: int, *, legs: int) -> _TimingDraft:
        return cls(
            arrival=[math.nan] * stop_count,
            start=[math.nan] * stop_count,
            departure=[math.nan] * stop_count,
            late=[math.nan] * stop_count,
            outside=[math.nan] * stop_count,
            legs=[(math.nan, math.nan)] * legs,
        )

    def freeze(self, leaves_at: float, returns_at: float) -> RouteTiming:
        return RouteTiming(
            leaves_at=leaves_at,
            arrival=np.array(self.arrival),
            start=np.array(self.start),
            departure=np.array(self.departure),
            late=np.array(self.late),
            outside_window=np.array(self.outside),
            leg_seconds=np.array([seconds for seconds, _ in self.legs]),
            leg_metres=np.array([metres for _, metres in self.legs]),
            returns_at=returns_at,
        )
