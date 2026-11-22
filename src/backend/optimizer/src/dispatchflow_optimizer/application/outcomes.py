"""What each use case answers: a validated, scored solution, a rejection or a failure, with stats.

Every solution leaves the service only after `Problem.validate` has judged it and `Problem.cost`
has scored it, whichever solver produced it (BR-09, BR-15, BR-42). A solver's own objective value
is never reported (ADR-004). A solver that fails, or finds no solution within its budget, is a
failed solve with its `SearchFailure` kind, and its message names the solver (ADR-004).

Built in M0; the evaluation and baseline outcomes in M1, the re-optimization outcome in M9.
"""

from collections.abc import Mapping
from dataclasses import dataclass

from dispatchflow_optimizer.application.ports import (
    Budget,
    IterationLimit,
    SearchFailure,
    SolutionLimit,
)
from dispatchflow_optimizer.domain.drops import DropReason
from dispatchflow_optimizer.domain.exclusions import VehicleExclusion
from dispatchflow_optimizer.domain.feasibility import ValidationReport
from dispatchflow_optimizer.domain.objective import Cost
from dispatchflow_optimizer.domain.solution import Solution


@dataclass(frozen=True, slots=True)
class SolveStats:
    """How a search ran, so a result can be replayed under the same budget (NFR-13, ADR-044).

    Attributes:
        solver: The solver's name.
        version: The solver's version.
        seed: The seed it searched with.
        budget: The budget it searched under.
        search_seconds: How long the search ran, by the stopwatch.
        reached: The budget that replays the search, the iterations or the solutions it reached,
            from its `SearchResult` (ADR-004, NFR-13, NFR-115); None in a `Failed` outcome, whose
            search returned none.
    """

    solver: str
    version: str
    seed: int
    budget: Budget
    search_seconds: float
    reached: IterationLimit | SolutionLimit | None


@dataclass(frozen=True, slots=True)
class Solved:
    """A solution the validator accepted, with its score and every drop's reason (BR-15, FR-18).

    Attributes:
        solution: The routes and the dropped orders.
        report: The validator's verdict, which accepts the solution; its timings, and in a
            re-optimization its late stops (BR-107).
        cost: The shared objective's terms (BR-09).
        drop_reasons: Each unserved order's reason, by its index in the problem (FR-144, FR-223).
        excluded: Each vehicle the problem kept out of the search, by its index in the problem's
            `vehicles`, with its exclusion; a vehicle that drives is not in it (FR-197, FR-267,
            ADR-050). A daily solve gives only `ROAD_EXCLUSIONS`.
        stats: How the search ran.
    """

    solution: Solution
    report: ValidationReport
    cost: Cost
    drop_reasons: Mapping[int, DropReason]
    excluded: Mapping[int, VehicleExclusion]
    stats: SolveStats


@dataclass(frozen=True, slots=True)
class Rejected:
    """A solution the validator refused, which is never stored as a plan (BR-15, BR-59).

    Attributes:
        report: The validator's verdict, with every violation found (BR-16).
        stats: How the search ran.
    """

    report: ValidationReport
    stats: SolveStats


@dataclass(frozen=True, slots=True)
class Failed:
    """A search that raised a `SearchFailedError`, which is never stored as a plan (ADR-004).

    Attributes:
        kind: Why the search returned no solution, the code the contract sends.
        error: The error's message, which names the solver, never the problem's contents.
        stats: How the search ran, up to its failure.
    """

    kind: SearchFailure
    error: str
    stats: SolveStats


type SolveOutcome = Solved | Rejected | Failed
"""A solve's answer: the validated solution, the validator's refusal, or the solver's failure.

The core records a rejection as a failed solve with the validator's violations (BR-15, BR-57).
A failure is no rejection: the core needs a failure reason of its own for it, which FR-65 does
not list yet, and never records it as a rejection with no violations (ADR-004).
"""


@dataclass(frozen=True, slots=True)
class ReoptimizationOutcome:
    """A re-optimization's answer, with what the dispatcher's preview names (ADR-050).

    Attributes:
        result: The validated solution, the validator's refusal, or the solver's failure.
        no_usable_position: The vehicles with no usable position, which the preview names
            (BR-80, BR-81).
    """

    result: SolveOutcome
    no_usable_position: frozenset[int]


@dataclass(frozen=True, slots=True)
class EvaluationOutcome:
    """The verdict and score of a given solution, for a manual edit or publishing (BR-21, FR-233).

    Attributes:
        report: The validator's verdict, with every violation found (BR-16, BR-42).
        cost: The shared objective's terms (BR-09).
        drop_reasons: Each unserved order's reason, by its index in the problem (FR-144, FR-223).
    """

    report: ValidationReport
    cost: Cost
    drop_reasons: Mapping[int, DropReason]


type BaselineOutcome = Solved | Rejected | Failed
"""The baseline's answer: it passes the validator like every other solution (FR-342, BR-15),
and a failure of its solver is loud like any other (ADR-004)."""


@dataclass(frozen=True, slots=True)
class SolverInfo:
    """A solver the service holds, as `GET /v1/solvers` lists it (ADR-044).

    Attributes:
        name: The name a request chooses it by (FR-20).
        version: Its version.
    """

    name: str
    version: str
