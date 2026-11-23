"""Why an order or a vehicle is kept out of the search, decided once by `ProblemFactory`."""

from enum import StrEnum
from typing import Final


class OrderExclusion(StrEnum):
    """Why an order never enters the search (BR-14, FR-66)."""

    MISSING_LOCATION = "missing_location"
    """Its customer has no coordinates (BR-14)."""
    UNREACHABLE_LOCATION = "unreachable_location"
    """No usable vehicle has a road from its start to the order and on to its end depot (FR-66)."""


class VehicleExclusion(StrEnum):
    """Why a vehicle can drive no route in the solve, so the solve names it (FR-197).

    The last five arise only in a re-optimization, from the vehicle's state (ADR-050). The factory
    gives a vehicle's state precedence over its depots and roads: a vehicle that is out is out,
    wherever its depot lies.
    """

    DEPOT_WITHOUT_LOCATION = "depot_without_location"
    """Its start or end depot has no coordinates (FR-197)."""
    NO_ROAD_BETWEEN_DEPOTS = "no_road_between_depots"
    """The road network has no route from its start to its end depot (FR-66, FR-197). In a
    re-optimization its start is where the vehicle is now (BR-51)."""
    UNAVAILABLE = "unavailable"
    """Marked unavailable before it made a stop (FR-07)."""
    NO_DRIVER = "no_driver"
    """Left without a driver before it made a stop (BR-106); the vehicle's driver is -1."""
    OUT_OF_SHIFT = "out_of_shift"
    """Its driver's shift ran out before it made a stop (BR-106)."""
    FINISHED = "finished"
    """Its driver finished its route (FR-68); a re-optimization adds no stop to it (BR-108)."""
    WITHDRAWN = "withdrawn"
    """Left out while it still has stops it made (FR-267). Its route counts as finished (BR-108),
    ends at its last reported stop (BR-52) and does not count under BR-39."""


ROAD_EXCLUSIONS: Final = frozenset(
    {VehicleExclusion.DEPOT_WITHOUT_LOCATION, VehicleExclusion.NO_ROAD_BETWEEN_DEPOTS}
)
"""The exclusions a daily solve knows: the vehicle has no depot location or no road to drive."""

FINISHED_ROUTES: Final = frozenset({VehicleExclusion.FINISHED, VehicleExclusion.WITHDRAWN})
"""The vehicles whose route counts as finished, so a re-optimization adds no stop (BR-108)."""

OUT_BEFORE_A_STOP: Final = frozenset(
    {VehicleExclusion.UNAVAILABLE, VehicleExclusion.NO_DRIVER, VehicleExclusion.OUT_OF_SHIFT}
)
"""The vehicles that are out with no stop made: their stops are orphans, allowed only on an
active vehicle (BR-106, ADR-050)."""

REOPTIMIZATION_EXCLUSIONS: Final = FINISHED_ROUTES | OUT_BEFORE_A_STOP
"""The exclusions only a re-optimization's vehicle state gives."""
