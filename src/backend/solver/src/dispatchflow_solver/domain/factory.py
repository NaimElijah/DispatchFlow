"""The problem factory: builds a `Problem` from plain values and decides each convention once.

The HTTP adapter translates the contract into these inputs, and maps its -1 for "no road" to NaN,
so no transport type or convention reaches the domain (TS §2.1, rule 4; ADR-052). Each convention
is written into the `Problem`, and `validate` checks the constraints it carries (ADR-050).
"""

import math
import numbers
from collections.abc import Sequence
from dataclasses import dataclass

import numpy as np
import numpy.typing as npt

from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.exclusions import OrderExclusion, VehicleExclusion
from dispatchflow_solver.domain.indices import NO_INDEX
from dispatchflow_solver.domain.penalty import PenaltyScale
from dispatchflow_solver.domain.problem import (
    Drivers,
    Orders,
    Pin,
    Problem,
    Vehicles,
    converted,
    depots_unconnected,
    require_matrix,
    require_same_roads,
    unreachable_locations,
)
from dispatchflow_solver.domain.travel_time import TravelTimeModel


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
        driver: The index of the driver the roster gives it (FR-54).
    """

    start_depot: int
    end_depot: int
    weight_capacity_kg: float
    volume_capacity_l: float | None
    driver: int


@dataclass(frozen=True, slots=True)
class DriverSpec:
    """A driver's shift on the date (BR-06, FR-190).

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
        fixed_vehicle_cost: float,
        door_window_rate: float,
        breaks_reserved: bool,
        removed_by_dispatcher: frozenset[int] = frozenset(),
    ) -> Problem:
        """Builds the problem of a daily solve.

        - A vehicle whose start or end depot has no coordinates is kept, excluded and named
          (FR-197). So is one whose start depot has no road to its end depot.
        - An order with no location is excluded as *missing location* (BR-14). One that no
          usable vehicle can reach by road, from its start depot to the order and on to its end
          depot, is excluded as *unreachable location* (FR-66). One unreachable depot excludes
          only its own vehicles, so it never sinks the date (FR-18). With no usable vehicle,
          nothing is judged unreachable: there is no depot to judge from.
        - Each order's drop penalty is K x (6 - priority), and K comes from the longest shift, the
          largest finite matrix entry at the highest factor, unbuffered and rounded up, and the
          fixed vehicle cost (BR-54, BR-126).

        The scoring inputs have no defaults, so the study and the product cannot score the same
        routes differently by leaving one out (BR-58).

        Args:
            orders: Every order of the date.
            depots: The depots the vehicles start and end at.
            vehicles: The date's vehicles.
            drivers: The date's drivers.
            durations: Base durations in seconds between distinct locations; NaN for no road.
            distances: Metres between distinct locations; NaN exactly where durations are.
            travel: The day's buckets, factors and buffer.
            day_length: The date's length in seconds (ADR-088).
            fixed_vehicle_cost: Seconds per vehicle used, 0 when disabled (FR-21).
            door_window_rate: The door-window rate (BR-120).
            breaks_reserved: Whether routes reserve time for breaks (FR-81, BR-105).
            removed_by_dispatcher: Orders a dispatcher took off the current routes (FR-146), so
                `explain_drop` gives that reason; a new solve may serve them again (BR-95).

        Returns:
            The problem.

        Raises:
            InvalidProblemError: If an input is malformed or contradicts another.
        """
        duration_matrix = require_matrix("durations", durations)
        distance_matrix = require_matrix("distances", distances)
        require_same_roads(duration_matrix, distance_matrix)
        size = duration_matrix.shape[0]
        vehicle_struct = _vehicles(vehicles, depots, duration_matrix)
        driver_struct = Drivers(
            shift_start=_floats("drivers.shift_start", [d.shift_start for d in drivers]),
            shift_end=_floats("drivers.shift_end", [d.shift_end for d in drivers]),
        )
        order_struct = _orders(orders, vehicle_struct, duration_matrix, size)
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
            drop_penalties=_floats(
                "drop_penalties",
                [penalties.drop_penalty(int(priority)) for priority in order_struct.priority],
            ),
            day_length=day_length,
            breaks_reserved=breaks_reserved,
            removed_by_dispatcher=removed_by_dispatcher,
        )


def _vehicles(
    vehicles: Sequence[VehicleSpec],
    depots: Sequence[DepotSpec],
    durations: npt.NDArray[np.float64],
) -> Vehicles:
    for index, depot in enumerate(depots):
        _require_depot(index, depot, durations.shape[0])
    for index, vehicle in enumerate(vehicles):
        for field_name in ("start_depot", "end_depot"):
            depot_index = getattr(vehicle, field_name)
            if not 0 <= _require_int(f"vehicles[{index}].{field_name}", depot_index) < len(depots):
                raise InvalidProblemError(
                    f"vehicles[{index}].{field_name} {depot_index} is not one of "
                    f"{len(depots)} depots"
                )
        volume = vehicle.volume_capacity_l
        if volume is not None and not math.isfinite(volume):
            raise InvalidProblemError(
                f"vehicles.volume_capacity_l[{index}] must be finite; give None for no volume "
                f"limit: {volume}"
            )
    starts = [depots[vehicle.start_depot] for vehicle in vehicles]
    ends = [depots[vehicle.end_depot] for vehicle in vehicles]
    start_location = _ints("vehicles.start_location", [_location(depot) for depot in starts])
    end_location = _ints("vehicles.end_location", [_location(depot) for depot in ends])
    unconnected = depots_unconnected(start_location, end_location, durations)

    def exclusion(vehicle: int) -> VehicleExclusion | None:
        if NO_INDEX in (start_location[vehicle], end_location[vehicle]):
            return VehicleExclusion.DEPOT_WITHOUT_LOCATION
        if unconnected[vehicle]:
            return VehicleExclusion.NO_ROAD_BETWEEN_DEPOTS
        return None

    return Vehicles(
        weight_capacity_kg=_floats(
            "vehicles.weight_capacity_kg", [v.weight_capacity_kg for v in vehicles]
        ),
        volume_capacity_l=_floats(
            "vehicles.volume_capacity_l",
            [math.inf if v.volume_capacity_l is None else v.volume_capacity_l for v in vehicles],
        ),
        start_location=start_location,
        end_location=end_location,
        start_depot_opens=_floats(
            "vehicles.start_depot_opens",
            [-math.inf if depot.opens is None else depot.opens for depot in starts],
        ),
        end_depot_closes=_floats(
            "vehicles.end_depot_closes",
            [math.inf if depot.closes is None else depot.closes for depot in ends],
        ),
        driver=_ints("vehicles.driver", [vehicle.driver for vehicle in vehicles]),
        exclusion=tuple(exclusion(vehicle) for vehicle in range(len(vehicles))),
    )


def _require_depot(index: int, depot: DepotSpec, size: int) -> None:
    location = depot.location
    if location is not None and not 0 <= _require_int(f"depots[{index}].location", location) < size:
        raise InvalidProblemError(
            f"depots[{index}].location {depot.location} is not one of {size} locations"
        )
    opens = -math.inf if depot.opens is None else depot.opens
    closes = math.inf if depot.closes is None else depot.closes
    if math.isnan(opens) or opens == math.inf:
        raise InvalidProblemError(f"depots[{index}].opens must be finite or -inf: {opens}")
    if math.isnan(closes) or closes == -math.inf:
        raise InvalidProblemError(f"depots[{index}].closes must be finite or +inf: {closes}")
    if opens > closes:
        raise InvalidProblemError(f"depots[{index}].opens {opens} is after its closes {closes}")


def _location(depot: DepotSpec) -> int:
    return NO_INDEX if depot.location is None else depot.location


def _orders(
    orders: Sequence[OrderSpec],
    vehicles: Vehicles,
    durations: npt.NDArray[np.float64],
    size: int,
) -> Orders:
    for index, order in enumerate(orders):
        given = order.location
        if given is not None and not 0 <= _require_int(f"orders[{index}].location", given) < size:
            raise InvalidProblemError(
                f"orders[{index}].location {order.location} is not one of {size} locations"
            )
    location = _ints("orders.location", [_order_location(order) for order in orders])
    usable = np.array(vehicles.usable(), dtype=np.int64)
    unreachable = unreachable_locations(
        location, vehicles.start_location[usable], vehicles.end_location[usable], durations
    )

    def exclusion(order: int) -> OrderExclusion | None:
        if location[order] == NO_INDEX:
            return OrderExclusion.MISSING_LOCATION
        if unreachable[order]:
            return OrderExclusion.UNREACHABLE_LOCATION
        return None

    return Orders(
        location=location,
        weight_kg=_floats("orders.weight_kg", [order.weight_kg for order in orders]),
        volume_l=_floats("orders.volume_l", [order.volume_l for order in orders]),
        service_s=_floats("orders.service_s", [order.service_s for order in orders]),
        window_start=_floats("orders.window_start", [order.window_start for order in orders]),
        window_end=_floats("orders.window_end", [order.window_end for order in orders]),
        priority=_ints("orders.priority", [order.priority for order in orders]),
        leave_at_door=converted(
            "orders.leave_at_door", [order.leave_at_door for order in orders], np.bool_
        ),
        pinned_vehicle=_ints("orders.pinned_vehicle", [_pinned_vehicle(o) for o in orders]),
        pinned_driver=_ints("orders.pinned_driver", [_pinned_driver(o) for o in orders]),
        exclusion=tuple(exclusion(order) for order in range(len(orders))),
    )


def _floats(name: str, values: Sequence[float]) -> npt.NDArray[np.float64]:
    return converted(name, values, np.float64)


def _ints(name: str, values: Sequence[int]) -> npt.NDArray[np.int64]:
    for index, value in enumerate(values):
        _require_int(f"{name}[{index}]", value)
    return converted(name, values, np.int64)


def _require_int(name: str, value: object) -> int:
    # NumPy would truncate 2.7 to 2 and parse "3"; bool is an int subclass, but True as a
    # priority or an index is a bug. Only whole numbers of an integer type pass.
    if isinstance(value, bool | np.bool_) or not isinstance(value, numbers.Integral):
        raise InvalidProblemError(f"{name} must be an int: {value!r}")
    return int(value)


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
