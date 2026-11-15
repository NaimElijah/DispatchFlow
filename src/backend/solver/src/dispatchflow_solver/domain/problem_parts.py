"""The parts of a problem: its orders, vehicles, drivers and pins, and a re-optimization's context.

Each part copies its arrays read-only and refuses values no such part can have. `Problem` checks
the parts against each other (DL §4.9). They live apart from `problem`, so the validator, the
objective and the drop reasons can name their types through `ProblemView` without importing
`problem` (ADR-113).
"""

import math
import numbers
from dataclasses import dataclass
from typing import Final

import numpy as np
import numpy.typing as npt

from dispatchflow_solver.domain._arrays import (
    refuse_first,
    require_finite_non_negative,
    require_lengths,
    set_column,
)
from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.exclusions import (
    REOPTIMIZATION_EXCLUSIONS,
    OrderExclusion,
    VehicleExclusion,
)
from dispatchflow_solver.domain.indices import NO_INDEX, in_int64, require_float, require_int
from dispatchflow_solver.domain.penalty import HIGHEST_PRIORITY, LOWEST_PRIORITY
from dispatchflow_solver.domain.solution import Solution

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
            set_column(self, "orders", name, np.int64)
        for name in ("weight_kg", "volume_l", "service_s", "window_start", "window_end"):
            set_column(self, "orders", name, np.float64)
        set_column(self, "orders", "leave_at_door", np.bool_)
        object.__setattr__(self, "exclusion", tuple(self.exclusion))
        require_lengths(
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
            require_finite_non_negative(f"orders.{name}", getattr(self, name))
        refuse_first(
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
        refuse_first(
            np.abs(self.window_start) > MATRIX_ENTRY_MAX,
            lambda i: (
                f"orders.window_start[{i}] must lie within {MATRIX_ENTRY_MAX} seconds of the "
                f"date's start: {self.window_start[i]}"
            ),
        )
        refuse_first(
            np.abs(self.window_end) > MATRIX_ENTRY_MAX,
            lambda i: (
                f"orders.window_end[{i}] must lie within {MATRIX_ENTRY_MAX} seconds of the "
                f"date's start: {self.window_end[i]}"
            ),
        )
        refuse_first(
            (self.priority < HIGHEST_PRIORITY) | (self.priority > LOWEST_PRIORITY),
            lambda i: f"orders.priority[{i}] runs from 1 to 5 (BR-53): {self.priority[i]}",
        )
        refuse_first(
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
            set_column(self, "vehicles", name, np.int64)
        for name in (
            "weight_capacity_kg",
            "volume_capacity_l",
            "start_depot_opens",
            "end_depot_closes",
        ):
            set_column(self, "vehicles", name, np.float64)
        object.__setattr__(self, "exclusion", tuple(self.exclusion))
        require_lengths(
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
        refuse_first(
            ~(np.isfinite(weight) & (weight >= 0)),
            lambda i: (
                f"vehicles.weight_capacity_kg[{i}] must be finite and zero or more: {weight[i]}"
            ),
        )
        volume = self.volume_capacity_l
        refuse_first(
            np.isnan(volume) | (volume < 0),
            lambda i: (
                f"vehicles.volume_capacity_l[{i}] must be zero or more, or +inf for no "
                f"limit: {volume[i]}"
            ),
        )
        refuse_first(
            np.isnan(self.start_depot_opens) | (self.start_depot_opens == math.inf),
            lambda i: (
                f"vehicles.start_depot_opens[{i}] must be finite or -inf: "
                f"{self.start_depot_opens[i]}"
            ),
        )
        refuse_first(
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
        set_column(self, "drivers", "shift_start", np.float64)
        set_column(self, "drivers", "shift_end", np.float64)
        require_lengths("drivers", len(self.shift_start), shift_end=self.shift_end)
        refuse_first(
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
        pays_to_leave: Per order, whether its stop stays on a vehicle that can serve it, so
            leaving that vehicle, by being dropped, costs the moved-stop cost (BR-115, FR-256). A
            stop that stays but cannot be routed there is dropped whatever the solution does, and
            pays nothing for it (ADR-105). `Problem` checks the flag against the roads.
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
    pays_to_leave: npt.NDArray[np.bool_]
    no_usable_position: frozenset[int]
    warm_start: Solution

    def __post_init__(self) -> None:
        """Copies every array read-only and refuses a context that contradicts itself."""
        set_column(self, "reoptimization", "start_time", np.float64)
        set_column(self, "reoptimization", "locked", np.bool_)
        set_column(self, "reoptimization", "stays_on", np.int64)
        set_column(self, "reoptimization", "orphaned", np.bool_)
        set_column(self, "reoptimization", "pays_to_leave", np.bool_)
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
        refuse_first(
            ~np.isfinite(self.start_time),
            lambda i: f"reoptimization.start_time[{i}] must be finite: {self.start_time[i]}",
        )
        require_lengths(
            "reoptimization",
            len(self.locked),
            stays_on=self.stays_on,
            orphaned=self.orphaned,
            pays_to_leave=self.pays_to_leave,
        )
        refuse_first(
            self.locked & ((self.stays_on != NO_INDEX) | self.orphaned),
            lambda i: f"reoptimization.locked[{i}] is set, but the stop may still move or stay",
        )
        refuse_first(
            self.orphaned & (self.stays_on != NO_INDEX),
            lambda i: f"reoptimization.orphaned[{i}] is set, but the stop stays on a vehicle",
        )
