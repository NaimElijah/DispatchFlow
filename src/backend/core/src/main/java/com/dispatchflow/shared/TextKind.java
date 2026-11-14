package com.dispatchflow.shared;

/**
 * The kinds of free text {@link PlainText} holds, each with its cap in code points (ADR-106). The
 * caps are starting values, not requirements: no requirement states a length yet.
 */
public enum TextKind {
    /** A person's, customer's, vehicle's or depot's name, or another short field. */
    NAME(100, false, false),
    /** An address as typed. */
    ADDRESS(200, false, false),
    /** A note for a driver or a free comment, which may run over several lines. */
    NOTE(500, true, true);

    private final int maxLength;
    private final boolean allowsLineFeed;
    private final boolean allowsFormatCharacters;

    TextKind(int maxLength, boolean allowsLineFeed, boolean allowsFormatCharacters) {
        this.maxLength = maxLength;
        this.allowsLineFeed = allowsLineFeed;
        this.allowsFormatCharacters = allowsFormatCharacters;
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
}
