package com.dispatchflow.audit.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.audit.domain.AuditError.InvalidTraceId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ADR-110: an audit record's trace ID is a W3C trace-id (NFR-93, NFR-20). */
class TraceIdTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00000000000000000000000000000001",
                "ffffffffffffffffffffffffffffffff",
                "0123456789abcdef0123456789abcdef"
            })
    void GivenThirtyTwoLowerCaseHexDigitsNotAllZero_WhenRead_ThenTheyAreKept(String text) {
        assertThat(TraceId.of(text).value()).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "00000000000000000000000000000000",
                "4bf92f3577b34da6a3ce929d0e0e473",
                "4bf92f3577b34da6a3ce929d0e0e47360",
                "4BF92F3577B34DA6A3CE929D0E0E4736",
                "4bf92f3577b34da6a3ce929d0e0e473g",
                "4bf92f35-77b3-4da6-a3ce-929d0e0e4736",
                " 4bf92f3577b34da6a3ce929d0e0e4736",
                "4bf92f3577b34da6a3ce929d0e0e473\n"
            })
    void GivenTextThatIsNotAW3cTraceId_WhenRead_ThenInvalidTraceId(String text) {
        assertRefused(() -> TraceId.of(text), new InvalidTraceId());
    }
}
