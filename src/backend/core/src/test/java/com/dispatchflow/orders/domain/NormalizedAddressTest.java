package com.dispatchflow.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * FR-398's normalization, step by step, as ADR-086 and ADR-102 define it: punctuation becomes a space,
 * and the geresh and gershayim stay. Each case names the step it shows. The abbreviation table holds
 * ADR-086's three entries and ADR-137's six, each with a case of its own, and the abbreviations ADR-137
 * leaves out stay as typed.
 */
class NormalizedAddressTest {

    static Stream<Arguments> steps() {
        return Stream.of(
                Arguments.of("NFC composes a decomposed letter", "cafe\u0301", "caf\u00e9"),
                Arguments.of(
                        "a format character inside a decomposed letter is dropped before NFC",
                        "cafe\u200c\u0301",
                        "caf\u00e9"),
                Arguments.of("whitespace is trimmed", "  הרצל 5  ", "הרצל 5"),
                Arguments.of("whitespace runs collapse to one space", "הרצל \u2003  5", "הרצל 5"),
                Arguments.of("a no-break space is whitespace", "הרצל\u00a0\u00a05", "הרצל 5"),
                Arguments.of("an apostrophe is the geresh", "רח' הרצל", "רחוב הרצל"),
                Arguments.of("a left single quote is the geresh", "רח\u2018 הרצל", "רחוב הרצל"),
                Arguments.of("a right single quote is the geresh", "רח\u2019 הרצל", "רחוב הרצל"),
                Arguments.of("a backtick is the geresh", "רח` הרצל", "רחוב הרצל"),
                Arguments.of("the geresh itself", "רח\u05f3 הרצל", "רחוב הרצל"),
                Arguments.of("a geresh ends its word", "רח'הרצל 5", "רחוב הרצל 5"),
                Arguments.of("a double quote is the gershayim", "ת\"א", "תל אביב"),
                Arguments.of("two apostrophes are the gershayim", "ת''א", "תל אביב"),
                Arguments.of("two right single quotes are the gershayim", "ת\u2019\u2019א", "תל אביב"),
                Arguments.of("two gereshes are the gershayim", "ת\u05f3\u05f3א", "תל אביב"),
                Arguments.of("a zero-width joiner inside a word is dropped", "הר\u200dצל 5", "הרצל 5"),
                Arguments.of("a right-to-left mark inside a word is dropped", "הר\u200fצל 5", "הרצל 5"),
                Arguments.of("niqqud is dropped", "ה\u05b6ר\u05b0צ\u05b5ל 5", "הרצל 5"),
                Arguments.of("a left double quote is the gershayim", "ת\u201cא", "תל אביב"),
                Arguments.of("a right double quote is the gershayim", "ת\u201dא", "תל אביב"),
                Arguments.of("the gershayim itself", "ת\u05f4א", "תל אביב"),
                Arguments.of("שד\u05f3 expands to שדרות", "שד\u05f3 רוטשילד 1", "שדרות רוטשילד 1"),
                Arguments.of("an abbreviation inside a word is left alone", "ברח\u05f3 3", "ברח\u05f3 3"),
                Arguments.of("שכ\u05f3 expands to שכונה", "שכ\u05f3 ד\u05f3", "שכונה ד\u05f3"),
                Arguments.of("סמ\u05f3 expands to סמטה", "סמ\u05f3 הגפן 2", "סמטה הגפן 2"),
                Arguments.of("ת\u05f4ד expands to תא דואר", "ת\u05f4ד 1234", "תא דואר 1234"),
                Arguments.of("דיר\u05f3 expands to דירה", "הרצל 5 דיר\u05f3 3", "הרצל 5 דירה 3"),
                Arguments.of("כנ\u05f3 expands to כניסה", "הרצל 5 כנ\u05f3 ב\u05f3", "הרצל 5 כניסה ב\u05f3"),
                Arguments.of("מס\u05f3 expands to מספר", "הרצל מס\u05f3 5", "הרצל מספר 5"),
                Arguments.of(
                        "an abbreviation touching a digit is left alone", "הרצל 5 דיר\u05f33", "הרצל 5 דיר\u05f33"),
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
                Arguments.of("caf\u00e9 5", "cafe\u200c\u0301 5"),
                Arguments.of("caf\u00e9 5", "cafe\u200d\u0301 5"),
                Arguments.of("caf\u00e9 5", "cafe\u200e\u0301 5"),
                Arguments.of("\u0622 5", "\u0627\u061c\u0653 5"),
                Arguments.of("רח'הרצל", "רח\u05f3 הרצל"),
                Arguments.of("שכ' ד', באר שבע", "שכונה ד\u05f3 באר שבע"),
                Arguments.of("שכ\u05f3 התקווה", "שכונה התקווה"));
    }

    /** ADR-137 decision 2: each of these has two meanings or two full forms in an address. */
    static Stream<String> leftOut() {
        return Stream.of(
                "הרצל 5 ד\u05f3",
                "הרצל 5 ק\u05f3 2",
                "כ\u05f3 המדינה 4",
                "א\u05f4ת עמק שרה 7",
                "אזה\u05f4ת עמק שרה 7",
                "הרצל 5 ב\u05f4ש");
    }

    static Stream<Arguments> differentAddresses() {
        return Stream.of(Arguments.of("הרצל 5/3", "הרצל 53"), Arguments.of("הרצל 5 ק\u05f3 2", "הרצל 5 קומה 2"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("steps")
    void GivenAnAddress_WhenNormalized_ThenEachStepOfFr398Applies(String step, String typed, String normalized) {
        assertThat(NormalizedAddress.of(Address.of(typed)).text()).isEqualTo(normalized);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leftOut")
    void GivenAnAbbreviationTheTableLeavesOut_WhenNormalized_ThenItStaysAsTyped(String typed) {
        assertThat(NormalizedAddress.of(Address.of(typed)).text()).isEqualTo(typed);
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
    void GivenAnAddressWithInvisibleMarks_WhenKept_ThenTheAddressKeepsThemAndOnlyTheMatchingKeyDropsThem() {
        Address withJoiner = Address.of("הר\u200dצל 5");

        assertThat(withJoiner.text()).isEqualTo("הר\u200dצל 5");
        assertThat(NormalizedAddress.of(withJoiner)).isEqualTo(NormalizedAddress.of(Address.of("הרצל 5")));
    }

    @Test
    void GivenANormalizedAddress_WhenPrinted_ThenTheAddressIsHidden() {
        assertThat(NormalizedAddress.of(Address.of("הרצל 5")).toString()).doesNotContain("הרצל");
        assertThat(Address.of("הרצל 5").toString()).doesNotContain("הרצל");
    }
}
