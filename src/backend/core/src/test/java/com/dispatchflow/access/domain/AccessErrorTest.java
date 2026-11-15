package com.dispatchflow.access.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins Access's error codes, which both apps translate (NFR-144). That each error has a refusal
 * test is checked for every module by {@code ErrorRefusalTestsTest}.
 */
class AccessErrorTest {

    @Test
    void GivenTheAccessErrors_WhenTheirCodesAreListed_ThenTheyAreThePinnedCodes() {
        List<String> codes = Arrays.stream(AccessError.class.getPermittedSubclasses())
                .map(type -> DomainError.codeOf(type.asSubclass(DomainError.class)))
                .sorted()
                .toList();

        assertThat(codes)
                .containsExactly(
                        "ACCOUNT_ANONYMIZED",
                        "ACCOUNT_DEACTIVATED",
                        "ACCOUNT_HAS_NO_ROLE",
                        "ACCOUNT_NOT_DEACTIVATED",
                        "ACTIVE_ADMIN_EXISTS",
                        "BLANK_COMPANY_NAME",
                        "COMPANY_NAME_TOO_LONG",
                        "EMAIL_IN_USE",
                        "EMAIL_OF_ANOTHER_ADMIN",
                        "INVALID_DEFAULT_WINDOW",
                        "INVALID_EMAIL",
                        "LINK_ALREADY_USED",
                        "LINK_EXPIRED",
                        "LINK_VOIDED",
                        "NON_POSITIVE_DEFAULT_SERVICE_TIME",
                        "NOT_AN_ADMIN",
                        "NO_ROLE_GIVEN",
                        "PASSWORD_ALREADY_SET",
                        "PASSWORD_HAS_UNPAIRED_SURROGATE",
                        "PASSWORD_NOT_SET",
                        "PASSWORD_TOO_COMMON",
                        "PASSWORD_TOO_SHORT",
                        "REFRESH_REFUSED",
                        "RETENTION_NOT_OVER",
                        "SESSION_REVOKED",
                        "STEP_UP_REQUIRED",
                        "TOO_FEW_ADMINS",
                        "UNKNOWN_LINK");
    }
}
