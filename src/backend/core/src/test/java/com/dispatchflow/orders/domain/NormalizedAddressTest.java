package com.dispatchflow.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * FR-398's normalization, step by step, as ADR-086 and ADR-102 define it: punctuation becomes a space,
 * and the geresh and gershayim stay. Each case names the step it shows; the abbreviation table holds
 * only ADR-086's three entries.
 */
class NormalizedAddressTest {

    static Stream<Arguments> steps() {
        return Stream.of(
                Arguments.of("NFC composes a decomposed letter", "cafe\u0301", "caf\u00e9"),
                Arguments.of("whitespace is trimmed", "  הרצל 5  ", "הרצל 5"),
                Arguments.of("whitespace runs collapse to one space", "הרצל \t\n  5", "הרצל 5"),
                Arguments.of("a no-break space is whitespace", "הרצל\u00a0\u00a05", "הרצל 5"),
                Arguments.of("an apostrophe is the geresh", "רח' הרצל", "רחוב הרצל"),
                Arguments.of("a left single quote is the geresh", "רח\u2018 הרצל", "רחוב הרצל"),
                Arguments.of("a right single quote is the geresh", "רח\u2019 הרצל", "רחוב הרצל"),
                Arguments.of("a backtick is the geresh", "רח` הרצל", "רחוב הרצל"),
                Arguments.of("the geresh itself", "רח\u05f3 הרצל", "רחוב הרצל"),
                Arguments.of("a geresh ends its word", "רח'הרצל 5", "רחוב הרצל 5"),
                Arguments.of("a double quote is the gershayim", "ת\"א", "תל אביב"),
                Arguments.of("two apostrophes are the gershayim", "ת''א", "תל אביב"),
                Arguments.of("a left double quote is the gershayim", "ת\u201cא", "תל אביב"),
                Arguments.of("a right double quote is the gershayim", "ת\u201dא", "תל אביב"),
                Arguments.of("the gershayim itself", "ת\u05f4א", "תל אביב"),
                Arguments.of("שד\u05f3 expands to שדרות", "שד\u05f3 רוטשילד 1", "שדרות רוטשילד 1"),
                Arguments.of("an abbreviation inside a word is left alone", "ברח\u05f3 3", "ברח\u05f3 3"),
                Arguments.of("an abbreviation not in the table is left alone", "שכ\u05f3 התקווה", "שכ\u05f3 התקווה"),
                Arguments.of("a geresh outside an abbreviation stays", "צ'רנחובסקי 4", "צ\u05f3 רנחובסקי 4"),
                Arguments.of("punctuation becomes a space", "הרצל (5), דירה 3!", "הרצל 5 דירה 3"),
                Arguments.of("a slash keeps numbers apart", "הרצל 5/3", "הרצל 5 3"),
                Arguments.of("a comma between words keeps them apart", "5,תל", "5 תל"),
                Arguments.of("a hyphen keeps words apart", "תל-אביב", "תל אביב"),
                Arguments.of("Latin letters are lower-cased", "HaYarkon ST 12", "hayarkon st 12"),
                Arguments.of("only Latin letters are lower-cased", "ΑΘΗΝΑ 7", "ΑΘΗΝΑ 7"));
    }

    static Stream<Arguments> sameAddress() {
        return Stream.of(
                Arguments.of("רח' הרצל 5, ת\"א", "  רחוב  הרצל 5 תל אביב "),
                Arguments.of("5,תל", "5, תל"),
                Arguments.of("רח'הרצל", "רח\u05f3 הרצל"));
    }

    static Stream<Arguments> differentAddresses() {
        return Stream.of(Arguments.of("הרצל 5/3", "הרצל 53"), Arguments.of("שכ\u05f3 התקווה", "שכונה התקווה"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("steps")
    void GivenAnAddress_WhenNormalized_ThenEachStepOfFr398Applies(String step, String typed, String normalized) {
        assertThat(NormalizedAddress.of(Address.of(typed)).text()).isEqualTo(normalized);
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("sameAddress")
    void GivenTwoWaysOfTypingOneAddress_WhenNormalized_ThenTheyAreEqual(String one, String other) {
        NormalizedAddress first = NormalizedAddress.of(Address.of(one));
        NormalizedAddress second = NormalizedAddress.of(Address.of(other));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @ParameterizedTest(name = "{0} differs from {1}")
    @MethodSource("differentAddresses")
    void GivenTwoDifferentAddresses_WhenNormalized_ThenTheyDiffer(String one, String other) {
        assertThat(NormalizedAddress.of(Address.of(one))).isNotEqualTo(NormalizedAddress.of(Address.of(other)));
    }

    @Test
    void GivenANormalizedAddress_WhenPrinted_ThenTheAddressIsHidden() {
        assertThat(NormalizedAddress.of(Address.of("הרצל 5")).toString()).doesNotContain("הרצל");
        assertThat(Address.of("הרצל 5").toString()).doesNotContain("הרצל");
    }
}
