"""The feasibility validator: the single definition of a feasible solution (BR-15, BR-42).

It re-times every route itself and reports every broken rule, never only the first, and repairs
nothing (BR-16). Times use buffered travel (FR-401), and limits are inclusive within ADR-098's
tolerance (NFR-168). `drops.explain_drop` times a lone order through `check_route`, so a drop
reason and the validator never disagree.

A re-optimization's problem carries its conventions, and the validator checks them where the
problem holds them (ADR-050, ADR-105): only what follows `now` is timed (BR-23), a locked stop is
a fact (BR-10), a stop keeps to the vehicles it may go to (BR-106, BR-108), and a stop past its
window end is a late stop, flagged and not refused (BR-107).

Every time is in seconds since the date's local midnight (ADR-088). A route that breaks a time
rule may be timed past the date's end, `Problem.day_length`; the times are reported as computed,
never capped, so the core carries them as seconds from the date's start (ADR-113).

It reads its problem as a `ProblemView` and never imports `problem`, which imports it (ADR-113).
"""

import math
from collections import Counter
from dataclasses import dataclass
from typing import Final

import numpy as np
import numpy.typing as npt

from dispatchflow_optimizer.domain._arrays import frozen_floats
from dispatchflow_optimizer.domain.exclusions import (
    FINISHED_ROUTES,
    OUT_BEFORE_A_STOP,
    ROAD_EXCLUSIONS,
    VehicleExclusion,
)
from dispatchflow_optimizer.domain.indices import NO_INDEX
from dispatchflow_optimizer.domain.problem_view import ProblemView
from dispatchflow_optimizer.domain.rules import LIMIT_TOLERANCE, RuleId
from dispatchflow_optimizer.domain.solution import InvalidSolutionError, Route, Solution

BREAK_SHIFT_THRESHOLD_S: Final = 6 * 3600.0
"""A shift this long or longer reserves time for a break, where breaks are reserved (BR-105).

Compared exactly: a shift short of it by no more than `LIMIT_TOLERANCE` reserves no break, because
the tolerance only ever relaxes a rule (ADR-098)."""
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
    """The validator's own times along one route; NaN where something could not be timed.

    Three things are not timed, and their times are NaN (ADR-113):

    - **A route with no stops** is not driven: `leaves_at` and `returns_at` are NaN, and every
      array is empty.
    - **A stop the route cannot serve** is skipped: one excluded or out of road reach breaks BR-14,
      and in a re-optimization a locked one breaks BR-10. Its `arrival`, `start`, `departure`,
      `late` and `outside_window`, and the leg into it, are NaN, and the route goes on from the
      last place it reached.
    - **The return, with no road to the end** from the last place reached: `returns_at` and the
      last leg are NaN, and the last stop served breaks BR-14.

    Every other time is a finite number of seconds since the date's local midnight. Times are not
    capped at the date's end: a route that breaks a time rule, such as BR-06, may arrive, serve or
    return past `Problem.day_length`. A route the validator accepts returns within its driver's
    shift, which lies on the date (FR-190), so none of its times passes the date's end by more
    than `LIMIT_TOLERANCE`.

    Attributes:
        leaves_at: When the route leaves its start; NaN for a route with no stops.
        arrival: When the vehicle reaches each stop; NaN for a stop not timed.
        start: When service starts at each stop; NaN for a stop not timed.
        departure: When the vehicle leaves each stop; NaN for a stop not timed.
        late: Seconds each service starts after its window ends; NaN for a stop not timed.
        outside_window: Seconds each service starts outside its window, early or late; NaN for a
            stop not timed.
        leg_seconds: Buffered travel seconds of the leg into each stop, then of the leg into the
            end; none for a route with no stops, and NaN for a leg not driven. After a skipped
            stop, the leg runs from the last place the route reached.
        leg_metres: Metres of the same legs, NaN where `leg_seconds` is.
        returns_at: When the route reaches its end; NaN for a route with no stops or with no road
            to its end.
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
class LateStop:
    """A stop a re-optimization serves past its window end: flagged, never refused (BR-107).

    The preview shows it as a broken window (FR-103), and it does not refuse acceptance.

    Attributes:
        route: The route's index in the solution.
        order: The order served late.
        vehicle: The route's vehicle.
        seconds: How many seconds after its window end its service starts.
    """

    route: int
    order: int
    vehicle: int
    seconds: float


@dataclass(frozen=True, slots=True)
class ValidationReport:
    """The validator's verdict on one solution.

    Attributes:
        violations: Every violation found, in the order found.
        timings: The validator's times for each route, which `Problem.cost` reads.
        late_stops: The stops a re-optimization serves past their window end, in route order;
            none in a daily solve, where lateness is a violation (BR-03, BR-107).
    """

    violations: tuple[Violation, ...]
    timings: Timings
    late_stops: tuple[LateStop, ...] = ()

    @property
    def accepted(self) -> bool:
        """Whether the solution breaks no rule (BR-15)."""
        return not self.violations

    def rules(self) -> frozenset[RuleId]:
        """The set of rules broken."""
        return frozenset(violation.rule for violation in self.violations)


def validate(problem: ProblemView, solution: Solution) -> ValidationReport:
    """Judges a solution; `Problem.validate` delegates here.

    Args:
        problem: The problem.
        solution: Any solution whose indices fall inside the problem.

    Returns:
        Every violation, one timing per route, and in a re-optimization the late stops it flags
        (`late_stops`, BR-107).

    Raises:
        InvalidSolutionError: If an index in the solution points past the problem.
    """
    require_inside(problem, solution)
    violations: list[Violation] = []
    _check_single_assignment(problem, solution, violations)
    _check_one_route_each(problem, solution, violations)
    timings: list[RouteTiming] = []
    for index, route in enumerate(solution.routes):
        found, timing = check_route(problem, route, index=index)
        violations.extend(found)
        timings.append(timing)
    return ValidationReport(
        violations=tuple(violations),
        timings=tuple(timings),
        late_stops=_late_stops(problem, solution, timings),
    )


def check_route(
    problem: ProblemView, route: Route, *, index: int = 0
) -> tuple[tuple[Violation, ...], RouteTiming]:
    """Checks one route's own rules and times it: everything but BR-07 and BR-39.

    - The route leaves its start at its vehicle's earliest departure (`earliest_departure`). Its
      shift limits are the rostered driver's, whoever the route names (BR-20). A vehicle with no
      driver has no shift to check, and any route on it breaks BR-20.
    - A stop with a hard window waits for the window to open (BR-03); a door stop is served on
      arrival, whatever its window (BR-122, FR-403).
    - A stop that is excluded, or that the route cannot reach by road, breaks BR-14. It is
      skipped, its times are NaN, and the route goes on from the last place it reached. With no
      road from the last stop to the end, that stop breaks BR-14 and the return is NaN.
    - A route with no stops is not driven, so its departure and return are NaN.
    - Times are never capped at the date's end (`RouteTiming`).
    - In a re-optimization, a locked stop breaks BR-10 and is skipped as an unroutable one is. A
      stop served past its window end is not a violation (BR-107).

    Args:
        problem: The problem.
        route: A route whose indices fall inside the problem.
        index: The route's index in its solution, for the violations.

    Returns:
        The violations found, and the route's timing.
    """
    check = _RouteCheck(problem, index, route, [])
    timing = check.run()
    return tuple(check.violations), timing


def earliest_departure(problem: ProblemView, vehicle: int) -> float:
    """When a vehicle's route may leave its start.

    In a daily solve it is the later of its rostered driver's shift start and its start depot's
    opening (BR-06, BR-46). In a re-optimization it is the start time the factory placed the
    vehicle at, never before now (BR-23, BR-51).

    Args:
        problem: The problem.
        vehicle: The route's vehicle; in a daily solve, one with a rostered driver.

    Returns:
        The earliest departure, in seconds since the date's local midnight.
    """
    if problem.reoptimization is not None:
        return float(problem.reoptimization.start_time[vehicle])
    driver = int(problem.vehicles.driver[vehicle])
    return max(
        float(problem.drivers.shift_start[driver]),
        float(problem.vehicles.start_depot_opens[vehicle]),
    )


def shift_return_limit(problem: ProblemView, driver: int) -> tuple[float, RuleId]:
    """The latest return a driver's shift allows, and the rule that sets it.

    Where breaks are reserved, a shift of 6 hours or more must end with 45 minutes free (BR-105);
    otherwise the shift end is the limit (BR-06). A shift short of 6 hours by any amount reserves
    no break (`BREAK_SHIFT_THRESHOLD_S`).

    Args:
        problem: The problem.
        driver: The driver.

    Returns:
        The latest return and the rule behind it.
    """
    shift_end = float(problem.drivers.shift_end[driver])
    if problem.breaks_reserved and (
        problem.drivers.shift_length(driver) >= BREAK_SHIFT_THRESHOLD_S
    ):
        return shift_end - BREAK_RESERVE_S, RuleId.BR_105
    return shift_end, RuleId.BR_06


def road(problem: ProblemView, origin: int, destination: int) -> float | None:
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


def require_inside(problem: ProblemView, solution: Solution) -> None:
    """Refuses a solution with an index that points past the problem.

    Args:
        problem: The problem.
        solution: The solution, whose indices are already non-negative.

    Raises:
        InvalidSolutionError: Naming the first field and index past the problem.
    """
    sizes = {
        "vehicle": len(problem.vehicles),
        "driver": len(problem.drivers),
        "start": problem.durations.shape[0],
        "end": problem.durations.shape[0],
    }
    orders = len(problem.orders)
    for index, route in enumerate(solution.routes):
        for name, size in sizes.items():
            value = getattr(route, name)
            if value >= size:
                raise InvalidSolutionError(f"routes[{index}].{name} {value} is not one of {size}")
        for position, order in enumerate(route.stops):
            if order >= orders:
                raise InvalidSolutionError(
                    f"routes[{index}].stops[{position}] {order} is not one of {orders}"
                )
    for order in sorted(solution.dropped):
        if order >= orders:
            raise InvalidSolutionError(f"dropped order {order} is not one of {orders}")


def _check_single_assignment(
    problem: ProblemView, solution: Solution, violations: list[Violation]
) -> None:
    # BR-07. An order the problem excluded counts as dropped even when the solution does not list
    # it, because no solver ever saw it. A locked order is served by its report: listing it as
    # dropped moves it (BR-10), and a route that serves it reports BR-10 where it is timed.
    stops = Counter(order for route in solution.routes for order in route.stops)
    for order in range(len(problem.orders)):
        if problem.is_locked(order):
            if order in solution.dropped:
                violations.append(Violation(RuleId.BR_10, order=order))
            continue
        served = stops[order]
        listed = order in solution.dropped
        dropped = listed or not problem.orders.is_routable(order)
        if served > 1 or (served == 1 and listed) or (served == 0 and not dropped):
            violations.append(Violation(RuleId.BR_07, order=order))


def _check_one_route_each(
    problem: ProblemView, solution: Solution, violations: list[Violation]
) -> None:
    # BR-39: every route after a vehicle's or a driver's first is a violation of its own. A route
    # withdrawn mid-route does not count: its driver may drive another vehicle now.
    vehicles: set[int] = set()
    drivers: set[int] = set()
    for index, route in enumerate(solution.routes):
        if problem.vehicles.exclusion[route.vehicle] is VehicleExclusion.WITHDRAWN:
            continue
        if route.vehicle in vehicles:
            violations.append(Violation(RuleId.BR_39, route=index, vehicle=route.vehicle))
        if route.driver in drivers:
            violations.append(Violation(RuleId.BR_39, route=index, driver=route.driver))
        vehicles.add(route.vehicle)
        drivers.add(route.driver)


@dataclass(slots=True)
class _RouteCheck:
    """Checks one route's own rules and times it, appending what it finds."""

    problem: ProblemView
    index: int
    route: Route
    violations: list[Violation]

    @property
    def rostered(self) -> int:
        return int(self.problem.vehicles.driver[self.route.vehicle])

    def run(self) -> RouteTiming:
        self._check_depots()
        if self.route.driver != self.rostered:
            self._report(RuleId.BR_20, driver=self.route.driver)
        self._check_loads()
        self._check_pins()
        self._check_vehicle_may_take_its_stops()
        return self._time(self.route.start, earliest_departure(self.problem, self.route.vehicle))

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

    def _check_depots(self) -> None:
        vehicles, vehicle = self.problem.vehicles, self.route.vehicle
        exclusion = vehicles.exclusion[vehicle]
        wrong_start = self.route.start != vehicles.start_location[vehicle]
        wrong_end = self.route.end != vehicles.end_location[vehicle]
        if wrong_start:
            # In a re-optimization every route starts where its vehicle is placed (BR-51).
            reoptimizing = self.problem.reoptimization is not None
            self._report(RuleId.BR_51 if reoptimizing else RuleId.BR_40)
        if wrong_end:
            withdrawn = exclusion is VehicleExclusion.WITHDRAWN
            self._report(RuleId.BR_52 if withdrawn else RuleId.BR_40)
        if not (wrong_start or wrong_end) and exclusion in ROAD_EXCLUSIONS:
            # The right depots, but no road between them (FR-197): no route can be driven.
            self._report(RuleId.BR_40)

    def _check_loads(self) -> None:
        orders, vehicles, vehicle = self.problem.orders, self.problem.vehicles, self.route.vehicle
        # A locked stop's goods left the vehicle before the remaining route starts.
        stops = [order for order in self.route.stops if not self.problem.is_locked(order)]
        weight_excess = float(orders.weight_kg[stops].sum() - vehicles.weight_capacity_kg[vehicle])
        volume_excess = float(orders.volume_l[stops].sum() - vehicles.volume_capacity_l[vehicle])
        if weight_excess > LIMIT_TOLERANCE:
            self._report(RuleId.BR_05, kilograms=weight_excess)
        if volume_excess > LIMIT_TOLERANCE:
            self._report(RuleId.BR_05, litres=volume_excess)

    def _check_pins(self) -> None:
        orders = self.problem.orders
        for order in self.route.stops:
            pinned_vehicle = int(orders.pinned_vehicle[order])
            pinned_driver = int(orders.pinned_driver[order])
            # A driver pin is judged by the vehicle's rostered driver, as every other check is; a
            # route naming someone else already breaks BR-20.
            if pinned_vehicle not in (NO_INDEX, self.route.vehicle) or pinned_driver not in (
                NO_INDEX,
                self.rostered,
            ):
                self._report(RuleId.FR_91, order=order)

    def _check_vehicle_may_take_its_stops(self) -> None:
        context = self.problem.reoptimization
        if context is None:
            return
        vehicle = self.route.vehicle
        exclusion = self.problem.vehicles.exclusion[vehicle]
        for order in self.route.stops:
            if context.locked[order]:
                continue
            if exclusion in FINISHED_ROUTES:
                self._report(RuleId.BR_108, order=order)
            stays_on = int(context.stays_on[order])
            if exclusion in OUT_BEFORE_A_STOP or stays_on not in (NO_INDEX, vehicle):
                self._report(RuleId.BR_106, order=order)

    def _time(self, start: int, leaves_at: float) -> RouteTiming:
        stops = self.route.stops
        if not stops:
            return _TimingDraft.of(0, legs=0).freeze(math.nan, math.nan)
        problem, route = self.problem, self.route
        draft = _TimingDraft.of(len(stops), legs=len(stops) + 1)
        clock, here, last = leaves_at, start, None
        for position, order in enumerate(stops):
            if problem.is_locked(order):
                self._report(RuleId.BR_10, order=order)
                continue
            there = int(problem.orders.location[order])
            base = road(problem, here, there) if problem.orders.is_routable(order) else None
            if base is None:
                self._report(RuleId.BR_14, order=order)
                continue
            leg = problem.travel.buffered(base, clock)
            draft.legs[position] = (leg, float(problem.distances[here, there]))
            clock = self._serve(draft, position, order, arrival=clock + leg)
            here, last = there, order
        base = road(problem, here, route.end)
        if base is None:
            if last is not None:
                self._report(RuleId.BR_14, order=last)
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
            if self.problem.vehicles.has_driver(self.route.vehicle):
                # BR-119 asks for the shift and the date, and only the shift end is checked. A
                # shift lies on its date (FR-190, which Problem enforces), so its end also keeps
                # the stop on the date. A daily route leaves no earlier than its shift starts, so
                # the shift start cannot bind. A re-optimization's route may leave before the
                # shift starts, when its vehicle is already on the road (BR-23, ADR-105), and a
                # door stop it serves then is accepted.
                outside = start - float(self.problem.drivers.shift_end[self.rostered])
                if outside > LIMIT_TOLERANCE:
                    self._report(RuleId.BR_119, order=order, seconds=outside)
        elif late > LIMIT_TOLERANCE and self.problem.reoptimization is None:
            # A re-optimization keeps a late stop within its shift, flagged (BR-107).
            self._report(RuleId.BR_03, order=order, seconds=late)
        if self.route.planned_starts is not None:
            self._check_claim(order, self.route.planned_starts[position], start, door=door)
        return departure

    def _check_claim(self, order: int, claimed: float, start: float, *, door: bool) -> None:
        window_start = float(self.problem.orders.window_start[order])
        if not door and window_start - claimed > LIMIT_TOLERANCE:
            self._report(RuleId.BR_03, order=order, seconds=window_start - claimed)
        elif start - claimed > LIMIT_TOLERANCE:
            # Earlier than the validator's own start for some other reason: service or travel
            # left out, or unbuffered or off-peak travel assumed. A later claim is a planned
            # wait, which the validator's own times replace (FR-403).
            self._report(RuleId.BR_04, order=order, seconds=start - claimed)

    def _check_return(self, returns_at: float) -> None:
        problem, driver = self.problem, self.rostered
        if problem.vehicles.has_driver(self.route.vehicle):
            shift_end = float(problem.drivers.shift_end[driver])
            if returns_at - shift_end > LIMIT_TOLERANCE:
                self._report(RuleId.BR_06, driver=driver, seconds=returns_at - shift_end)
            limit, rule = shift_return_limit(problem, driver)
            if rule is RuleId.BR_105 and returns_at - limit > LIMIT_TOLERANCE:
                self._report(RuleId.BR_105, driver=driver, seconds=returns_at - limit)
        closes = float(problem.vehicles.end_depot_closes[self.route.vehicle])
        if returns_at - closes > LIMIT_TOLERANCE:
            self._report(RuleId.BR_46, seconds=returns_at - closes)


def _late_stops(
    problem: ProblemView, solution: Solution, timings: list[RouteTiming]
) -> tuple[LateStop, ...]:
    # BR-107: in a re-optimization a stop with a hard window may be served late; the preview
    # names it (FR-103). A door window is a preference, charged and never flagged (BR-120).
    if problem.reoptimization is None:
        return ()
    found: list[LateStop] = []
    for index, (route, timing) in enumerate(zip(solution.routes, timings, strict=True)):
        for position, order in enumerate(route.stops):
            late = float(timing.late[position])
            if not problem.orders.leave_at_door[order] and late > LIMIT_TOLERANCE:
                found.append(
                    LateStop(route=index, order=order, vehicle=route.vehicle, seconds=late)
                )
    return tuple(found)


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
