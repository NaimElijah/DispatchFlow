package com.dispatchflow.audit.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.audit.domain.AuditError.MalformedErrorCode;
import com.dispatchflow.shared.SharedKernelError.InvalidCoordinates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ADR-110: a refused attempt is audited with its refusal's error code, and nothing else. */
class ErrorCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"STALE_VERSION", "A", "BUCKET2_FULL", "SMS_GATEWAY_DOWN", "IDEMPOTENCY_KEY_REUSED"})
    void GivenAnUpperSnakeCaseCode_WhenRead_ThenItIsKeptAsGiven(String text) {
        assertThat(ErrorCode.of(text).value()).isEqualTo(text);
    }

    @Test
    void GivenACodeOfOneHundredCharacters_WhenRead_ThenItIsKept() {
        String longest = "A".repeat(100);

        assertThat(ErrorCode.of(longest).value()).hasSize(100);
    }

    @ParameterizedTest(name = "{0} characters")
    @ValueSource(ints = {101, 1_000_000})
    void GivenACodeLongerThanOneHundredCharacters_WhenRead_ThenMalformedErrorCode(int length) {
        String tooLong = "A".repeat(length);

        assertRefused(() -> ErrorCode.of(tooLong), new MalformedErrorCode());
    }

    @Test
    void GivenADomainError_WhenItsCodeIsTaken_ThenTheCodeIsTheErrorsStableCode() {
        assertThat(ErrorCode.of(new InvalidCoordinates()).value()).isEqualTo("INVALID_COORDINATES");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                "stale_version",
                "Stale_Version",
                "STALE__VERSION",
                "_STALE",
                "STALE_",
                "STALE VERSION",
                "STALE-VERSION",
                "2FA_FAILED",
                " STALE",
                "STALE\n",
                "ÉTAT"
            })
    void GivenTextThatIsNotAnUpperSnakeCaseCode_WhenRead_ThenMalformedErrorCode(String text) {
        assertRefused(() -> ErrorCode.of(text), new MalformedErrorCode());
    }
}
