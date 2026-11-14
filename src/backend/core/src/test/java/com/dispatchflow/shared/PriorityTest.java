package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.SharedKernelError.PriorityOutOfRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PriorityTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void GivenALevelFromOneToFive_WhenBuilt_ThenItIsKept(int level) {
        assertThat(Priority.of(level).level()).isEqualTo(level);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void GivenALevelOutsideOneToFive_WhenBuilt_ThenPriorityOutOfRange(int level) {
        assertRefused(() -> Priority.of(level), new PriorityOutOfRange(level));
    }

    @Test
    void GivenNoPriority_WhenTheDefaultIsTaken_ThenItIsThree() {
        assertThat(Priority.byDefault()).isEqualTo(Priority.of(3));
    }
}
