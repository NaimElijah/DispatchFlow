package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The characters under test are built from their code points: the formatter turns a printable
 * Unicode escape into the character itself, which would leave invisible or look-alike characters in
 * the source.
 */
class PlainTextTest {

    private static final String NO_BREAK_SPACE = Character.toString(0x00A0);
    private static final String FIGURE_SPACE = Character.toString(0x2007);
    private static final String NARROW_NO_BREAK_SPACE = Character.toString(0x202F);
    private static final String ZERO_WIDTH_SPACE = Character.toString(0x200B);
    private static final String BYTE_ORDER_MARK = Character.toString(0xFEFF);
    private static final String COMBINING_ACUTE = Character.toString(0x0301);
    private static final String E_ACUTE = Character.toString(0x00E9);
    private static final String GERESH = Character.toString(0x05F3);
    private static final String GERSHAYIM = Character.toString(0x05F4);
    private static final String HIGH_SURROGATE = Character.toString(0xD800);
    private static final String LOW_SURROGATE = Character.toString(0xDC00);
    /** DESERET CAPITAL LETTER LONG I, a letter outside the Basic Multilingual Plane. */
    private static final String SUPPLEMENTARY_LETTER = Character.toString(0x10400);

    @Test
    void GivenADecomposedAccent_WhenBuilt_ThenItEqualsTheComposedForm() {
        PlainText decomposed = PlainText.of(TextKind.NAME, "Rene" + COMBINING_ACUTE);
        PlainText composed = PlainText.of(TextKind.NAME, "Ren" + E_ACUTE);

        assertThat(decomposed.text()).isEqualTo("Ren" + E_ACUTE);
        assertThat(decomposed).isEqualTo(composed).hasSameHashCodeAs(composed);
    }

    @Test
    void GivenInvisibleSpacesAtTheEnds_WhenBuilt_ThenTheyAreTrimmedAndInteriorSpacesKept() {
        String raw = NO_BREAK_SPACE + ZERO_WIDTH_SPACE + " Van  1 " + BYTE_ORDER_MARK + NARROW_NO_BREAK_SPACE + "\t";

        assertThat(PlainText.of(TextKind.NAME, raw).text()).isEqualTo("Van  1");
    }

    @Test
    void GivenANameAndTheSameNameWithATrailingNoBreakSpace_WhenBuilt_ThenTheyAreEqual() {
        assertThat(PlainText.of(TextKind.NAME, "Van 1" + NO_BREAK_SPACE))
                .isEqualTo(PlainText.of(TextKind.NAME, "Van 1"));
    }

    @Test
    void GivenAnInteriorZeroWidthSpaceInANote_WhenBuilt_ThenItIsKept() {
        assertThat(PlainText.of(TextKind.NOTE, "Van" + ZERO_WIDTH_SPACE + "1").text())
                .isEqualTo("Van" + ZERO_WIDTH_SPACE + "1");
    }

    /** Zero-width space, soft hyphen, word joiner, byte-order mark and a tag letter. */
    @ParameterizedTest
    @ValueSource(ints = {0x200B, 0x00AD, 0x2060, 0xFEFF, 0xE0041})
    void GivenAnInteriorFormatCharacterInANameOrAddress_WhenBuilt_ThenTextHasForbiddenCharacters(int codePoint) {
        String raw = "Van" + Character.toString(codePoint) + "1";

        assertRefused(() -> PlainText.of(TextKind.NAME, raw), new TextHasForbiddenCharacters(TextKind.NAME));
        assertRefused(() -> PlainText.of(TextKind.ADDRESS, raw), new TextHasForbiddenCharacters(TextKind.ADDRESS));
    }

    /**
     * ZWNJ, ZWJ, LRM and RLM, which Hebrew and mixed-direction text use, and the Arabic letter mark,
     * which Arabic addresses use (Q-83).
     */
    @ParameterizedTest
    @ValueSource(ints = {0x200C, 0x200D, 0x200E, 0x200F, 0x061C})
    void GivenAnInteriorJoinerOrDirectionMarkInANameOrAddress_WhenBuilt_ThenItIsKept(int codePoint) {
        String raw = "Van" + Character.toString(codePoint) + "1";

        assertThat(PlainText.of(TextKind.NAME, raw).text()).isEqualTo(raw);
        assertThat(PlainText.of(TextKind.ADDRESS, raw).text()).isEqualTo(raw);
    }

    @Test
    void GivenAnEmojiWithAVariationSelectorInAName_WhenBuilt_ThenItIsUnchanged() {
        String raw = "Dana " + Character.toString(0x2764) + Character.toString(0xFE0F);

        assertThat(PlainText.of(TextKind.NAME, raw).text()).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(ints = {0x202A, 0x202E, 0x2066, 0x2069})
    void GivenABidiControlAtAnEnd_WhenBuilt_ThenItIsTrimmed(int codePoint) {
        String control = Character.toString(codePoint);

        assertThat(PlainText.of(TextKind.NAME, control + "Herzl 1" + control).text())
                .isEqualTo("Herzl 1");
    }

    @Test
    void GivenASubdivisionFlagAtTheEndOfANote_WhenBuilt_ThenItsTagsAreTrimmedToABlackFlag() {
        String blackFlag = Character.toString(0x1F3F4);
        String englandTags = "gbeng"
                        .codePoints()
                        .mapToObj(letter -> Character.toString(0xE0000 + letter))
                        .reduce("", String::concat)
                + Character.toString(0xE007F);

        assertThat(PlainText.of(TextKind.NOTE, "Go " + blackFlag + englandTags).text())
                .isEqualTo("Go " + blackFlag);
    }

    /**
     * Hangul fillers, the blank Braille pattern, the combining grapheme joiner, a soft hyphen, a
     * word joiner and two variation selectors: each renders as nothing.
     */
    @ParameterizedTest
    @ValueSource(ints = {0x3164, 0x115F, 0xFFA0, 0x2800, 0x034F, 0x00AD, 0x2060, 0xFE0F, 0xE0100})
    void GivenOnlyInvisibleCharacters_WhenBuilt_ThenItIsEmpty(int codePoint) {
        String invisible = Character.toString(codePoint);

        for (TextKind kind : TextKind.values()) {
            PlainText text = PlainText.of(kind, invisible + invisible + " " + invisible);

            assertThat(text.isEmpty()).isTrue();
            assertThat(text.text()).isEmpty();
        }
    }

    /**
     * Hangul fillers, the blank Braille pattern, the combining grapheme joiner and the Khmer
     * inherent vowels, which BR-48's "Van 1" must not hide at either end (ADR-111).
     */
    @ParameterizedTest
    @ValueSource(ints = {0x3164, 0x115F, 0x1160, 0xFFA0, 0x2800, 0x034F, 0x17B4, 0x17B5})
    void GivenAnInvisibleFillerAtAnEnd_WhenBuilt_ThenItIsTrimmed(int codePoint) {
        String filler = Character.toString(codePoint);

        for (TextKind kind : TextKind.values()) {
            PlainText plain = PlainText.of(kind, "Van 1");

            assertThat(PlainText.of(kind, "Van 1" + filler)).isEqualTo(plain);
            assertThat(PlainText.of(kind, filler + "Van 1")).isEqualTo(plain);
            assertThat(PlainText.of(kind, filler + " Van 1 " + filler + filler)).isEqualTo(plain);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0x3164, 0x115F, 0x1160, 0xFFA0, 0x2800, 0x034F, 0x17B4, 0x17B5})
    void GivenAnInteriorInvisibleFillerInANameOrAddress_WhenBuilt_ThenTextHasForbiddenCharacters(int codePoint) {
        String raw = "Van" + Character.toString(codePoint) + "1";

        assertRefused(() -> PlainText.of(TextKind.NAME, raw), new TextHasForbiddenCharacters(TextKind.NAME));
        assertRefused(() -> PlainText.of(TextKind.ADDRESS, raw), new TextHasForbiddenCharacters(TextKind.ADDRESS));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x3164, 0x115F, 0x1160, 0xFFA0, 0x2800, 0x034F, 0x17B4, 0x17B5})
    void GivenAnInteriorInvisibleFillerInANote_WhenBuilt_ThenItIsKept(int codePoint) {
        String raw = "Ring" + Character.toString(codePoint) + "twice";

        assertThat(PlainText.of(TextKind.NOTE, raw).text()).isEqualTo(raw);
    }

    /** A variation selector belongs to the character before it, so it goes only when that does. */
    @ParameterizedTest
    @ValueSource(ints = {0x3164, 0x2800, 0x0020, 0x00A0, 0x200B})
    void GivenAVariationSelectorAfterATrailingInvisibleCharacter_WhenBuilt_ThenBothAreTrimmed(int codePoint) {
        String raw = "Van 1" + Character.toString(codePoint) + Character.toString(0xFE0F);

        for (TextKind kind : TextKind.values()) {
            assertThat(PlainText.of(kind, raw)).isEqualTo(PlainText.of(kind, "Van 1"));
        }
    }

    /**
     * VS16, VS15, an ideographic variation selector and a Mongolian free variation selector: after a
     * digit each renders as nothing, so BR-48's "Van 1" must not hide one at its end (ADR-111).
     */
    @ParameterizedTest
    @ValueSource(ints = {0xFE0F, 0xFE0E, 0xE0100, 0x180B})
    void GivenAVariationSelectorAfterADigitAtTheEndOfANameOrAddress_WhenBuilt_ThenItIsTrimmed(int codePoint) {
        String raw = "Van 1" + Character.toString(codePoint);

        assertThat(PlainText.of(TextKind.NAME, raw)).isEqualTo(PlainText.of(TextKind.NAME, "Van 1"));
        assertThat(PlainText.of(TextKind.ADDRESS, raw)).isEqualTo(PlainText.of(TextKind.ADDRESS, "Van 1"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0xFE0F, 0xFE0E, 0xE0100, 0x180B})
    void GivenAVariationSelectorAfterALetterInsideANameOrAddress_WhenBuilt_ThenTextHasForbiddenCharacters(
            int codePoint) {
        String raw = "Van" + Character.toString(codePoint) + " 1";

        assertRefused(() -> PlainText.of(TextKind.NAME, raw), new TextHasForbiddenCharacters(TextKind.NAME));
        assertRefused(() -> PlainText.of(TextKind.ADDRESS, raw), new TextHasForbiddenCharacters(TextKind.ADDRESS));
    }

    @Test
    void GivenAVariationSelectorAfterASpaceInsideAName_WhenBuilt_ThenTextHasForbiddenCharacters() {
        String raw = "Van " + Character.toString(0xFE0F) + "1";

        assertRefused(() -> PlainText.of(TextKind.NAME, raw), new TextHasForbiddenCharacters(TextKind.NAME));
    }

    @Test
    void GivenAnEmojiWithAVariationSelectorInsideANameOrAddress_WhenBuilt_ThenItIsUnchanged() {
        String raw = "Dana " + Character.toString(0x2764) + Character.toString(0xFE0F) + " Levi";

        assertThat(PlainText.of(TextKind.NAME, raw).text()).isEqualTo(raw);
        assertThat(PlainText.of(TextKind.ADDRESS, raw).text()).isEqualTo(raw);
    }

    /**
     * VS15 asks for the text presentation an emoji such as U+2764 already has by default, so it
     * changes nothing a reader sees (BR-48, ADR-111).
     */
    @Test
    void GivenAnEmojiWithTheTextPresentationSelectorInsideANameOrAddress_WhenBuilt_ThenTextHasForbiddenCharacters() {
        String raw = "Dana " + Character.toString(0x2764) + Character.toString(0xFE0E) + " Levi";

        assertRefused(() -> PlainText.of(TextKind.NAME, raw), new TextHasForbiddenCharacters(TextKind.NAME));
        assertRefused(() -> PlainText.of(TextKind.ADDRESS, raw), new TextHasForbiddenCharacters(TextKind.ADDRESS));
    }

    @Test
    void GivenAnEmojiWithTheTextPresentationSelectorAtTheEndOfANameOrAddress_WhenBuilt_ThenTheSelectorIsTrimmed() {
        String heart = "Dana " + Character.toString(0x2764);

        assertThat(PlainText.of(TextKind.NAME, heart + Character.toString(0xFE0E)))
                .isEqualTo(PlainText.of(TextKind.NAME, heart));
        assertThat(PlainText.of(TextKind.ADDRESS, heart + Character.toString(0xFE0E)))
                .isEqualTo(PlainText.of(TextKind.ADDRESS, heart));
    }

    /**
     * A grinning face (U+1F600) and a delivery truck (U+1F69A) show as emoji without VS16, so VS16
     * after either changes nothing and "Van" with it would be a second name (BR-48, ADR-111).
     */
    @ParameterizedTest
    @ValueSource(ints = {0x1F600, 0x1F69A})
    void GivenAnEmojiShownAsAnEmojiByDefaultWithVs16InsideANameOrAddress_WhenBuilt_ThenTextHasForbiddenCharacters(
            int emoji) {
        String raw = "Van " + Character.toString(emoji) + Character.toString(0xFE0F) + " 1";

        assertRefused(() -> PlainText.of(TextKind.NAME, raw), new TextHasForbiddenCharacters(TextKind.NAME));
        assertRefused(() -> PlainText.of(TextKind.ADDRESS, raw), new TextHasForbiddenCharacters(TextKind.ADDRESS));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x1F600, 0x1F69A})
    void GivenAnEmojiShownAsAnEmojiByDefaultWithVs16AtTheEndOfANameOrAddress_WhenBuilt_ThenTheSelectorIsTrimmed(
            int emoji) {
        String van = "Van " + Character.toString(emoji);

        assertThat(PlainText.of(TextKind.NAME, van + Character.toString(0xFE0F)))
                .isEqualTo(PlainText.of(TextKind.NAME, van));
        assertThat(PlainText.of(TextKind.ADDRESS, van + Character.toString(0xFE0F)))
                .isEqualTo(PlainText.of(TextKind.ADDRESS, van));
    }

    /** The copyright sign is text by default, so VS16 makes it an emoji, which a reader sees. */
    @Test
    void GivenAnEmojiShownAsTextByDefaultWithVs16InANameOrAddress_WhenBuilt_ThenItIsUnchanged() {
        String copyright = Character.toString(0x00A9) + Character.toString(0xFE0F);

        assertThat(PlainText.of(TextKind.NAME, copyright + " Levi Ltd").text()).isEqualTo(copyright + " Levi Ltd");
        assertThat(PlainText.of(TextKind.ADDRESS, "Levi Ltd " + copyright).text())
                .isEqualTo("Levi Ltd " + copyright);
    }

    /**
     * A digit is an emoji base only in a keycap, and with VS16 but no keycap mark it shows as the
     * digit, so a keycap stays refused in a name, and "Van 1" keeps no selector (ADR-111).
     */
    @Test
    void GivenAKeycapEmojiInsideAName_WhenBuilt_ThenTextHasForbiddenCharacters() {
        String keycap = "1" + Character.toString(0xFE0F) + Character.toString(0x20E3);

        assertRefused(
                () -> PlainText.of(TextKind.NAME, "Van " + keycap), new TextHasForbiddenCharacters(TextKind.NAME));
    }

    /** A second selector after an emoji renders as nothing, so two names would look alike (BR-48). */
    @Test
    void GivenTwoVariationSelectorsAfterAnEmojiInsideAName_WhenBuilt_ThenTextHasForbiddenCharacters() {
        String raw = "Dana " + Character.toString(0x2764) + Character.toString(0xFE0F) + Character.toString(0xFE0F)
                + " Levi";

        assertRefused(() -> PlainText.of(TextKind.NAME, raw), new TextHasForbiddenCharacters(TextKind.NAME));
        assertRefused(() -> PlainText.of(TextKind.ADDRESS, raw), new TextHasForbiddenCharacters(TextKind.ADDRESS));
    }

    @Test
    void GivenTwoVariationSelectorsAfterAnEmojiAtTheEndOfAName_WhenBuilt_ThenOnlyTheFirstIsKept() {
        String heart = Character.toString(0x2764) + Character.toString(0xFE0F);

        assertThat(PlainText.of(TextKind.NAME, "Dana " + heart + Character.toString(0xFE0F)))
                .isEqualTo(PlainText.of(TextKind.NAME, "Dana " + heart));
        assertThat(PlainText.of(TextKind.NAME, "Dana " + heart + Character.toString(0xFE0E)))
                .isEqualTo(PlainText.of(TextKind.NAME, "Dana " + heart));
    }

    /** VS1, an ideographic selector and a Mongolian free variation selector change no emoji. */
    @ParameterizedTest
    @ValueSource(ints = {0xFE00, 0xE0100, 0x180B})
    void GivenAnEmojiWithANonPresentationSelectorInsideANameOrAddress_WhenBuilt_ThenTextHasForbiddenCharacters(
            int selector) {
        String raw = "Dana " + Character.toString(0x2764) + Character.toString(selector) + " Levi";

        assertRefused(() -> PlainText.of(TextKind.NAME, raw), new TextHasForbiddenCharacters(TextKind.NAME));
        assertRefused(() -> PlainText.of(TextKind.ADDRESS, raw), new TextHasForbiddenCharacters(TextKind.ADDRESS));
    }

    @ParameterizedTest
    @ValueSource(ints = {0xFE00, 0xE0100, 0x180B})
    void GivenAnEmojiWithANonPresentationSelectorAtTheEndOfAName_WhenBuilt_ThenTheSelectorIsTrimmed(int selector) {
        String heart = "Dana " + Character.toString(0x2764);

        assertThat(PlainText.of(TextKind.NAME, heart + Character.toString(selector)))
                .isEqualTo(PlainText.of(TextKind.NAME, heart));
    }

    @Test
    void GivenTwoVariationSelectorsAfterAnEmojiInANote_WhenBuilt_ThenBothAreKept() {
        String emoji = Character.toString(0x2764) + Character.toString(0xFE0E) + Character.toString(0xFE0F);

        assertThat(PlainText.of(TextKind.NOTE, "Ring " + emoji + " twice").text())
                .isEqualTo("Ring " + emoji + " twice");
        assertThat(PlainText.of(TextKind.NOTE, "Ring " + emoji).text()).isEqualTo("Ring " + emoji);
    }

    @Test
    void GivenAVariationSelectorAfterALetterInANote_WhenBuilt_ThenItIsKept() {
        String selector = Character.toString(0xFE0F);

        assertThat(PlainText.of(TextKind.NOTE, "Van" + selector + " 1").text()).isEqualTo("Van" + selector + " 1");
        assertThat(PlainText.of(TextKind.NOTE, "Van 1" + selector).text()).isEqualTo("Van 1" + selector);
    }

    @Test
    void GivenAnEmojiWithAVariationSelectorAtTheEndOfANote_WhenBuilt_ThenItIsUnchanged() {
        String raw = "Thanks " + Character.toString(0x2764) + Character.toString(0xFE0F);

        assertThat(PlainText.of(TextKind.NOTE, raw + " ").text()).isEqualTo(raw);
    }

    @Test
    void GivenALeadingVariationSelector_WhenBuilt_ThenItIsTrimmed() {
        assertThat(PlainText.of(TextKind.NAME, Character.toString(0xFE0F) + "Van 1"))
                .isEqualTo(PlainText.of(TextKind.NAME, "Van 1"));
    }

    @Test
    void GivenAnInvisibleCharacterBesideALetter_WhenBuilt_ThenItIsNotEmpty() {
        assertThat(PlainText.of(TextKind.NAME, Character.toString(0x3164) + "a").isEmpty())
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0x2028, 0x2029})
    void GivenALineOrParagraphSeparatorInANote_WhenBuilt_ThenItIsKept(int codePoint) {
        String raw = "Ring twice." + Character.toString(codePoint) + "Leave it.";

        assertThat(PlainText.of(TextKind.NOTE, raw).text()).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(ints = {0x2028, 0x2029})
    void GivenALineOrParagraphSeparatorInANameOrAddress_WhenBuilt_ThenTextHasForbiddenCharacters(int codePoint) {
        String raw = "Herzl 1" + Character.toString(codePoint) + "Tel Aviv";

        assertRefused(() -> PlainText.of(TextKind.NAME, raw), new TextHasForbiddenCharacters(TextKind.NAME));
        assertRefused(() -> PlainText.of(TextKind.ADDRESS, raw), new TextHasForbiddenCharacters(TextKind.ADDRESS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ring twice.\r\nLeave it.", "Ring twice.\rLeave it."})
    void GivenACarriageReturnInANote_WhenBuilt_ThenItBecomesALineFeed(String raw) {
        assertThat(PlainText.of(TextKind.NOTE, raw).text()).isEqualTo("Ring twice.\nLeave it.");
    }

    @Test
    void GivenCarriageReturnsAndLineFeedsInANote_WhenBuilt_ThenEachBreakIsOneLineFeed() {
        assertThat(PlainText.of(TextKind.NOTE, "a\r\n\r\nb\r\rc\n\rd").text()).isEqualTo("a\n\nb\n\nc\n\nd");
    }

    @ParameterizedTest
    @EnumSource(
            value = TextKind.class,
            names = {"NAME", "ADDRESS"})
    void GivenACarriageReturnInANameOrAddress_WhenBuilt_ThenTextHasForbiddenCharacters(TextKind kind) {
        assertRefused(() -> PlainText.of(kind, "Herzl 1\r\nTel Aviv"), new TextHasForbiddenCharacters(kind));
        assertRefused(() -> PlainText.of(kind, "Herzl 1\rTel Aviv"), new TextHasForbiddenCharacters(kind));
    }

    @Test
    void GivenAMillionAlternatingCombiningMarks_WhenBuilt_ThenTextTooLong() {
        String marks = (COMBINING_ACUTE + Character.toString(0x0316)).repeat(500_000);

        assertRefused(
                () -> PlainText.of(TextKind.NOTE, marks), new TextTooLong(TextKind.NOTE, TextKind.NOTE.maxLength()));
    }

    /** The raw bound is eight UTF-16 units per code point the kind holds. */
    @ParameterizedTest
    @EnumSource(TextKind.class)
    void GivenPaddedInputAtTheRawBound_WhenBuilt_ThenItIsTrimmed(TextKind kind) {
        String padded = " ".repeat(kind.maxLength() * 8 - 1) + "a";

        assertThat(PlainText.of(kind, padded).text()).isEqualTo("a");
    }

    @ParameterizedTest
    @EnumSource(TextKind.class)
    void GivenPaddedInputOnePastTheRawBound_WhenBuilt_ThenTextTooLong(TextKind kind) {
        String padded = " ".repeat(kind.maxLength() * 8) + "a";

        assertRefused(() -> PlainText.of(kind, padded), new TextTooLong(kind, kind.maxLength()));
    }

    @Test
    void GivenTheSameTextAsTwoKinds_WhenCompared_ThenTheyDiffer() {
        assertThat(PlainText.of(TextKind.NAME, "Herzl 1")).isNotEqualTo(PlainText.of(TextKind.ADDRESS, "Herzl 1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t \n"})
    void GivenEmptyOrWhitespaceOnlyText_WhenBuilt_ThenItIsEmpty(String raw) {
        PlainText text = PlainText.of(TextKind.NAME, raw);

        assertThat(text.isEmpty()).isTrue();
        assertThat(text.text()).isEmpty();
    }

    @Test
    void GivenOnlyInvisibleSpaces_WhenBuilt_ThenItIsEmpty() {
        String raw = NO_BREAK_SPACE + ZERO_WIDTH_SPACE + BYTE_ORDER_MARK + FIGURE_SPACE;

        assertThat(PlainText.of(TextKind.NAME, raw).isEmpty()).isTrue();
    }

    @Test
    void GivenText_WhenBuilt_ThenItIsNotEmpty() {
        assertThat(PlainText.of(TextKind.NAME, "a").isEmpty()).isFalse();
    }

    @Test
    void GivenHebrewWithGereshAndGershayim_WhenBuilt_ThenItIsUnchanged() {
        String street = "רח" + GERESH + " הרצל, צה" + GERSHAYIM + "ל";

        assertThat(PlainText.of(TextKind.ADDRESS, street).text()).isEqualTo(street);
    }

    @ParameterizedTest
    @EnumSource(TextKind.class)
    void GivenTextExactlyAtItsCap_WhenBuilt_ThenItIsKept(TextKind kind) {
        String atCap = "a".repeat(kind.maxLength());

        assertThat(PlainText.of(kind, atCap).text()).isEqualTo(atCap);
    }

    @ParameterizedTest
    @EnumSource(TextKind.class)
    void GivenTextOnePastItsCap_WhenBuilt_ThenTextTooLong(TextKind kind) {
        assertRefused(
                () -> PlainText.of(kind, "a".repeat(kind.maxLength() + 1)), new TextTooLong(kind, kind.maxLength()));
    }

    @ParameterizedTest
    @EnumSource(TextKind.class)
    void GivenSupplementaryLettersExactlyAtTheCap_WhenBuilt_ThenEachCountsAsOne(TextKind kind) {
        String atCap = SUPPLEMENTARY_LETTER.repeat(kind.maxLength());

        assertThat(PlainText.of(kind, atCap).text()).isEqualTo(atCap);
    }

    @ParameterizedTest
    @EnumSource(TextKind.class)
    void GivenSupplementaryLettersOnePastTheCap_WhenBuilt_ThenTextTooLong(TextKind kind) {
        assertRefused(
                () -> PlainText.of(kind, SUPPLEMENTARY_LETTER.repeat(kind.maxLength() + 1)),
                new TextTooLong(kind, kind.maxLength()));
    }

    @Test
    void GivenDecomposedAccentsThatFitOnlyOnceComposed_WhenBuilt_ThenTheyAreCountedAfterNormalization() {
        String decomposed = ("e" + COMBINING_ACUTE).repeat(TextKind.NAME.maxLength());

        assertThat(PlainText.of(TextKind.NAME, decomposed).text()).isEqualTo(E_ACUTE.repeat(TextKind.NAME.maxLength()));
    }

    @Test
    void GivenSpacesAroundTextAtTheCap_WhenBuilt_ThenTheyAreTrimmedBeforeCounting() {
        String atCap = "a".repeat(TextKind.NAME.maxLength());

        assertThat(PlainText.of(TextKind.NAME, "  " + atCap + NO_BREAK_SPACE).text())
                .isEqualTo(atCap);
    }

    /**
     * NUL, ESC, vertical tab, DEL and NEL (controls); a lone high and a lone low surrogate; private-use points
     * in the BMP and in plane 15; two unassigned points; and the bidi embedding, override and isolate
     * controls at both ends of their ranges.
     */
    @ParameterizedTest
    @ValueSource(
            ints = {
                0x0000, 0x001B, 0x000B, 0x007F, 0x0085, 0xD800, 0xDC00, 0xE000, 0xF0000, 0x0378, 0xFFFF, 0x202A, 0x202E,
                0x2066, 0x2069
            })
    void GivenAForbiddenCharacterInsideANote_WhenBuilt_ThenTextHasForbiddenCharacters(int codePoint) {
        String raw = "a" + Character.toString(codePoint) + "b";

        assertRefused(() -> PlainText.of(TextKind.NOTE, raw), new TextHasForbiddenCharacters(TextKind.NOTE));
    }

    @Test
    void GivenSurrogatesInTheWrongOrder_WhenBuilt_ThenTextHasForbiddenCharacters() {
        String raw = "a" + LOW_SURROGATE + HIGH_SURROGATE + "b";

        assertRefused(() -> PlainText.of(TextKind.NAME, raw), new TextHasForbiddenCharacters(TextKind.NAME));
    }

    @Test
    void GivenALoneSurrogateAtTheEnd_WhenBuilt_ThenTextHasForbiddenCharacters() {
        assertRefused(
                () -> PlainText.of(TextKind.NAME, "Van 1" + HIGH_SURROGATE),
                new TextHasForbiddenCharacters(TextKind.NAME));
    }

    @ParameterizedTest
    @EnumSource(
            value = TextKind.class,
            names = {"NAME", "ADDRESS"})
    void GivenALineFeedInAShortKind_WhenBuilt_ThenTextHasForbiddenCharacters(TextKind kind) {
        assertRefused(() -> PlainText.of(kind, "Herzl 1\nTel Aviv"), new TextHasForbiddenCharacters(kind));
    }

    @Test
    void GivenALineFeedInANote_WhenBuilt_ThenItIsKept() {
        assertThat(PlainText.of(TextKind.NOTE, "Ring twice.\nLeave at the door.")
                        .text())
                .isEqualTo("Ring twice.\nLeave at the door.");
    }

    @Test
    void GivenAName_WhenPrinted_ThenTheTextIsHidden() {
        assertThat(PlainText.of(TextKind.NAME, "Dana Levi").toString())
                .doesNotContain("Dana")
                .isEqualTo("PlainText[redacted]");
    }

    @Test
    void GivenRefusedText_WhenRefused_ThenTheMessageDoesNotQuoteIt() {
        assertThatThrownBy(() -> PlainText.of(TextKind.NAME, "Dana\u0000Levi"))
                .isInstanceOf(RuleViolation.class)
                .hasMessage("TEXT_HAS_FORBIDDEN_CHARACTERS");
    }

    @Test
    void GivenStoredText_WhenLoaded_ThenItEqualsTheEnteredText() {
        assertThat(PlainText.ofStored(TextKind.NAME, "Van 1")).isEqualTo(PlainText.of(TextKind.NAME, "Van 1"));
    }

    @Test
    void GivenStoredTextTheRuleRefuses_WhenLoaded_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> PlainText.ofStored(TextKind.NAME, "a".repeat(TextKind.NAME.maxLength() + 1)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid)
                                .hasMessage("PlainText: a stored value is refused with TEXT_TOO_LONG")
                                .hasCauseInstanceOf(RuleViolation.class));
    }
}
