package com.dispatchflow.fleet.domain.register;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.fleet.domain.FleetError.InvalidPlate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ADR-103 point 2: how a plate is normalized, and what is refused. */
class PlateTest {

    @ParameterizedTest
    @ValueSource(strings = {"12-345-67", "1234567", " 12 345 67 ", "12 - 345 - 67", "-1234567-"})
    void GivenOnePlateWrittenInSeveralWays_WhenRead_ThenEachNormalizesToTheSamePlate(String text) {
        assertThat(Plate.of(text)).isEqualTo(Plate.of("1234567"));
        assertThat(Plate.of(text).value()).isEqualTo("1234567");
    }

    @Test
    void GivenLowerCaseLatinLetters_WhenRead_ThenTheyAreUpperCased() {
        assertThat(Plate.of("ab-123-cd").value()).isEqualTo("AB123CD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1234567890", "ABCDEFGHIJ"})
    void GivenOneToTenLettersOrDigits_WhenRead_ThenTheyAreKept(String text) {
        assertThat(Plate.of(text).value()).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "- -",
                "12345678901",
                "12_345",
                "12.345.67",
                "12/345",
                "\u05d0\u05d1123",
                "12\u00a034567",
                "\u01311",
                "\u00df1",
                "\uff11\uff12\uff13",
                "12\t345"
            })
    void GivenTextThatIsNotOneToTenAsciiLettersOrDigits_WhenRead_ThenInvalidPlate(String text) {
        assertRefused(() -> Plate.of(text), new InvalidPlate());
    }

    @Test
    void GivenADottedLowerCaseI_WhenRead_ThenItIsTheLatinCapitalI() {
        assertThat(Plate.of("i1").value()).isEqualTo("I1");
    }
}
