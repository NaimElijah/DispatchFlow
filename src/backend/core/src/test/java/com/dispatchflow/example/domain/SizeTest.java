package com.dispatchflow.example.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Example: a domain test. It needs no Spring context and no database. */
class SizeTest {

    @Test
    void GivenTheSmallestPositiveDimensions_WhenCreated_ThenKeepsBoth() {
        Size size = new Size(1, 1);

        assertThat(size.width()).isEqualTo(1);
        assertThat(size.height()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void GivenAWidthThatIsNotPositive_WhenCreated_ThenRejected(int width) {
        assertThatThrownBy(() -> new Size(width, 10))
                .isInstanceOf(InvalidWidgetException.class)
                .hasMessageContaining("width must be positive");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void GivenAHeightThatIsNotPositive_WhenCreated_ThenRejected(int height) {
        assertThatThrownBy(() -> new Size(10, height))
                .isInstanceOf(InvalidWidgetException.class)
                .hasMessageContaining("height must be positive");
    }
}
