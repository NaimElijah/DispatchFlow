package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.PersonalData;
import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The form an address is matched by (FR-310, FR-398, ADR-086, ADR-102): an import row finds its
 * customer when the two normalized texts are equal. The steps:
 *
 * <ol>
 *   <li>format characters (such as ZWJ, ZWNJ, RLM and LRM) deleted. This comes before NFC,
 *       because one between a letter and its mark blocks composition: {@code e}, ZWNJ and U+0301
 *       would otherwise stay apart from {@code é};
 *   <li>Unicode NFC;
 *   <li>the combining marks NFC leaves (such as niqqud) deleted, so two ways of typing one address
 *       match: {@code הֶרְצֵל} matches {@code הרצל} (ADR-102). Only the matching key drops these and
 *       the format characters; the {@link Address} keeps them;
 *   <li>one geresh: {@code '}, {@code ‘}, {@code ’} and the backtick become {@code ׳};
 *   <li>one gershayim: two gereshes, {@code "}, {@code “} and {@code ”} become {@code ״}, so
 *       {@code ''} and {@code ’’} count as it too;
 *   <li>a geresh ends its word, so {@code רח'הרצל} reads as {@code רח׳ הרצל};
 *   <li>every character other than a letter, a digit, whitespace, the geresh or the gershayim
 *       becomes a space, so {@code הרצל 5/3} stays apart from {@code הרצל 53};
 *   <li>whitespace trimmed, and each run of it, no-break spaces included, collapsed to one space;
 *   <li>abbreviations expanded from a fixed table, where an abbreviation is a whole word: no letter
 *       or digit touches it;
 *   <li>Latin letters lower-cased. Hebrew has no case, and other scripts are left as they are.
 * </ol>
 *
 * <p>The table holds the three abbreviations ADR-086 names, and only those: רח׳ for רחוב, שד׳ for
 * שדרות and ת״א for תל אביב (ADR-102).
 *
 * <p>It is a class with one factory, so a normalized address is always computed from an {@link
 * Address} and never typed. It is personal data: {@link #toString()} hides it.
 */
public final class NormalizedAddress implements PersonalData {

    private static final String GERESH = "\u05f3";
    private static final String GERSHAYIM = "\u05f4";
    private static final Pattern FORMAT_CHARACTERS = Pattern.compile("\\p{Cf}");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{Mn}");
    private static final Pattern GERESH_VARIANTS = Pattern.compile("['\u2018\u2019`]");
    private static final Pattern GERSHAYIM_VARIANTS = Pattern.compile(GERESH + GERESH + "|[\"\u201c\u201d]");
    private static final Pattern GERESH_BEFORE_A_LETTER =
            Pattern.compile(GERESH + "(?=\\p{L})", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern PUNCTUATION =
            Pattern.compile("[^\\p{L}\\p{N}\\s" + GERESH + GERSHAYIM + "]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String SPACE = " ";

    /** An abbreviation, written with the one geresh or gershayim, and the words it stands for. */
    private record Abbreviation(Pattern word, String expansion) {

        static Abbreviation of(String abbreviation, String expansion) {
            Pattern word = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(abbreviation) + "(?![\\p{L}\\p{N}])");
            return new Abbreviation(word, Matcher.quoteReplacement(expansion));
        }
    }

    private static final List<Abbreviation> ABBREVIATIONS = List.of(
            Abbreviation.of("רח" + GERESH, "רחוב"),
            Abbreviation.of("שד" + GERESH, "שדרות"),
            Abbreviation.of("ת" + GERSHAYIM + "א", "תל אביב"));

    private final String text;

    private NormalizedAddress(String text) {
        this.text = text;
    }

    /**
     * Normalizes an address.
     *
     * @param address the address as typed
     * @return its normalized form, which holds a letter or a digit, because {@link Address} refuses
     *     an address whose form holds neither
     */
    public static NormalizedAddress of(Address address) {
        return new NormalizedAddress(normalize(address.text()));
    }

    /** FR-398's steps, for {@link Address} to check that something is left. */
    static String normalize(String typed) {
        String unformatted = FORMAT_CHARACTERS.matcher(typed).replaceAll("");
        String composed = Normalizer.normalize(unformatted, Normalizer.Form.NFC);
        String visible = COMBINING_MARKS.matcher(composed).replaceAll("");
        String geresh = GERESH_VARIANTS.matcher(visible).replaceAll(GERESH);
        String gershayim = GERSHAYIM_VARIANTS.matcher(geresh).replaceAll(GERSHAYIM);
        String wordEnds = GERESH_BEFORE_A_LETTER.matcher(gershayim).replaceAll(GERESH + SPACE);
        String spaced = PUNCTUATION.matcher(wordEnds).replaceAll(SPACE);
        String expanded = WHITESPACE.matcher(spaced).replaceAll(SPACE).strip();
        for (Abbreviation abbreviation : ABBREVIATIONS) {
            expanded = abbreviation.word().matcher(expanded).replaceAll(abbreviation.expansion());
        }
        return lowerCaseLatin(expanded);
    }

    private static String lowerCaseLatin(String text) {
        StringBuilder lowered = new StringBuilder(text.length());
        text.codePoints()
                .map(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN
                        ? Character.toLowerCase(codePoint)
                        : codePoint)
                .forEach(lowered::appendCodePoint);
        return lowered.toString();
    }

    /**
     * The normalized text, which the persistence adapter stores and matches on.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof NormalizedAddress that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    /**
     * A form that hides the address, because it is personal data.
     *
     * @return {@code NormalizedAddress[redacted]}
     */
    @Override
    public String toString() {
        return "NormalizedAddress[redacted]";
    }
}
