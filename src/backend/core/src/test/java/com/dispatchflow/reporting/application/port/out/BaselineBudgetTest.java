package com.dispatchflow.reporting.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BaselineBudgetTest {

    @Test
    void GivenOneSolution_WhenTheBudgetIsBuilt_ThenItIsKept() {
        assertThat(new BaselineBudget(1).solutions()).isEqualTo(1);
    }

    @Test
    void GivenZeroSolutions_WhenTheBudgetIsBuilt_ThenItIsRefused() {
        assertThatThrownBy(() -> new BaselineBudget(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenANegativeCount_WhenTheBudgetIsBuilt_ThenItIsRefused() {
        assertThatThrownBy(() -> new BaselineBudget(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
