"""What the validator, the objective and the drop reasons read from a problem (ADR-113).

`Problem` owns `validate`, `cost` and `explain_drop` and delegates each to `feasibility`,
`objective` or `drops` (TS §7.1, ADR-044). Those modules type their problem as `ProblemView`, which
`Problem` satisfies structurally, so they never import `problem`, and no import cycle runs through
it. import-linter keeps it so (`pyproject.toml`).
"""

from typing import Protocol

import numpy as np
import numpy.typing as npt

from dispatchflow_optimizer.domain.penalty import PenaltyScale
from dispatchflow_optimizer.domain.problem_parts import Drivers, Orders, ReoptContext, Vehicles
from dispatchflow_optimizer.domain.solution import Solution
from dispatchflow_optimizer.domain.travel_time import TravelTimeModel


class ProblemView(Protocol):
    """A problem as the rules read it: its parts, its matrices and its scoring inputs.

    Each member means what the `Problem` attribute or method of the same name means.
    """

    @property
    def orders(self) -> Orders:
        """Every order of the date, excluded ones included."""

    @property
    def vehicles(self) -> Vehicles:
        """Every vehicle of the date, excluded ones included."""

    @property
    def drivers(self) -> Drivers:
        """Every driver of the date."""

    @property
    def durations(self) -> npt.NDArray[np.float64]:
        """Base durations in seconds; 0 on the diagonal, NaN where there is no road."""

    @property
    def distances(self) -> npt.NDArray[np.float64]:
        """Metres; 0 on the diagonal, NaN exactly where durations are."""

    @property
    def travel(self) -> TravelTimeModel:
        """The travel-time model."""

    @property
    def penalties(self) -> PenaltyScale:
        """The drop unit and the rates the objective charges."""

    @property
    def drop_penalties(self) -> npt.NDArray[np.float64]:
        """What dropping each order costs a solver, the moved-stop cost included."""

    @property
    def breaks_reserved(self) -> bool:
        """Whether routes reserve time for breaks."""

    @property
    def removed_by_dispatcher(self) -> frozenset[int]:
        """The orders a dispatcher took off the current routes."""

    @property
    def reoptimization(self) -> ReoptContext | None:
        """The re-optimization's context, or None for a daily solve."""

    def is_locked(self, order: int) -> bool:
        """Says whether a re-optimization holds an order's stop as reported."""

    def unserved(self, solution: Solution) -> tuple[int, ...]:
        """Every order a solution leaves on no route, locked ones excepted."""
