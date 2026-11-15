"""Drop reasons: why an order is unserved, and the fact that caused it (FR-18, FR-144, FR-223).

`explain_drop` tests an unserved order in ADR-053's fixed order, with *no vehicle available* second
(ADR-100), and stops at the first test that fails. Each reason carries its fact as fields, which
the core turns into a sentence. It times an order alone through the validator's own
`check_route`, never a copy of it, so a drop reason cannot disagree with what `validate` would say
(BR-42).

In a re-optimization a stop that stays on its vehicle is judged on that vehicle alone, as an order
is judged only on the vehicles allowed to serve it (ADR-053, BR-106). A stop with a hard window
may be served late there (BR-107), so *time window* is never a re-optimization's reason.

A fact's time is in seconds since the date's local midnight, and is never capped: an earliest
arrival or return may pass the date's end (ADR-101, ADR-113).

It reads its problem as a `ProblemView` and never imports `problem`, which imports it (ADR-113).
"""

from dataclasses import dataclass
from typing import Final, assert_never

from dispatchflow_solver.domain.exclusions import OrderExclusion
from dispatchflow_solver.domain.feasibility import (
    RouteTiming,
    Violation,
    check_route,
    shift_return_limit,
)
from dispatchflow_solver.domain.indices import NO_INDEX
from dispatchflow_solver.domain.problem_parts import Pin
from dispatchflow_solver.domain.problem_view import ProblemView
from dispatchflow_solver.domain.rules import RuleId
from dispatchflow_solver.domain.solution import Route, Solution


@dataclass(frozen=True, slots=True)
class MissingLocation:
    """The order's customer has no coordinates (BR-14)."""


@dataclass(frozen=True, slots=True)
class NoVehicleAvailable:
    """No vehicle can drive a route on the order's date (FR-197, FR-223, ADR-100).

    Like `MissingLocation`, it carries no field: its fact is the date's vehicle exclusions, which
    name each vehicle and its cause (FR-144).
    """


@dataclass(frozen=True, slots=True)
class UnreachableLocation:
    """No usable vehicle can reach the order by road and return to its end depot (FR-66).

    In a re-optimization, also given when the one vehicle its stop stays on cannot (BR-106).
    """


@dataclass(frozen=True, slots=True)
class RemovedByDispatcher:
    """A dispatcher took the order's stop off its route (FR-146)."""


@dataclass(frozen=True, slots=True)
class PinNotHonoured:
    """The order's pin cannot be honoured (FR-228).

    Attributes:
        pin: The vehicle or driver the order is pinned to.
        target_out: True when that vehicle or driver drives no route in the solve; False when it
            cannot serve the order even alone.
    """

    pin: Pin
    target_out: bool


@dataclass(frozen=True, slots=True)
class Capacity:
    """No vehicle that can reach the order can carry it, even alone (FR-223).

    Attributes:
        weight_kg: The order's weight.
        volume_l: The order's volume.
        largest_weight_capacity_kg: The largest weight capacity among those vehicles.
        largest_volume_capacity_l: The largest volume capacity among those vehicles.
    """

    weight_kg: float
    volume_l: float
    largest_weight_capacity_kg: float
    largest_volume_capacity_l: float


@dataclass(frozen=True, slots=True)
class TimeWindow:
    """The earliest arrival any vehicle that can carry the order can make is after its window.

    Never the reason for a leave-at-door order, whose window is a preference (BR-121).

    Attributes:
        window_end: When the order's window ends.
        earliest_arrival: The earliest such arrival, on the validator's buffered times. Finite,
            and it may pass the date's end (ADR-113).
    """

    window_end: float
    earliest_arrival: float


@dataclass(frozen=True, slots=True)
class Shift:
    """Depot to order to depot fits no vehicle's rostered driver's shift (BR-06, BR-105, BR-119).

    Attributes:
        latest_return: The binding limit: the shift end, less the break reserve where one applies
            (BR-105). A door stop served after its shift end is followed by a return after it, so
            the return is the fact for door orders too.
        earliest_return: The earliest return, serving the order alone. Finite, and it may pass
            the date's end (ADR-113).
    """

    latest_return: float
    earliest_return: float


@dataclass(frozen=True, slots=True)
class DepotHours:
    """Depot to order to depot does not fit before the end depot closes (FR-195, BR-46).

    Attributes:
        depot_closes: When the end depot closes.
        earliest_return: The earliest return, serving the order alone. Finite, and it may pass
            the date's end (ADR-113).
    """

    depot_closes: float
    earliest_return: float


@dataclass(frozen=True, slots=True)
class Displaced:
    """The order would fit alone, and other orders took its place (Q-51: no single cause)."""


type DropReason = (
    MissingLocation
    | NoVehicleAvailable
    | UnreachableLocation
    | RemovedByDispatcher
    | PinNotHonoured
    | Capacity
    | TimeWindow
    | Shift
    | DepotHours
    | Displaced
)
"""Every reason a daily solve or a re-optimization gives for a drop (FR-223). A re-optimization
never gives *time window* (BR-107). Vehicle limits (FR-201) are not built."""

_TIME_LIMITS: Final = {RuleId.BR_06: 2, RuleId.BR_105: 2, RuleId.BR_119: 1, RuleId.BR_46: 0}
"""The rules a lone round trip can break by time, ranked to settle a tie in seconds: a shift
limit binds before the depot's closing, so equal limits give *shift*."""


def explain_drop(problem: ProblemView, order: int, solution: Solution) -> DropReason:
    """Explains an unserved order; `Problem.explain_drop` delegates here.

    The tests run in ADR-053's order, as ADR-100 amends it, and the first that fails is the
    reason:

    1. missing location, decided before the search (BR-14);
    2. no vehicle available: no vehicle can drive a route on the date (FR-197). A fact about the
       whole date comes before every per-vehicle test, and a missing location stays the order's
       own fault even when a vehicle is added (ADR-100);
    3. unreachable location, decided before the search (FR-66); a dispatcher's removal comes
       next, because it is why a routable order is off its route (FR-146);
    4. a pin to a vehicle or driver that is out, or that cannot serve the order even alone
       (FR-228);
    5. capacity: no vehicle that can reach it carries it alone;
    6. time window: the earliest arrival any vehicle that can carry it makes, leaving its depot
       at its earliest, is after the window ends; never for a door order (BR-121);
    7. shift or depot hours: depot to order to depot passes every such vehicle's latest return,
       and the reason and its fact are those of the vehicle that comes closest;
    8. displaced: it would fit alone.

    "Alone" is a one-stop route on a usable vehicle with its rostered driver, judged by
    `check_route`. In a re-optimization a stop that stays on its vehicle is tried on that vehicle
    only, and when it cannot reach the stop by road the reason is *unreachable location*.

    Args:
        problem: The problem.
        order: An order the solution leaves on no route.
        solution: The solution.

    Returns:
        The reason, with its fact.

    Raises:
        ValueError: If the order is past the problem, the solution serves it, or its stop is
            locked, so its report serves it (BR-10).
    """
    orders = problem.orders
    if not 0 <= order < len(orders):
        raise ValueError(f"order {order} is not one of {len(orders)}")
    if order in solution.served():
        raise ValueError(f"order {order} is served, not dropped")
    if problem.is_locked(order):
        raise ValueError(f"order {order} is locked by its report, not dropped")
    exclusion = orders.exclusion[order]
    if exclusion is OrderExclusion.MISSING_LOCATION:
        return MissingLocation()
    candidates = problem.vehicles.usable()
    if not candidates:
        return NoVehicleAvailable()
    match exclusion:
        case OrderExclusion.UNREACHABLE_LOCATION:
            return UnreachableLocation()
        case None:
            pass
        case _:
            assert_never(exclusion)
    if order in problem.removed_by_dispatcher:
        return RemovedByDispatcher()
    context = problem.reoptimization
    if context is not None and context.stays_on[order] != NO_INDEX:
        # The problem holds only an active vehicle here, so the candidate is usable.
        candidates = (int(context.stays_on[order]),)
    pin = orders.pin(order)
    if pin is not None:
        candidates = tuple(vehicle for vehicle in candidates if _honours(problem, vehicle, pin))
        if not candidates:
            return PinNotHonoured(pin=pin, target_out=True)
    trials = [_alone(problem, order, vehicle) for vehicle in candidates]
    if pin is not None and not any(trial.accepted for trial in trials):
        return PinNotHonoured(pin=pin, target_out=False)
    return _first_failing_limit(problem, order, trials)


@dataclass(frozen=True, slots=True)
class _Alone:
    """One vehicle serving one order and nothing else, as `check_route` judges it."""

    vehicle: int
    violations: tuple[Violation, ...]
    timing: RouteTiming

    @property
    def rules(self) -> frozenset[RuleId]:
        return frozenset(violation.rule for violation in self.violations)

    @property
    def accepted(self) -> bool:
        return not self.violations

    @property
    def arrival(self) -> float:
        return float(self.timing.arrival[0])

    def binding(self) -> Violation:
        """The time limit passed by the most seconds; a tie goes to the higher-ranked limit.

        Raises:
            RuntimeError: If the trial breaks no time limit, so it is rejected for a rule
                `explain_drop` does not classify; a new rule must be given its reason first.
        """
        blocking = [violation for violation in self.violations if violation.rule in _TIME_LIMITS]
        if not blocking:
            rules = ", ".join(sorted(rule.value for rule in self.rules))
            raise RuntimeError(f"a lone route breaks {rules}, which no drop reason classifies")
        return max(blocking, key=_severity)


def _severity(violation: Violation) -> tuple[float, int]:
    return (violation.seconds or 0.0, _TIME_LIMITS[violation.rule])


def _alone(problem: ProblemView, order: int, vehicle: int) -> _Alone:
    vehicles = problem.vehicles
    route = Route(
        vehicle=vehicle,
        driver=int(vehicles.driver[vehicle]),
        start=int(vehicles.start_location[vehicle]),
        end=int(vehicles.end_location[vehicle]),
        stops=(order,),
    )
    violations, timing = check_route(problem, route)
    return _Alone(vehicle=vehicle, violations=violations, timing=timing)


def _first_failing_limit(problem: ProblemView, order: int, trials: list[_Alone]) -> DropReason:
    reachable = [trial for trial in trials if RuleId.BR_14 not in trial.rules]
    if not reachable:
        # Only a stop that stays on its vehicle gets here: a routable order is reached by some
        # usable vehicle (FR-66), and a pin's failure is decided before this.
        return UnreachableLocation()
    carrying = [trial for trial in reachable if RuleId.BR_05 not in trial.rules]
    if not carrying:
        return _capacity(problem, order, [trial.vehicle for trial in reachable])
    on_time = carrying
    if not problem.orders.leave_at_door[order]:
        on_time = [trial for trial in carrying if RuleId.BR_03 not in trial.rules]
        if not on_time:
            return TimeWindow(
                window_end=float(problem.orders.window_end[order]),
                earliest_arrival=min(trial.arrival for trial in carrying),
            )
    if any(trial.accepted for trial in on_time):
        return Displaced()
    closest = min(on_time, key=_closeness)
    return _time_reason(problem, closest)


def _closeness(trial: _Alone) -> tuple[float, int, float]:
    # Fewest seconds over first; on a tie the higher-ranked limit, then the earlier return, so
    # the reason never depends on the order the vehicles are listed in.
    seconds, rank = _severity(trial.binding())
    return (seconds, -rank, float(trial.timing.returns_at))


def _time_reason(problem: ProblemView, trial: _Alone) -> Shift | DepotHours:
    returns_at = float(trial.timing.returns_at)
    if trial.binding().rule is RuleId.BR_46:
        closes = float(problem.vehicles.end_depot_closes[trial.vehicle])
        return DepotHours(depot_closes=closes, earliest_return=returns_at)
    # BR-06, BR-105, or BR-119, which never binds alone: a door stop served after its shift end
    # is followed by a return after it, and BR-06 passes the shift by at least as many seconds.
    latest, _ = shift_return_limit(problem, int(problem.vehicles.driver[trial.vehicle]))
    return Shift(latest_return=latest, earliest_return=returns_at)


def _honours(problem: ProblemView, vehicle: int, pin: Pin) -> bool:
    if pin.vehicle is not None:
        return vehicle == pin.vehicle
    return int(problem.vehicles.driver[vehicle]) == pin.driver


def _capacity(problem: ProblemView, order: int, vehicles: list[int]) -> Capacity:
    # Never empty: the caller returns before it when no trial reaches the order, so max() needs no
    # default.
    capacities = problem.vehicles
    return Capacity(
        weight_kg=float(problem.orders.weight_kg[order]),
        volume_l=float(problem.orders.volume_l[order]),
        largest_weight_capacity_kg=max(
            float(capacities.weight_capacity_kg[vehicle]) for vehicle in vehicles
        ),
        largest_volume_capacity_l=max(
            float(capacities.volume_capacity_l[vehicle]) for vehicle in vehicles
        ),
    )
