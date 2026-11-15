package com.dispatchflow.planning.domain.plan;

/** Whether the validator has judged a route since it last changed (FR-395, ADR-058). */
public enum CheckState {
    /** The validator judged the route as it is. */
    CHECKED,
    /** The route changed without a check, such as while the optimizer was down; the re-check job settles it. */
    NOT_YET_CHECKED
}
