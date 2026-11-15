package com.dispatchflow.fleet.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.fleet.domain.FleetError.BlankDepotName;
import com.dispatchflow.fleet.domain.FleetError.BlankDriverName;
import com.dispatchflow.fleet.domain.FleetError.BlankVehicleName;
import com.dispatchflow.fleet.domain.driver.DriverBuilder;
import com.dispatchflow.fleet.domain.driver.DriverContact;
import com.dispatchflow.fleet.domain.driver.DriverName;
import com.dispatchflow.fleet.domain.register.DepotName;
import com.dispatchflow.fleet.domain.register.VehicleName;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The three names of Fleet, read through the kernel's text rule (ADR-106). */
class NamesTest {

    private static final String NO_BREAK_SPACE = "\u00a0";

    /** Each name's factory, its stored-form factory, and its blank error. */
    static Stream<Arguments> names() {
        return Stream.of(
                Arguments.of(
                        "vehicle",
                        (Function<String, Object>) VehicleName::of,
                        (Function<String, Object>) VehicleName::ofStored,
                        new BlankVehicleName()),
                Arguments.of(
                        "depot",
                        (Function<String, Object>) DepotName::of,
                        (Function<String, Object>) DepotName::ofStored,
                        new BlankDepotName()),
                Arguments.of(
                        "driver",
                        (Function<String, Object>) DriverName::of,
                        (Function<String, Object>) DriverName::ofStored,
                        new BlankDriverName()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void GivenANameWithAControlCharacterInside_WhenRead_ThenTextHasForbiddenCharacters(
            String kind, Function<String, Object> read, Function<String, Object> stored, DomainError blank) {
        assertRefused(() -> read.apply("Van \u00071"), new TextHasForbiddenCharacters(TextKind.NAME));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void GivenANameWithABidiOverrideInside_WhenRead_ThenTextHasForbiddenCharacters(
            String kind, Function<String, Object> read, Function<String, Object> stored, DomainError blank) {
        assertRefused(() -> read.apply("Van \u202e1"), new TextHasForbiddenCharacters(TextKind.NAME));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void GivenANameOf101Characters_WhenRead_ThenTextTooLong(
            String kind, Function<String, Object> read, Function<String, Object> stored, DomainError blank) {
        assertRefused(() -> read.apply("a".repeat(101)), new TextTooLong(TextKind.NAME, 100));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void GivenANameOf100Characters_WhenRead_ThenItIsKept(
            String kind, Function<String, Object> read, Function<String, Object> stored, DomainError blank) {
        assertThat(read.apply("a".repeat(100))).extracting("value").isEqualTo("a".repeat(100));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void GivenANameOfOnlyNoBreakSpaces_WhenRead_ThenItIsRefusedAsBlank(
            String kind, Function<String, Object> read, Function<String, Object> stored, DomainError blank) {
        assertRefused(() -> read.apply(NO_BREAK_SPACE + NO_BREAK_SPACE), blank);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void GivenANameEndingInANoBreakSpace_WhenRead_ThenItEqualsTheNameWithout(
            String kind, Function<String, Object> read, Function<String, Object> stored, DomainError blank) {
        assertThat(read.apply("Van 1" + NO_BREAK_SPACE)).isEqualTo(read.apply("Van 1"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void GivenADecomposedLetter_WhenRead_ThenItEqualsTheComposedOne(
            String kind, Function<String, Object> read, Function<String, Object> stored, DomainError blank) {
        assertThat(read.apply("Cafe\u0301")).isEqualTo(read.apply("Caf\u00e9"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void GivenAStoredNameTheRuleRefuses_WhenRestored_ThenStoredStateInvalid(
            String kind, Function<String, Object> read, Function<String, Object> stored, DomainError blank) {
        assertThatThrownBy(() -> stored.apply("Van \u00071")).isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> stored.apply("  ")).isInstanceOf(StoredStateInvalid.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void GivenAStoredNameTheRuleAccepts_WhenRestored_ThenItEqualsTheEnteredName(
            String kind, Function<String, Object> read, Function<String, Object> stored, DomainError blank) {
        assertThat(stored.apply("Van 1")).isEqualTo(read.apply(" Van 1 "));
    }

    @Test
    void GivenADriversNameAndContact_WhenCheckedForPersonalData_ThenBothAreMarked() {
        assertThat(DriverName.of("Dana Levi")).isInstanceOf(PersonalData.class);
        assertThat(DriverBuilder.DANA_CONTACT).isInstanceOf(PersonalData.class);
        assertThat(DriverBuilder.aDriver().snapshot()).isInstanceOf(PersonalData.class);
    }

    @Test
    void GivenANameWithSurroundingSpaces_WhenRead_ThenItIsTrimmed() {
        assertThat(VehicleName.of("  Truck 1 ").value()).isEqualTo("Truck 1");
        assertThat(DepotName.of(" North ").value()).isEqualTo("North");
        assertThat(DriverName.of(" Dana Levi ").value()).isEqualTo("Dana Levi");
    }

    @Test
    void GivenTwoNamesThatDifferOnlyInSurroundingSpaces_WhenCompared_ThenTheyAreEqual() {
        assertThat(VehicleName.of("Truck 1")).isEqualTo(VehicleName.of(" Truck 1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void GivenABlankVehicleName_WhenRead_ThenBlankVehicleName(String text) {
        assertRefused(() -> VehicleName.of(text), new BlankVehicleName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void GivenABlankDepotName_WhenRead_ThenBlankDepotName(String text) {
        assertRefused(() -> DepotName.of(text), new BlankDepotName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void GivenABlankDriverName_WhenRead_ThenBlankDriverName(String text) {
        assertRefused(() -> DriverName.of(text), new BlankDriverName());
    }

    @Test
    void GivenADriversNameAndPhone_WhenPrinted_ThenNeitherAppears() {
        DriverContact contact = new DriverContact(DriverName.of("Dana Levi"), PhoneNumber.parse("052-345-6789"));

        assertThat(DriverName.of("Dana Levi").toString()).doesNotContain("Dana");
        assertThat(contact.toString()).doesNotContain("Dana").doesNotContain("345");
    }
}
