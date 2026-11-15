"""The solver list: the names and versions `GET /v1/solvers` answers with (ADR-004, ADR-044).

Built in M1.
"""

from dispatchflow_solver.application.outcomes import SolverInfo
from dispatchflow_solver.application.ports import SolverRegistry


class ListSolvers:
    """Lists the solvers a request may choose from (FR-20, ADR-004).

    Built in M1.
    """

    def __init__(self, solvers: SolverRegistry) -> None:
        """Holds the registry it lists.

        Args:
            solvers: The solvers, by name.
        """
        self._solvers = solvers

    def run(self) -> tuple[SolverInfo, ...]:
        """Lists every solver the registry holds, in the registry's order.

        Returns:
            Each solver's name and version.

        Raises:
            NotImplementedError: Until M1 builds it.
        """
        raise NotImplementedError("Built in M1")
