package com.dispatchflow.planning.domain.solution;

/**
 * Why a solve left a vehicle out, so that it drives no route the solve could change (FR-197). It
 * mirrors the Python {@code exclusions.VehicleExclusion}, which the optimizer alone decides, in
 * Planning's words. A daily solve gives only the first two; the rest come from a re-optimization's
 * vehicle state (ADR-050).
 */
public enum ExclusionCause {
    /** Its start or end depot has no coordinates (FR-197). */
    DEPOT_WITHOUT_LOCATION,
    /** The road network has no route from its start to its end depot (FR-66, FR-197). */
    NO_ROAD_BETWEEN_DEPOTS,
    /** It was marked unavailable before it made a stop (FR-07). */
    VEHICLE_UNAVAILABLE,
    /** It was left without a driver before it made a stop (BR-106). */
    NO_DRIVER,
    /** Its driver's shift ran out before it made a stop (BR-106). */
    SHIFT_RAN_OUT,
    /** Its driver finished its route, so a re-optimization adds no stop to it (FR-68, BR-108). */
    ROUTE_FINISHED,
    /** It still has stops it made, and is withdrawn mid-route (FR-267, BR-108). */
    WITHDRAWN
}
