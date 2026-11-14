"""The shared objective of a daily solve, in seconds (BR-09, BR-58).

Travel costs the unbuffered per-bucket time of each leg, departing when the validator says it
departs (FR-402). Each unserved order costs K x (6 - priority) (BR-54, BR-126). Each second a door
stop is served outside its window costs the door-window rate (BR-120), on the validator's times.
Each vehicle used costs the fixed vehicle cost (FR-21).
"""

import math
from dataclasses import dataclass
from typing import TYPE_CHECKING

from dispatchflow_solver.domain.feasibility import Timings
from dispatchflow_solver.domain.solution import Solution

if TYPE_CHECKING:
    from dispatchflow_solver.domain.problem import Problem


@dataclass(frozen=True, slots=True)
class Cost:
    """The objective's terms, in seconds.

    A route the validator could not time to its end has a NaN travel term, so its total is NaN
    rather than a number that leaves legs out.

    Attributes:
        travel: Unbuffered per-bucket travel of every leg (FR-402).
        drops: K x (6 - priority) for every unserved order, excluded ones included (BR-54).
        door_window: The door-window rate times the seconds door stops spend outside their
            windows (BR-120).
        fixed_vehicles: The fixed vehicle cost times the vehicles used (FR-21).
    """

    travel: float
    drops: float
    door_window: float
    fixed_vehicles: float

    @property
    def total(self) -> float:
        """The objective's value: the sum of the terms."""
        return self.travel + self.drops + self.door_window + self.fixed_vehicles


def cost(problem: Problem, solution: Solution, timings: Timings) -> Cost:
    """Scores a solution on the validator's timings; `Problem.cost` delegates here.

    A vehicle is used when its route serves at least one stop. A route with no stops is not
    driven, so it costs nothing.

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
    used = 0
    for route, timing in zip(solution.routes, timings, strict=True):
        if not route.stops:
            continue
        used += 1
        places = [
            route.start,
            *(int(problem.orders.location[order]) for order in route.stops),
            route.end,
        ]
        departures = [timing.leaves_at, *(float(t) for t in timing.departure)]
        legs = zip(places, places[1:], departures, timing.leg_seconds, strict=False)
        for origin, destination, departs, timed in legs:
            if math.isnan(timed):
                travel = math.nan
                continue
            base = float(problem.durations[origin, destination])
            travel += problem.travel.unbuffered(base, departs)
        for position, order in enumerate(route.stops):
            if problem.orders.leave_at_door[order]:
                door_window += float(timing.outside_window[position])
    penalties = problem.penalties
    drop_total = sum(
        penalties.drop_penalty(int(problem.orders.priority[order]))
        for order in problem.unserved(solution)
    )
    return Cost(
        travel=travel,
        drops=float(drop_total),
        door_window=penalties.door_window_rate * door_window,
        fixed_vehicles=penalties.fixed_vehicle_cost * used,
    )
