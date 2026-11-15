package com.dispatchflow.orders.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.orders.domain.OrdersError.BlankAddress;
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

/**
 * The Orders context's free text goes through the kernel's {@code PlainText} (ADR-106): each value
 * with its kind, its cap and its forbidden characters, and its own blank error.
 */
class FreeTextTest {

    private static final String RIGHT_TO_LEFT_OVERRIDE = String.valueOf((char) 0x202E);
    private static final String NO_BREAK_SPACE = String.valueOf((char) 0x00A0);

    /** Each free-text value of the context, with the kind it is checked as. */
    static Stream<Arguments> values() {
        return Stream.of(
                Arguments.of("customer or recipient name", TextKind.NAME, (Function<String, Object>) ContactName::of),
                Arguments.of("address", TextKind.ADDRESS, (Function<String, Object>) Address::of),
                Arguments.of("note for the driver", TextKind.NOTE, (Function<String, Object>) DriverNote::of),
                Arguments.of("floor", TextKind.NAME, (Function<String, Object>) Floor::of),
                Arguments.of("apartment", TextKind.NAME, (Function<String, Object>) Apartment::of),
                Arguments.of("entrance code", TextKind.NAME, (Function<String, Object>) EntranceCode::of),
                Arguments.of("outside reference", TextKind.NAME, (Function<String, Object>) ExternalReference::of));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("values")
    void GivenABidiOverrideInside_WhenTheValueIsRead_ThenTextHasForbiddenCharacters(
            String value, TextKind kind, Function<String, Object> read) {
        assertRefused(() -> read.apply("4" + RIGHT_TO_LEFT_OVERRIDE + "5 הרצל"), new TextHasForbiddenCharacters(kind));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("values")
    void GivenOneCodePointPastTheCap_WhenTheValueIsRead_ThenTextTooLong(
            String value, TextKind kind, Function<String, Object> read) {
        assertRefused(() -> read.apply("א".repeat(kind.maxLength() + 1)), new TextTooLong(kind, kind.maxLength()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("values")
    void GivenTextAtTheCap_WhenTheValueIsRead_ThenItIsKept(String value, TextKind kind, Function<String, Object> read) {
        assertThat(read.apply("א".repeat(kind.maxLength()))).isNotNull();
    }

    @Test
    void GivenOnlyNoBreakSpaces_WhenAnAddressIsBuilt_ThenBlankAddress() {
        assertRefused(() -> Address.of(NO_BREAK_SPACE.repeat(3)), new BlankAddress());
    }

    @Test
    void GivenANoteWithCrlfLineEnds_WhenRead_ThenItIsStoredWithLineFeeds() {
        assertThat(DriverNote.of("קומה 3\r\nדלת שמאל\r\n").orElseThrow().text()).isEqualTo("קומה 3\nדלת שמאל");
    }

    @Test
    void GivenALineBreakInAName_WhenRead_ThenTextHasForbiddenCharacters() {
        assertRefused(() -> ContactName.of("דנה\nלוי"), new TextHasForbiddenCharacters(TextKind.NAME));
    }

    @Test
    void GivenAReferenceWithATrailingNoBreakSpaceOrDecomposedLetters_WhenRead_ThenItEqualsItsNormalForm() {
        assertThat(ExternalReference.of("R1" + NO_BREAK_SPACE)).isEqualTo(ExternalReference.of("R1"));
        assertThat(ExternalReference.of("Cafe" + (char) 0x0301)).isEqualTo(ExternalReference.of("Caf" + (char) 0x00E9));
    }

    @Test
    void GivenStoredTextTheRuleRefuses_WhenRebuilt_ThenStoredStateInvalid() {
        String damaged = "דנה" + RIGHT_TO_LEFT_OVERRIDE + "לוי";

        assertThatThrownBy(() -> ContactName.ofStored(damaged)).isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> Address.ofStored(damaged)).isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> DriverNote.ofStored(damaged)).isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> Floor.ofStored(damaged)).isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> ExternalReference.ofStored(damaged)).isInstanceOf(StoredStateInvalid.class);
    }

    @Test
    void GivenABlankStoredNote_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> DriverNote.ofStored("  ")).isInstanceOf(StoredStateInvalid.class);
    }

    @Test
    void GivenStoredText_WhenRebuilt_ThenItEqualsTheEnteredValue() {
        assertThat(ContactName.ofStored("דנה לוי")).isEqualTo(ContactName.of("דנה לוי"));
        assertThat(EntranceCode.ofStored("12#"))
                .isEqualTo(EntranceCode.of("12#").orElseThrow());
    }
}
