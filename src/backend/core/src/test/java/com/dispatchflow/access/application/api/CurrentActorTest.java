package com.dispatchflow.access.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.TestActors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CurrentActorTest {

    @Test
    void GivenNothingBound_WhenCurrentActorGetIsCalled_ThenItFails() {
        assertThatThrownBy(CurrentActor::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside a signed-in web call");
    }

    @Test
    void GivenABoundActor_WhenCurrentActorGetIsCalled_ThenItIsThatActor() {
        Actor dispatcher = TestActors.dispatcher();

        assertThat(CurrentActor.callWith(dispatcher, CurrentActor::get)).isSameAs(dispatcher);
    }

    @Test
    void GivenABoundActor_WhenTheCallEnds_ThenNothingIsBound() {
        AtomicReference<Actor> during = new AtomicReference<>();

        CurrentActor.callWith(TestActors.driver(), () -> {
            during.set(CurrentActor.get());
            return null;
        });

        assertThat(during.get()).isNotNull();
        assertThatThrownBy(CurrentActor::get).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void GivenABoundActorWhoseCallThrows_WhenTheCallEnds_ThenItsExceptionPassesAndNothingIsBound() {
        IllegalStateException thrown = new IllegalStateException("the call failed");

        assertThatThrownBy(() -> CurrentActor.callWith(TestActors.admin(), () -> {
                    throw thrown;
                }))
                .isSameAs(thrown);
        assertThatThrownBy(CurrentActor::get).hasMessageContaining("outside a signed-in web call");
    }
}
