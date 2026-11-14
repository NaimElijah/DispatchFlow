"""Drop reasons: why an order is unserved, and the fact that caused it (FR-18, FR-144, FR-223).

`explain_drop` tests an unserved order in ADR-053's fixed order and stops at the first test that
fails. Each reason carries its fact as fields, which the core turns into a sentence.
"""

import math
from dataclasses import dataclass
from typing import TYPE_CHECKING

from dispatchflow_solver.domain._arrays import NO_INDEX
from dispatchflow_solver.domain.feasibility import (
    door_stop_outside,
    earliest_departure,
    road,
    shift_return_limit,
)
from dispatchflow_solver.domain.rules import TOLERANCE
from dispatchflow_solver.domain.solution import Solution

if TYPE_CHECKING:
    from dispatchflow_solver.domain.problem import Pin, Problem


@dataclass(frozen=True, slots=True)
class MissingLocation:
    """The order's customer has no coordinates (BR-14)."""


@dataclass(frozen=True, slots=True)
class UnreachableLocation:
    """The road network has no route between the order's location and a depot (FR-66)."""


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
    """No allowed vehicle can carry the order, even alone (FR-223).

    Attributes:
        weight_kg: The order's weight.
        volume_l: The order's volume.
        largest_weight_capacity_kg: The largest weight capacity among the allowed vehicles.
        largest_volume_capacity_l: The largest volume capacity among the allowed vehicles.
    """

    weight_kg: float
    volume_l: float
    largest_weight_capacity_kg: float
    largest_volume_capacity_l: float


@dataclass(frozen=True, slots=True)
class TimeWindow:
    """The earliest arrival from any allowed vehicle's depot is after the window ends.

    Never the reason for a leave-at-door order, whose window is a preference (BR-121).

    Attributes:
        window_end: When the order's window ends.
        earliest_arrival: The earliest arrival any allowed vehicle can make.
    """

    window_end: float
    earliest_arrival: float


@dataclass(frozen=True, slots=True)
class Shift:
    """Depot to order to depot does not fit any allowed vehicle's driver's shift (BR-06).

    Attributes:
        latest_return: The latest return the shift allows, after any break reserve (BR-105).
        earliest_return: The earliest return, serving the order alone.
    """

    latest_return: float
    earliest_return: float


@dataclass(frozen=True, slots=True)
class DepotHours:
    """Depot to order to depot does not fit before the end depot closes (FR-195, BR-46).

    Attributes:
        depot_closes: When the end depot closes.
        earliest_return: The earliest return, serving the order alone.
    """

    depot_closes: float
    earliest_return: float


@dataclass(frozen=True, slots=True)
class Displaced:
    """The order would fit alone, and other orders took its place (Q-51: no single cause)."""


type DropReason = (
    MissingLocation
    | UnreachableLocation
    | RemovedByDispatcher
    | PinNotHonoured
    | Capacity
    | TimeWindow
    | Shift
    | DepotHours
    | Displaced
)
"""Every reason a daily solve gives for a drop (FR-223). Vehicle limits (FR-201) are not built."""


def explain_drop(problem: Problem, order: int, solution: Solution) -> DropReason:
    """Explains an unserved order; `Problem.explain_drop` delegates here.

    The tests run in ADR-053's order, and the first that fails is the reason:

    1. missing or unreachable location, decided before the search (BR-14, FR-66);
       a dispatcher's removal comes next, because it is why a routable order is off its route
       (FR-146);
    2. a pin to a vehicle or driver that is out, or that cannot serve the order even alone
       (FR-228);
    3. capacity: no allowed vehicle carries it alone;
    4. time window: the earliest arrival any allowed vehicle can make, leaving its depot at its
       earliest, is after the window ends; never for a door order (BR-121);
    5. shift or depot hours: depot to order to depot fits no allowed vehicle's latest return;
    6. displaced: it would fit alone.

    An order "alone" is timed the way the validator times a route of that one stop.

    Args:
        problem: The problem.
        order: An order the solution leaves on no route.
        solution: The solution.

    Returns:
        The reason, with its fact.

    Raises:
        ValueError: If the order is past the problem or the solution serves it.
    """
    orders = problem.orders
    if not 0 <= order < len(orders):
        raise ValueError(f"order {order} is not in the problem")
    if order in solution.served():
        raise ValueError(f"order {order} is served, not dropped")
    if not orders.is_routable(order):
        # Orders holds that an order lacks a location exactly when it is excluded for it, so any
        # other exclusion is a missing road route.
        if orders.location[order] == NO_INDEX:
            return MissingLocation()
        return UnreachableLocation()
    if order in problem.removed_by_dispatcher:
        return RemovedByDispatcher()
    allowed = problem.vehicles.usable()
    pin = orders.pin(order)
    if pin is not None:
        allowed = tuple(vehicle for vehicle in allowed if _carries_pin(problem, vehicle, pin))
        if not allowed:
            return PinNotHonoured(pin=pin, target_out=True)
        if not any(_alone(problem, order, vehicle).fits for vehicle in allowed):
            return PinNotHonoured(pin=pin, target_out=False)
    trials = [_alone(problem, order, vehicle) for vehicle in allowed]
    carrying = [trial for trial in trials if trial.carries]
    if not carrying:
        return _capacity(problem, order, allowed)
    on_time = carrying
    if not orders.leave_at_door[order]:
        window_end = float(orders.window_end[order])
        earliest = min(trial.arrival for trial in carrying)
        if earliest - window_end > TOLERANCE:
            return TimeWindow(window_end=window_end, earliest_arrival=earliest)
        on_time = [trial for trial in carrying if trial.arrival - window_end <= TOLERANCE]
    if any(trial.fits for trial in on_time):
        return Displaced()
    closest = min(on_time, key=lambda trial: trial.overshoot)
    if closest.depot_closes < closest.shift_limit:
        return DepotHours(depot_closes=closest.depot_closes, earliest_return=closest.returns_at)
    return Shift(latest_return=closest.shift_limit, earliest_return=closest.returns_at)


@dataclass(frozen=True, slots=True)
class _Alone:
    """One vehicle serving one order and nothing else, timed as the validator would."""

    carries: bool
    arrival: float
    meets_window: bool
    returns_at: float
    shift_limit: float
    depot_closes: float
    door_outside: float

    @property
    def overshoot(self) -> float:
        latest = min(self.shift_limit, self.depot_closes)
        return max(self.returns_at - latest, self.door_outside)

    @property
    def fits(self) -> bool:
        return self.carries and self.meets_window and self.overshoot <= TOLERANCE


def _alone(problem: Problem, order: int, vehicle: int) -> _Alone:
    orders, vehicles, travel = problem.orders, problem.vehicles, problem.travel
    driver = int(vehicles.driver[vehicle])
    location = int(orders.location[order])
    leaves = earliest_departure(problem, vehicle, driver)
    out = road(problem, int(vehicles.start_location[vehicle]), location)
    back = road(problem, location, int(vehicles.end_location[vehicle]))
    if out is None or back is None:
        # Problem refuses to exist without these roads, so reaching here is a bug, not a drop.
        raise RuntimeError(f"no road between vehicle {vehicle}'s depots and order {order}")
    arrival = leaves + travel.buffered(out, leaves)
    door = bool(orders.leave_at_door[order])
    window_end = float(orders.window_end[order])
    start = arrival if door else max(arrival, float(orders.window_start[order]))
    departure = start + float(orders.service_s[order])
    shift_limit, _ = shift_return_limit(problem, driver)
    return _Alone(
        carries=bool(
            orders.weight_kg[order] <= vehicles.weight_capacity_kg[vehicle] + TOLERANCE
            and orders.volume_l[order] <= vehicles.volume_capacity_l[vehicle] + TOLERANCE
        ),
        arrival=arrival,
        meets_window=door or start - window_end <= TOLERANCE,
        returns_at=departure + travel.buffered(back, departure),
        shift_limit=shift_limit,
        depot_closes=float(vehicles.end_depot_closes[vehicle]),
        door_outside=door_stop_outside(problem, driver, start) if door else -math.inf,
    )


def _carries_pin(problem: Problem, vehicle: int, pin: Pin) -> bool:
    if pin.vehicle is not None:
        return vehicle == pin.vehicle
    return int(problem.vehicles.driver[vehicle]) == pin.driver


def _capacity(problem: Problem, order: int, allowed: tuple[int, ...]) -> Capacity:
    # With no allowed vehicle at all, the largest capacity is honestly 0: nothing carries it.
    vehicles = problem.vehicles
    return Capacity(
        weight_kg=float(problem.orders.weight_kg[order]),
        volume_l=float(problem.orders.volume_l[order]),
        largest_weight_capacity_kg=max(
            (float(vehicles.weight_capacity_kg[vehicle]) for vehicle in allowed), default=0.0
        ),
        largest_volume_capacity_l=max(
            (float(vehicles.volume_capacity_l[vehicle]) for vehicle in allowed), default=0.0
        ),
    )
