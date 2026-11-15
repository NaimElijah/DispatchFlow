package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import java.text.Normalizer;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Free text as every module keeps it: a name, an address or a note, normalized and capped by one
 * rule (ADR-106).
 *
 * <p>The text is Unicode NFC, with every character that renders as nothing trimmed from both
 * ends: whitespace, space separators such as the no-break space, format characters such as the
 * zero-width space, Unicode's default-ignorable code points such as the Hangul fillers and the
 * combining grapheme joiner, and the two symbols that render as blank, the blank Braille pattern
 * and the musical null notehead U+1D159 (ADR-106, ADR-111). A variation selector at the end
 * belongs to the character before it, as an emoji's does. In a name or an address only one
 * selector stays: VS16, right after an emoji that shows as text by default, such as U+2764, so
 * that it shows as an emoji. Every other selector at the end is trimmed: one after a letter or a
 * digit, VS16 after an emoji that already shows as one, VS15, or a second after an emoji. In a note
 * a selector is trimmed only with an invisible character it follows. The interior is kept as
 * entered, except that a {@link TextKind#NOTE}'s carriage returns become line feeds. It holds at
 * most its {@link TextKind}'s cap in code points, and none of these:
 *
 * <ul>
 *   <li>a control character, other than a line feed in a note;
 *   <li>a line or paragraph separator (U+2028, U+2029), other than in a note;
 *   <li>an interior format character in a name or an address, other than U+061C and U+200C to
 *       U+200F;
 *   <li>an interior invisible filler in a name or an address, which would make two names or
 *       addresses look alike (BR-48, ADR-111): a default-ignorable code point that is not a
 *       format character, such as a Hangul filler, the combining grapheme joiner or a Khmer
 *       inherent vowel; the blank Braille pattern or the musical null notehead; or a variation
 *       selector other than VS16 right after an emoji that shows as text by default;
 *   <li>a bidi embedding, override or isolate control, in any kind;
 *   <li>an unpaired surrogate, or a private-use or unassigned code point.
 * </ul>
 *
 * <p>It may be empty, and text that renders as nothing, such as a Hangul filler or a lone variation
 * selector, is trimmed to empty: each module refuses blank text with its own error, so the message
 * names the field. Matching forms, such as an address normalized for lookup (FR-398), belong to
 * their module.
 *
 * <p>Free text is personal data: {@link #toString()} hides it, and a refusal names no text.
 */
public final class PlainText {

    /**
     * The most UTF-16 units of raw input per code point the kind holds. The JDK's {@link Normalizer}
     * takes time quadratic in a run of combining marks (160,000 marks took 9.8 seconds), so input
     * this far past the cap is refused before it is normalized. Eight leaves room for padding and
     * for decomposed letters, which NFC composes, while keeping the normalizer's work small.
     */
    private static final int MAX_RAW_UNITS_PER_CODE_POINT = 8;

    private static final String CARRIAGE_RETURN_LINE_FEED = "\r\n";
    private static final char CARRIAGE_RETURN = '\r';
    private static final char LINE_FEED = '\n';
    private static final int ARABIC_LETTER_MARK = 0x061C;
    private static final int ZERO_WIDTH_NON_JOINER = 0x200C;
    private static final int RIGHT_TO_LEFT_MARK = 0x200F;
    private static final int LEFT_TO_RIGHT_EMBEDDING = 0x202A;
    private static final int RIGHT_TO_LEFT_OVERRIDE = 0x202E;
    private static final int LEFT_TO_RIGHT_ISOLATE = 0x2066;
    private static final int POP_DIRECTIONAL_ISOLATE = 0x2069;

    /** Renders as an empty cell, though Unicode does not class it as ignorable. */
    private static final int BRAILLE_PATTERN_BLANK = 0x2800;

    /** Renders as an empty space in common fonts, though Unicode classes it as a symbol (So). */
    private static final int MUSICAL_SYMBOL_NULL_NOTEHEAD = 0x1D159;

    /**
     * Unicode's variation selectors, as of Unicode 15: the Mongolian free variation selectors, VS1
     * to VS16 and VS17 to VS256. Each selects the presentation of the character before it, as
     * U+FE0F makes U+2764 an emoji.
     */
    private static final int[][] VARIATION_SELECTORS = {
        {0x180B, 0x180D}, {0x180F, 0x180F}, {0xFE00, 0xFE0F}, {0xE0100, 0xE01EF}
    };

    /** VS16, which asks for an emoji's emoji presentation. */
    private static final int EMOJI_PRESENTATION_SELECTOR = 0xFE0F;

    /**
     * Unicode's Default_Ignorable_Code_Point ranges, as of Unicode 15 (DerivedCoreProperties.txt),
     * which the JDK does not expose: what a renderer shows as nothing, such as the soft hyphen, the
     * combining grapheme joiner, Hangul fillers and variation selectors.
     */
    private static final int[][] DEFAULT_IGNORABLE = {
        {0x00AD, 0x00AD}, {0x034F, 0x034F}, {0x061C, 0x061C}, {0x115F, 0x1160}, {0x17B4, 0x17B5},
        {0x180B, 0x180F}, {0x200B, 0x200F}, {0x202A, 0x202E}, {0x2060, 0x206F}, {0x3164, 0x3164},
        {0xFE00, 0xFE0F}, {0xFEFF, 0xFEFF}, {0xFFA0, 0xFFA0}, {0xFFF0, 0xFFF8}, {0x1BCA0, 0x1BCA3},
        {0x1D173, 0x1D17A}, {0xE0000, 0xE0FFF}
    };

    private final TextKind kind;
    private final String text;

    private PlainText(TextKind kind, String text) {
        this.kind = kind;
        this.text = text;
    }

    /**
     * Normalizes and checks entered or imported text.
     *
     * @param kind what the text is, which sets its cap and the characters it accepts
     * @param raw the text as entered
     * @return the normalized text, possibly empty
     * @throws RuleViolation with {@link TextTooLong} when the raw text is more than eight UTF-16
     *     units per code point of the kind's cap, or the normalized text is longer than the cap; or
     *     with {@link TextHasForbiddenCharacters} when it holds a character the kind refuses
     */
    public static PlainText of(TextKind kind, String raw) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(raw, "raw");
        if (raw.length() > kind.maxLength() * MAX_RAW_UNITS_PER_CODE_POINT) {
            throw new RuleViolation(new TextTooLong(kind, kind.maxLength()));
        }
        String lines = kind.allowsLineFeed() ? withLineFeeds(raw) : raw;
        String normal = trimmed(Normalizer.normalize(lines, Normalizer.Form.NFC), kind);
        if (normal.codePointCount(0, normal.length()) > kind.maxLength()) {
            throw new RuleViolation(new TextTooLong(kind, kind.maxLength()));
        }
        if (holdsForbidden(normal, kind)) {
            throw new RuleViolation(new TextHasForbiddenCharacters(kind));
        }
        return new PlainText(kind, normal);
    }

    /**
     * Rebuilds stored text through the same rule as {@link #of}, so text the rule refuses is
     * damaged state, not a user's mistake.
     *
     * @param kind what the stored text is
     * @param stored the text as stored
     * @return the text
     * @throws StoredStateInvalid when the rule refuses the stored text; its cause is the refusal
     */
    public static PlainText ofStored(TextKind kind, String stored) {
        return StoredStateInvalid.whileRestoring(PlainText.class, () -> of(kind, stored));
    }

    /**
     * What the text is.
     *
     * @return its kind
     */
    public TextKind kind() {
        return kind;
    }

    /**
     * The normalized text.
     *
     * @return the text, possibly empty
     */
    public String text() {
        return text;
    }

    /**
     * Whether nothing visible is left once the ends are trimmed.
     *
     * @return {@code true} for empty or blank input, and for input that renders as nothing
     */
    public boolean isEmpty() {
        return text.isEmpty();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof PlainText that && kind == that.kind && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, text);
    }

    /**
     * A form that hides the text, because names, addresses and notes are personal data (NFR-19).
     *
     * @return {@code PlainText[redacted]}
     */
    @Override
    public String toString() {
        return "PlainText[redacted]";
    }

    private static String withLineFeeds(String text) {
        return text.replace(CARRIAGE_RETURN_LINE_FEED, String.valueOf(LINE_FEED))
                .replace(CARRIAGE_RETURN, LINE_FEED);
    }

    /**
     * At the start nothing comes before a variation selector, so it goes with the rest. At the end
     * a run of selectors keeps what the character before it carries, so an emoji keeps its
     * presentation.
     */
    private static String trimmed(String text, TextKind kind) {
        int start = 0;
        int end = text.length();
        while (start < end && isInvisible(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        while (end > start) {
            int last = text.codePointBefore(end);
            if (isVariationSelector(last)) {
                int runStart = startOfSelectorRun(text, start, end);
                int keptEnd = runStart > start ? endOfCarriedSelectors(text, runStart, end, kind) : runStart;
                if (keptEnd > runStart) {
                    end = keptEnd;
                    break;
                }
                end = runStart;
            } else if (isInvisible(last)) {
                end -= Character.charCount(last);
            } else {
                break;
            }
        }
        return text.substring(start, end);
    }

    private static int startOfSelectorRun(String text, int start, int end) {
        int runStart = end;
        while (runStart > start && isVariationSelector(text.codePointBefore(runStart))) {
            runStart -= Character.charCount(text.codePointBefore(runStart));
        }
        return runStart;
    }

    /**
     * A name or an address keeps a variation selector only as VS16 right after an emoji that shows
     * as text by default. Anywhere else a selector changes nothing a reader sees, so "Van 1" with
     * one, an emoji with a second, or the truck emoji U+1F69A with VS16 would look like the text
     * without it (BR-48).
     */
    private static boolean holdsForbidden(String text, TextKind kind) {
        boolean afterTextEmoji = false;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            if (isVariationSelector(codePoint)) {
                if (!(kind.allowsInvisibleFillers() || (afterTextEmoji && codePoint == EMOJI_PRESENTATION_SELECTOR))) {
                    return true;
                }
                afterTextEmoji = false;
            } else if (isForbidden(codePoint, kind)) {
                return true;
            } else {
                afterTextEmoji = showsAsTextByDefault(codePoint);
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    /**
     * Where a run of variation selectors at the end of the text should end, keeping what the
     * character before it carries. A note keeps the whole run after any visible character, as it
     * keeps every selector inside it. A name or an address keeps only VS16 after an emoji that
     * shows as text by default, and trims the rest.
     */
    private static int endOfCarriedSelectors(String text, int runStart, int end, TextKind kind) {
        int base = text.codePointBefore(runStart);
        if (kind.allowsInvisibleFillers()) {
            return isInvisible(base) ? runStart : end;
        }
        int first = text.codePointAt(runStart);
        return showsAsTextByDefault(base) && first == EMOJI_PRESENTATION_SELECTOR
                ? runStart + Character.charCount(first)
                : runStart;
    }

    /**
     * An emoji that shows as text unless VS16 follows it, such as U+2764 or U+00A9, so VS16 changes
     * what a reader sees. One that already shows as an emoji, such as U+1F600, is left out. So are
     * the keycap bases, the digits, {@code #} and {@code *}, which Unicode counts as emoji though
     * they are not pictographs: with VS16 but no keycap mark, U+20E3, they show as themselves.
     */
    private static boolean showsAsTextByDefault(int codePoint) {
        return Character.isEmoji(codePoint)
                && !Character.isEmojiPresentation(codePoint)
                && Character.isExtendedPictographic(codePoint);
    }

    /**
     * An unpaired surrogate reaches here as its own code point, of type {@code SURROGATE}, because
     * {@link String#codePoints()} yields a surrogate that is not half of a pair unchanged.
     */
    private static boolean isForbidden(int codePoint, TextKind kind) {
        if (!kind.allowsInvisibleFillers() && isInvisibleFiller(codePoint)) {
            return true;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL -> !(codePoint == LINE_FEED && kind.allowsLineFeed());
            case Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> !kind.allowsLineFeed();
            case Character.FORMAT ->
                isBidiControl(codePoint) || !(kind.allowsFormatCharacters() || isJoinerOrDirectionMark(codePoint));
            case Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED -> true;
            default -> false;
        };
    }

    private static boolean isJoinerOrDirectionMark(int codePoint) {
        return codePoint == ARABIC_LETTER_MARK
                || (codePoint >= ZERO_WIDTH_NON_JOINER && codePoint <= RIGHT_TO_LEFT_MARK);
    }

    private static boolean isInvisible(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || Character.getType(codePoint) == Character.FORMAT
                || isBlankSymbol(codePoint)
                || isInAny(codePoint, DEFAULT_IGNORABLE);
    }

    /**
     * The format characters among the default-ignorable code points have a rule of their own, and
     * the variation selectors one that depends on the character before them.
     */
    private static boolean isInvisibleFiller(int codePoint) {
        return isBlankSymbol(codePoint)
                || (isInAny(codePoint, DEFAULT_IGNORABLE)
                        && Character.getType(codePoint) != Character.FORMAT
                        && !isVariationSelector(codePoint));
    }

    /** A symbol that renders as nothing, which no Unicode property marks as ignorable. */
    private static boolean isBlankSymbol(int codePoint) {
        return codePoint == BRAILLE_PATTERN_BLANK || codePoint == MUSICAL_SYMBOL_NULL_NOTEHEAD;
    }

    private static boolean isVariationSelector(int codePoint) {
        return isInAny(codePoint, VARIATION_SELECTORS);
    }

    private static boolean isInAny(int codePoint, int[][] ranges) {
        for (int[] range : ranges) {
            if (codePoint >= range[0] && codePoint <= range[1]) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBidiControl(int codePoint) {
        return (codePoint >= LEFT_TO_RIGHT_EMBEDDING && codePoint <= RIGHT_TO_LEFT_OVERRIDE)
                || (codePoint >= LEFT_TO_RIGHT_ISOLATE && codePoint <= POP_DIRECTIONAL_ISOLATE);
    }
}
