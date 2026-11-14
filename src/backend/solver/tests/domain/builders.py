"""Test builders for small daily problems on a line.

Every location sits at a point on a line, and a leg's base duration is the distance between its
two points, in seconds; its metres are ten times that. With the static travel model, every time in
a test is plain arithmetic.
"""

from dataclasses import dataclass, field
from typing import Final

import numpy as np

from dispatchflow_solver.domain.factory import (
    NO_ROAD,
    DepotSpec,
    DriverSpec,
    OrderSpec,
    ProblemFactory,
    VehicleSpec,
)
from dispatchflow_solver.domain.problem import Pin, Problem
from dispatchflow_solver.domain.travel_time import TravelTimeModel

DAY: Final = 86_400.0
METRES_PER_SECOND: Final = 10.0
DEPOT_LOCATION: Final = 0


@dataclass
class DayBuilder:
    """A delivery date under construction. Location 0 is the first depot, at point 0."""

    points: list[float] = field(default_factory=lambda: [0.0])
    cut_off: set[int] = field(default_factory=set)
    orders: list[OrderSpec] = field(default_factory=list)
    depots: list[DepotSpec] = field(default_factory=lambda: [DepotSpec(location=DEPOT_LOCATION)])
    vehicles: list[VehicleSpec] = field(default_factory=list)
    drivers: list[DriverSpec] = field(default_factory=list)
    travel: TravelTimeModel = field(default_factory=TravelTimeModel.static)
    day_length: float = DAY
    fixed_vehicle_cost: float = 0.0
    breaks_reserved: bool = False
    removed_by_dispatcher: set[int] = field(default_factory=set)

    def location(self, at: float, *, reachable: bool = True) -> int:
        self.points.append(at)
        if not reachable:
            self.cut_off.add(len(self.points) - 1)
        return len(self.points) - 1

    def order(
        self,
        at: float | None,
        *,
        weight: float = 1.0,
        volume: float = 1.0,
        service: float = 0.0,
        window: tuple[float, float] = (0.0, DAY),
        priority: int = 3,
        door: bool = False,
        pin: Pin | None = None,
        reachable: bool = True,
    ) -> int:
        location = None if at is None else self.location(at, reachable=reachable)
        self.orders.append(
            OrderSpec(
                location=location,
                weight_kg=weight,
                volume_l=volume,
                service_s=service,
                window_start=window[0],
                window_end=window[1],
                priority=priority,
                leave_at_door=door,
                pin=pin,
            )
        )
        return len(self.orders) - 1

    def depot(
        self, at: float | None, *, opens: float | None = None, closes: float | None = None
    ) -> int:
        location = None if at is None else self.location(at)
        self.depots.append(DepotSpec(location=location, opens=opens, closes=closes))
        return len(self.depots) - 1

    def depot_hours(self, *, opens: float | None = None, closes: float | None = None) -> None:
        self.depots[0] = DepotSpec(location=DEPOT_LOCATION, opens=opens, closes=closes)

    def driver(self, shift: tuple[float, float] = (0.0, DAY)) -> int:
        self.drivers.append(DriverSpec(shift_start=shift[0], shift_end=shift[1]))
        return len(self.drivers) - 1

    def vehicle(
        self,
        *,
        weight: float = 100.0,
        volume: float | None = 100.0,
        shift: tuple[float, float] = (0.0, DAY),
        start_depot: int = 0,
        end_depot: int = 0,
        driver: int | None = None,
    ) -> int:
        rostered = self.driver(shift) if driver is None else driver
        self.vehicles.append(
            VehicleSpec(
                start_depot=start_depot,
                end_depot=end_depot,
                weight_capacity_kg=weight,
                volume_capacity_l=volume,
                driver=rostered,
            )
        )
        return len(self.vehicles) - 1

    def location_of(self, order: int) -> int:
        location = self.orders[order].location
        assert location is not None
        return location

    def build(self) -> Problem:
        points = np.array(self.points)
        durations = np.abs(points[:, None] - points[None, :])
        for location in self.cut_off:
            durations[location, :] = NO_ROAD
            durations[:, location] = NO_ROAD
            durations[location, location] = 0.0
        distances = np.where(durations == NO_ROAD, NO_ROAD, durations * METRES_PER_SECOND)
        return ProblemFactory.daily(
            orders=self.orders,
            depots=self.depots,
            vehicles=self.vehicles,
            drivers=self.drivers,
            durations=durations,
            distances=distances,
            travel=self.travel,
            day_length=self.day_length,
            fixed_vehicle_cost=self.fixed_vehicle_cost,
            breaks_reserved=self.breaks_reserved,
            removed_by_dispatcher=frozenset(self.removed_by_dispatcher),
        )
