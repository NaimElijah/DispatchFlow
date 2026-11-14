package com.dispatchflow.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins Audit's error codes, which both apps translate (NFR-144). That each error has a refusal test
 * is checked for every module by {@code ErrorRefusalTestsTest}.
 */
class AuditErrorTest {

    @Test
    void GivenTheAuditErrors_WhenTheirCodesAreListed_ThenTheyAreThePinnedCodes() {
        List<String> codes = Arrays.stream(AuditError.class.getPermittedSubclasses())
                .map(type -> DomainError.codeOf(type.asSubclass(DomainError.class)))
                .sorted()
                .toList();

        assertThat(codes)
                .containsExactly(
                        "INVALID_APP_VERSION", "INVALID_TRACE_ID", "MALFORMED_ERROR_CODE", "MALFORMED_OPERATION_NAME");
    }
}
