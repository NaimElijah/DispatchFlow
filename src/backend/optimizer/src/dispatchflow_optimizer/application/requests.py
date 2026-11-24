"""What each use case is asked to do: the problem's inputs, with the solver, budget and seed.

The HTTP adapter builds these from the contract, so no transport type reaches a use case (TS §2.1,
rule 4). The inputs are `ProblemFactory`'s keyword arguments, which the use case hands it
unchanged, so the factory alone decides each convention (ADR-050).

Gap until geocoding is built: `OrderSpec` has no input for an order's outside-service-area flag
(FR-320), so the core has nowhere to send it. Geocoding adds that input to the domain, and the
factory then drops such an order as *unreachable location* (FR-66). The core never sets the flag
for a location placed by hand or a missing one.

The rates and the break flag come from the core's configuration, fixed values no one changes on
screen (ADR-029, ADR-121).

Domain gap: the optimizer applies the departure allowance (FR-67) and a handed-over route's
confirmed return (FR-394), and the core sends both as they are, never folded into the shift times
(ADR-050, ADR-121). `ProblemFactory` has no input for either yet: the daily solve adds
`departure_allowance` to `daily` and `reoptimization`, and re-optimization adds a per-driver
`confirmed_return` to `reoptimization`. The core doesn't send its route locks until the domain has
a lock rule (ADR-112, ADR-121).

To Be Implemented.
"""

from dataclasses import dataclass

import numpy.typing as npt

from dispatchflow_optimizer.application.ports import Budget
from dispatchflow_optimizer.domain.factory import (
    DepotSpec,
    DriverSpec,
    LiveVehicleSpec,
    OrderSpec,
    VehicleSpec,
)
from dispatchflow_optimizer.domain.solution import Solution
from dispatchflow_optimizer.domain.travel_time import TravelTimeModel


@dataclass(frozen=True, slots=True, kw_only=True, eq=False)
class DailyInputs:
    """The inputs of `ProblemFactory.daily`, shared by a solve, an evaluation and the baseline.

    Attributes:
        orders: Every order of the date.
        depots: The depots the vehicles start and end at.
        vehicles: The date's vehicles.
        drivers: The date's drivers.
        durations: Base durations in seconds between locations: 0 from a location to itself, and
            NaN for no road.
        distances: Metres between locations: 0 from a location to itself, and NaN exactly where
            durations are.
        travel: The day's buckets, factors and buffer (ADR-048).
        day_length: The date's length in seconds (ADR-088).
        fixed_vehicle_cost: Seconds per vehicle used, 0 when disabled (FR-21).
        door_window_rate: The door-window rate, from the core's configuration (BR-120, ADR-121).
        breaks_reserved: Whether routes reserve time for breaks, from the core's configuration
            (FR-81, BR-105, ADR-121).
        removed_by_dispatcher: Orders a dispatcher took off the current routes (FR-146).

    It has no `departure_allowance` yet, a domain gap the daily solve closes (FR-67, ADR-121).
    """

    orders: tuple[OrderSpec, ...]
    depots: tuple[DepotSpec, ...]
    vehicles: tuple[VehicleSpec, ...]
    drivers: tuple[DriverSpec, ...]
    durations: npt.ArrayLike
    distances: npt.ArrayLike
    travel: TravelTimeModel
    day_length: float
    fixed_vehicle_cost: float
    door_window_rate: float
    breaks_reserved: bool
    removed_by_dispatcher: frozenset[int] = frozenset()


@dataclass(frozen=True, slots=True, kw_only=True, eq=False)
class DailyRequest:
    """A daily solve: the date's inputs, and the solver, budget and seed to solve them with.

    Attributes:
        inputs: The date's inputs.
        solver: The solver's name; the core always sends one, as the registry has no default
            (ADR-004, FR-20).
        budget: What ends the search (ADR-044).
        seed: The seed, so the solve can be replayed (NFR-13, ADR-005).
    """

    inputs: DailyInputs
    solver: str
    budget: Budget
    seed: int


@dataclass(frozen=True, slots=True, kw_only=True, eq=False)
class ReoptimizationRequest:
    """A re-optimization: `ProblemFactory.reoptimization`'s inputs, and the solver, budget and seed.

    Attributes:
        now: When the core gathered the facts, in seconds since the date's local midnight.
        orders: Every order of the date: on the current routes, reported or not, and the new,
            dropped, put-back and moved ones.
        depots: The depots the vehicles start and end at.
        vehicles: Every vehicle of the plan, with its state, its current route and its positions.
        drivers: The date's drivers.
        put_back: The orders whose failed stop a dispatcher put back for today (FR-75).
        durations: Base durations in seconds, with a source row for each vehicle's latest
            position.
        distances: Metres, NaN exactly where durations are.
        travel: The day's buckets, factors and buffer (ADR-048).
        day_length: The date's length in seconds (ADR-088).
        fixed_vehicle_cost: Seconds per vehicle used, 0 when disabled (FR-21).
        door_window_rate: The door-window rate, from the core's configuration (BR-120, ADR-121).
        lateness_rate: Seconds per second served past a hard window's end, from the core's
            configuration (BR-115, ADR-121).
        moved_stop_cost: Seconds per stop that leaves its vehicle, from the core's configuration
            (BR-115, ADR-121).
        breaks_reserved: Whether routes reserve time for breaks, from the core's configuration
            (FR-81, BR-105, ADR-121).
        solver: The solver's name (ADR-004, FR-20).
        budget: What ends the search (ADR-044, BR-84).
        seed: The seed, so the re-optimization can be replayed (NFR-13).

    It has no `departure_allowance` yet, a domain gap the daily solve closes, and no
    per-driver `confirmed_return`, a domain gap re-optimization closes (FR-67, FR-394, ADR-121).
    """

    now: float
    orders: tuple[OrderSpec, ...]
    depots: tuple[DepotSpec, ...]
    vehicles: tuple[LiveVehicleSpec, ...]
    drivers: tuple[DriverSpec, ...]
    put_back: frozenset[int]
    durations: npt.ArrayLike
    distances: npt.ArrayLike
    travel: TravelTimeModel
    day_length: float
    fixed_vehicle_cost: float
    door_window_rate: float
    lateness_rate: float
    moved_stop_cost: float
    breaks_reserved: bool
    solver: str
    budget: Budget
    seed: int


@dataclass(frozen=True, slots=True, kw_only=True, eq=False)
class EvaluationRequest:
    """A solution to judge and score against a date's inputs, with no search (BR-21, FR-233).

    Attributes:
        inputs: The date's inputs.
        solution: The routes and drops to judge, such as a plan after a manual edit.
    """

    inputs: DailyInputs
    solution: Solution


@dataclass(frozen=True, slots=True, kw_only=True, eq=False)
class BaselineRequest:
    """The nearest-neighbour baseline of a plan's orders and fleet (FR-39, FR-342).

    Attributes:
        inputs: The plan's inputs, the same orders and fleet the plan was solved on.
        budget: What ends the baseline's construction (ADR-044).
        seed: The seed, so the baseline can be replayed (NFR-13).
    """

    inputs: DailyInputs
    budget: Budget
    seed: int
