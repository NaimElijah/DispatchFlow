package com.dispatchflow.example.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Example: an aggregate's test. It needs no Spring context and no database. */
class WidgetTest {

    private static final Size SIZE = new Size(10, 20);

    @Test
    void GivenASize_WhenCreated_ThenKeepsItAndGetsAnIdentity() {
        Widget widget = Widget.create(SIZE);

        assertThat(widget.size()).isEqualTo(SIZE);
        assertThat(widget.id()).isNotNull();
    }

    @Test
    void GivenTwoCreations_WhenCompared_ThenTheirIdentitiesDiffer() {
        assertThat(Widget.create(SIZE).id()).isNotEqualTo(Widget.create(SIZE).id());
    }

    @Test
    void GivenAStoredIdentity_WhenRestored_ThenKeepsThatIdentity() {
        WidgetId stored = new WidgetId(UUID.fromString("7d1f0a52-3b8e-4c1a-9a57-0e8b6f2c4d11"));

        assertThat(Widget.restore(stored, SIZE).id()).isEqualTo(stored);
    }

    @Test
    void GivenNoSize_WhenCreated_ThenRejected() {
        assertThatNullPointerException().isThrownBy(() -> Widget.create(null));
    }

    @Test
    void GivenNoIdentity_WhenRestored_ThenRejected() {
        assertThatNullPointerException().isThrownBy(() -> Widget.restore(null, SIZE));
    }

    @Test
    void GivenNoValue_WhenAWidgetIdIsCreated_ThenRejected() {
        assertThatNullPointerException().isThrownBy(() -> new WidgetId(null));
    }
}
