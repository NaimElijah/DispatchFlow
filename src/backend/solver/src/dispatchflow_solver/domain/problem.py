"""The problem: everything one solve needs, and the single definition of what a solution means.

`Problem` holds every order of the delivery date, including those kept out of the search with
their reason, the vehicles, the drivers, the base matrices, the travel-time model and the penalty
scale (DL §4.9, TS §7.1). Its methods are the validator, the objective and the drop reasons, and
every solver, edit check, publishing re-check and research score goes through them (BR-42).

Every time is in seconds since the delivery date's local midnight in `Asia/Jerusalem` (ADR-088).
Every array is a read-only float64 copy, or int64 for indices, where -1 means "none".
"""

import math
from collections.abc import Sized
from dataclasses import dataclass
from enum import StrEnum

import numpy as np
import numpy.typing as npt

from dispatchflow_solver.domain import drops, feasibility, objective
from dispatchflow_solver.domain._arrays import NO_INDEX, frozen_bools, frozen_floats, frozen_ints
from dispatchflow_solver.domain.drops import DropReason
from dispatchflow_solver.domain.feasibility import Timings, ValidationReport
from dispatchflow_solver.domain.objective import Cost
from dispatchflow_solver.domain.penalty import HIGHEST_PRIORITY, LOWEST_PRIORITY, PenaltyScale
from dispatchflow_solver.domain.solution import Solution
from dispatchflow_solver.domain.travel_time import TravelTimeModel

__all__ = [
    "NO_INDEX",
    "Drivers",
    "InvalidProblemError",
    "OrderExclusion",
    "Orders",
    "Pin",
    "Problem",
    "VehicleExclusion",
    "Vehicles",
]


class InvalidProblemError(ValueError):
    """A problem whose data contradicts itself or the rules it must hold before any solve."""


class OrderExclusion(StrEnum):
    """Why an order never enters the search (BR-14, FR-66)."""

    MISSING_LOCATION = "missing_location"
    """Its customer has no coordinates (BR-14)."""
    UNREACHABLE_LOCATION = "unreachable_location"
    """The road network has no route between its location and a depot (FR-66)."""


class VehicleExclusion(StrEnum):
    """Why a vehicle can drive no route in the solve (FR-197)."""

    DEPOT_WITHOUT_LOCATION = "depot_without_location"
    """Its start or end depot has no coordinates (FR-197)."""


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
            raise InvalidProblemError(f"a pin names exactly one vehicle or driver: {self}")


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
            object.__setattr__(self, name, frozen_ints(getattr(self, name)))
        for name in ("weight_kg", "volume_l", "service_s", "window_start", "window_end"):
            object.__setattr__(self, name, frozen_floats(getattr(self, name)))
        object.__setattr__(self, "leave_at_door", frozen_bools(self.leave_at_door))
        object.__setattr__(self, "exclusion", tuple(self.exclusion))
        count = len(self.location)
        _require_lengths(
            "orders",
            count,
            self.weight_kg,
            self.volume_l,
            self.service_s,
            self.window_start,
            self.window_end,
            self.priority,
            self.leave_at_door,
            self.pinned_vehicle,
            self.pinned_driver,
            self.exclusion,
        )
        for name in ("weight_kg", "volume_l", "service_s"):
            _require_finite_non_negative(f"order {name}", getattr(self, name))
        windows_finite = np.isfinite(self.window_start) & np.isfinite(self.window_end)
        if not np.all(windows_finite & (self.window_start <= self.window_end)):
            raise InvalidProblemError("every order's window must be finite and end after it starts")
        if not np.all((self.priority >= HIGHEST_PRIORITY) & (self.priority <= LOWEST_PRIORITY)):
            raise InvalidProblemError("every order's priority runs from 1 to 5 (BR-53)")
        if np.any((self.pinned_vehicle != NO_INDEX) & (self.pinned_driver != NO_INDEX)):
            raise InvalidProblemError("an order is pinned to a vehicle or to a driver, not both")
        for order, exclusion in enumerate(self.exclusion):
            missing = self.location[order] == NO_INDEX
            if missing != (exclusion is OrderExclusion.MISSING_LOCATION):
                raise InvalidProblemError(f"order {order}: no location iff excluded for it")

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

    Depot hours are folded in: a vehicle leaves no earlier than its start depot opens and returns
    no later than its end depot closes (BR-46).

    Attributes:
        weight_capacity_kg: Weight capacity in kilograms (BR-05).
        volume_capacity_l: Volume capacity in litres; infinite for research instances (FR-390).
        start_location: The start depot's location, or -1 when the depot has none (FR-197).
        end_location: The end depot's location, or -1 when the depot has none (FR-197).
        start_depot_opens: When the start depot opens; -inf when it has no hours (FR-129).
        end_depot_closes: When the end depot closes; +inf when it has no hours (FR-129).
        driver: The driver the roster gives the vehicle on the date.
        exclusion: Why each vehicle can drive no route, or None for a usable vehicle.

    Raises:
        InvalidProblemError: If the arrays differ in length or hold a value no vehicle can have.
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
            object.__setattr__(self, name, frozen_ints(getattr(self, name)))
        for name in (
            "weight_capacity_kg",
            "volume_capacity_l",
            "start_depot_opens",
            "end_depot_closes",
        ):
            object.__setattr__(self, name, frozen_floats(getattr(self, name)))
        object.__setattr__(self, "exclusion", tuple(self.exclusion))
        _require_lengths(
            "vehicles",
            len(self.driver),
            self.weight_capacity_kg,
            self.volume_capacity_l,
            self.start_location,
            self.end_location,
            self.start_depot_opens,
            self.end_depot_closes,
            self.exclusion,
        )
        for capacity in (self.weight_capacity_kg, self.volume_capacity_l):
            if np.any(np.isnan(capacity) | (capacity < 0)):
                raise InvalidProblemError("every vehicle capacity must be zero or more")
        if np.any(np.isnan(self.start_depot_opens) | np.isnan(self.end_depot_closes)):
            raise InvalidProblemError("depot hours must be numbers; ±inf means none")
        for vehicle, exclusion in enumerate(self.exclusion):
            no_depot = NO_INDEX in (self.start_location[vehicle], self.end_location[vehicle])
            if no_depot != (exclusion is VehicleExclusion.DEPOT_WITHOUT_LOCATION):
                raise InvalidProblemError(
                    f"vehicle {vehicle}: no depot location iff excluded for it"
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
        InvalidProblemError: If the arrays differ in length or a shift is not finite or ends before
            it starts.
    """

    shift_start: npt.NDArray[np.float64]
    shift_end: npt.NDArray[np.float64]

    def __post_init__(self) -> None:
        """Copies the arrays read-only and refuses a shift that ends before it starts."""
        object.__setattr__(self, "shift_start", frozen_floats(self.shift_start))
        object.__setattr__(self, "shift_end", frozen_floats(self.shift_end))
        _require_lengths("drivers", len(self.shift_start), self.shift_end)
        finite = np.isfinite(self.shift_start) & np.isfinite(self.shift_end)
        if not np.all(finite & (self.shift_start <= self.shift_end)):
            raise InvalidProblemError("every shift must be finite and end after it starts")

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

    Attributes:
        orders: Every order of the date, excluded ones included.
        vehicles: Every vehicle of the date, excluded ones included.
        drivers: Every driver of the date.
        durations: Base durations in seconds between distinct locations; NaN where the road
            network has no route.
        distances: Metres between distinct locations; NaN where there is no route.
        travel: The travel-time model, the only place factors and the buffer apply.
        penalties: The drop unit and the rates the objective charges.
        day_length: The date's length in seconds: 23, 24 or 25 hours (ADR-088).
        breaks_reserved: Whether routes reserve time for breaks (FR-81, BR-105).
        removed_by_dispatcher: Orders a dispatcher took off their route (FR-146).

    Raises:
        InvalidProblemError: If the parts contradict each other, an index points past its array, or
            two locations a solve may use have no road route between them.
    """

    orders: Orders
    vehicles: Vehicles
    drivers: Drivers
    durations: npt.NDArray[np.float64]
    distances: npt.NDArray[np.float64]
    travel: TravelTimeModel
    penalties: PenaltyScale
    day_length: float
    breaks_reserved: bool = False
    removed_by_dispatcher: frozenset[int] = frozenset()

    def __post_init__(self) -> None:
        """Copies the matrices read-only and refuses a problem that contradicts itself."""
        object.__setattr__(self, "durations", frozen_floats(self.durations))
        object.__setattr__(self, "distances", frozen_floats(self.distances))
        size = self._require_matrices()
        if not (math.isfinite(self.day_length) and self.day_length > 0):
            raise InvalidProblemError(f"the date must last a positive time: {self.day_length}")
        _require_indices("order location", self.orders.location, size, allow_none=True)
        _require_indices("vehicle start", self.vehicles.start_location, size, allow_none=True)
        _require_indices("vehicle end", self.vehicles.end_location, size, allow_none=True)
        _require_indices("rostered driver", self.vehicles.driver, len(self.drivers))
        _require_indices(
            "pinned vehicle", self.orders.pinned_vehicle, len(self.vehicles), allow_none=True
        )
        _require_indices(
            "pinned driver", self.orders.pinned_driver, len(self.drivers), allow_none=True
        )
        _require_indices(
            "removed order",
            np.array(sorted(self.removed_by_dispatcher), dtype=np.int64),
            len(self.orders),
        )
        self._require_roads_between_usable_locations()

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

    def _require_matrices(self) -> int:
        shape = self.durations.shape
        if len(shape) != 2 or shape[0] != shape[1] or self.distances.shape != shape:
            raise InvalidProblemError("both matrices must be square and of one size")
        for name, matrix in (("durations", self.durations), ("distances", self.distances)):
            known = matrix[~np.isnan(matrix)]
            if np.any(~np.isfinite(known) | (known < 0)):
                raise InvalidProblemError(f"{name} must be zero or more, or NaN for no road")
        return int(shape[0])

    def _require_roads_between_usable_locations(self) -> None:
        # The validator and the solvers time any leg between two locations a solve may use, so
        # each such pair needs a road. ProblemFactory excludes what lacks one; this guards the rest.
        # With no usable vehicle there is no depot to judge reachability from, and every route
        # breaks BR-40 anyway, so nothing is guaranteed.
        usable = self.vehicles.usable()
        if not usable:
            return
        locations = {
            *(int(self.vehicles.start_location[vehicle]) for vehicle in usable),
            *(int(self.vehicles.end_location[vehicle]) for vehicle in usable),
            *(
                int(self.orders.location[order])
                for order in range(len(self.orders))
                if self.orders.is_routable(order)
            ),
        }
        index = np.array(sorted(locations), dtype=np.int64)
        block = np.ix_(index, index)
        if np.any(np.isnan(self.durations[block]) | np.isnan(self.distances[block])):
            raise InvalidProblemError(
                "two locations a solve may use have no road route between them"
            )


def _require_lengths(what: str, count: int, *columns: Sized) -> None:
    if any(len(column) != count for column in columns):
        raise InvalidProblemError(f"every {what} column must have {count} entries")


def _require_finite_non_negative(name: str, values: npt.NDArray[np.float64]) -> None:
    if not np.all(np.isfinite(values) & (values >= 0)):
        raise InvalidProblemError(f"every {name} must be finite and zero or more")


def _require_indices(
    name: str, indices: npt.NDArray[np.int64], size: int, *, allow_none: bool = False
) -> None:
    lowest = NO_INDEX if allow_none else 0
    if indices.size and (indices.min() < lowest or indices.max() >= size):
        raise InvalidProblemError(f"every {name} must index one of {size}")
