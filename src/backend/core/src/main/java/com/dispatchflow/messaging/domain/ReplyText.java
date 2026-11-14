package com.dispatchflow.messaging.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** The text rules {@link Reply#parse} reads a reply by (FR-363, FR-376). */
final class ReplyText {

    /** The opt-out words, compared upper-cased (FR-363). */
    private static final Set<String> OPT_OUT_WORDS = Set.of("הסר", "הסרה", "להסרה", "STOP");

    /** Ten, the one two-digit rating (FR-376). */
    private static final String TEN = "10";

    private static final char ZERO = '0';
    private static final char NINE = '9';
    private static final char DECIMAL_POINT = '.';

    /** Variation selectors 1 to 16, which pick an emoji's or a text glyph's form. */
    private static final int FIRST_VARIATION_SELECTOR = 0xFE00;

    private static final int LAST_VARIATION_SELECTOR = 0xFE0F;

    /** The combining keycap that makes "1\uFE0F\u20E3" of a digit. */
    private static final int COMBINING_KEYCAP = 0x20E3;

    private ReplyText() {}

    /** Trims what the kernel trims: whitespace, space separators and format characters. */
    static String trimmed(String text) {
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

    /** The text up to its first whitespace or space separator. */
    static String firstToken(String trimmed) {
        int end = 0;
        while (end < trimmed.length() && !isSpace(trimmed.codePointAt(end))) {
            end += Character.charCount(trimmed.codePointAt(end));
        }
        return trimmed.substring(0, end);
    }

    /**
     * Whether a token is an opt-out word (FR-363, ADR-108): in NFKC, so a full-width "\uFF33\uFF34\uFF2F\uFF30" reads
     * as "STOP", with emoji, other and modifier symbols (So, Sk), format characters (Cf) and
     * punctuation (P) removed, and upper-cased.
     */
    static boolean isOptOutWord(String token) {
        return OPT_OUT_WORDS.contains(
                wordOnly(Normalizer.normalize(token, Normalizer.Form.NFKC)).toUpperCase(Locale.ROOT));
    }

    /** The text with every character that isn't part of a word removed. */
    private static String wordOnly(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        text.codePoints().filter(codePoint -> !isIgnoredInAWord(codePoint)).forEach(kept::appendCodePoint);
        return kept.toString();
    }

    /**
     * Whether the opt-out word ignores a character: punctuation, symbols, format characters, and
     * the pieces of an emoji. {@link Character#isExtendedPictographic} is used rather than {@code
     * isEmoji}, which counts digits, and it also covers emoji the JDK's Unicode doesn't assign yet.
     */
    private static boolean isIgnoredInAWord(int codePoint) {
        return isPunctuation(codePoint)
                || switch (Character.getType(codePoint)) {
                    case Character.OTHER_SYMBOL, Character.MODIFIER_SYMBOL, Character.FORMAT -> true;
                    default -> false;
                }
                || Character.isExtendedPictographic(codePoint)
                || Character.isEmojiModifier(codePoint)
                || (codePoint >= FIRST_VARIATION_SELECTOR && codePoint <= LAST_VARIATION_SELECTOR)
                || codePoint == COMBINING_KEYCAP;
    }

    /** The rating that starts a non-empty trimmed text, under FR-376. */
    static Optional<Rating> leadingRating(String trimmed) {
        if (trimmed.startsWith(TEN) && endsANumber(trimmed, TEN.length())) {
            return Optional.of(Rating.of(Rating.HIGHEST));
        }
        char first = trimmed.charAt(0);
        if (first > ZERO && first <= NINE && endsANumber(trimmed, 1)) {
            return Optional.of(Rating.of(first - ZERO));
        }
        return Optional.empty();
    }

    /** Whether the number ending at {@code end} is followed by neither a digit nor "." and a digit. */
    private static boolean endsANumber(String text, int end) {
        if (end == text.length()) {
            return true;
        }
        char next = text.charAt(end);
        boolean decimal = next == DECIMAL_POINT && end + 1 < text.length() && isDigit(text.charAt(end + 1));
        return !isDigit(next) && !decimal;
    }

    private static boolean isDigit(char character) {
        return character >= ZERO && character <= NINE;
    }

    private static boolean isTrimmed(int codePoint) {
        return isSpace(codePoint) || Character.getType(codePoint) == Character.FORMAT;
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean isPunctuation(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION,
                    Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }
}
