"""The shared objective of a solve or a re-optimization, in seconds (BR-09, BR-56, BR-58).

Travel costs the unbuffered per-bucket time of each leg, departing when the validator says it
departs (FR-402). Each unserved order costs the drop penalty the problem holds for it, K x
(6 - priority) in a daily solve (BR-54, BR-126). Each second a door stop is served outside its
window costs the door-window rate (BR-120), on the validator's times. Each vehicle used costs the
fixed vehicle cost (FR-21).

A re-optimization adds BR-115's two terms. Each second a stop with a hard window is served past
its end costs the lateness rate, on the validator's times; a door stop pays its window penalty
instead (BR-120). A stop that leaves its vehicle costs the moved-stop cost: a stop that stays on its
vehicle carries it in its drop penalty, and each orphan pays it as a constant, which scores it but
cannot steer the search (ADR-050). A daily solve has neither, so its terms are 0.

`Problem` appears here in annotations only, under `TYPE_CHECKING`: `problem` imports this module
at run time, and this module never imports it.
"""

import math
from dataclasses import dataclass
from typing import TYPE_CHECKING

from dispatchflow_solver.domain.feasibility import RouteTiming, Timings
from dispatchflow_solver.domain.solution import Route, Solution

if TYPE_CHECKING:
    from dispatchflow_solver.domain.problem import Problem


@dataclass(frozen=True, slots=True)
class Cost:
    """The objective's terms, in seconds.

    Attributes:
        travel: Unbuffered per-bucket travel of every leg the validator timed (FR-402). A stop it
            skipped, which makes the solution infeasible, costs no travel.
        drops: The drop penalty of every unserved order, excluded ones included (BR-54).
        door_window: The door-window rate times the seconds door stops spend outside their
            windows (BR-120).
        fixed_vehicles: The fixed vehicle cost times the vehicles used (FR-21).
        lateness: The lateness rate times the seconds stops with a hard window are served past
            their window end; 0 in a daily solve (BR-107, BR-115).
        orphaned_stops: The moved-stop cost times the orphans, the stops on a vehicle that is out;
            0 in a daily solve (BR-115, FR-256). A dropped stop that stays on its vehicle pays the
            moved-stop cost in `drops`.
    """

    travel: float
    drops: float
    door_window: float
    fixed_vehicles: float
    lateness: float = 0.0
    orphaned_stops: float = 0.0

    @property
    def total(self) -> float:
        """The objective's value: the sum of the terms."""
        return (
            self.travel
            + self.drops
            + self.door_window
            + self.fixed_vehicles
            + self.lateness
            + self.orphaned_stops
        )


def cost(problem: Problem, solution: Solution, timings: Timings) -> Cost:
    """Scores a solution on the validator's timings; `Problem.cost` delegates here.

    A vehicle is used when its route serves at least one stop. A route with no stops is not
    driven, so it costs nothing. The legs follow the validator's walk: a stop it skipped is passed
    over, and the next leg starts from the last place reached, so `cost` never fails on a
    validated solution, feasible or not.

    Args:
        problem: The problem.
        solution: The solution `validate` was given.
        timings: The timings `validate` returned for it.

    Returns:
        Each term and the total.

    Raises:
        ValueError: If the timings do not match the solution's routes.
    """
    if len(timings) != len(solution.routes) or any(
        len(timing.start) != len(route.stops)
        for route, timing in zip(solution.routes, timings, strict=False)
    ):
        raise ValueError("the timings do not belong to this solution")
    travel = 0.0
    door_window = 0.0
    late = 0.0
    used = 0
    for route, timing in zip(solution.routes, timings, strict=True):
        if not route.stops:
            continue
        used += 1
        travel += _route_travel(problem, route, timing)
        for position, order in enumerate(route.stops):
            outside = float(timing.outside_window[position])
            if math.isnan(outside):
                continue
            if problem.orders.leave_at_door[order]:
                door_window += outside
            else:
                late += float(timing.late[position])
    penalties = problem.penalties
    unserved = list(problem.unserved(solution))
    context = problem.reoptimization
    orphans = 0 if context is None else int(context.orphaned.sum())
    return Cost(
        travel=travel,
        drops=float(problem.drop_penalties[unserved].sum()),
        door_window=penalties.door_window_rate * door_window,
        fixed_vehicles=penalties.fixed_vehicle_cost * used,
        lateness=0.0 if context is None else penalties.lateness_rate * late,
        orphaned_stops=penalties.moved_stop_cost * orphans,
    )


def _route_travel(problem: Problem, route: Route, timing: RouteTiming) -> float:
    travel = 0.0
    here, departs = route.start, timing.leaves_at
    for position, order in enumerate(route.stops):
        if math.isnan(timing.start[position]):
            continue
        there = int(problem.orders.location[order])
        travel += problem.travel.unbuffered(float(problem.durations[here, there]), departs)
        here, departs = there, float(timing.departure[position])
    if not math.isnan(timing.returns_at):
        travel += problem.travel.unbuffered(float(problem.durations[here, route.end]), departs)
    return travel
