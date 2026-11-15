"""The solver list: the names and versions `GET /v1/solvers` answers with (ADR-004, ADR-044).

Built in M4, with the core's solver choice (`PlanningSettingsQueries.solvers`).
"""

from dispatchflow_solver.application.outcomes import SolverInfo
from dispatchflow_solver.application.ports import SolverRegistry


class ListSolvers:
    """Lists the solvers a request may choose from (FR-20, ADR-004).

    Built in M4.
    """

    def __init__(self, solvers: SolverRegistry) -> None:
        """Holds the registry it lists.

        Args:
            solvers: The service's registry, which holds only FR-20's solvers, OR-Tools and the
                DispatchFlow solver; the baseline's solver is not in it, so it is never listed
                (FR-39).
        """
        self._solvers = solvers

    def run(self) -> tuple[SolverInfo, ...]:
        """Lists every solver the registry holds, in the registry's order.

        Returns:
            Each solver's name and version.

        Raises:
            NotImplementedError: Until M4 builds it.
        """
        raise NotImplementedError("Built in M4")
