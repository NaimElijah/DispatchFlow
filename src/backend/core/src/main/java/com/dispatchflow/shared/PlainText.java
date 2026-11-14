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
 * <p>The text is Unicode NFC, with whitespace, space separators such as the no-break space, and
 * format characters such as the zero-width space trimmed from both ends; the interior is kept as
 * entered, except that a {@link TextKind#NOTE}'s carriage returns become line feeds. It holds at
 * most its {@link TextKind}'s cap in code points, and none of these:
 *
 * <ul>
 *   <li>a control character, other than a line feed in a note;
 *   <li>a line or paragraph separator (U+2028, U+2029), other than in a note;
 *   <li>an interior format character in a name or an address, other than U+061C and U+200C to
 *       U+200F;
 *   <li>a bidi embedding, override or isolate control, in any kind;
 *   <li>an unpaired surrogate, or a private-use or unassigned code point.
 * </ul>
 *
 * <p>It may be empty, and text that renders as nothing, such as a Hangul filler or a lone variation
 * selector, counts as empty: each module refuses blank text with its own error, so the message
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
        String normal = trimmed(Normalizer.normalize(lines, Normalizer.Form.NFC));
        if (normal.codePointCount(0, normal.length()) > kind.maxLength()) {
            throw new RuleViolation(new TextTooLong(kind, kind.maxLength()));
        }
        if (normal.codePoints().allMatch(PlainText::isInvisible)) {
            return new PlainText(kind, "");
        }
        if (normal.codePoints().anyMatch(codePoint -> isForbidden(codePoint, kind))) {
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

    private static String trimmed(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isTrimmed(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        while (end > start && isTrimmed(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(start, end);
    }

    private static boolean isTrimmed(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || Character.getType(codePoint) == Character.FORMAT;
    }

    /**
     * An unpaired surrogate reaches here as its own code point, of type {@code SURROGATE}, because
     * {@link String#codePoints()} yields a surrogate that is not half of a pair unchanged.
     */
    private static boolean isForbidden(int codePoint, TextKind kind) {
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
        return isTrimmed(codePoint) || codePoint == BRAILLE_PATTERN_BLANK || isDefaultIgnorable(codePoint);
    }

    private static boolean isDefaultIgnorable(int codePoint) {
        for (int[] range : DEFAULT_IGNORABLE) {
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
