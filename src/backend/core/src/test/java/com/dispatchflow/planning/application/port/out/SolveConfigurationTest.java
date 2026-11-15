package com.dispatchflow.planning.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.planning.application.port.out.SolveConfiguration.Factor;
import com.dispatchflow.planning.application.port.out.SolveConfiguration.Values;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SolveConfigurationTest {

    private static final Factor FIVE = new Factor(new BigDecimal("5"));
    private static final Factor TEN = new Factor(new BigDecimal("10"));
    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    @Test
    void GivenTheStartingValues_WhenTheyAreBuilt_ThenTheyAreKept() {
        Values values = new Values(FIVE, TEN, FIVE_MINUTES, true);

        assertThat(values.doorWindowRate()).isEqualTo(FIVE);
        assertThat(values.latenessRate()).isEqualTo(TEN);
        assertThat(values.movedStopCost()).isEqualTo(FIVE_MINUTES);
        assertThat(values.breaksReserved()).isTrue();
    }

    @Test
    void GivenANegativeMovedStopCost_WhenTheValuesAreBuilt_ThenTheyAreRefused() {
        Duration negative = Duration.ofSeconds(-1);

        assertThatThrownBy(() -> new Values(FIVE, FIVE, negative, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenAZeroMovedStopCost_WhenTheValuesAreBuilt_ThenTheyAreKept() {
        assertThat(new Values(FIVE, FIVE, Duration.ZERO, false).movedStopCost()).isZero();
    }

    @Test
    void GivenAFactorBelowZero_WhenItIsBuilt_ThenItIsRefused() {
        BigDecimal belowZero = new BigDecimal("-0.1");

        assertThatThrownBy(() -> new Factor(belowZero)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenAZeroFactor_WhenItIsBuilt_ThenItIsKept() {
        assertThat(new Factor(BigDecimal.ZERO).value()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void GivenTheSameFactorWrittenTwoWays_WhenBothAreBuilt_ThenTheyAreEqual() {
        assertThat(new Factor(new BigDecimal("5.0"))).isEqualTo(new Factor(new BigDecimal("5")));
    }
}
