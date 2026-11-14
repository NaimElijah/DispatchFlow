"""NFR-167: the validator accepts published best-known Solomon solutions.

The rounding convention (ADR-098): travel times are the Euclidean distances in float64, unrounded,
with travel-time factor 1 and no buffer (NFR-110), and every limit compares within 1e-6. Demands
are weights, with no volume (FR-390). The depot's due date is each vehicle's shift end and the
date's length. The data and its sources are in data/solomon/README.md.
"""

from dataclasses import replace
from pathlib import Path
from typing import Any, Final

import pytest
import vrplib

from dispatchflow_solver.domain.factory import (
    DepotSpec,
    DriverSpec,
    OrderSpec,
    ProblemFactory,
    VehicleSpec,
)
from dispatchflow_solver.domain.penalty import DEFAULT_DOOR_WINDOW_RATE
from dispatchflow_solver.domain.problem import Problem
from dispatchflow_solver.domain.rules import RuleId
from dispatchflow_solver.domain.solution import Route, Solution
from dispatchflow_solver.domain.travel_time import TravelTimeModel

DATA: Final = Path(__file__).parent / "data" / "solomon"
DEPOT: Final = 0
UNUSED_PRIORITY: Final = 3

# The distances SINTEF publishes for each best-known solution, to two decimals.
PUBLISHED_DISTANCE: Final = {
    "c101": 828.94,
    "r101": 1650.80,
    "rc101": 1696.95,
    "c201": 591.56,
    "r201": 1252.37,
    "rc201": 1406.94,
}


def solomon_problem(instance: dict[str, Any]) -> Problem:
    windows = instance["time_window"]
    horizon = float(windows[DEPOT][1])
    customers = range(1, len(instance["demand"]))
    return ProblemFactory.daily(
        orders=[
            OrderSpec(
                location=customer,
                weight_kg=float(instance["demand"][customer]),
                volume_l=0.0,
                service_s=float(instance["service_time"][customer]),
                window_start=float(windows[customer][0]),
                window_end=float(windows[customer][1]),
                priority=UNUSED_PRIORITY,
            )
            for customer in customers
        ],
        depots=[DepotSpec(location=DEPOT)],
        vehicles=[
            VehicleSpec(
                start_depot=0,
                end_depot=0,
                weight_capacity_kg=float(instance["capacity"]),
                volume_capacity_l=None,
                driver=vehicle,
            )
            for vehicle in range(instance["vehicles"])
        ],
        drivers=[
            DriverSpec(shift_start=float(windows[DEPOT][0]), shift_end=horizon)
            for _ in range(instance["vehicles"])
        ],
        durations=instance["edge_weight"],
        distances=instance["edge_weight"],
        travel=TravelTimeModel.static(),
        day_length=horizon,
        # NFR-110: no fixed vehicle cost. The instances carry no door orders or breaks, so the
        # door rate is the product's and changes nothing.
        fixed_vehicle_cost=0.0,
        door_window_rate=DEFAULT_DOOR_WINDOW_RATE,
        breaks_reserved=False,
    )


def best_known(name: str) -> Solution:
    # Customers are numbered from 1 in the files and are orders 0 onwards in the problem.
    routes = vrplib.read_solution(DATA / "solutions" / f"{name}.txt")["routes"]
    return Solution(
        routes=tuple(
            Route(
                vehicle=index,
                driver=index,
                start=DEPOT,
                end=DEPOT,
                stops=tuple(customer - 1 for customer in route),
            )
            for index, route in enumerate(routes)
        ),
        dropped=frozenset(),
    )


def instance(name: str) -> dict[str, Any]:
    data: dict[str, Any] = vrplib.read_instance(
        DATA / "instances" / f"{name}.txt", instance_format="solomon"
    )
    return data


@pytest.mark.parametrize("name", sorted(PUBLISHED_DISTANCE))
def test_given_a_best_known_solomon_solution_when_validated_then_accepted(name: str) -> None:
    problem = solomon_problem(instance(name))
    solution = best_known(name)

    report = problem.validate(solution)

    assert report.violations == ()
    assert len(solution.served()) == len(problem.orders)


@pytest.mark.parametrize("name", sorted(PUBLISHED_DISTANCE))
def test_given_a_best_known_solomon_solution_when_costed_then_travel_matches_the_published_value(
    name: str,
) -> None:
    problem = solomon_problem(instance(name))
    solution = best_known(name)

    cost = problem.cost(solution, problem.validate(solution).timings)

    assert round(cost.travel, 2) == PUBLISHED_DISTANCE[name]
    assert (cost.drops, cost.door_window, cost.fixed_vehicles) == (0, 0, 0)


def test_given_a_solomon_window_closed_a_second_early_when_validated_then_rejected() -> None:
    data = instance("c101")
    solution = best_known("c101")
    timing = solomon_problem(data).validate(solution).timings[0]
    windows = data["time_window"].astype(float)
    # The first stop whose service starts a second or more after its window opens, so closing
    # the window a second before that start still leaves a valid window.
    position, order = next(
        (position, order)
        for position, order in enumerate(solution.routes[0].stops)
        if timing.start[position] - 1 >= windows[order + 1][0]
    )
    windows[order + 1][1] = timing.start[position] - 1
    problem = solomon_problem({**data, "time_window": windows})

    report = problem.validate(solution)

    assert report.rules() == {RuleId.BR_03}
    assert report.violations[0].order == order
    assert report.violations[0].seconds == pytest.approx(1)


def test_given_a_solomon_solution_with_one_customer_moved_when_validated_then_rejected() -> None:
    # Route 2's first customer, moved to the end of route 1, reaches it after its window closes.
    solution = best_known("c101")
    first, second = solution.routes[0], solution.routes[1]
    customer = second.stops[0]
    moved = (
        replace(first, stops=(*first.stops, customer)),
        replace(second, stops=second.stops[1:]),
        *solution.routes[2:],
    )

    report = solomon_problem(instance("c101")).validate(replace(solution, routes=moved))

    assert report.rules() == {RuleId.BR_03}
    assert [(v.route, v.order) for v in report.violations] == [(0, customer)]
