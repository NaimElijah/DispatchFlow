package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TraceparentTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PARENT_ID = "00f067aa0ba902b7";
    private static final String VALID = "00-" + TRACE_ID + "-" + PARENT_ID + "-01";

    @Test
    void GivenAValidVersion00Header_WhenRead_ThenItsTraceIdAlone() {
        assertThat(Traceparent.traceIdOf(List.of(VALID))).contains(TRACE_ID);
    }

    @Test
    void GivenAnUpperCaseHeader_WhenRead_ThenNone() {
        assertThat(Traceparent.traceIdOf(List.of(VALID.toUpperCase(Locale.ROOT))))
                .isEmpty();
    }

    @Test
    void GivenVersionFf_WhenRead_ThenNone() {
        assertThat(Traceparent.traceIdOf(List.of("ff-" + TRACE_ID + "-" + PARENT_ID + "-01")))
                .isEmpty();
    }

    @Test
    void GivenAHigherVersionWithExtraFields_WhenRead_ThenItIsReadInVersion00sLayout() {
        assertThat(Traceparent.traceIdOf(List.of("01-" + TRACE_ID + "-" + PARENT_ID + "-01-what-comes-next")))
                .contains(TRACE_ID);
    }

    @Test
    void GivenAHigherVersionOfExactlyVersion00sLength_WhenRead_ThenItsTraceId() {
        String exact = "01-" + TRACE_ID + "-" + PARENT_ID + "-01";

        assertThat(exact).hasSize(55);
        assertThat(Traceparent.traceIdOf(List.of(exact))).contains(TRACE_ID);
    }

    @Test
    void GivenAHigherVersionRunningOnPastItsFlags_WhenRead_ThenNone() {
        assertThat(Traceparent.traceIdOf(List.of("01-" + TRACE_ID + "-" + PARENT_ID + "-01x")))
                .isEmpty();
    }

    @Test
    void GivenAVersion00HeaderLongerThanItsLayout_WhenRead_ThenNone() {
        assertThat(Traceparent.traceIdOf(List.of(VALID + "-extra"))).isEmpty();
    }

    @Test
    void GivenAnAllZeroParentId_WhenRead_ThenNone() {
        assertThat(Traceparent.traceIdOf(List.of("00-" + TRACE_ID + "-0000000000000000-01")))
                .isEmpty();
    }

    @Test
    void GivenAnAllZeroTraceId_WhenRead_ThenNone() {
        assertThat(Traceparent.traceIdOf(List.of("00-00000000000000000000000000000000-" + PARENT_ID + "-01")))
                .isEmpty();
    }

    @Test
    void GivenTwoHeaders_WhenRead_ThenNone() {
        assertThat(Traceparent.traceIdOf(List.of(VALID, VALID))).isEmpty();
    }

    @Test
    void GivenNoHeader_WhenRead_ThenNone() {
        assertThat(Traceparent.traceIdOf(List.of())).isEmpty();
    }

    @Test
    void GivenAVersion00HeaderShorterThanItsLayout_WhenRead_ThenNone() {
        assertThat(Traceparent.traceIdOf(List.of(VALID.substring(0, VALID.length() - 1))))
                .isEmpty();
    }

    @Test
    void GivenAHigherVersionShorterThanVersion00sLayout_WhenRead_ThenNone() {
        String exact = "01-" + TRACE_ID + "-" + PARENT_ID + "-01";

        assertThat(Traceparent.traceIdOf(List.of(exact.substring(0, exact.length() - 1))))
                .isEmpty();
        assertThat(Traceparent.traceIdOf(List.of("01-" + TRACE_ID))).isEmpty();
    }
}
