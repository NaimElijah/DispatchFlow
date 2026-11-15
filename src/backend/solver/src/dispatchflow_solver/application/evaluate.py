"""The evaluation: judges and scores a solution the core already holds, with no search.

The manual-edit checks and the publishing re-check use the same validator as every solve (BR-21,
BR-42, FR-233). It runs in the HTTP process, not in a solver worker (ADR-051). Whether a published
plan's mid-day evaluation uses the daily or the re-optimization rules is decided in M9; until then
it takes the daily inputs.

Built in M1.
"""

from dispatchflow_solver.application.outcomes import EvaluationOutcome
from dispatchflow_solver.application.requests import EvaluationRequest


class Evaluate:
    """Judges and scores a given solution against its date's inputs (BR-21, BR-42, FR-233).

    It runs no solver, so it takes no port.

    Built in M1.
    """

    def run(self, request: EvaluationRequest) -> EvaluationOutcome:
        """Builds the problem with `ProblemFactory.daily`, then validates, scores and explains.

        Args:
            request: The date's inputs and the solution to judge.

        Returns:
            Every violation found, the shared objective's terms, and each unserved order's reason
            (BR-09, BR-16, FR-144).

        Raises:
            InvalidProblemError: If an input is malformed or contradicts another.
            InvalidSolutionError: If the solution has an index past the problem.
            NotImplementedError: Until M1 builds it.
        """
        raise NotImplementedError("Built in M1")
