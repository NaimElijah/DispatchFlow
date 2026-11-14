package com.dispatchflow.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins Reporting's error codes, which both apps translate (NFR-144). That each error has a refusal
 * test is checked for every module by {@code ErrorRefusalTestsTest}.
 */
class ReportingErrorTest {

    @Test
    void GivenTheReportingErrors_WhenTheirCodesAreListed_ThenTheyAreThePinnedCodes() {
        List<String> codes = Arrays.stream(ReportingError.class.getPermittedSubclasses())
                .map(type -> DomainError.codeOf(type.asSubclass(DomainError.class)))
                .sorted()
                .toList();

        assertThat(codes)
                .containsExactly(
                        "INVALID_ON_TIME_COUNTS",
                        "NEGATIVE_DISTANCE",
                        "NEGATIVE_DRIVER_HOURS",
                        "ORDER_COUNTED_TWICE",
                        "STOP_COUNTED_TWICE");
    }
}
