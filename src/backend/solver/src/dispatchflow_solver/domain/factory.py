"""The problem factory: builds a `Problem` from plain values and decides each convention once.

The HTTP adapter translates the contract into these inputs, so no transport type reaches the
domain (TS §2.1, rule 4). Each convention is written into the `Problem`, and `validate` checks the
constraints it carries (ADR-050).
"""

import math
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Final

import numpy as np
import numpy.typing as npt

from dispatchflow_solver.domain.penalty import DEFAULT_DOOR_WINDOW_RATE, PenaltyScale
from dispatchflow_solver.domain.problem import (
    NO_INDEX,
    Drivers,
    InvalidProblemError,
    OrderExclusion,
    Orders,
    Pin,
    Problem,
    VehicleExclusion,
    Vehicles,
)
from dispatchflow_solver.domain.travel_time import TravelTimeModel

NO_ROAD: Final = -1.0
"""A matrix entry for a pair the road network cannot connect, as the contract sends it (ADR-052)."""


@dataclass(frozen=True, slots=True)
class DepotSpec:
    """A depot as the date's input gives it.

    Attributes:
        location: Its location in the matrices, or None when it has no coordinates (FR-197).
        opens: When it opens, or None with no hours (FR-129).
        closes: When it closes, or None with no hours (FR-129).
    """

    location: int | None
    opens: float | None = None
    closes: float | None = None


@dataclass(frozen=True, slots=True)
class VehicleSpec:
    """A vehicle as the date's input gives it.

    Attributes:
        start_depot: The index of the depot it starts at.
        end_depot: The index of the depot it ends at.
        weight_capacity_kg: Weight capacity in kilograms.
        volume_capacity_l: Volume capacity in litres, or None for no volume limit (FR-390).
        driver: The index of the driver the roster gives it.
    """

    start_depot: int
    end_depot: int
    weight_capacity_kg: float
    volume_capacity_l: float | None
    driver: int


@dataclass(frozen=True, slots=True)
class DriverSpec:
    """A driver's shift on the date (BR-06).

    Attributes:
        shift_start: When the shift starts.
        shift_end: When the shift ends.
    """

    shift_start: float
    shift_end: float


@dataclass(frozen=True, slots=True)
class OrderSpec:
    """An order as the date's input gives it.

    Attributes:
        location: Its location in the matrices, or None when its customer has no coordinates.
        weight_kg: Weight demand in kilograms.
        volume_l: Volume demand in litres.
        service_s: Service time in seconds.
        window_start: The time window's start.
        window_end: The time window's end.
        priority: 1, the highest, to 5.
        leave_at_door: Whether it may be left at the door (FR-364).
        pin: The vehicle or driver it is pinned to, if any (FR-91).
    """

    location: int | None
    weight_kg: float
    volume_l: float
    service_s: float
    window_start: float
    window_end: float
    priority: int
    leave_at_door: bool = False
    pin: Pin | None = None


class ProblemFactory:
    """Builds problems; the only place a convention is decided (ADR-050)."""

    @staticmethod
    def daily(
        *,
        orders: Sequence[OrderSpec],
        depots: Sequence[DepotSpec],
        vehicles: Sequence[VehicleSpec],
        drivers: Sequence[DriverSpec],
        durations: npt.ArrayLike,
        distances: npt.ArrayLike,
        travel: TravelTimeModel,
        day_length: float,
        fixed_vehicle_cost: float = 0.0,
        door_window_rate: float = DEFAULT_DOOR_WINDOW_RATE,
        breaks_reserved: bool = False,
        removed_by_dispatcher: frozenset[int] = frozenset(),
    ) -> Problem:
        """Builds the problem of a daily solve.

        - A vehicle whose start or end depot has no coordinates is kept, excluded and named, so
          it drives no route (FR-197).
        - An order with no location is excluded as *missing location* (BR-14). One whose location
          lacks a road route from any usable vehicle's start depot, or to any usable vehicle's end
          depot, is excluded as *unreachable location* (FR-66). Both stay indexable, so
          `explain_drop` names them. With no usable vehicle, no order is judged unreachable.
        - K comes from the longest shift, the largest finite matrix entry at the highest factor,
          unbuffered and rounded up, and the fixed vehicle cost (BR-126).

        Args:
            orders: Every order of the date.
            depots: The depots the vehicles start and end at.
            vehicles: The date's vehicles.
            drivers: The date's drivers.
            durations: Base durations in seconds between distinct locations; -1 for no road.
            distances: Metres between distinct locations; -1 for no road.
            travel: The day's buckets, factors and buffer.
            day_length: The date's length in seconds (ADR-088).
            fixed_vehicle_cost: Seconds per vehicle used, 0 when disabled (FR-21).
            door_window_rate: The door-window rate (BR-120).
            breaks_reserved: Whether routes reserve time for breaks (FR-81, BR-105).
            removed_by_dispatcher: Orders a dispatcher took off their route (FR-146).

        Returns:
            The problem.

        Raises:
            InvalidProblemError: If an input is malformed or contradicts another.
        """
        duration_matrix = _with_no_road_as_nan("durations", durations)
        distance_matrix = _with_no_road_as_nan("distances", distances)
        vehicle_struct = _vehicles(vehicles, depots)
        driver_struct = Drivers(
            shift_start=np.array([driver.shift_start for driver in drivers], dtype=np.float64),
            shift_end=np.array([driver.shift_end for driver in drivers], dtype=np.float64),
        )
        order_struct = _orders(orders, vehicle_struct, duration_matrix, distance_matrix)
        finite = duration_matrix[~np.isnan(duration_matrix)]
        largest_travel = travel.largest_unbuffered(float(finite.max(initial=0.0)))
        longest_shift = max(
            (driver_struct.shift_length(driver) for driver in range(len(driver_struct))),
            default=0.0,
        )
        penalties = PenaltyScale.for_date(
            longest_shift=longest_shift,
            largest_travel=largest_travel,
            fixed_vehicle_cost=fixed_vehicle_cost,
            door_window_rate=door_window_rate,
        )
        return Problem(
            orders=order_struct,
            vehicles=vehicle_struct,
            drivers=driver_struct,
            durations=duration_matrix,
            distances=distance_matrix,
            travel=travel,
            penalties=penalties,
            day_length=day_length,
            breaks_reserved=breaks_reserved,
            removed_by_dispatcher=removed_by_dispatcher,
        )


def _with_no_road_as_nan(name: str, values: npt.ArrayLike) -> npt.NDArray[np.float64]:
    # NaN, unlike -1, can never be read as a duration: arithmetic on it stays NaN instead of
    # producing a plausible time, and np.isnan finds every gap.
    matrix = np.array(values, dtype=np.float64, copy=True)
    if matrix.ndim != 2 or matrix.shape[0] != matrix.shape[1]:
        raise InvalidProblemError(f"{name} must be a square matrix: shape {matrix.shape}")
    no_road = matrix == NO_ROAD
    if np.any(np.isnan(matrix) | (~no_road & (matrix < 0))):
        raise InvalidProblemError(f"{name} entries are zero or more, or -1 for no road")
    matrix[no_road] = np.nan
    return matrix


def _vehicles(vehicles: Sequence[VehicleSpec], depots: Sequence[DepotSpec]) -> Vehicles:
    for vehicle in vehicles:
        for depot in (vehicle.start_depot, vehicle.end_depot):
            if not 0 <= depot < len(depots):
                raise InvalidProblemError(f"a vehicle names depot {depot} of {len(depots)}")
    starts = [depots[vehicle.start_depot] for vehicle in vehicles]
    ends = [depots[vehicle.end_depot] for vehicle in vehicles]
    return Vehicles(
        weight_capacity_kg=np.array([v.weight_capacity_kg for v in vehicles], dtype=np.float64),
        volume_capacity_l=np.array(
            [math.inf if v.volume_capacity_l is None else v.volume_capacity_l for v in vehicles],
            dtype=np.float64,
        ),
        start_location=np.array([_location(depot) for depot in starts], dtype=np.int64),
        end_location=np.array([_location(depot) for depot in ends], dtype=np.int64),
        start_depot_opens=np.array(
            [-math.inf if depot.opens is None else depot.opens for depot in starts],
            dtype=np.float64,
        ),
        end_depot_closes=np.array(
            [math.inf if depot.closes is None else depot.closes for depot in ends],
            dtype=np.float64,
        ),
        driver=np.array([vehicle.driver for vehicle in vehicles], dtype=np.int64),
        exclusion=tuple(
            VehicleExclusion.DEPOT_WITHOUT_LOCATION
            if start.location is None or end.location is None
            else None
            for start, end in zip(starts, ends, strict=True)
        ),
    )


def _location(depot: DepotSpec) -> int:
    return NO_INDEX if depot.location is None else depot.location


def _orders(
    orders: Sequence[OrderSpec],
    vehicles: Vehicles,
    durations: npt.NDArray[np.float64],
    distances: npt.NDArray[np.float64],
) -> Orders:
    size = durations.shape[0]
    for order in orders:
        if order.location is not None and not 0 <= order.location < size:
            raise InvalidProblemError(f"an order names location {order.location} of {size}")
    usable = vehicles.usable()
    starts = sorted({int(vehicles.start_location[vehicle]) for vehicle in usable})
    ends = sorted({int(vehicles.end_location[vehicle]) for vehicle in usable})

    def exclusion(order: OrderSpec) -> OrderExclusion | None:
        if order.location is None:
            return OrderExclusion.MISSING_LOCATION
        legs = [*((start, order.location) for start in starts)]
        legs += [(order.location, end) for end in ends]
        if any(np.isnan(durations[leg]) or np.isnan(distances[leg]) for leg in legs):
            return OrderExclusion.UNREACHABLE_LOCATION
        return None

    return Orders(
        location=np.array([_order_location(order) for order in orders], dtype=np.int64),
        weight_kg=np.array([order.weight_kg for order in orders], dtype=np.float64),
        volume_l=np.array([order.volume_l for order in orders], dtype=np.float64),
        service_s=np.array([order.service_s for order in orders], dtype=np.float64),
        window_start=np.array([order.window_start for order in orders], dtype=np.float64),
        window_end=np.array([order.window_end for order in orders], dtype=np.float64),
        priority=np.array([order.priority for order in orders], dtype=np.int64),
        leave_at_door=np.array([order.leave_at_door for order in orders], dtype=np.bool_),
        pinned_vehicle=np.array([_pinned_vehicle(order) for order in orders], dtype=np.int64),
        pinned_driver=np.array([_pinned_driver(order) for order in orders], dtype=np.int64),
        exclusion=tuple(exclusion(order) for order in orders),
    )


def _order_location(order: OrderSpec) -> int:
    return NO_INDEX if order.location is None else order.location


def _pinned_vehicle(order: OrderSpec) -> int:
    if order.pin is None or order.pin.vehicle is None:
        return NO_INDEX
    return order.pin.vehicle


def _pinned_driver(order: OrderSpec) -> int:
    if order.pin is None or order.pin.driver is None:
        return NO_INDEX
    return order.pin.driver
