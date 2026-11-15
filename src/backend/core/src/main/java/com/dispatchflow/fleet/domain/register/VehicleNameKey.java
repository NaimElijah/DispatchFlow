package com.dispatchflow.fleet.domain.register;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * The form BR-48 compares vehicle names in, so two names that look alike are one name. The steps,
 * on the name's normalized text:
 *
 * <ol>
 *   <li>every variation selector and every format character (category Cf, such as the left-to-right
 *       mark and the zero-width joiner) deleted, because neither changes the letters a dispatcher
 *       reads. This comes before NFC, because one between a letter and its mark blocks composition:
 *       {@code e}, ZWNJ and U+0301 would otherwise stay apart from {@code é};
 *   <li>Unicode NFC;
 *   <li>one geresh: {@code '}, {@code ‘}, {@code ’} and the backtick become {@code ׳};
 *   <li>one gershayim: two gereshes, {@code "}, {@code “} and {@code ”} become {@code ״}, so
 *       {@code ''} and {@code ’’} count as it too;
 *   <li>one hyphen: the hyphens and dashes U+2010 to U+2015, the minus sign U+2212, the small
 *       hyphen-minus U+FE63 and the fullwidth hyphen-minus U+FF0D become {@code -};
 *   <li>each run of Unicode whitespace, no-break spaces and the hair space included, collapsed to
 *       one space, and the ends trimmed.
 * </ol>
 *
 * <p>The geresh and gershayim folds are those of Customers &amp; Orders' address matching (FR-398,
 * ADR-102), copied rather than shared: Fleet can't import another module's domain, and the kernel
 * leaves each matching form to its module (ADR-106). A change to one copy is a change to both.
 *
 * <p>Only the comparison uses it: the {@link VehicleName} keeps the text as entered. Case still
 * counts, as it does for the name itself. Lookalike letters from other scripts and the order a
 * mixed-direction name is shown in are not folded (ADR-111).
 *
 * @param text the name in its matching form
 */
record VehicleNameKey(String text) {

    /**
     * Format characters, and Unicode's variation selectors as of Unicode 15: the Mongolian free
     * variation selectors, VS1 to VS16 and VS17 to VS256, for which the JDK's regular expressions
     * have no property.
     */
    private static final Pattern INVISIBLE =
            Pattern.compile("[\\p{Cf}\\x{180B}-\\x{180D}\\x{180F}\\x{FE00}-\\x{FE0F}\\x{E0100}-\\x{E01EF}]");

    private static final String GERESH = "\u05f3";
    private static final String GERSHAYIM = "\u05f4";
    private static final Pattern GERESH_VARIANTS = Pattern.compile("['\u2018\u2019`]");
    private static final Pattern GERSHAYIM_VARIANTS = Pattern.compile(GERESH + GERESH + "|[\"\u201c\u201d]");

    private static final String HYPHEN_MINUS = "-";
    private static final Pattern HYPHEN_VARIANTS = Pattern.compile("[\u2010-\u2015\u2212\ufe63\uff0d]");

    /** With this flag {@code \s} is Unicode's White_Space, which holds U+00A0 and U+200A. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private static final String SPACE = " ";

    /**
     * The matching form of a name.
     *
     * @param name the name
     * @return its key
     */
    static VehicleNameKey of(VehicleName name) {
        String visible = INVISIBLE.matcher(name.value()).replaceAll("");
        String composed = Normalizer.normalize(visible, Normalizer.Form.NFC);
        String geresh = GERESH_VARIANTS.matcher(composed).replaceAll(GERESH);
        String gershayim = GERSHAYIM_VARIANTS.matcher(geresh).replaceAll(GERSHAYIM);
        String hyphens = HYPHEN_VARIANTS.matcher(gershayim).replaceAll(HYPHEN_MINUS);
        return new VehicleNameKey(WHITESPACE.matcher(hyphens).replaceAll(SPACE).strip());
    }
}
