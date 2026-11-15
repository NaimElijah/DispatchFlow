package com.dispatchflow.liveops.domain.alert;

/** The kinds of alert in the dispatcher's one alert list (FR-157, DL §4.5). */
public enum AlertKind {
    /** A stop is projected to miss its window (FR-34, FR-253). */
    STOP_AT_RISK,
    /** A driver reported a problem (FR-95). */
    DRIVER_PROBLEM,
    /** A route has not started 15 minutes after shift start plus the allowance (FR-101). */
    ROUTE_NOT_STARTED,
    /** A driver marked a stop failed (FR-102). */
    STOP_FAILED,
    /** A driver has not confirmed a route change (FR-97, ADR-107). */
    ROUTE_CHANGE_UNCONFIRMED,
    /** A late outcome was accepted for a stop the plan had moved, dropped or cancelled (BR-89, BR-130). */
    LATE_REPORT,
    /** A late report is held for the dispatcher's decision (BR-132). */
    REPORT_HELD,
    /** A report was refused: it contradicted an outcome, came after a cancellation, or was overtaken (BR-90). */
    REPORT_REFUSED,
    /** A driver finished a route with stops pending (FR-329). */
    ROUTE_FINISHED_WITH_PENDING_STOPS,
    /** A recipient rated a delivery 1 to 4 (FR-361). */
    LOW_RATING
}
