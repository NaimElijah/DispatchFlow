package com.dispatchflow.liveops.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins Live Operations' error codes, which both apps translate (NFR-144). That each error has a
 * refusal test is checked for every module by {@code ErrorRefusalTestsTest}.
 */
class LiveOpsErrorTest {

    @Test
    void GivenTheLiveOpsErrors_WhenTheirCodesAreListed_ThenTheyAreThePinnedCodes() {
        List<String> codes = Arrays.stream(LiveOpsError.class.getPermittedSubclasses())
                .map(type -> DomainError.codeOf(type.asSubclass(DomainError.class)))
                .sorted()
                .toList();

        assertThat(codes)
                .containsExactly(
                        "ALERT_ALREADY_ACKNOWLEDGED",
                        "ARRIVAL_AFTER_OUTCOME",
                        "ARRIVAL_ALREADY_RECORDED",
                        "CORRECTION_CHANGES_NOTHING",
                        "CORRECTION_REASON_REQUIRED",
                        "CORRECTION_TIME_IN_FUTURE",
                        "FAILURE_REASON_REQUIRED",
                        "INVALID_REVISION",
                        "LATER_ATTEMPT_HAS_OUTCOME",
                        "LEFT_AT_DOOR_NEEDS_PHOTO",
                        "NOT_A_LEAVE_AT_DOOR_ORDER",
                        "NO_OUTCOME_TO_CORRECT",
                        "OUTCOME_ALREADY_RECORDED",
                        "PROOF_HAS_NO_FILE",
                        "PROOF_REQUIRED",
                        "REPORT_AFTER_CANCELLATION",
                        "REPORT_NOT_HELD",
                        "ROUTE_ALREADY_FINISHED",
                        "RUN_ALREADY_STARTED",
                        "SAME_OUTCOME_ALREADY_RECORDED",
                        "STOP_NOT_ON_YOUR_ROUTE",
                        "TRACKING_CLOSED",
                        "UNKNOWN_REVISION");
    }
}
