"""The re-optimization: rebuilds a live day's problem and repairs its routes from where they stand.

`ProblemFactory.reoptimization` alone applies ADR-050's conventions, so the product and the
disruption simulation build the same problem (BR-42). The worker process and its overrun kill live
in the adapter (ADR-051, BR-32).

Built in M9.
"""

from dispatchflow_optimizer.application.outcomes import ReoptimizationOutcome
from dispatchflow_optimizer.application.ports import SolverRegistry, Stopwatch
from dispatchflow_optimizer.application.requests import ReoptimizationRequest


class Reoptimize:
    """Re-optimizes a live day with the solver the request names (FR-37, FR-273, ADR-050).

    Built in M9.
    """

    def __init__(self, solvers: SolverRegistry, stopwatch: Stopwatch) -> None:
        """Holds the ports the re-optimization uses.

        Args:
            solvers: The service's registry, which holds only FR-20's solvers, OR-Tools and the
                DispatchFlow solver; the baseline's solver is not in it (FR-39).
            stopwatch: Times the search for the stats.
        """
        self._solvers = solvers
        self._stopwatch = stopwatch

    def run(self, request: ReoptimizationRequest) -> ReoptimizationOutcome:
        """Builds the problem with `ProblemFactory.reoptimization`, and solves from its warm start.

        The solver starts from the problem's warm start: each active vehicle's stops not yet
        reported, in planned order (ADR-044, FR-280). Only what follows `now` is judged, a
        reported stop is a fact, and a stop past its window end is a late stop, flagged and not
        refused (BR-23, BR-10, BR-107). Route stability is part of the cost (FR-37, BR-115), and
        validation runs inside the limit (NFR-119). The solver's `SearchResult.reached` goes into
        the stats (NFR-13), and the problem's vehicle exclusions, the withdrawn vehicles
        included, into `Solved.excluded` (FR-197, FR-267).

        It catches the `SearchFailedError` the solver raises, failing or finding no solution
        within its budget, and answers `Failed` with its kind as its result; no other solver is
        tried in its place (ADR-004).

        Args:
            request: The live day's facts, and the solver, budget and seed.

        Returns:
            The validated, scored solution, the validator's refusal or the solver's failure, with
            the vehicles that have no usable position (BR-81).

        Raises:
            InvalidProblemError: If an input is malformed or contradicts another, such as a
                report timed after now.
            InvalidSolutionError: If the solver returns a solution of the wrong shape.
            UnknownSolverError: If the registry holds no solver of that name, which is a refused
                request, not a failed search (ADR-004).
            UnsupportedBudgetError: If the solver cannot honour the budget's kind, which is a
                refused request too (ADR-044).
            NotImplementedError: Until M9 builds it.
        """
        raise NotImplementedError("Built in M9")
