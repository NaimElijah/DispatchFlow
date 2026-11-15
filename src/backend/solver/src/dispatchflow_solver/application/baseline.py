"""The baseline: a naive nearest-neighbour solution of a plan's orders and fleet (FR-39, FR-342).

It goes through the same `Solver` port, validator and objective as the product's solvers, so the
comparison is fair (ADR-004, ADR-044). Its solver is given to it directly, never through the
registry, so no request can choose the baseline as a product solver (FR-20). It runs in the HTTP
process, not in a solver worker (ADR-051).

Built in M1.
"""

from dispatchflow_solver.application.outcomes import BaselineOutcome
from dispatchflow_solver.application.ports import Solver, Stopwatch
from dispatchflow_solver.application.requests import BaselineRequest


class Baseline:
    """Builds the baseline a plan is compared with (FR-39, FR-342).

    Built in M1.
    """

    def __init__(self, solver: Solver, stopwatch: Stopwatch) -> None:
        """Holds the ports the baseline uses.

        Args:
            solver: The nearest-neighbour solver, `nearest_neighbour` (TS §7.1).
            stopwatch: Times the construction for the stats.
        """
        self._solver = solver
        self._stopwatch = stopwatch

    def run(self, request: BaselineRequest) -> BaselineOutcome:
        """Builds the problem with `ProblemFactory.daily` and solves it with the baseline solver.

        The baseline meets windows, capacity and shifts and drops what does not fit; it counts
        only once the validator accepts it (FR-342, BR-15, BR-119).

        Args:
            request: The plan's inputs, and the budget and seed.

        Returns:
            The validated, scored baseline with its drop reasons and excluded vehicles, the
            validator's refusal, or the failure of the baseline solver, which the use case catches
            from its `SearchFailedError` as `Failed` with its kind (ADR-004).

        Raises:
            InvalidProblemError: If an input is malformed or contradicts another.
            InvalidSolutionError: If the baseline solver returns a solution of the wrong shape.
            UnsupportedBudgetError: If the baseline solver cannot honour the budget's kind, which
                is a refused request (ADR-044).
            NotImplementedError: Until M1 builds it.
        """
        raise NotImplementedError("Built in M1")
