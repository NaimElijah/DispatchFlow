package com.dispatchflow.audit.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentCallTest {

    private static final CallContext SIGNED_IN = new CallContext(
            Optional.of(AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-0000000000a1"))),
            Optional.of(SessionId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-0000000000b1"))),
            Optional.of(new ClientRelease(ClientKind.WEB, "1.4.0")),
            "publishPlan",
            "4bf92f3577b34da6a3ce929d0e0e4736");
    private static final CallContext OTHER =
            CallContext.serverStep("expireImportPreviews", "0af7651916cd43dd8448eb211c80319c");

    @Test
    void GivenNothingBound_WhenCurrentCallGetIsCalled_ThenItFails() {
        assertThatThrownBy(CurrentCall::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside a web call");
    }

    @Test
    void GivenABoundCall_WhenCurrentCallGetIsCalled_ThenItIsThatCall() {
        CallContext read = CurrentCall.callWith(SIGNED_IN, CurrentCall::get);

        assertThat(read).isSameAs(SIGNED_IN);
    }

    @Test
    void GivenABoundCall_WhenTheCallEnds_ThenNothingIsBound() {
        CurrentCall.callWith(SIGNED_IN, CurrentCall::get);

        assertThatThrownBy(CurrentCall::get).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void GivenABoundCallThatThrows_WhenTheCallEnds_ThenItsExceptionPassesAndNothingIsBound() {
        IllegalArgumentException thrown = new IllegalArgumentException("the call failed");

        assertThatThrownBy(() -> CurrentCall.callWith(SIGNED_IN, () -> {
                    throw thrown;
                }))
                .isSameAs(thrown);
        assertThatThrownBy(CurrentCall::get).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void GivenACallBoundInsideAnother_WhenEachIsRead_ThenTheInnerHidesTheOuterUntilItEnds() {
        CallContext outerAfter = CurrentCall.callWith(SIGNED_IN, () -> {
            assertThat(CurrentCall.callWith(OTHER, CurrentCall::get)).isSameAs(OTHER);
            return CurrentCall.get();
        });

        assertThat(outerAfter).isSameAs(SIGNED_IN);
    }
}
