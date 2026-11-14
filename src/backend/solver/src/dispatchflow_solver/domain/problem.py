"""The problem: everything one solve needs, and the single definition of what a solution means.

`Problem` holds every order of the delivery date, including those kept out of the search with
their reason, the vehicles, the drivers, the base matrices, the travel-time model and the penalty
scale (DL §4.9, TS §7.1). Its methods are the validator, the objective and the drop reasons, and
every solver, edit check, publishing re-check and research score goes through them (BR-42).

Every time is in seconds since the delivery date's local midnight in `Asia/Jerusalem` (ADR-088).
Every array is a read-only float64 copy, or int64 for indices, where -1 means "none".

`Problem`'s methods delegate to `feasibility`, `objective` and `drops`, which name `Problem` only
in type annotations, under `TYPE_CHECKING`. The cycle exists for the type checker alone: at run
time this module imports them, and they never import it.
"""

import math
import numbers
from collections.abc import Callable, Sized
from dataclasses import dataclass
from typing import Any, Final

import numpy as np
import numpy.typing as npt

from dispatchflow_solver.domain import drops, feasibility, objective
from dispatchflow_solver.domain.drops import DropReason
from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.exclusions import (
    REOPTIMIZATION_EXCLUSIONS,
    OrderExclusion,
    VehicleExclusion,
)
from dispatchflow_solver.domain.feasibility import Timings, ValidationReport, require_inside
from dispatchflow_solver.domain.indices import NO_INDEX, in_int64, require_float, require_int
from dispatchflow_solver.domain.objective import Cost
from dispatchflow_solver.domain.penalty import HIGHEST_PRIORITY, LOWEST_PRIORITY, PenaltyScale
from dispatchflow_solver.domain.solution import InvalidSolutionError, Solution
from dispatchflow_solver.domain.travel_time import TravelTimeModel

MATRIX_ENTRY_MAX: Final = 2_147_483_647
"""The largest matrix entry: the contract carries the matrices as int32 (ADR-052). A window time is
held to the same magnitude in seconds (ADR-100)."""


@dataclass(frozen=True, slots=True)
class Pin:
    """An order fixed to one vehicle or one driver before a solve (FR-91).

    Attributes:
        vehicle: The pinned vehicle's index, or None for a driver pin.
        driver: The pinned driver's index, or None for a vehicle pin.

    Raises:
        InvalidProblemError: Unless exactly one of the two is a non-negative index, if one is no
            number, or if a whole number lies outside int64.
    """

    vehicle: int | None = None
    driver: int | None = None

    def __post_init__(self) -> None:
        """Refuses a pin to neither, to both, to a negative index, or past int64."""
        for name in ("vehicle", "driver"):
            target = getattr(self, name)
            # The comparison below needs a number, so anything else is refused here. A number that
            # is not a whole one, such as 0.0, passes on to the factory, whose message names the
            # order the pin belongs to. The range comes first, so no message formats a
            # 4,300-digit int.
            if target is not None and not isinstance(target, numbers.Real):
                raise InvalidProblemError(f"a pin's {name} must be a number: {target!r}")
            if isinstance(target, numbers.Integral) and not in_int64(int(target)):
                raise InvalidProblemError(f"a pin's {name} is outside the int64 range")
        targets = [target for target in (self.vehicle, self.driver) if target is not None]
        if len(targets) != 1 or targets[0] < 0:
            raise InvalidProblemError(
                "a pin names exactly one vehicle or driver, as a non-negative index: "
                f"vehicle={self.vehicle}, driver={self.driver}"
            )


@dataclass(frozen=True, slots=True, eq=False)
class Orders:
    """Every order of the delivery date, as a struct of arrays indexed by order.

    Attributes:
        location: Each order's location in the matrices, or -1 with no location.
        weight_kg: Weight demand in kilograms.
        volume_l: Volume demand in litres; 0 for research instances (FR-390).
        service_s: Service time in seconds (BR-04).
        window_start: The time window's start (BR-03).
        window_end: The time window's end, inclusive (NFR-168).
        priority: 1, the highest, to 5 (BR-53).
        leave_at_door: True for a leave-at-door order, whose window is a preference (BR-119).
        pinned_vehicle: The vehicle the order is pinned to, or -1 (FR-91).
        pinned_driver: The driver the order is pinned to, or -1 (FR-91).
        exclusion: Why each order is kept out of the search, or None for a routable order.

    Raises:
        InvalidProblemError: If the arrays differ in length or hold a value no order can have.
    """

    location: npt.NDArray[np.int64]
    weight_kg: npt.NDArray[np.float64]
    volume_l: npt.NDArray[np.float64]
    service_s: npt.NDArray[np.float64]
    window_start: npt.NDArray[np.float64]
    window_end: npt.NDArray[np.float64]
    priority: npt.NDArray[np.int64]
    leave_at_door: npt.NDArray[np.bool_]
    pinned_vehicle: npt.NDArray[np.int64]
    pinned_driver: npt.NDArray[np.int64]
    exclusion: tuple[OrderExclusion | None, ...]

    def __post_init__(self) -> None:
        """Copies every array read-only and refuses values no order can have."""
        for name in ("location", "priority", "pinned_vehicle", "pinned_driver"):
            _set_column(self, "orders", name, np.int64)
        for name in ("weight_kg", "volume_l", "service_s", "window_start", "window_end"):
            _set_column(self, "orders", name, np.float64)
        _set_column(self, "orders", "leave_at_door", np.bool_)
        object.__setattr__(self, "exclusion", tuple(self.exclusion))
        _require_lengths(
            "orders",
            len(self.location),
            weight_kg=self.weight_kg,
            volume_l=self.volume_l,
            service_s=self.service_s,
            window_start=self.window_start,
            window_end=self.window_end,
            priority=self.priority,
            leave_at_door=self.leave_at_door,
            pinned_vehicle=self.pinned_vehicle,
            pinned_driver=self.pinned_driver,
            exclusion=self.exclusion,
        )
        for name in ("weight_kg", "volume_l", "service_s"):
            _require_finite_non_negative(f"orders.{name}", getattr(self, name))
        _refuse_first(
            ~(
                np.isfinite(self.window_start)
                & np.isfinite(self.window_end)
                & (self.window_start <= self.window_end)
            ),
            lambda i: (
                f"orders.window_start[{i}] and window_end[{i}] must be finite, the start no "
                f"later than the end: [{self.window_start[i]}, {self.window_end[i]}]"
            ),
        )
        # A window may lie off the date, but one door window sets K for the whole date (ADR-100),
        # so a magnitude past the matrices' int32 range of seconds (ADR-052) is refused before it
        # can drown the travel terms or overflow K.
        _refuse_first(
            np.abs(self.window_start) > MATRIX_ENTRY_MAX,
            lambda i: (
                f"orders.window_start[{i}] must lie within {MATRIX_ENTRY_MAX} seconds of the "
                f"date's start: {self.window_start[i]}"
            ),
        )
        _refuse_first(
            np.abs(self.window_end) > MATRIX_ENTRY_MAX,
            lambda i: (
                f"orders.window_end[{i}] must lie within {MATRIX_ENTRY_MAX} seconds of the "
                f"date's start: {self.window_end[i]}"
            ),
        )
        _refuse_first(
            (self.priority < HIGHEST_PRIORITY) | (self.priority > LOWEST_PRIORITY),
            lambda i: f"orders.priority[{i}] runs from 1 to 5 (BR-53): {self.priority[i]}",
        )
        _refuse_first(
            (self.pinned_vehicle != NO_INDEX) & (self.pinned_driver != NO_INDEX),
            lambda i: f"orders[{i}] is pinned to a vehicle and a driver; a pin names one",
        )
        for order, exclusion in enumerate(self.exclusion):
            if (self.location[order] == NO_INDEX) != (exclusion is OrderExclusion.MISSING_LOCATION):
                raise InvalidProblemError(
                    f"orders.exclusion[{order}] is {exclusion}, but orders.location[{order}] is "
                    f"{self.location[order]}: an order lacks a location exactly when excluded "
                    "for it"
                )

    def __len__(self) -> int:
        """The number of orders on the date."""
        return len(self.location)

    def is_routable(self, order: int) -> bool:
        """Says whether an order may be served: it has a location and a road route."""
        return self.exclusion[order] is None

    def pin(self, order: int) -> Pin | None:
        """The order's pin, or None when it has none."""
        vehicle = int(self.pinned_vehicle[order])
        driver = int(self.pinned_driver[order])
        if vehicle != NO_INDEX:
            return Pin(vehicle=vehicle)
        if driver != NO_INDEX:
            return Pin(driver=driver)
        return None


@dataclass(frozen=True, slots=True, eq=False)
class Vehicles:
    """The date's vehicles, each with where it starts and ends and its driver, indexed by vehicle.

    The roster pairs each vehicle with one driver, and no driver drives two vehicles on a date
    (FR-54, BR-20). Depot hours are folded in: a vehicle leaves no earlier than its start depot
    opens and returns no later than its end depot closes (BR-46).

    In a re-optimization the factory writes each convention here once (ADR-050, ADR-105): a
    vehicle starts where it is placed (BR-51), a withdrawn vehicle ends at its last reported stop
    (BR-52), and a vehicle that is out is excluded for its state. Only such a vehicle may have no
    driver, so a vehicle that can take a stop always has one. A vehicle that is out keeps the
    driver it last had, who may drive another vehicle now, so FR-54 counts only the others.

    Attributes:
        weight_capacity_kg: Weight capacity in kilograms (BR-05).
        volume_capacity_l: Volume capacity in litres; +inf for no volume limit (FR-390).
        start_location: Where its route starts: its start depot's location, or in a
            re-optimization where it is placed; -1 when that place has none (FR-197).
        end_location: Where its route ends: its end depot's location, or a withdrawn vehicle's
            last reported stop; -1 when the depot has none (FR-197).
        start_depot_opens: When the start depot opens; -inf when it has no hours (FR-129).
        end_depot_closes: When the end depot closes; +inf when it has no hours (FR-129), and for
            a withdrawn vehicle, whose route ends at a stop.
        driver: The driver the roster gives the vehicle on the date, or -1 for none, which only a
            vehicle excluded for a re-optimization state may have.
        exclusion: Why each vehicle can drive no route, or None for a usable vehicle.

    Raises:
        InvalidProblemError: If the arrays differ in length, hold a value no vehicle can have,
            roster one driver to two vehicles, or leave a vehicle without a driver that is not out.
    """

    weight_capacity_kg: npt.NDArray[np.float64]
    volume_capacity_l: npt.NDArray[np.float64]
    start_location: npt.NDArray[np.int64]
    end_location: npt.NDArray[np.int64]
    start_depot_opens: npt.NDArray[np.float64]
    end_depot_closes: npt.NDArray[np.float64]
    driver: npt.NDArray[np.int64]
    exclusion: tuple[VehicleExclusion | None, ...]

    def __post_init__(self) -> None:
        """Copies every array read-only and refuses values no vehicle can have."""
        for name in ("start_location", "end_location", "driver"):
            _set_column(self, "vehicles", name, np.int64)
        for name in (
            "weight_capacity_kg",
            "volume_capacity_l",
            "start_depot_opens",
            "end_depot_closes",
        ):
            _set_column(self, "vehicles", name, np.float64)
        object.__setattr__(self, "exclusion", tuple(self.exclusion))
        _require_lengths(
            "vehicles",
            len(self.driver),
            weight_capacity_kg=self.weight_capacity_kg,
            volume_capacity_l=self.volume_capacity_l,
            start_location=self.start_location,
            end_location=self.end_location,
            start_depot_opens=self.start_depot_opens,
            end_depot_closes=self.end_depot_closes,
            exclusion=self.exclusion,
        )
        weight = self.weight_capacity_kg
        _refuse_first(
            ~(np.isfinite(weight) & (weight >= 0)),
            lambda i: (
                f"vehicles.weight_capacity_kg[{i}] must be finite and zero or more: {weight[i]}"
            ),
        )
        volume = self.volume_capacity_l
        _refuse_first(
            np.isnan(volume) | (volume < 0),
            lambda i: (
                f"vehicles.volume_capacity_l[{i}] must be zero or more, or +inf for no "
                f"limit: {volume[i]}"
            ),
        )
        _refuse_first(
            np.isnan(self.start_depot_opens) | (self.start_depot_opens == math.inf),
            lambda i: (
                f"vehicles.start_depot_opens[{i}] must be finite or -inf: "
                f"{self.start_depot_opens[i]}"
            ),
        )
        _refuse_first(
            np.isnan(self.end_depot_closes) | (self.end_depot_closes == -math.inf),
            lambda i: (
                f"vehicles.end_depot_closes[{i}] must be finite or +inf: {self.end_depot_closes[i]}"
            ),
        )
        self._require_drivers()
        for vehicle, exclusion in enumerate(self.exclusion):
            no_depot = NO_INDEX in (self.start_location[vehicle], self.end_location[vehicle])
            for_depot = exclusion is VehicleExclusion.DEPOT_WITHOUT_LOCATION
            # A vehicle that is out may lack a place too: its state is why it drives no route.
            out = exclusion in REOPTIMIZATION_EXCLUSIONS
            if (for_depot and not no_depot) or (no_depot and not (for_depot or out)):
                raise InvalidProblemError(
                    f"vehicles.exclusion[{vehicle}] is {exclusion}: a vehicle lacks a depot "
                    "location exactly when excluded for it"
                )

    def __len__(self) -> int:
        """The number of vehicles on the date."""
        return len(self.driver)

    def is_usable(self, vehicle: int) -> bool:
        """Says whether a vehicle can drive a route in the solve (FR-197)."""
        return self.exclusion[vehicle] is None

    def usable(self) -> tuple[int, ...]:
        """The vehicles that can drive a route, in index order."""
        return tuple(vehicle for vehicle in range(len(self)) if self.is_usable(vehicle))

    def has_driver(self, vehicle: int) -> bool:
        """Says whether the roster gives a vehicle a driver; every usable vehicle has one."""
        return int(self.driver[vehicle]) != NO_INDEX

    def _require_drivers(self) -> None:
        rostered: dict[int, int] = {}
        for vehicle, driver in enumerate(int(driver) for driver in self.driver):
            exclusion = self.exclusion[vehicle]
            if driver == NO_INDEX and exclusion not in REOPTIMIZATION_EXCLUSIONS:
                raise InvalidProblemError(
                    f"vehicles.driver[{vehicle}] is none, but its exclusion is {exclusion}: only a "
                    "vehicle out of a re-optimization may have no driver (ADR-050)"
                )
            if exclusion is VehicleExclusion.NO_DRIVER and driver != NO_INDEX:
                raise InvalidProblemError(
                    f"vehicles.exclusion[{vehicle}] is no_driver, but its driver is {driver}"
                )
            if driver == NO_INDEX or exclusion in REOPTIMIZATION_EXCLUSIONS:
                continue
            if driver in rostered:
                raise InvalidProblemError(
                    f"driver {driver} is rostered to vehicles {rostered[driver]} and {vehicle}; "
                    "a driver drives one vehicle on a date (FR-54)"
                )
            rostered[driver] = vehicle


@dataclass(frozen=True, slots=True, eq=False)
class Drivers:
    """The date's drivers and their shifts, indexed by driver (BR-06).

    Attributes:
        shift_start: When each driver's shift starts.
        shift_end: When each driver's shift ends.

    Raises:
        InvalidProblemError: If the arrays differ in length or a shift is not finite or does not
            end after it starts.
    """

    shift_start: npt.NDArray[np.float64]
    shift_end: npt.NDArray[np.float64]

    def __post_init__(self) -> None:
        """Copies the arrays read-only and refuses a shift that does not end after it starts."""
        _set_column(self, "drivers", "shift_start", np.float64)
        _set_column(self, "drivers", "shift_end", np.float64)
        _require_lengths("drivers", len(self.shift_start), shift_end=self.shift_end)
        _refuse_first(
            ~(
                np.isfinite(self.shift_start)
                & np.isfinite(self.shift_end)
                & (self.shift_start < self.shift_end)
            ),
            lambda i: (
                f"drivers.shift_start[{i}] and shift_end[{i}] must be finite, the end after "
                f"the start: [{self.shift_start[i]}, {self.shift_end[i]}]"
            ),
        )

    def __len__(self) -> int:
        """The number of drivers on the date."""
        return len(self.shift_start)

    def shift_length(self, driver: int) -> float:
        """A driver's shift length in elapsed seconds."""
        return float(self.shift_end[driver] - self.shift_start[driver])


@dataclass(frozen=True, slots=True, eq=False)
class ReoptContext:
    """What a re-optimization's problem holds beyond a daily one, decided once by the factory.

    The rest of ADR-050's conventions are written into `Vehicles`: where each vehicle starts
    (BR-51) and ends (BR-52), and the state that keeps a vehicle out (BR-106, BR-108). The
    validator checks only what follows `now`, so a fact never fails a solution (BR-23).

    Attributes:
        now: When the facts were gathered, in seconds since the date's local midnight.
        start_time: When each vehicle's remaining route may leave its start, indexed by vehicle;
            never before `now` (BR-23, BR-51).
        locked: Per order, whether its stop is reported delivered, failed or arrived, so the
            report serves it and nothing moves it (BR-10, BR-91).
        stays_on: Per order, the vehicle its stop must stay on, or -1 when it may go to any active
            vehicle (BR-106, BR-117).
        orphaned: Per order, whether its stop was on a vehicle that is out, so it leaves that
            vehicle whatever the solution does (BR-106, BR-115).
        no_usable_position: The vehicles with no usable position, for the preview (BR-80, BR-81).
        warm_start: The current remaining sequences with the orphans left out, for a solver to
            start from (ADR-044, FR-280).

    Raises:
        InvalidProblemError: If `now` is no number, a time is not finite, a vehicle in
            `no_usable_position` is not an int, the warm start is not a `Solution`, the per-order
            arrays differ in length, a locked stop may still move or stay, or an orphan also stays
            on a vehicle.
    """

    now: float
    start_time: npt.NDArray[np.float64]
    locked: npt.NDArray[np.bool_]
    stays_on: npt.NDArray[np.int64]
    orphaned: npt.NDArray[np.bool_]
    no_usable_position: frozenset[int]
    warm_start: Solution

    def __post_init__(self) -> None:
        """Copies every array read-only and refuses a context that contradicts itself."""
        _set_column(self, "reoptimization", "start_time", np.float64)
        _set_column(self, "reoptimization", "locked", np.bool_)
        _set_column(self, "reoptimization", "stays_on", np.int64)
        _set_column(self, "reoptimization", "orphaned", np.bool_)
        object.__setattr__(
            self,
            "no_usable_position",
            frozenset(
                require_int("reoptimization.no_usable_position[]", vehicle)
                for vehicle in self.no_usable_position
            ),
        )
        if not isinstance(self.warm_start, Solution):
            raise InvalidProblemError(
                f"reoptimization.warm_start is a {type(self.warm_start).__name__}, not Solution"
            )
        now = require_float("reoptimization.now", self.now)
        if not math.isfinite(now):
            raise InvalidProblemError(f"reoptimization.now must be finite: {now}")
        object.__setattr__(self, "now", now)
        _refuse_first(
            ~np.isfinite(self.start_time),
            lambda i: f"reoptimization.start_time[{i}] must be finite: {self.start_time[i]}",
        )
        _require_lengths(
            "reoptimization",
            len(self.locked),
            stays_on=self.stays_on,
            orphaned=self.orphaned,
        )
        _refuse_first(
            self.locked & ((self.stays_on != NO_INDEX) | self.orphaned),
            lambda i: f"reoptimization.locked[{i}] is set, but the stop may still move or stay",
        )
        _refuse_first(
            self.orphaned & (self.stays_on != NO_INDEX),
            lambda i: f"reoptimization.orphaned[{i}] is set, but the stop stays on a vehicle",
        )


@dataclass(frozen=True, slots=True, eq=False)
class Problem:
    """Everything one solve needs, and the rules that judge a solution (DL §4.9).

    Build it with `ProblemFactory`, which decides each convention once and writes it here
    (ADR-050). A daily problem has no re-optimization context and behaves as it always has; a
    re-optimization's context makes `validate`, `cost` and `explain_drop` apply its conventions
    (ADR-105).

    A date with no usable vehicle drops every order, and `explain_drop` reports *no vehicle
    available* for each one that has a location (ADR-100).

    Attributes:
        orders: Every order of the date, excluded ones included.
        vehicles: Every vehicle of the date, excluded ones included.
        drivers: Every driver of the date.
        durations: Base durations in seconds between distinct locations; NaN where the road
            network has no route.
        distances: Metres between distinct locations; NaN exactly where durations are.
        travel: The travel-time model, the only place factors and the buffer apply.
        penalties: The drop unit and the rates the objective charges.
        drop_penalties: What dropping each order costs, which `cost` reads (BR-54).
        day_length: The date's length in seconds: 23, 24 or 25 hours (ADR-088).
        breaks_reserved: Whether routes reserve time for breaks (FR-81, BR-105).
        removed_by_dispatcher: Orders a dispatcher took off the current routes (FR-146), so
            `explain_drop` can give that reason. The validator does not enforce it: a new solve
            may serve them again (BR-95).
        reoptimization: The re-optimization's context, or None for a daily solve.

    Raises:
        InvalidProblemError: If the parts contradict each other, an index points past its array,
            a shift lies outside the date, an exclusion disagrees with the matrices, or a
            re-optimization's state appears without its context.
    """

    orders: Orders
    vehicles: Vehicles
    drivers: Drivers
    durations: npt.NDArray[np.float64]
    distances: npt.NDArray[np.float64]
    travel: TravelTimeModel
    penalties: PenaltyScale
    drop_penalties: npt.NDArray[np.float64]
    day_length: float
    breaks_reserved: bool
    removed_by_dispatcher: frozenset[int] = frozenset()
    reoptimization: ReoptContext | None = None

    def __post_init__(self) -> None:
        """Copies every input read-only and refuses a problem that contradicts itself."""
        durations = require_matrix("durations", self.durations)
        distances = require_matrix("distances", self.distances)
        require_same_roads(durations, distances)
        object.__setattr__(self, "durations", durations)
        object.__setattr__(self, "distances", distances)
        _set_column(self, "problem", "drop_penalties", np.float64)
        object.__setattr__(self, "removed_by_dispatcher", frozenset(self.removed_by_dispatcher))
        day_length = require_float("day_length", self.day_length)
        if not (math.isfinite(day_length) and day_length > 0):
            raise InvalidProblemError(f"day_length must be positive and finite: {day_length}")
        object.__setattr__(self, "day_length", day_length)
        size = durations.shape[0]
        _require_indices("orders.location", self.orders.location, size, allow_none=True)
        _require_indices(
            "vehicles.start_location", self.vehicles.start_location, size, allow_none=True
        )
        _require_indices("vehicles.end_location", self.vehicles.end_location, size, allow_none=True)
        _require_indices(
            "vehicles.driver", self.vehicles.driver, len(self.drivers), allow_none=True
        )
        _require_indices(
            "orders.pinned_vehicle", self.orders.pinned_vehicle, len(self.vehicles), allow_none=True
        )
        _require_indices(
            "orders.pinned_driver", self.orders.pinned_driver, len(self.drivers), allow_none=True
        )
        for order in sorted(
            require_int("removed_by_dispatcher[]", order) for order in self.removed_by_dispatcher
        ):
            if not 0 <= order < len(self.orders):
                raise InvalidProblemError(
                    f"removed_by_dispatcher holds order {order}, not one of {len(self.orders)}"
                )
        self._require_drop_penalties()
        self._require_shifts_on_the_date()
        self._require_exclusions_match_the_roads()
        self._require_reoptimization()

    def validate(self, solution: Solution) -> ValidationReport:
        """Judges a solution against every rule the problem carries, and times every route.

        A daily problem's rules are the daily ones; a re-optimization's add its conventions
        (BR-15, BR-42, ADR-050).

        Args:
            solution: Any solution whose indices fall inside this problem.

        Returns:
            Every violation, never only the first, the validator's own timings, and in a
            re-optimization the late stops it flags (`late_stops`, BR-107).

        Raises:
            InvalidSolutionError: If an index in the solution points past this problem.
        """
        return feasibility.validate(self, solution)

    def cost(self, solution: Solution, timings: Timings) -> Cost:
        """Scores a solution with the shared objective, in seconds (BR-09, BR-58).

        Args:
            solution: The solution `validate` was given.
            timings: The timings `validate` returned for it.

        Returns:
            Each term of the objective and their total.

        Raises:
            ValueError: If the timings do not belong to the solution.
        """
        return objective.cost(self, solution, timings)

    def explain_drop(self, order: int, solution: Solution) -> DropReason:
        """Says why an order is unserved, with the fact behind it (FR-144, ADR-053).

        Args:
            order: An order the solution does not serve.
            solution: The solution that leaves it unserved.

        Returns:
            The first reason in ADR-053's order that applies.

        Raises:
            ValueError: If the solution serves the order, the index is past the problem, or a
                re-optimization holds the order's stop as locked, so its report serves it (BR-10).
        """
        return drops.explain_drop(self, order, solution)

    def unserved(self, solution: Solution) -> tuple[int, ...]:
        """Every order a solution leaves on no route, excluded ones included, in index order.

        A locked order is served by its report, so it is never among them (BR-10).
        """
        served = solution.served()
        return tuple(
            order
            for order in range(len(self.orders))
            if order not in served and not self.is_locked(order)
        )

    def is_locked(self, order: int) -> bool:
        """Says whether a re-optimization holds an order's stop as reported, done or in progress.

        Such a stop is a fact: its report serves the order, and no solution moves it (BR-10).
        """
        return self.reoptimization is not None and bool(self.reoptimization.locked[order])

    def _require_reoptimization(self) -> None:
        context, vehicles = self.reoptimization, self.vehicles
        if context is None:
            for vehicle, exclusion in enumerate(vehicles.exclusion):
                if exclusion in REOPTIMIZATION_EXCLUSIONS:
                    raise InvalidProblemError(
                        f"vehicles.exclusion[{vehicle}] is {exclusion}, which only a "
                        "re-optimization's vehicle state gives"
                    )
            return
        if not 0 <= context.now <= self.day_length:
            raise InvalidProblemError(
                f"reoptimization.now must lie on the date, 0 to {self.day_length}: {context.now}"
            )
        _require_lengths("reoptimization", len(vehicles), start_time=context.start_time)
        _require_lengths("reoptimization", len(self.orders), locked=context.locked)
        _refuse_first(
            context.start_time < context.now,
            lambda i: (
                f"reoptimization.start_time[{i}] is before now, {context.now}: "
                f"{context.start_time[i]}"
            ),
        )
        _require_indices(
            "reoptimization.stays_on", context.stays_on, len(vehicles), allow_none=True
        )
        for order, vehicle in enumerate(int(vehicle) for vehicle in context.stays_on):
            if vehicle != NO_INDEX and not vehicles.is_usable(vehicle):
                raise InvalidProblemError(
                    f"reoptimization.stays_on[{order}] is vehicle {vehicle}, which can drive no "
                    "route: a stop stays only on an active vehicle (BR-106)"
                )
        for vehicle in sorted(context.no_usable_position):
            if not 0 <= vehicle < len(vehicles):
                raise InvalidProblemError(
                    f"reoptimization.no_usable_position holds vehicle {vehicle}, not one of "
                    f"{len(vehicles)}"
                )
        try:
            require_inside(self, context.warm_start)
        except InvalidSolutionError as error:
            raise InvalidProblemError(f"reoptimization.warm_start: {error}") from error

    def _require_drop_penalties(self) -> None:
        penalties = self.drop_penalties
        if len(penalties) != len(self.orders):
            raise InvalidProblemError(
                f"drop_penalties has {len(penalties)} entries for {len(self.orders)} orders"
            )
        _refuse_first(
            ~(np.isfinite(penalties) & (penalties >= 0)),
            lambda i: f"drop_penalties[{i}] must be finite and zero or more: {penalties[i]}",
        )

    def _require_shifts_on_the_date(self) -> None:
        # FR-190: a shift lies on its date, so a door stop after the date is also after its shift.
        drivers = self.drivers
        _refuse_first(
            drivers.shift_start < 0,
            lambda i: (
                f"drivers.shift_start[{i}] is before the date starts: {drivers.shift_start[i]}"
            ),
        )
        _refuse_first(
            drivers.shift_end > self.day_length,
            lambda i: (
                f"drivers.shift_end[{i}] is after the date ends at {self.day_length}: "
                f"{drivers.shift_end[i]}"
            ),
        )

    def _require_exclusions_match_the_roads(self) -> None:
        vehicles, orders = self.vehicles, self.orders
        unconnected = depots_unconnected(
            vehicles.start_location, vehicles.end_location, self.durations
        )
        for vehicle, exclusion in enumerate(vehicles.exclusion):
            # A vehicle out of a re-optimization is out whatever its roads, so they are not judged.
            if (
                exclusion is VehicleExclusion.DEPOT_WITHOUT_LOCATION
                or exclusion in REOPTIMIZATION_EXCLUSIONS
            ):
                continue
            if unconnected[vehicle] != (exclusion is VehicleExclusion.NO_ROAD_BETWEEN_DEPOTS):
                raise InvalidProblemError(
                    f"vehicles.exclusion[{vehicle}] is {exclusion}, but the road from its start "
                    f"to its end depot {'is missing' if unconnected[vehicle] else 'exists'}"
                )
        usable = np.array(vehicles.usable(), dtype=np.int64)
        unreachable = unreachable_locations(
            orders.location,
            vehicles.start_location[usable],
            vehicles.end_location[usable],
            self.durations,
        )
        for order, order_exclusion in enumerate(orders.exclusion):
            if order_exclusion is OrderExclusion.MISSING_LOCATION:
                continue
            if unreachable[order] != (order_exclusion is OrderExclusion.UNREACHABLE_LOCATION):
                raise InvalidProblemError(
                    f"orders.exclusion[{order}] is {order_exclusion}, but the matrices make it "
                    f"{'unreachable' if unreachable[order] else 'reachable'}"
                )


def require_matrix(name: str, values: npt.ArrayLike) -> npt.NDArray[np.float64]:
    """Copies a matrix read-only, refusing anything the contract cannot carry (ADR-052).

    Args:
        name: The matrix's name, for the error.
        values: A square matrix of seconds or metres, NaN where there is no road.

    Returns:
        A read-only float64 copy.

    Raises:
        InvalidProblemError: If it is not a square matrix of numbers, or an entry is negative,
            infinite or past the int32 range.
    """
    matrix = converted(name, values, np.float64)
    if matrix.ndim != 2 or matrix.shape[0] != matrix.shape[1]:
        raise InvalidProblemError(f"{name} must be a square matrix: shape {matrix.shape}")
    known = ~np.isnan(matrix)
    outside = known & ~((matrix >= 0) & (matrix <= MATRIX_ENTRY_MAX))
    if np.any(outside):
        row, column = (int(index) for index in np.argwhere(outside)[0])
        raise InvalidProblemError(
            f"{name}[{row}, {column}] must be 0 to {MATRIX_ENTRY_MAX}, or NaN for no road: "
            f"{matrix[row, column]}"
        )
    matrix.setflags(write=False)
    return matrix


def require_same_roads(
    durations: npt.NDArray[np.float64], distances: npt.NDArray[np.float64]
) -> None:
    """Refuses matrices that disagree on their size or on which pairs have no road.

    Args:
        durations: The base durations.
        distances: The distances.

    Raises:
        InvalidProblemError: If the shapes differ or one matrix has a road the other lacks.
    """
    if durations.shape != distances.shape:
        raise InvalidProblemError(
            f"durations and distances must be the same size: {durations.shape} and "
            f"{distances.shape}"
        )
    disagree = np.isnan(durations) != np.isnan(distances)
    if np.any(disagree):
        row, column = (int(index) for index in np.argwhere(disagree)[0])
        raise InvalidProblemError(
            f"durations and distances disagree on whether there is no road at [{row}, {column}]"
        )


def depots_unconnected(
    start_location: npt.NDArray[np.int64],
    end_location: npt.NDArray[np.int64],
    durations: npt.NDArray[np.float64],
) -> npt.NDArray[np.bool_]:
    """Finds the vehicles whose start has no road to their end.

    A start is the start depot, or in a re-optimization where the vehicle is placed (BR-51); an
    end is the end depot, or a withdrawn vehicle's last reported stop (BR-52).

    Args:
        start_location: Each vehicle's start location, or -1.
        end_location: Each vehicle's end location, or -1.
        durations: The base durations, NaN for no road.

    Returns:
        True for a vehicle with both locations and no road from its start to its end.
    """
    located = (start_location != NO_INDEX) & (end_location != NO_INDEX)
    result = np.zeros(len(start_location), dtype=np.bool_)
    result[located] = np.isnan(durations[start_location[located], end_location[located]])
    return result


def unreachable_locations(
    locations: npt.NDArray[np.int64],
    start_locations: npt.NDArray[np.int64],
    end_locations: npt.NDArray[np.int64],
    durations: npt.NDArray[np.float64],
) -> npt.NDArray[np.bool_]:
    """Finds the locations no usable vehicle can serve by road (FR-66).

    A location is reachable when some usable vehicle has a road from its start to it and from it
    to its end. A start is the start depot, or in a re-optimization where the vehicle is placed
    (BR-51). With no usable vehicle, nothing is judged unreachable: there is no start to judge
    from.

    Args:
        locations: The locations to judge, or -1 for none, which is never unreachable.
        start_locations: Each usable vehicle's start location.
        end_locations: The same vehicles' end locations, in the same order.
        durations: The base durations, NaN for no road.

    Returns:
        True for each location with no such vehicle.
    """
    result = np.zeros(len(locations), dtype=np.bool_)
    located = locations != NO_INDEX
    if not start_locations.size or not np.any(located):
        return result
    targets = locations[located]
    out = ~np.isnan(durations[np.ix_(start_locations, targets)])
    back = ~np.isnan(durations[np.ix_(targets, end_locations)]).T
    result[located] = ~np.any(out & back, axis=0)
    return result


def converted(name: str, values: npt.ArrayLike, dtype: type[np.generic]) -> npt.NDArray[Any]:
    """Copies values into a NumPy array of one type, refusing what does not convert.

    Args:
        name: The input's name, for the error.
        values: Anything NumPy reads.
        dtype: The element type, such as `np.float64`.

    Returns:
        A new, writable array.

    Raises:
        InvalidProblemError: If a value is not a number of that type or overflows it, such as
            2**64 as an int64.
    """
    try:
        return np.array(values, dtype=dtype, copy=True)
    except (TypeError, ValueError, OverflowError) as error:
        raise InvalidProblemError(f"{name} must hold {dtype.__name__} values: {error}") from error


def _set_column(owner: object, prefix: str, name: str, dtype: type[np.generic]) -> None:
    column = converted(f"{prefix}.{name}", getattr(owner, name), dtype)
    if column.ndim != 1:
        raise InvalidProblemError(f"{prefix}.{name} must be one-dimensional: shape {column.shape}")
    column.setflags(write=False)
    object.__setattr__(owner, name, column)


def _require_lengths(what: str, count: int, **columns: Sized) -> None:
    for name, column in columns.items():
        if len(column) != count:
            raise InvalidProblemError(f"{what}.{name} has {len(column)} entries, not {count}")


def _refuse_first(bad: npt.NDArray[np.bool_], message: Callable[[int], str]) -> None:
    if np.any(bad):
        index = int(np.flatnonzero(bad)[0])
        raise InvalidProblemError(message(index))


def _require_finite_non_negative(name: str, values: npt.NDArray[np.float64]) -> None:
    _refuse_first(
        ~(np.isfinite(values) & (values >= 0)),
        lambda i: f"{name}[{i}] must be finite and zero or more: {values[i]}",
    )


def _require_indices(
    name: str, indices: npt.NDArray[np.int64], size: int, *, allow_none: bool = False
) -> None:
    lowest = NO_INDEX if allow_none else 0
    _refuse_first(
        (indices < lowest) | (indices >= size),
        lambda i: f"{name}[{i}] is {indices[i]}, not one of {size}",
    )
