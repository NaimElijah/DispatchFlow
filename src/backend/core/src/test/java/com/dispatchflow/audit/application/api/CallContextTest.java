package com.dispatchflow.audit.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CallContextTest {

    private static final String STEP = "expireImportPreviews";
    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    void GivenAStepAndATraceId_WhenServerStepIsBuilt_ThenItHasNoUserSessionOrClientAndTheStepAsItsOperation() {
        CallContext step = CallContext.serverStep(STEP, TRACE);

        assertThat(step.user()).isEmpty();
        assertThat(step.session()).isEmpty();
        assertThat(step.client()).isEmpty();
        assertThat(step.operation()).isEqualTo(STEP);
        assertThat(step.traceId()).isEqualTo(TRACE);
    }

    @Test
    void GivenNoStep_WhenServerStepIsBuilt_ThenItIsRefused() {
        assertThatThrownBy(() -> CallContext.serverStep(nothing(), TRACE)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void GivenNoTraceId_WhenServerStepIsBuilt_ThenItIsRefused() {
        assertThatThrownBy(() -> CallContext.serverStep(STEP, nothing())).isInstanceOf(NullPointerException.class);
    }

    // A null that the null-marked signature doesn't admit, to test the refusal itself.
    private static String nothing() {
        return null;
    }
}
