package com.dispatchflow.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.audit.domain.AuditOutcome.Refused;
import com.dispatchflow.audit.domain.AuditOutcome.Succeeded;
import com.dispatchflow.shared.SharedKernelError.InvalidCoordinates;
import org.junit.jupiter.api.Test;

/**
 * ADR-110 point 1: an audited attempt either succeeded or was refused with its refusal's error
 * code, so a failed attempt such as a refused sign-in is audited too (NFR-23, NFR-93).
 */
class AuditOutcomeTest {

    @Test
    void GivenASuccess_WhenRecorded_ThenTheOutcomeIsSucceeded() {
        assertThat(AuditOutcome.succeeded()).isEqualTo(new Succeeded());
    }

    @Test
    void GivenARefusalWithANamedError_WhenRecorded_ThenTheOutcomeHoldsOnlyTheErrorsCode() {
        assertThat(AuditOutcome.refused(new InvalidCoordinates()))
                .isEqualTo(new Refused(ErrorCode.of("INVALID_COORDINATES")));
    }

    @Test
    void GivenARefusalOutsideTheDomain_WhenRecordedWithItsCode_ThenTheOutcomeHoldsThatCode() {
        assertThat(AuditOutcome.refused(ErrorCode.of("STALE_VERSION")))
                .isEqualTo(new Refused(ErrorCode.of("STALE_VERSION")));
    }
}
