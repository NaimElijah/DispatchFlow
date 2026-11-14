"""Why an order or a vehicle is kept out of the search, decided once by `ProblemFactory`."""

from enum import StrEnum


class OrderExclusion(StrEnum):
    """Why an order never enters the search (BR-14, FR-66)."""

    MISSING_LOCATION = "missing_location"
    """Its customer has no coordinates (BR-14)."""
    UNREACHABLE_LOCATION = "unreachable_location"
    """No usable vehicle has a road from its start depot to the order and on to its end depot
    (FR-66)."""


class VehicleExclusion(StrEnum):
    """Why a vehicle can drive no route in the solve, so the solve names it (FR-197)."""

    DEPOT_WITHOUT_LOCATION = "depot_without_location"
    """Its start or end depot has no coordinates (FR-197)."""
    NO_ROAD_BETWEEN_DEPOTS = "no_road_between_depots"
    """The road network has no route from its start depot to its end depot (FR-66, FR-197)."""
