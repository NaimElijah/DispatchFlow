"""The baseline: a naive nearest-neighbour solution of a plan's orders and fleet (FR-39, FR-342).

It goes through the same `Solver` port, validator and objective as the product's solvers, so the
comparison is fair (ADR-004, ADR-044). It runs in the HTTP process, not in a solver worker
(ADR-051).

Built in M1.
"""

from typing import Final

from dispatchflow_solver.application.outcomes import BaselineOutcome
from dispatchflow_solver.application.ports import SolverRegistry, Stopwatch
from dispatchflow_solver.application.requests import BaselineRequest

BASELINE_SOLVER: Final = "nearest_neighbour"
"""The registry name of the baseline solver (ADR-044, TS §7.1)."""


class Baseline:
    """Builds the baseline a plan is compared with (FR-39, FR-342).

    Built in M1.
    """

    def __init__(self, solvers: SolverRegistry, stopwatch: Stopwatch) -> None:
        """Holds the ports the baseline uses.

        Args:
            solvers: The solvers, by name; it must hold `BASELINE_SOLVER`.
            stopwatch: Times the construction for the stats.
        """
        self._solvers = solvers
        self._stopwatch = stopwatch

    def run(self, request: BaselineRequest) -> BaselineOutcome:
        """Builds the problem with `ProblemFactory.daily` and solves it with the baseline solver.

        The baseline meets windows, capacity and shifts and drops what does not fit; it counts
        only once the validator accepts it (FR-342, BR-15, BR-119).

        Args:
            request: The plan's inputs, and the budget and seed.

        Returns:
            The validated, scored baseline with its drop reasons, the validator's refusal, or the
            failure of the baseline solver, which the use case catches as `Failed` (ADR-004).

        Raises:
            InvalidProblemError: If an input is malformed or contradicts another.
            InvalidSolutionError: If the baseline solver returns a solution of the wrong shape.
            SolverError: If the registry holds no baseline solver.
            NotImplementedError: Until M1 builds it.
        """
        raise NotImplementedError("Built in M1")
