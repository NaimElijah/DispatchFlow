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
from collections.abc import Callable, Sized
from dataclasses import dataclass
from typing import Any, Final

import numpy as np
import numpy.typing as npt

from dispatchflow_solver.domain import drops, feasibility, objective
from dispatchflow_solver.domain.drops import DropReason
from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.exclusions import OrderExclusion, VehicleExclusion
from dispatchflow_solver.domain.feasibility import Timings, ValidationReport
from dispatchflow_solver.domain.indices import NO_INDEX
from dispatchflow_solver.domain.objective import Cost
from dispatchflow_solver.domain.penalty import HIGHEST_PRIORITY, LOWEST_PRIORITY, PenaltyScale
from dispatchflow_solver.domain.solution import Solution
from dispatchflow_solver.domain.travel_time import TravelTimeModel

MATRIX_ENTRY_MAX: Final = 2_147_483_647
"""The largest matrix entry: the contract carries the matrices as int32 (ADR-052)."""


@dataclass(frozen=True, slots=True)
class Pin:
    """An order fixed to one vehicle or one driver before a solve (FR-91).

    Attributes:
        vehicle: The pinned vehicle's index, or None for a driver pin.
        driver: The pinned driver's index, or None for a vehicle pin.

    Raises:
        InvalidProblemError: Unless exactly one of the two is a non-negative index.
    """

    vehicle: int | None = None
    driver: int | None = None

    def __post_init__(self) -> None:
        """Refuses a pin to neither, to both, or to a negative index."""
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
    """The date's vehicles, each with its depots and its rostered driver, indexed by vehicle.

    The roster pairs each vehicle with one driver, and no driver drives two vehicles on a date
    (FR-54, BR-20). Depot hours are folded in: a vehicle leaves no earlier than its start depot
    opens and returns no later than its end depot closes (BR-46).

    Attributes:
        weight_capacity_kg: Weight capacity in kilograms (BR-05).
        volume_capacity_l: Volume capacity in litres; +inf for no volume limit (FR-390).
        start_location: The start depot's location, or -1 when the depot has none (FR-197).
        end_location: The end depot's location, or -1 when the depot has none (FR-197).
        start_depot_opens: When the start depot opens; -inf when it has no hours (FR-129).
        end_depot_closes: When the end depot closes; +inf when it has no hours (FR-129).
        driver: The driver the roster gives the vehicle on the date.
        exclusion: Why each vehicle can drive no route, or None for a usable vehicle.

    Raises:
        InvalidProblemError: If the arrays differ in length, hold a value no vehicle can have, or
            roster one driver to two vehicles.
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
        rostered: dict[int, int] = {}
        for vehicle, driver in enumerate(int(driver) for driver in self.driver):
            if driver in rostered:
                raise InvalidProblemError(
                    f"driver {driver} is rostered to vehicles {rostered[driver]} and {vehicle}; "
                    "a driver drives one vehicle on a date (FR-54)"
                )
            rostered[driver] = vehicle
        for vehicle, exclusion in enumerate(self.exclusion):
            no_depot = NO_INDEX in (self.start_location[vehicle], self.end_location[vehicle])
            if no_depot != (exclusion is VehicleExclusion.DEPOT_WITHOUT_LOCATION):
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
class Problem:
    """Everything one daily solve needs, and the rules that judge a solution (DL §4.9).

    Build it with `ProblemFactory`, which decides each convention once and writes it here
    (ADR-050). The re-optimization context joins as a further field.

    A date with no usable vehicle drops every order, and FR-223 names no reason for that; until the
    owner decides one, `explain_drop` reports *capacity* with a largest capacity of 0.

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

    Raises:
        InvalidProblemError: If the parts contradict each other, an index points past its array,
            a shift lies outside the date, or an exclusion disagrees with the matrices.
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

    def __post_init__(self) -> None:
        """Copies every input read-only and refuses a problem that contradicts itself."""
        durations = require_matrix("durations", self.durations)
        distances = require_matrix("distances", self.distances)
        require_same_roads(durations, distances)
        object.__setattr__(self, "durations", durations)
        object.__setattr__(self, "distances", distances)
        _set_column(self, "problem", "drop_penalties", np.float64)
        object.__setattr__(self, "removed_by_dispatcher", frozenset(self.removed_by_dispatcher))
        if not (math.isfinite(self.day_length) and self.day_length > 0):
            raise InvalidProblemError(f"day_length must be positive and finite: {self.day_length}")
        size = durations.shape[0]
        _require_indices("orders.location", self.orders.location, size, allow_none=True)
        _require_indices(
            "vehicles.start_location", self.vehicles.start_location, size, allow_none=True
        )
        _require_indices("vehicles.end_location", self.vehicles.end_location, size, allow_none=True)
        _require_indices("vehicles.driver", self.vehicles.driver, len(self.drivers))
        _require_indices(
            "orders.pinned_vehicle", self.orders.pinned_vehicle, len(self.vehicles), allow_none=True
        )
        _require_indices(
            "orders.pinned_driver", self.orders.pinned_driver, len(self.drivers), allow_none=True
        )
        for order in sorted(self.removed_by_dispatcher):
            if not 0 <= order < len(self.orders):
                raise InvalidProblemError(
                    f"removed_by_dispatcher holds order {order}, not one of {len(self.orders)}"
                )
        self._require_drop_penalties()
        self._require_shifts_on_the_date()
        self._require_exclusions_match_the_roads()

    def validate(self, solution: Solution) -> ValidationReport:
        """Judges a solution against every daily rule and times every route (BR-15, BR-42).

        Args:
            solution: Any solution whose indices fall inside this problem.

        Returns:
            Every violation, never only the first, and the validator's own timings.

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
            ValueError: If the solution serves the order, or the index is past the problem.
        """
        return drops.explain_drop(self, order, solution)

    def unserved(self, solution: Solution) -> tuple[int, ...]:
        """Every order a solution leaves on no route, excluded ones included, in index order."""
        served = solution.served()
        return tuple(order for order in range(len(self.orders)) if order not in served)

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
            if exclusion is VehicleExclusion.DEPOT_WITHOUT_LOCATION:
                continue
            if unconnected[vehicle] != (exclusion is VehicleExclusion.NO_ROAD_BETWEEN_DEPOTS):
                raise InvalidProblemError(
                    f"vehicles.exclusion[{vehicle}] is {exclusion}, but the road from its start "
                    f"depot to its end depot {'is missing' if unconnected[vehicle] else 'exists'}"
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
    """Finds the vehicles whose start depot has no road to their end depot.

    Args:
        start_location: Each vehicle's start depot location, or -1.
        end_location: Each vehicle's end depot location, or -1.
        durations: The base durations, NaN for no road.

    Returns:
        True for a vehicle with both depot locations and no road from start to end.
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

    A location is reachable when some usable vehicle has a road from its start depot to it and
    from it to its end depot. With no usable vehicle, nothing is judged unreachable: there is no
    depot to judge from.

    Args:
        locations: The locations to judge, or -1 for none, which is never unreachable.
        start_locations: Each usable vehicle's start depot location.
        end_locations: The same vehicles' end depot locations, in the same order.
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
