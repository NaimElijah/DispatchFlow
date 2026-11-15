"""The daily solve: builds the date's problem, runs the chosen solver, and judges what it returns.

The worker process that runs it, its lane and the 30-second overrun kill live in the adapter
(ADR-051, BR-32), so this use case is a plain function of its request.

Built in M0.
"""

from dispatchflow_solver.application.outcomes import SolveOutcome
from dispatchflow_solver.application.ports import SolverRegistry, Stopwatch
from dispatchflow_solver.application.requests import DailyRequest


class Solve:
    """Solves a date's orders with the solver the request names (FR-18, FR-20, ADR-044).

    Built in M0.
    """

    def __init__(self, solvers: SolverRegistry, stopwatch: Stopwatch) -> None:
        """Holds the ports the solve uses.

        Args:
            solvers: The solvers, by name.
            stopwatch: Times the search for the stats.
        """
        self._solvers = solvers
        self._stopwatch = stopwatch

    def run(self, request: DailyRequest) -> SolveOutcome:
        """Builds the problem with `ProblemFactory.daily`, solves it cold, and judges the result.

        The solver searches from no warm start under the request's budget and seed. Its solution
        is judged by `Problem.validate` and scored by `Problem.cost`, never by the solver itself,
        and each unserved order gets its reason from `Problem.explain_drop` (BR-09, BR-15, BR-42,
        FR-144). Validation runs inside the limit (NFR-119). The solver's `SearchResult.reached`
        goes into the stats (NFR-13).

        It catches the `SolverError` the solver raises, failing or finding no solution within its
        budget, and answers `Failed`; no other solver is tried in its place (ADR-004).

        Args:
            request: The date's inputs, and the solver, budget and seed.

        Returns:
            The validated, scored solution with its drop reasons, the validator's refusal, or the
            solver's failure.

        Raises:
            InvalidProblemError: If an input is malformed or contradicts another.
            InvalidSolutionError: If the solver returns a solution of the wrong shape.
            SolverError: If the registry holds no solver of that name, which is a refused request,
                not a failed search (ADR-004).
            NotImplementedError: Until M0 builds it.
        """
        raise NotImplementedError("Built in M0")
