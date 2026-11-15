"""The ports the use cases call: the solver, its registry and the stopwatch (ADR-004, ADR-044).

Every solver implements `Solver`, and a solve picks one by name from the `SolverRegistry`, which
has no default (ADR-004). A `Budget` ends the search: a time limit, an iteration budget or a
solution limit, so a test never runs on the wall clock (NFR-82, NFR-175). The `Stopwatch` times
the search for the response's stats, so the use cases read no clock of their own.

Built in M0.
"""

from collections.abc import Sequence
from dataclasses import dataclass
from typing import Protocol

from dispatchflow_solver.domain.problem import Problem
from dispatchflow_solver.domain.solution import Solution


@dataclass(frozen=True, slots=True)
class TimeLimit:
    """A budget that ends the search after a number of seconds (ADR-044, BR-75).

    Attributes:
        seconds: How long the search may run, from its start, after the matrix is built.
    """

    seconds: float


@dataclass(frozen=True, slots=True)
class IterationLimit:
    """A budget that ends the search after a number of iterations (ADR-044, NFR-13, NFR-82).

    Attributes:
        iterations: How many iterations the search may make.
    """

    iterations: int


@dataclass(frozen=True, slots=True)
class SolutionLimit:
    """A budget that ends the search after a number of solutions found (ADR-044, NFR-82).

    Attributes:
        solutions: How many solutions the search may find.
    """

    solutions: int


type Budget = TimeLimit | IterationLimit | SolutionLimit
"""What ends a search: a time limit, an iteration budget or a solution limit (ADR-044)."""


class SolverError(Exception):
    """A solve that cannot run or found nothing (ADR-004).

    A solver name the registry does not hold, a solver that failed, or one that found no solution
    within its budget.

    The message names the solver, never the problem's contents.
    """


@dataclass(frozen=True, slots=True)
class SearchResult:
    """What a search returns: its routes, and how far it got (ADR-004, ADR-044).

    Attributes:
        solution: The routes and the dropped orders it proposes, not yet validated.
        reached: The iterations or the solutions the search reached, whichever its budget counts,
            so a time-limited result can be replayed under that budget (NFR-13, NFR-115).
    """

    solution: Solution
    reached: int


class Solver(Protocol):
    """One solver behind the one port, chosen by name for each solve (ADR-004, ADR-044).

    Its own objective value is never reported: `Problem.cost` scores what it returns, and
    `Problem.validate` judges it (BR-09, BR-15, BR-42).
    """

    @property
    def name(self) -> str:
        """The name a request chooses it by, such as ``ortools`` (FR-20)."""

    @property
    def version(self) -> str:
        """Its version, which the stats and `GET /v1/solvers` report."""

    def solve(
        self, problem: Problem, start: Solution | None, budget: Budget, seed: int
    ) -> SearchResult:
        """Searches for routes until the budget ends the search (ADR-044).

        Args:
            problem: The problem to solve, built by `ProblemFactory`.
            start: A warm start to search from, or None for a cold start (FR-273, FR-280).
            budget: What ends the search.
            seed: The seed of every random choice, so a run can be replayed (NFR-13, ADR-005).

        Returns:
            The routes and the dropped orders it proposes, not yet validated, and the iterations or
            solutions the search reached.

        Raises:
            SolverError: If the solver fails, cannot honour the budget's kind, or finds no solution
                within its budget.
        """


class SolverRegistry:
    """The solvers the service holds, by name; it has no default (ADR-004).

    Built in M0.
    """

    def __init__(self, solvers: Sequence[Solver]) -> None:
        """Holds the given solvers.

        Args:
            solvers: Every solver the service runs, each under its own name.
        """
        self._solvers = tuple(solvers)

    def get(self, name: str) -> Solver:
        """The solver of that name (ADR-004, FR-20).

        Args:
            name: The solver's name, as the request gives it.

        Returns:
            The solver.

        Raises:
            SolverError: If the registry holds no solver of that name; it never falls back to
                another.
            NotImplementedError: Until M0 builds it.
        """
        raise NotImplementedError("Built in M0")

    def names(self) -> tuple[str, ...]:
        """The names of the solvers held, in the order they were given (ADR-004).

        Returns:
            The names.

        Raises:
            NotImplementedError: Until M0 builds it.
        """
        raise NotImplementedError("Built in M0")


class Lap(Protocol):
    """A running measurement, started by a `Stopwatch`."""

    def elapsed(self) -> float:
        """The seconds since the lap started."""


class Stopwatch(Protocol):
    """Measures the search time for the stats; a test injects one it controls (NFR-175)."""

    def start(self) -> Lap:
        """Starts a lap.

        Returns:
            The lap, whose elapsed time runs from now.
        """
