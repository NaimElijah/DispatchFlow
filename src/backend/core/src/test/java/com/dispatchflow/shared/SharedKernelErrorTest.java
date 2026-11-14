package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the kernel's error codes, which both apps translate (NFR-144). That each error has a
 * refusal test is checked for every module by {@code ErrorRefusalTestsTest}.
 */
class SharedKernelErrorTest {

    @Test
    void GivenTheSharedKernelErrors_WhenTheirCodesAreListed_ThenTheyAreThePinnedCodes() {
        List<String> codes = Arrays.stream(SharedKernelError.class.getPermittedSubclasses())
                .map(type -> DomainError.codeOf(type.asSubclass(DomainError.class)))
                .sorted()
                .toList();

        assertThat(codes)
                .containsExactly(
                        "DATE_OUT_OF_RANGE",
                        "INVALID_COORDINATES",
                        "INVALID_PHONE_NUMBER",
                        "INVALID_TIME_WINDOW",
                        "NONEXISTENT_LOCAL_TIME",
                        "NON_POSITIVE_QUANTITY",
                        "OUTSIDE_THE_DAY",
                        "PRIORITY_OUT_OF_RANGE",
                        "QUANTITY_OUT_OF_RANGE",
                        "TEXT_HAS_FORBIDDEN_CHARACTERS",
                        "TEXT_TOO_LONG");
    }
}
