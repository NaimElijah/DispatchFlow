package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.SharedKernelError.NonPositiveQuantity;
import com.dispatchflow.shared.SharedKernelError.NonPositiveQuantity.Measure;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link Weight} and {@link Volume}, which share their rule and their error (BR-50). */
class QuantityTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "1", "12.3", "1500"})
    void GivenAPositiveWeight_WhenBuilt_ThenItIsKeptExactly(String kilograms) {
        assertThat(Weight.kilograms(new BigDecimal(kilograms)).kilograms()).isEqualByComparingTo(kilograms);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "1", "12.3", "1500"})
    void GivenAPositiveVolume_WhenBuilt_ThenItIsKeptExactly(String litres) {
        assertThat(Volume.litres(new BigDecimal(litres)).litres()).isEqualByComparingTo(litres);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.000", "-0.001", "-5"})
    void GivenAWeightThatIsNotPositive_WhenBuilt_ThenNonPositiveQuantity(String kilograms) {
        assertRefused(() -> Weight.kilograms(new BigDecimal(kilograms)), new NonPositiveQuantity(Measure.WEIGHT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.000", "-0.001", "-5"})
    void GivenAVolumeThatIsNotPositive_WhenBuilt_ThenNonPositiveQuantity(String litres) {
        assertRefused(() -> Volume.litres(new BigDecimal(litres)), new NonPositiveQuantity(Measure.VOLUME));
    }

    @Test
    void GivenTheSameWeightAtTwoScales_WhenCompared_ThenTheyAreEqual() {
        assertThat(Weight.kilograms(new BigDecimal("1.50"))).isEqualTo(Weight.kilograms(new BigDecimal("1.5")));
        assertThat(Weight.kilograms(new BigDecimal("1.50")).hashCode())
                .isEqualTo(Weight.kilograms(new BigDecimal("1.5")).hashCode());
    }

    @Test
    void GivenTheSameVolumeAtTwoScales_WhenCompared_ThenTheyAreEqual() {
        assertThat(Volume.litres(new BigDecimal("2.0"))).isEqualTo(Volume.litres(new BigDecimal("2")));
    }
}
