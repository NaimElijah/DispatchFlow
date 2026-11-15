"""The problem: everything one solve needs, and the single definition of what a solution means.

`Problem` holds every order of the delivery date, including those kept out of the search with
their reason, the vehicles, the drivers, the base matrices, the travel-time model and the penalty
scale (DL §4.9, TS §7.1). Its methods are the validator, the objective and the drop reasons, and
every solver, edit check, publishing re-check and research score goes through them (BR-42).

Every time is in seconds since the delivery date's local midnight in `Asia/Jerusalem` (ADR-088).
Every array is a read-only float64 copy, or int64 for indices, where -1 means "none".

`Problem`'s methods delegate to `feasibility`, `objective` and `drops`, which read it as a
`ProblemView` and never import this module (ADR-113). Its parts live in `problem_parts`, and this
module re-exports them.
"""

import math
from dataclasses import dataclass

import numpy as np
import numpy.typing as npt

from dispatchflow_solver.domain import drops, feasibility, objective
from dispatchflow_solver.domain._arrays import (
    converted,
    refuse_first,
    require_indices,
    require_lengths,
    set_column,
)
from dispatchflow_solver.domain.drops import DropReason
from dispatchflow_solver.domain.errors import InvalidProblemError
from dispatchflow_solver.domain.exclusions import (
    REOPTIMIZATION_EXCLUSIONS,
    OrderExclusion,
    VehicleExclusion,
)
from dispatchflow_solver.domain.feasibility import Timings, ValidationReport, require_inside
from dispatchflow_solver.domain.indices import NO_INDEX, require_float, require_int
from dispatchflow_solver.domain.objective import Cost
from dispatchflow_solver.domain.penalty import PenaltyScale
from dispatchflow_solver.domain.problem_parts import (
    MATRIX_ENTRY_MAX,
    Drivers,
    Orders,
    Pin,
    ReoptContext,
    Vehicles,
)
from dispatchflow_solver.domain.solution import InvalidSolutionError, Solution
from dispatchflow_solver.domain.travel_time import TravelTimeModel

__all__ = [
    "MATRIX_ENTRY_MAX",
    "Drivers",
    "Orders",
    "Pin",
    "Problem",
    "ReoptContext",
    "Vehicles",
    "depots_unconnected",
    "require_matrix",
    "require_same_roads",
    "servable_where_they_may_go",
    "unreachable_locations",
]


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
        durations: Base durations in seconds between locations: 0 from a location to itself,
            and NaN where the road network has no route.
        distances: Metres between locations: 0 from a location to itself, and NaN exactly where
            durations are.
        travel: The travel-time model, the only place factors and the buffer apply.
        penalties: The drop unit and the rates the objective charges.
        drop_penalties: What dropping each order costs a solver, which `cost` reads: K x
            (6 - priority) (BR-54), plus the moved-stop cost for a stop that pays to leave its
            vehicle in a re-optimization (BR-115). `cost` reports the two apart (ADR-113).
        day_length: The date's length in seconds: 23, 24 or 25 hours (ADR-088). The timings and
            the drop facts may pass it (ADR-113).
        breaks_reserved: Whether routes reserve time for breaks (FR-81, BR-105); a bool.
        removed_by_dispatcher: Orders a dispatcher took off the current routes (FR-146), so
            `explain_drop` can give that reason. The validator does not enforce it: a new solve
            may serve them again (BR-95).
        reoptimization: The re-optimization's context, or None for a daily solve.

    Raises:
        InvalidProblemError: If the parts contradict each other, an index points past its array,
            a matrix has an entry other than 0 on its diagonal, `breaks_reserved` is no bool, a
            shift lies outside the date, an exclusion or a pays-to-leave flag disagrees with the
            matrices, a stop that pays to leave has a drop penalty below the moved-stop cost, or a
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
        set_column(self, "problem", "drop_penalties", np.float64)
        object.__setattr__(self, "removed_by_dispatcher", frozenset(self.removed_by_dispatcher))
        day_length = require_float("day_length", self.day_length)
        if not (math.isfinite(day_length) and day_length > 0):
            raise InvalidProblemError(f"day_length must be positive and finite: {day_length}")
        object.__setattr__(self, "day_length", day_length)
        if not isinstance(self.breaks_reserved, bool | np.bool_):
            raise InvalidProblemError(f"breaks_reserved must be a bool: {self.breaks_reserved!r}")
        object.__setattr__(self, "breaks_reserved", bool(self.breaks_reserved))
        size = durations.shape[0]
        require_indices("orders.location", self.orders.location, size, allow_none=True)
        require_indices(
            "vehicles.start_location", self.vehicles.start_location, size, allow_none=True
        )
        require_indices("vehicles.end_location", self.vehicles.end_location, size, allow_none=True)
        require_indices("vehicles.driver", self.vehicles.driver, len(self.drivers), allow_none=True)
        require_indices(
            "orders.pinned_vehicle", self.orders.pinned_vehicle, len(self.vehicles), allow_none=True
        )
        require_indices(
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
            Each term of the objective and their total; `Cost` says what each term counts.

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
        require_lengths("reoptimization", len(vehicles), start_time=context.start_time)
        require_lengths("reoptimization", len(self.orders), locked=context.locked)
        refuse_first(
            context.start_time < context.now,
            lambda i: (
                f"reoptimization.start_time[{i}] is before now, {context.now}: "
                f"{context.start_time[i]}"
            ),
        )
        require_indices("reoptimization.stays_on", context.stays_on, len(vehicles), allow_none=True)
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
        self._require_pays_to_leave(context)
        try:
            require_inside(self, context.warm_start)
        except InvalidSolutionError as error:
            raise InvalidProblemError(f"reoptimization.warm_start: {error}") from error

    def _require_pays_to_leave(self, context: ReoptContext) -> None:
        # The flag is ADR-105's reading of BR-115, so it must say what the roads say: a stop pays
        # to leave exactly when it stays on a vehicle that can serve it. Its drop penalty then
        # holds the moved-stop cost, which `cost` takes out of the drops term again.
        servable = servable_where_they_may_go(
            context.stays_on, self.orders, self.vehicles, self.durations
        )
        expected = (context.stays_on != NO_INDEX) & servable
        for order in np.flatnonzero(context.pays_to_leave != expected):
            if expected[order]:
                raise InvalidProblemError(
                    f"reoptimization.pays_to_leave[{order}] is False, but the stop stays on "
                    f"vehicle {context.stays_on[order]}, which can serve it"
                )
            raise InvalidProblemError(
                f"reoptimization.pays_to_leave[{order}] is True, but the stop does not stay on a "
                "vehicle that can serve it"
            )
        moved_stop_cost = self.penalties.moved_stop_cost
        refuse_first(
            context.pays_to_leave & (self.drop_penalties < moved_stop_cost),
            lambda i: (
                f"drop_penalties[{i}] is {self.drop_penalties[i]}, below the moved-stop cost "
                f"{moved_stop_cost} its stop pays to leave its vehicle"
            ),
        )

    def _require_drop_penalties(self) -> None:
        penalties = self.drop_penalties
        if len(penalties) != len(self.orders):
            raise InvalidProblemError(
                f"drop_penalties has {len(penalties)} entries for {len(self.orders)} orders"
            )
        refuse_first(
            ~(np.isfinite(penalties) & (penalties >= 0)),
            lambda i: f"drop_penalties[{i}] must be finite and zero or more: {penalties[i]}",
        )

    def _require_shifts_on_the_date(self) -> None:
        # FR-190: a shift lies on its date, so a door stop after the date is also after its shift.
        drivers = self.drivers
        refuse_first(
            drivers.shift_start < 0,
            lambda i: (
                f"drivers.shift_start[{i}] is before the date starts: {drivers.shift_start[i]}"
            ),
        )
        refuse_first(
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
        values: A square matrix of seconds or metres, 0 on the diagonal and NaN where there is
            no road.

    Returns:
        A read-only float64 copy.

    Raises:
        InvalidProblemError: If it is not a square matrix of numbers, an entry on its diagonal is
            not 0, or an entry is negative, infinite or past the int32 range.
    """
    matrix = converted(name, values, np.float64)
    if matrix.ndim != 2 or matrix.shape[0] != matrix.shape[1]:
        raise InvalidProblemError(f"{name} must be a square matrix: shape {matrix.shape}")
    # A NaN here would read as no road from a depot to itself, and exclude every vehicle that
    # starts and ends there.
    diagonal = np.diagonal(matrix)
    refuse_first(
        diagonal != 0,
        lambda i: f"{name}[{i}, {i}] must be 0, from a location to itself: {diagonal[i]}",
    )
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


def servable_where_they_may_go(
    stays_on: npt.NDArray[np.int64],
    orders: Orders,
    vehicles: Vehicles,
    durations: npt.NDArray[np.float64],
) -> npt.NDArray[np.bool_]:
    """Says, per order, whether it can be served at all where a re-optimization lets it go.

    A stop that stays on its vehicle may go only there (BR-106), so it needs a road from that
    vehicle's start to it and on to that vehicle's end, whatever other vehicles reach. Every other
    order needs only to be routable (FR-66).

    Args:
        stays_on: Per order, the vehicle its stop stays on, or -1.
        orders: The orders.
        vehicles: The vehicles, placed as the re-optimization places them (BR-51, BR-52).
        durations: The base durations, NaN for no road.

    Returns:
        True for each order that some allowed vehicle can serve by road.
    """
    servable = np.array([orders.is_routable(order) for order in range(len(orders))], dtype=np.bool_)
    for order in np.flatnonzero(servable & (stays_on != NO_INDEX)):
        vehicle = int(stays_on[order])
        servable[order] = not unreachable_locations(
            orders.location[[order]],
            vehicles.start_location[[vehicle]],
            vehicles.end_location[[vehicle]],
            durations,
        )[0]
    return servable
