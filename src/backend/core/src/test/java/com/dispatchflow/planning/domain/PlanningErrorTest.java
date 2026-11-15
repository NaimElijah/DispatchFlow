package com.dispatchflow.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins Planning's error codes, which both apps translate (NFR-144). That each error has a refusal
 * test is checked for every module by {@code ErrorRefusalTestsTest}.
 */
class PlanningErrorTest {

    @Test
    void GivenThePlanningErrors_WhenTheirCodesAreListed_ThenTheyAreThePinnedCodes() {
        List<String> codes = Arrays.stream(PlanningError.class.getPermittedSubclasses())
                .map(type -> DomainError.codeOf(type.asSubclass(DomainError.class)))
                .sorted()
                .toList();

        assertThat(codes)
                .containsExactly(
                        "BREAK_NOT_CONFIRMED",
                        "CAPACITY_FOR_ANOTHER_LANE",
                        "DATE_IN_PAST",
                        "DATE_NOT_OVER",
                        "DRIVER_ALREADY_HAS_ROUTE",
                        "DRIVER_NOT_AVAILABLE",
                        "DRIVER_ROUTED_TWICE",
                        "INVALID_SOLVER_NAME",
                        "INVALID_STOP_TIMES",
                        "INVALID_TRAVEL_BUCKETS",
                        "INVALID_TRAVEL_LEG",
                        "JOB_ALREADY_ENDED",
                        "JOB_NOT_BUILDING_MATRIX",
                        "JOB_NOT_QUEUED",
                        "JOB_NOT_RUNNING",
                        "MALFORMED_RULE_CODE",
                        "NEGATIVE_BUFFER",
                        "NEGATIVE_DEPARTURE_ALLOWANCE",
                        "NON_POSITIVE_FIXED_VEHICLE_COST",
                        "NON_POSITIVE_TRAVEL_FACTOR",
                        "NOTHING_TO_REDO",
                        "NOTHING_TO_UNDO",
                        "NOT_AN_ACTIVE_DRIVER",
                        "OPEN_VIOLATION",
                        "ORDER_ALREADY_IN_PLAN",
                        "ORDER_CHANGED_SINCE_SOLVE",
                        "ORDER_NOT_ACCOUNTED",
                        "ORDER_NOT_DROPPED",
                        "ORDER_NOT_PINNED",
                        "ORDER_NOT_ROUTABLE",
                        "PLAN_ALREADY_EXISTS",
                        "PLAN_NOT_DRAFT_OR_READY",
                        "PLAN_NOT_DRAFT_SOLVING_OR_READY",
                        "PLAN_NOT_PUBLISHED",
                        "PLAN_NOT_READY",
                        "POSITION_OUT_OF_RANGE",
                        "PROPOSAL_ALREADY_DECLINED",
                        "PROPOSAL_DECLINED",
                        "PROPOSAL_FOR_ANOTHER_PLAN",
                        "PROPOSAL_OUTDATED",
                        "PUBLISHING_REFUSED",
                        "RECHECK_FAILED",
                        "RECHECK_UNAVAILABLE",
                        "REOPTIMIZATION_ALREADY_RUNNING",
                        "RESULT_FOR_ANOTHER_LANE",
                        "ROUTE_ALREADY_STARTED",
                        "ROUTE_INDEXES_USED_UP",
                        "ROUTE_LOCKED",
                        "ROUTE_NOT_IN_PLAN",
                        "ROUTE_NOT_LOCKED",
                        "ROUTE_NOT_STAFFED",
                        "ROUTE_WITHDRAWN",
                        "SOLUTION_REJECTED",
                        "SOLVE_LIMIT_OUT_OF_RANGE",
                        "SOLVE_QUEUE_FULL",
                        "STALE_EVALUATION",
                        "STOP_ALREADY_REPORTED",
                        "STOP_NOT_IN_PLAN",
                        "VALIDATOR_UNAVAILABLE",
                        "VEHICLE_ALREADY_HAS_ROUTE",
                        "VEHICLE_ROUTED_TWICE",
                        "VEHICLE_UNAVAILABLE");
    }
}
