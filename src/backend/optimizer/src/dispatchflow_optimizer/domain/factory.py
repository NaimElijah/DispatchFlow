"""The problem factory: builds a `Problem` from plain values and decides each convention once.

The HTTP adapter translates the contract into these inputs, and maps its -1 for "no road" to NaN,
so no transport type or convention reaches the domain (TS §2.1, rule 4; ADR-052). Each convention
is written into the `Problem`, and `validate` checks the constraints it carries (ADR-050).

A daily solve comes from `ProblemFactory.daily`. A re-optimization comes from
`ProblemFactory.reoptimization`, which takes the raw facts of a live day and applies ADR-050's six
conventions, so the product and the disruption simulation build the same problem (BR-42).
"""

import math
from collections.abc import Sequence
from dataclasses import dataclass, field, replace
from enum import StrEnum
from typing import Final

import numpy as np
import numpy.typing as npt

from dispatchflow_optimizer.domain._arrays import converted
from dispatchflow_optimizer.domain.errors import InvalidProblemError
from dispatchflow_optimizer.domain.exclusions import OrderExclusion, VehicleExclusion
from dispatchflow_optimizer.domain.indices import NO_INDEX, require_float, require_int
from dispatchflow_optimizer.domain.penalty import PenaltyScale
from dispatchflow_optimizer.domain.problem import (
    Drivers,
    Orders,
    Pin,
    Problem,
    ReoptContext,
    Vehicles,
    depots_unconnected,
    require_matrix,
    require_same_roads,
    servable_where_they_may_go,
    unreachable_locations,
)
from dispatchflow_optimizer.domain.rules import LIMIT_TOLERANCE
from dispatchflow_optimizer.domain.solution import Route, Solution
from dispatchflow_optimizer.domain.travel_time import TravelTimeModel

POSITION_MAX_AGE_S: Final = 5 * 60.0
"""A position older than this at `now` is not usable (BR-80)."""

POSITION_MAX_SPEED_M_S: Final = 200 / 3.6
"""A position that implies a speed above 200 km/h from the one before it is not usable (BR-80)."""

EARTH_RADIUS_M: Final = 6_371_008.8
"""The Earth's mean radius (IUGG), for the great-circle distance between two positions."""

_LATITUDE_LIMIT: Final = 90.0
_LONGITUDE_LIMIT: Final = 180.0


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
        driver: The index of the driver the roster gives it (FR-54). None means it has no driver,
            which only a re-optimization accepts, and only for a vehicle that is out (ADR-050).
    """

    start_depot: int
    end_depot: int
    weight_capacity_kg: float
    volume_capacity_l: float | None
    driver: int | None


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


class VehicleState(StrEnum):
    """A vehicle's state, as the core sends it for a re-optimization (ADR-050)."""

    ACTIVE = "active"
    """It drives the rest of its route and may take more stops."""
    UNAVAILABLE = "unavailable"
    """A dispatcher marked it unavailable (FR-07)."""
    NO_DRIVER = "no_driver"
    """It was left without a driver (FR-82, BR-106)."""
    OUT_OF_SHIFT = "out_of_shift"
    """Its driver's shift ran out (BR-106)."""
    FINISHED = "finished"
    """Its driver finished its route (FR-68, FR-329)."""
    WITHDRAWN = "withdrawn"
    """An earlier re-optimization withdrew it mid-route (FR-267)."""


class Report(StrEnum):
    """What a driver reported about a stop (FR-29)."""

    ARRIVED = "arrived"
    """At the stop and serving it: the stop is in progress (BR-10)."""
    DELIVERED = "delivered"
    """Served."""
    FAILED = "failed"
    """Tried and not served."""


@dataclass(frozen=True, slots=True)
class StopFact:
    """A stop on a vehicle's current route, and what has been reported about it.

    Attributes:
        order: The order the stop serves.
        report: Its latest report, or None while nothing is reported.
        reported_at: The corrected time of its first report (BR-114): its arrival when it was
            marked arrived, otherwise its outcome. None exactly when `report` is None.
    """

    order: int
    report: Report | None = None
    reported_at: float | None = None


@dataclass(frozen=True, slots=True)
class VehiclePosition:
    """A position a vehicle reported (FR-31), with what BR-80 needs to judge it.

    A driver's position is personal data (NFR-19), and so is when it was taken, so the repr leaves
    out the coordinates and the time.

    Attributes:
        latitude: Degrees north, from -90 to 90.
        longitude: Degrees east, from -180 to 180.
        received_at: When the server received it, in seconds since the date's local midnight.
        in_service_area: Whether it lies in the service area. The core holds the area and
            computes the flag (ADR-050).
    """

    latitude: float = field(repr=False)
    longitude: float = field(repr=False)
    received_at: float = field(repr=False)
    in_service_area: bool


@dataclass(frozen=True, slots=True)
class LiveVehicleSpec:
    """A vehicle with the facts a re-optimization needs about it (ADR-050).

    Attributes:
        vehicle: The vehicle as a daily solve gives it, its driver None when it has none.
        state: Its state.
        stops: Its current route's stops in planned order, reported ones included; empty when it
            has no route.
        latest_position: Its latest position, or None.
        previous_position: The position before the latest, or None.
        position_location: The latest position's location in the matrices, the source row the core
            added for it; None exactly when there is no latest position.
    """

    vehicle: VehicleSpec
    state: VehicleState
    stops: tuple[StopFact, ...] = ()
    latest_position: VehiclePosition | None = None
    previous_position: VehiclePosition | None = None
    position_location: int | None = None


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
        - Each order's drop penalty is K x (6 - priority), and K comes from L, from T (the largest
          finite matrix entry at the highest factor, unbuffered and rounded up) and from the fixed
          vehicle cost F (BR-54, BR-126). L is the larger of the longest shift and the longest a
          door stop can be served outside its window within a usable vehicle's rostered shift
          (ADR-100).

        The scoring inputs have no defaults, so the study and the product cannot score the same
        routes differently by leaving one out (BR-58).

        Args:
            orders: Every order of the date.
            depots: The depots the vehicles start and end at.
            vehicles: The date's vehicles.
            drivers: The date's drivers.
            durations: Base durations in seconds between locations: 0 from a location to itself,
                and NaN for no road.
            distances: Metres between locations: 0 from a location to itself, and NaN exactly
                where durations are.
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
        vehicle_struct = _vehicles(vehicles, depots, len(drivers), duration_matrix)
        driver_struct = _drivers(drivers)
        order_struct = _orders(orders, vehicle_struct, duration_matrix, size)
        every_order = np.ones(len(order_struct), dtype=np.bool_)
        penalties = PenaltyScale.for_date(
            longest_shift=_longest_shift(driver_struct),
            longest_door_outside_window=_longest_door_outside_window(
                order_struct,
                vehicle_struct,
                driver_struct,
                every_order,
                driver_struct.shift_start[_rostered(vehicle_struct)],
            ),
            largest_travel=_largest_travel(duration_matrix, travel),
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

    @staticmethod
    def reoptimization(
        *,
        now: float,
        orders: Sequence[OrderSpec],
        depots: Sequence[DepotSpec],
        vehicles: Sequence[LiveVehicleSpec],
        drivers: Sequence[DriverSpec],
        put_back: frozenset[int],
        durations: npt.ArrayLike,
        distances: npt.ArrayLike,
        travel: TravelTimeModel,
        day_length: float,
        fixed_vehicle_cost: float,
        door_window_rate: float,
        lateness_rate: float,
        moved_stop_cost: float,
        breaks_reserved: bool,
    ) -> Problem:
        """Builds the problem of a re-optimization from the facts of a live day (ADR-050).

        It applies ADR-050's six conventions, each written into the `Problem` once (ADR-105):

        1. **Fixes what happened** (BR-10, BR-91). A stop reported delivered, failed or arrived is
           locked: its report serves its order, and it stays out of the search. A vehicle's
           reported stops count as done in the order they were reported, by their first report's
           time. When the last of them is only *arrived*, it is in progress.
        2. **Places each vehicle** (BR-24, BR-51, BR-79, BR-80). Its route starts at its stop in
           progress, at the later of now and that stop's arrival plus its service; otherwise at
           its usable position, now; otherwise at its last reported stop, now; otherwise at its
           start depot, no earlier than now, its driver's shift start and the depot's opening.
           Every vehicle whose latest position is not usable is named for the preview (BR-81).
        3. **Decides where each stop may go** (BR-106, BR-108, BR-117). A vehicle that is out
           (unavailable, without a driver, out of shift or finished) takes no stop; one of them
           that made a stop is withdrawn (FR-267), and its route ends at its last reported stop
           (BR-52). A stop not yet reported on an active vehicle stays on it, and so does a failed
           stop put back. A stop on a vehicle that is out is an orphan, allowed on any active
           vehicle; so is every order on no route: new, dropped or moved to the date.
        4. **Relaxes windows** (BR-107): every stop with a hard window may be served past its
           end, which the validator flags and the objective charges at the lateness rate. Its
           start stays hard, and the vehicle's latest return stays the hard limit.
        5. **Charges for instability** (BR-115, FR-37, FR-256). A stop that stays on its vehicle
           pays to leave it: the moved-stop cost is added to its drop penalty, and `cost` reports
           it as `moved_stops`. A stop that stays but cannot be routed there, because it lost its
           location or its vehicle has no road to it and back, pays nothing for leaving (ADR-105).
           Each orphan pays the cost as a constant, `orphaned_stops`.
        6. **Builds the warm start** (ADR-044, FR-280): each active vehicle's stops not yet
           reported, in planned order, without the orphans and the orders that cannot be routed.

        K also covers the longest a stop can be late within a shift (ADR-105), over every order
        not locked. As in a daily solve, the scoring inputs have no defaults (BR-58).

        Args:
            now: When the core gathered the facts, in seconds since the date's local midnight.
            orders: Every order of the date: on the current routes, reported or not, and the new,
                dropped, put-back and moved ones. None may carry a pin, which applies only before
                a solve (FR-91).
            depots: The depots the vehicles start and end at.
            vehicles: Every vehicle of the plan, with its state, its current route and its
                positions.
            drivers: The date's drivers.
            put_back: The orders whose failed stop a dispatcher put back for today (FR-75).
            durations: Base durations in seconds, 0 from a location to itself and NaN for no
                road, with a source row for each vehicle's latest position.
            distances: Metres, 0 from a location to itself and NaN exactly where durations are.
            travel: The day's buckets, factors and buffer.
            day_length: The date's length in seconds (ADR-088).
            fixed_vehicle_cost: Seconds per vehicle used, 0 when disabled (FR-21).
            door_window_rate: The door-window rate (BR-120).
            lateness_rate: Seconds per second served past a hard window's end (BR-115).
            moved_stop_cost: Seconds per stop that leaves its vehicle (BR-115).
            breaks_reserved: Whether routes reserve time for breaks (FR-81, BR-105).

        Returns:
            The problem, its re-optimization context holding the conventions and the warm start.

        Raises:
            InvalidProblemError: If an input is malformed or contradicts another, such as a
                report timed after now or a withdrawn vehicle that made no stop.
        """
        duration_matrix = require_matrix("durations", durations)
        distance_matrix = require_matrix("distances", distances)
        require_same_roads(duration_matrix, distance_matrix)
        size = duration_matrix.shape[0]
        now = require_float("now", now)
        day_length = require_float("day_length", day_length)
        if not (math.isfinite(now) and 0 <= now <= day_length):
            raise InvalidProblemError(f"now must lie on the date, 0 to {day_length}: {now}")
        for index, depot in enumerate(depots):
            _require_depot(index, depot, size)
        driver_struct = _drivers(drivers)
        for index, order in enumerate(orders):
            _require_order_location(index, order, size)
            if order.pin is not None:
                raise InvalidProblemError(
                    f"orders[{index}] is pinned, but a pin applies only before a solve (FR-91)"
                )
        vehicles = [
            _require_live_vehicle(index, live, now, orders, len(depots), len(drivers), size)
            for index, live in enumerate(vehicles)
        ]
        _require_one_stop_each(vehicles)
        _require_put_back(put_back, vehicles, len(orders))
        locations = _ints("orders.location", [_order_location(order) for order in orders])
        service = _floats("orders.service_s", [order.service_s for order in orders])
        placements = [
            _place(live, now, locations, service, depots, driver_struct) for live in vehicles
        ]
        vehicle_struct = _placed_vehicles(vehicles, depots, placements, duration_matrix)
        order_struct = _orders(orders, vehicle_struct, duration_matrix, size)
        locked, stays_on, orphaned = _where_stops_may_go(
            vehicles, put_back, vehicle_struct, len(orders)
        )
        open_orders = ~locked
        start_time = _floats("start_time", [placement.start_time for placement in placements])
        penalties = PenaltyScale.for_date(
            longest_shift=_longest_shift(driver_struct),
            longest_door_outside_window=_longest_door_outside_window(
                order_struct,
                vehicle_struct,
                driver_struct,
                open_orders,
                start_time[np.array(vehicle_struct.usable(), dtype=np.int64)],
            ),
            largest_travel=_largest_travel(duration_matrix, travel),
            fixed_vehicle_cost=fixed_vehicle_cost,
            door_window_rate=door_window_rate,
            longest_late=_longest_late(order_struct, vehicle_struct, driver_struct, open_orders),
            lateness_rate=lateness_rate,
            moved_stop_cost=moved_stop_cost,
        )
        servable = servable_where_they_may_go(
            stays_on, order_struct, vehicle_struct, duration_matrix
        )
        # Step 5. A stop that stays but that its vehicle cannot serve is dropped whatever the
        # solver does, so a charge could only shift every solution's score by the same amount, as
        # the orphans' constant does, without that constant's term to show it (ADR-105).
        pays_to_leave = (stays_on != NO_INDEX) & servable
        drop_penalties = [
            penalties.drop_penalty(int(priority))
            + (penalties.moved_stop_cost if pays_to_leave[order] else 0.0)
            for order, priority in enumerate(order_struct.priority)
        ]
        context = ReoptContext(
            now=now,
            start_time=start_time,
            locked=locked,
            stays_on=stays_on,
            orphaned=orphaned,
            pays_to_leave=pays_to_leave,
            no_usable_position=frozenset(
                vehicle
                for vehicle, placement in enumerate(placements)
                if not placement.usable_position
            ),
            warm_start=_warm_start(vehicles, vehicle_struct, servable, locked, stays_on),
        )
        return Problem(
            orders=order_struct,
            vehicles=vehicle_struct,
            drivers=driver_struct,
            durations=duration_matrix,
            distances=distance_matrix,
            travel=travel,
            penalties=penalties,
            drop_penalties=_floats("drop_penalties", drop_penalties),
            day_length=day_length,
            breaks_reserved=breaks_reserved,
            reoptimization=context,
        )


@dataclass(frozen=True, slots=True)
class _Placement:
    """Where one vehicle's remaining route starts and ends, and the state that may keep it out."""

    start_location: int
    start_time: float
    end_location: int
    end_closes: float
    state: VehicleExclusion | None
    driver: int
    usable_position: bool


_OUT_BEFORE_A_STOP: Final = {
    VehicleState.UNAVAILABLE: VehicleExclusion.UNAVAILABLE,
    VehicleState.NO_DRIVER: VehicleExclusion.NO_DRIVER,
    VehicleState.OUT_OF_SHIFT: VehicleExclusion.OUT_OF_SHIFT,
}
"""The states that put a vehicle out, as its exclusion while it has made no stop (BR-106)."""


def _place(
    live: LiveVehicleSpec,
    now: float,
    locations: npt.NDArray[np.int64],
    service: npt.NDArray[np.float64],
    depots: Sequence[DepotSpec],
    drivers: Drivers,
) -> _Placement:
    # Steps 1 and 2: the reported prefix, in the order reported (BR-91), places the vehicle.
    spec = live.vehicle
    reported = sorted(
        (
            (position, stop)
            for position, stop in enumerate(live.stops)
            if stop.reported_at is not None
        ),
        key=lambda item: (item[1].reported_at, item[0]),
    )
    last = reported[-1][1] if reported else None
    driver = NO_INDEX if spec.driver is None else spec.driver
    usable_position = _position_usable(live, now)
    if last is not None and last.report is Report.ARRIVED and last.reported_at is not None:
        start = int(locations[last.order])
        start_time = max(now, last.reported_at + float(service[last.order]))
    elif usable_position and live.position_location is not None:
        start, start_time = live.position_location, now
    elif last is not None:
        start, start_time = int(locations[last.order]), now
    else:
        depot = depots[spec.start_depot]
        start = _location(depot)
        floors = [now, -math.inf if depot.opens is None else depot.opens]
        if driver != NO_INDEX:
            floors.append(float(drivers.shift_start[driver]))
        start_time = max(floors)
    state = _state_exclusion(live.state, made_stops=last is not None)
    end_depot = depots[spec.end_depot]
    if state is VehicleExclusion.WITHDRAWN and last is not None:
        # BR-52: the route ends where the vehicle last served; that is not a depot, so no hours.
        end, end_closes = int(locations[last.order]), math.inf
    else:
        end = _location(end_depot)
        end_closes = math.inf if end_depot.closes is None else end_depot.closes
    return _Placement(
        start_location=start,
        start_time=start_time,
        end_location=end,
        end_closes=end_closes,
        state=state,
        driver=driver,
        usable_position=usable_position,
    )


def _state_exclusion(state: VehicleState, *, made_stops: bool) -> VehicleExclusion | None:
    match state:
        case VehicleState.ACTIVE:
            return None
        case VehicleState.FINISHED:
            return VehicleExclusion.FINISHED
        case VehicleState.WITHDRAWN:
            return VehicleExclusion.WITHDRAWN
        case VehicleState.UNAVAILABLE | VehicleState.NO_DRIVER | VehicleState.OUT_OF_SHIFT:
            # FR-267: left out while it still has stops it made, the vehicle is withdrawn.
            return VehicleExclusion.WITHDRAWN if made_stops else _OUT_BEFORE_A_STOP[state]


def _position_usable(live: LiveVehicleSpec, now: float) -> bool:
    # BR-80: no older than 5 minutes, inside the service area, and no faster than 200 km/h from
    # the position before it. With no position before it, there is no speed to judge.
    latest, previous = live.latest_position, live.previous_position
    if latest is None or not latest.in_service_area:
        return False
    if now - latest.received_at > POSITION_MAX_AGE_S + LIMIT_TOLERANCE:
        return False
    if previous is None:
        return True
    # Compared as times, so the tolerance stays in seconds (ADR-098), and two positions received
    # at the same moment are usable only if they are the same place.
    needed = _metres_between(previous, latest) / POSITION_MAX_SPEED_M_S
    return needed <= latest.received_at - previous.received_at + LIMIT_TOLERANCE


def _metres_between(first: VehiclePosition, second: VehiclePosition) -> float:
    # The haversine formula: the great-circle distance, which a straight drive can only exceed.
    lat1, lat2 = math.radians(first.latitude), math.radians(second.latitude)
    half_lat = (lat2 - lat1) / 2
    half_lon = math.radians(second.longitude - first.longitude) / 2
    a = math.sin(half_lat) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(half_lon) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(min(1.0, math.sqrt(a)))


def _placed_vehicles(
    vehicles: Sequence[LiveVehicleSpec],
    depots: Sequence[DepotSpec],
    placements: list[_Placement],
    durations: npt.NDArray[np.float64],
) -> Vehicles:
    start_location = _ints("vehicles.start_location", [p.start_location for p in placements])
    end_location = _ints("vehicles.end_location", [p.end_location for p in placements])
    unconnected = depots_unconnected(start_location, end_location, durations)

    def exclusion(vehicle: int) -> VehicleExclusion | None:
        # A vehicle's state comes first: a vehicle that is out is out, wherever its depot lies.
        state = placements[vehicle].state
        if state is not None:
            return state
        if NO_INDEX in (start_location[vehicle], end_location[vehicle]):
            return VehicleExclusion.DEPOT_WITHOUT_LOCATION
        if unconnected[vehicle]:
            return VehicleExclusion.NO_ROAD_BETWEEN_DEPOTS
        return None

    specs = [live.vehicle for live in vehicles]
    return Vehicles(
        weight_capacity_kg=_floats(
            "vehicles.weight_capacity_kg", [v.weight_capacity_kg for v in specs]
        ),
        volume_capacity_l=_floats(
            "vehicles.volume_capacity_l",
            [math.inf if v.volume_capacity_l is None else v.volume_capacity_l for v in specs],
        ),
        start_location=start_location,
        end_location=end_location,
        start_depot_opens=_floats(
            "vehicles.start_depot_opens",
            [_opens(depots[v.start_depot]) for v in specs],
        ),
        end_depot_closes=_floats("vehicles.end_depot_closes", [p.end_closes for p in placements]),
        driver=_ints("vehicles.driver", [p.driver for p in placements]),
        exclusion=tuple(exclusion(vehicle) for vehicle in range(len(placements))),
    )


def _where_stops_may_go(
    vehicles: Sequence[LiveVehicleSpec],
    put_back: frozenset[int],
    placed: Vehicles,
    order_count: int,
) -> tuple[npt.NDArray[np.bool_], npt.NDArray[np.int64], npt.NDArray[np.bool_]]:
    # Step 3. An order on no route is new to the re-optimization, and may go to any active vehicle.
    # A vehicle that can drive no route, for its state or for want of a road, takes no stop, so its
    # stops are orphans (BR-106).
    locked = np.zeros(order_count, dtype=np.bool_)
    stays_on = np.full(order_count, NO_INDEX, dtype=np.int64)
    orphaned = np.zeros(order_count, dtype=np.bool_)
    for vehicle, live in enumerate(vehicles):
        for stop in live.stops:
            remaining = stop.report is None or (
                stop.report is Report.FAILED and stop.order in put_back
            )
            if not remaining:
                locked[stop.order] = True
            elif placed.is_usable(vehicle):
                stays_on[stop.order] = vehicle
            else:
                orphaned[stop.order] = True
    return locked, stays_on, orphaned


def _warm_start(
    vehicles: Sequence[LiveVehicleSpec],
    placed: Vehicles,
    servable: npt.NDArray[np.bool_],
    locked: npt.NDArray[np.bool_],
    stays_on: npt.NDArray[np.int64],
) -> Solution:
    # Step 6: the current remaining sequences, orphans left out. A failed stop put back has no
    # place in its sequence yet, so it starts dropped, and so does a stop its vehicle cannot serve.
    routes = tuple(
        Route(
            vehicle=vehicle,
            driver=int(placed.driver[vehicle]),
            start=int(placed.start_location[vehicle]),
            end=int(placed.end_location[vehicle]),
            stops=tuple(
                stop.order
                for stop in vehicles[vehicle].stops
                if stop.report is None and stays_on[stop.order] == vehicle and servable[stop.order]
            ),
        )
        for vehicle in placed.usable()
    )
    served = {order for route in routes for order in route.stops}
    dropped = frozenset(
        order for order in range(len(locked)) if not locked[order] and order not in served
    )
    return Solution(routes=routes, dropped=dropped)


def _require_live_vehicle(
    index: int,
    live: LiveVehicleSpec,
    now: float,
    orders: Sequence[OrderSpec],
    depot_count: int,
    driver_count: int,
    size: int,
) -> LiveVehicleSpec:
    # Returns the vehicle with its stops' orders as plain ints, so every later step can use them
    # as indices and as `Route` stops, whatever integer type the caller gave.
    if not isinstance(live.state, VehicleState):
        raise InvalidProblemError(f"vehicles[{index}].state is not a VehicleState: {live.state!r}")
    _require_vehicle_spec(index, live.vehicle, depot_count, driver_count, driver_required=False)
    has_driver = live.vehicle.driver is not None
    if live.state is VehicleState.ACTIVE and not has_driver:
        raise InvalidProblemError(f"vehicles[{index}] is active, so it needs a driver")
    if live.state is VehicleState.NO_DRIVER and has_driver:
        raise InvalidProblemError(f"vehicles[{index}] has no driver by its state, but names one")
    stops = tuple(
        _require_stop_fact(f"vehicles[{index}].stops[{position}]", stop, now, orders)
        for position, stop in enumerate(live.stops)
    )
    if live.state is VehicleState.WITHDRAWN and not any(stop.report for stop in stops):
        raise InvalidProblemError(
            f"vehicles[{index}] is withdrawn, but a withdrawn vehicle has made a stop (FR-267)"
        )
    _require_positions(index, live, now, size)
    return replace(live, stops=stops)


def _require_stop_fact(
    name: str, stop: StopFact, now: float, orders: Sequence[OrderSpec]
) -> StopFact:
    order = require_int(f"{name}.order", stop.order)
    if not 0 <= order < len(orders):
        raise InvalidProblemError(f"{name}.order {order} is not one of {len(orders)}")
    if stop.report is not None and not isinstance(stop.report, Report):
        raise InvalidProblemError(f"{name}.report is not a Report: {stop.report!r}")
    if (stop.report is None) != (stop.reported_at is None):
        raise InvalidProblemError(f"{name} has a report exactly when it has a report time")
    if stop.reported_at is None:
        return replace(stop, order=order)
    reported_at = require_float(f"{name}.reported_at", stop.reported_at)
    if not (math.isfinite(reported_at) and reported_at <= now):
        raise InvalidProblemError(
            f"{name}.reported_at must be finite and no later than now, {now}: {reported_at}"
        )
    if orders[order].location is None:
        raise InvalidProblemError(
            f"{name} is reported, but orders[{order}] has no location to place the vehicle by"
        )
    return replace(stop, order=order, reported_at=reported_at)


def _require_positions(index: int, live: LiveVehicleSpec, now: float, size: int) -> None:
    latest, previous = live.latest_position, live.previous_position
    if (latest is None) != (live.position_location is None):
        raise InvalidProblemError(
            f"vehicles[{index}] has a position location exactly when it has a latest position"
        )
    if latest is None:
        if previous is not None:
            raise InvalidProblemError(f"vehicles[{index}] has a previous position but no latest")
        return
    location = require_int(f"vehicles[{index}].position_location", live.position_location)
    if not 0 <= location < size:
        raise InvalidProblemError(
            f"vehicles[{index}].position_location {location} is not one of {size} locations"
        )
    for which, position in (("latest", latest), ("previous", previous)):
        if position is not None:
            _require_position(f"vehicles[{index}].{which}_position", position, now)
    if previous is not None and latest is not None and previous.received_at > latest.received_at:
        raise InvalidProblemError(
            f"vehicles[{index}].previous_position was received after the latest one"
        )


def _require_position(name: str, position: VehiclePosition, now: float) -> None:
    # NFR-19: a driver's position is personal data, so each error names the field, never its value.
    latitude = require_float(f"{name}.latitude", position.latitude, personal=True)
    longitude = require_float(f"{name}.longitude", position.longitude, personal=True)
    received_at = require_float(f"{name}.received_at", position.received_at, personal=True)
    if not (math.isfinite(latitude) and abs(latitude) <= _LATITUDE_LIMIT):
        raise InvalidProblemError(f"{name}.latitude must lie from -90 to 90")
    if not (math.isfinite(longitude) and abs(longitude) <= _LONGITUDE_LIMIT):
        raise InvalidProblemError(f"{name}.longitude must lie from -180 to 180")
    if not (math.isfinite(received_at) and received_at <= now):
        raise InvalidProblemError(f"{name}.received_at must be finite and no later than now")
    if not isinstance(position.in_service_area, bool):
        raise InvalidProblemError(f"{name}.in_service_area must be a bool")


def _require_one_stop_each(vehicles: Sequence[LiveVehicleSpec]) -> None:
    on: dict[int, int] = {}
    for vehicle, live in enumerate(vehicles):
        for stop in live.stops:
            if stop.order in on:
                raise InvalidProblemError(
                    f"orders[{stop.order}] has stops on vehicles {on[stop.order]} and {vehicle}; "
                    "an order has one current stop"
                )
            on[stop.order] = vehicle


def _require_put_back(
    put_back: frozenset[int], vehicles: Sequence[LiveVehicleSpec], order_count: int
) -> None:
    failed = {
        stop.order for live in vehicles for stop in live.stops if stop.report is Report.FAILED
    }
    for order in sorted(require_int("put_back[]", order) for order in put_back):
        if not 0 <= order < order_count:
            raise InvalidProblemError(f"put_back holds order {order}, not one of {order_count}")
        if order not in failed:
            raise InvalidProblemError(
                f"put_back holds order {order}, whose stop is not failed; a dropped order on no "
                "route is new to a re-optimization already (BR-117)"
            )


def _vehicles(
    vehicles: Sequence[VehicleSpec],
    depots: Sequence[DepotSpec],
    driver_count: int,
    durations: npt.NDArray[np.float64],
) -> Vehicles:
    for index, depot in enumerate(depots):
        _require_depot(index, depot, durations.shape[0])
    rostered = [
        _require_vehicle_spec(index, vehicle, len(depots), driver_count, driver_required=True)
        for index, vehicle in enumerate(vehicles)
    ]
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
            "vehicles.start_depot_opens", [_opens(depot) for depot in starts]
        ),
        end_depot_closes=_floats(
            "vehicles.end_depot_closes",
            [math.inf if depot.closes is None else depot.closes for depot in ends],
        ),
        driver=_ints("vehicles.driver", rostered),
        exclusion=tuple(exclusion(vehicle) for vehicle in range(len(vehicles))),
    )


def _require_vehicle_spec(
    index: int,
    vehicle: VehicleSpec,
    depot_count: int,
    driver_count: int,
    *,
    driver_required: bool,
) -> int:
    for field_name in ("start_depot", "end_depot"):
        depot_index = getattr(vehicle, field_name)
        if not 0 <= require_int(f"vehicles[{index}].{field_name}", depot_index) < depot_count:
            raise InvalidProblemError(
                f"vehicles[{index}].{field_name} {depot_index} is not one of {depot_count} depots"
            )
    driver = NO_INDEX
    if vehicle.driver is not None or driver_required:
        # Checked here as well as in Problem, because K reads each rostered driver's shift first.
        driver = require_int(f"vehicles.driver[{index}]", vehicle.driver)
        if not 0 <= driver < driver_count:
            raise InvalidProblemError(
                f"vehicles.driver[{index}] is {driver}, not one of {driver_count}"
            )
    volume = vehicle.volume_capacity_l
    if volume is not None:
        volume = require_float(f"vehicles.volume_capacity_l[{index}]", volume)
    if volume is not None and not math.isfinite(volume):
        raise InvalidProblemError(
            f"vehicles.volume_capacity_l[{index}] must be finite; give None for no volume "
            f"limit: {volume}"
        )
    return driver


def _require_depot(index: int, depot: DepotSpec, size: int) -> None:
    location = depot.location
    if location is not None and not 0 <= require_int(f"depots[{index}].location", location) < size:
        raise InvalidProblemError(
            f"depots[{index}].location {depot.location} is not one of {size} locations"
        )
    opens = (
        -math.inf if depot.opens is None else require_float(f"depots[{index}].opens", depot.opens)
    )
    closes = (
        math.inf if depot.closes is None else require_float(f"depots[{index}].closes", depot.closes)
    )
    if math.isnan(opens) or opens == math.inf:
        raise InvalidProblemError(f"depots[{index}].opens must be finite or -inf: {opens}")
    if math.isnan(closes) or closes == -math.inf:
        raise InvalidProblemError(f"depots[{index}].closes must be finite or +inf: {closes}")
    if opens > closes:
        raise InvalidProblemError(f"depots[{index}].opens {opens} is after its closes {closes}")


def _location(depot: DepotSpec) -> int:
    return NO_INDEX if depot.location is None else depot.location


def _opens(depot: DepotSpec) -> float:
    return -math.inf if depot.opens is None else depot.opens


def _drivers(drivers: Sequence[DriverSpec]) -> Drivers:
    return Drivers(
        shift_start=_floats("drivers.shift_start", [d.shift_start for d in drivers]),
        shift_end=_floats("drivers.shift_end", [d.shift_end for d in drivers]),
    )


def _require_order_location(index: int, order: OrderSpec, size: int) -> None:
    given = order.location
    if given is not None and not 0 <= require_int(f"orders[{index}].location", given) < size:
        raise InvalidProblemError(
            f"orders[{index}].location {order.location} is not one of {size} locations"
        )


def _orders(
    orders: Sequence[OrderSpec],
    vehicles: Vehicles,
    durations: npt.NDArray[np.float64],
    size: int,
) -> Orders:
    for index, order in enumerate(orders):
        _require_order_location(index, order, size)
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


def _longest_shift(drivers: Drivers) -> float:
    return max((drivers.shift_length(driver) for driver in range(len(drivers))), default=0.0)


def _largest_travel(durations: npt.NDArray[np.float64], travel: TravelTimeModel) -> float:
    finite = durations[~np.isnan(durations)]
    return travel.largest_unbuffered(float(finite.max(initial=0.0)))


def _rostered(vehicles: Vehicles) -> npt.NDArray[np.int64]:
    """The rostered drivers of the usable vehicles, in index order; each has one."""
    return vehicles.driver[np.array(vehicles.usable(), dtype=np.int64)]


def _longest_door_outside_window(
    orders: Orders,
    vehicles: Vehicles,
    drivers: Drivers,
    considered: npt.NDArray[np.bool_],
    earliest_starts: npt.NDArray[np.float64],
) -> float:
    # A door stop is served on arrival, no earlier than its vehicle's route can leave, and BR-119
    # refuses a start after the shift end. So over every door order and usable vehicle,
    # max(0, w0 - s0, s1 - w1) bounds the time outside the window, s0 being that earliest start:
    # the shift start in a daily solve, where no route leaves before it, and in a re-optimization
    # the start time, which is now for a vehicle already on the road, even before its shift
    # (ADR-105). Each term depends on the order alone and the vehicle alone, so the largest over
    # all pairs is taken from the extremes, in one pass over each. It is not capped at the day's
    # length: with every window on the date it is at most that already, because every shift lies
    # on the date (FR-190), and a cap would understate it for a window off the date (ADR-100).
    door = orders.leave_at_door & considered
    rostered = _rostered(vehicles)
    if not door.any() or rostered.size == 0:
        return 0.0
    early = orders.window_start[door].max() - earliest_starts.min()
    late = drivers.shift_end[rostered].max() - orders.window_end[door].min()
    return float(max(early, late, 0.0))


def _longest_late(
    orders: Orders, vehicles: Vehicles, drivers: Drivers, considered: npt.NDArray[np.bool_]
) -> float:
    # BR-107 keeps a late stop within its shift, and a stop's service starts before its route
    # returns, so s1 - w1 bounds its lateness (ADR-105). Taken from the extremes, as for doors.
    hard = ~orders.leave_at_door & considered
    rostered = _rostered(vehicles)
    if not hard.any() or rostered.size == 0:
        return 0.0
    late = drivers.shift_end[rostered].max() - orders.window_end[hard].min()
    return float(max(late, 0.0))


def _floats(name: str, values: Sequence[float]) -> npt.NDArray[np.float64]:
    for index, value in enumerate(values):
        require_float(f"{name}[{index}]", value)
    return converted(name, values, np.float64)


def _ints(name: str, values: Sequence[int]) -> npt.NDArray[np.int64]:
    for index, value in enumerate(values):
        require_int(f"{name}[{index}]", value)
    return converted(name, values, np.int64)


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
