package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.SharedKernelError.Measure;
import com.dispatchflow.shared.SharedKernelError.NonPositiveQuantity;
import com.dispatchflow.shared.SharedKernelError.QuantityOutOfRange;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link Weight} and {@link Volume}, which share their rules and their errors (BR-50). Their
 * columns are {@code numeric(8,2)} and {@code numeric(9,2)} (ADR-061).
 */
class QuantityTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1", "12.3", "999999.99", "999999.990000"})
    void GivenAWeightTheColumnHolds_WhenBuilt_ThenItIsKeptExactly(String kilograms) {
        assertThat(Weight.kilograms(new BigDecimal(kilograms)).kilograms()).isEqualByComparingTo(kilograms);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1", "12.3", "9999999.99"})
    void GivenAVolumeTheColumnHolds_WhenBuilt_ThenItIsKeptExactly(String litres) {
        assertThat(Volume.litres(new BigDecimal(litres)).litres()).isEqualByComparingTo(litres);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.000", "-0.01", "-5"})
    void GivenAWeightThatIsNotPositive_WhenBuilt_ThenNonPositiveQuantity(String kilograms) {
        assertRefused(() -> Weight.kilograms(new BigDecimal(kilograms)), new NonPositiveQuantity(Measure.WEIGHT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.000", "-0.01", "-5"})
    void GivenAVolumeThatIsNotPositive_WhenBuilt_ThenNonPositiveQuantity(String litres) {
        assertRefused(() -> Volume.litres(new BigDecimal(litres)), new NonPositiveQuantity(Measure.VOLUME));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0.001",
                "1.005",
                "1000000",
                "1E+6",
                "123456789012345678901",
                "0.000000000000000000001",
                "1E+21",
                "1E-21",
                "1E+1000000000",
                "1E-1000000000",
                "1E+2147483648"
            })
    void GivenAWeightTheColumnCannotHold_WhenBuilt_ThenQuantityOutOfRange(String kilograms) {
        assertRefused(() -> Weight.kilograms(new BigDecimal(kilograms)), new QuantityOutOfRange(Measure.WEIGHT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "10000000", "1E+7", "1E+1000000000", "1E-1000000000"})
    void GivenAVolumeTheColumnCannotHold_WhenBuilt_ThenQuantityOutOfRange(String litres) {
        assertRefused(() -> Volume.litres(new BigDecimal(litres)), new QuantityOutOfRange(Measure.VOLUME));
    }

    @Test
    void GivenTheLargestVolume_WhenTheWeightColumnIsAsked_ThenItIsRefusedThereButHeldAsAVolume() {
        BigDecimal sevenDigits = new BigDecimal("1000000");

        assertRefused(() -> Weight.kilograms(sevenDigits), new QuantityOutOfRange(Measure.WEIGHT));
        assertThat(Volume.litres(sevenDigits).litres()).isEqualByComparingTo(sevenDigits);
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

    @Test
    void GivenAWholeHundred_WhenPrinted_ThenItIsPlainDigits() {
        assertThat(Weight.kilograms(new BigDecimal("100.00")).kilograms().toString())
                .isEqualTo("100");
        assertThat(Volume.litres(new BigDecimal("1E+2")).litres().toString()).isEqualTo("100");
    }

    @Test
    void GivenTwoWeights_WhenCompared_ThenTheLighterComesFirst() {
        assertThat(Weight.kilograms(new BigDecimal("1.5"))).isLessThan(Weight.kilograms(new BigDecimal("2")));
        assertThat(Weight.kilograms(new BigDecimal("2.00")))
                .isEqualByComparingTo(Weight.kilograms(new BigDecimal("2")));
    }

    @Test
    void GivenTwoVolumes_WhenCompared_ThenTheSmallerComesFirst() {
        assertThat(Volume.litres(new BigDecimal("0.5"))).isLessThan(Volume.litres(new BigDecimal("0.51")));
    }
}
