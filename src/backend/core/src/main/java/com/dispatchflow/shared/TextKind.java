package com.dispatchflow.shared;

/**
 * The kinds of free text {@link PlainText} holds, each with its cap in code points (ADR-106). The
 * caps are starting values, not requirements: no requirement states a length yet.
 */
public enum TextKind {
    /** A person's, customer's, vehicle's or depot's name, or another short field. */
    NAME(100, false, false, false),
    /** An address as typed. */
    ADDRESS(200, false, false, false),
    /** A note for a driver or a free comment, which may run over several lines. */
    NOTE(500, true, true, true);

    private final int maxLength;
    private final boolean allowsLineFeed;
    private final boolean allowsFormatCharacters;
    private final boolean allowsInvisibleFillers;

    TextKind(int maxLength, boolean allowsLineFeed, boolean allowsFormatCharacters, boolean allowsInvisibleFillers) {
        this.maxLength = maxLength;
        this.allowsLineFeed = allowsLineFeed;
        this.allowsFormatCharacters = allowsFormatCharacters;
        this.allowsInvisibleFillers = allowsInvisibleFillers;
    }

    /**
     * The most text this kind holds.
     *
     * @return the cap, in Unicode code points after normalization
     */
    public int maxLength() {
        return maxLength;
    }

    /**
     * Whether text of this kind may break lines: a line feed (U+000A), the one control character
     * any kind accepts, and the line and paragraph separators U+2028 and U+2029. A carriage return
     * in such text becomes a line feed.
     *
     * @return {@code true} for {@link #NOTE} only
     */
    public boolean allowsLineFeed() {
        return allowsLineFeed;
    }

    /**
     * Whether text of this kind may hold format characters (category Cf) inside it. Every kind
     * accepts the zero-width non-joiner and joiner and the left-to-right and right-to-left marks,
     * U+200C to U+200F, which Hebrew and mixed-direction text use, and the Arabic letter mark,
     * U+061C, which Arabic addresses use (Q-83). None accepts a bidi embedding, override or isolate
     * control.
     *
     * @return {@code true} for {@link #NOTE} only
     */
    public boolean allowsFormatCharacters() {
        return allowsFormatCharacters;
    }

    /**
     * Whether text of this kind may hold, inside it, the invisible fillers that are not format
     * characters: Unicode's default-ignorable code points that are not of category Cf, such as the
     * combining grapheme joiner (U+034F), the Hangul fillers (U+115F, U+1160, U+3164, U+FFA0) and
     * the Khmer inherent vowels (U+17B4, U+17B5), and the blank Braille pattern (U+2800). In a name
     * or an address one would make two values look alike (BR-48, ADR-111). Every kind trims them
     * from its ends.
     *
     * <p>A variation selector is a filler too, unless it follows an emoji (a character that {@link
     * Character#isExtendedPictographic} accepts) or another selector that follows one. So a kind
     * that refuses fillers keeps a heart emoji, U+2764 U+FE0F, but refuses a selector after a
     * letter or a digit inside it, and trims one at its end. A kind that allows fillers keeps every
     * selector inside it, and one at its end after any visible character.
     *
     * @return {@code true} for {@link #NOTE} only
     */
    public boolean allowsInvisibleFillers() {
        return allowsInvisibleFillers;
    }
}
