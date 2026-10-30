package com.dispatchflow.example.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.example.api.CreateWidgetCommand;
import com.dispatchflow.example.api.ExampleApi;
import com.dispatchflow.example.api.WidgetRejectedException;
import com.dispatchflow.example.api.WidgetView;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Example: a service-layer test, through the context's api, with a fake of its own port. */
class ExampleServiceTest {

    private final InMemoryWidgetRepository widgets = new InMemoryWidgetRepository();
    private final ExampleApi example = new ExampleService(widgets);

    @Test
    void GivenPositiveDimensions_WhenCreated_ThenStoredAndReturnedWithAnIdentity() {
        WidgetView created = example.create(new CreateWidgetCommand(10, 20));

        assertThat(created.id()).isNotNull();
        assertThat(created.width()).isEqualTo(10);
        assertThat(created.height()).isEqualTo(20);
        assertThat(widgets.size()).isEqualTo(1);
    }

    @Test
    void GivenAZeroHeight_WhenCreated_ThenRejectedAndNothingStored() {
        assertThatThrownBy(() -> example.create(new CreateWidgetCommand(10, 0)))
                .isInstanceOf(WidgetRejectedException.class)
                .hasMessageContaining("height must be positive");
        assertThat(widgets.size()).isZero();
    }

    @Test
    void GivenACreatedWidget_WhenFound_ThenReturnedAsCreated() {
        WidgetView created = example.create(new CreateWidgetCommand(10, 20));

        assertThat(example.find(created.id())).contains(created);
    }

    @Test
    void GivenAnUnknownIdentity_WhenFound_ThenEmpty() {
        assertThat(example.find(UUID.fromString("7d1f0a52-3b8e-4c1a-9a57-0e8b6f2c4d11")))
                .isEmpty();
    }
}
