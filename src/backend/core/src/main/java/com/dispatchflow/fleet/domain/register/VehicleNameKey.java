package com.dispatchflow.fleet.domain.register;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * The form BR-48 compares vehicle names in, so two names that look alike are one name. The steps,
 * on the name's normalized text:
 *
 * <ol>
 *   <li>Unicode NFC;
 *   <li>every variation selector and every format character (category Cf, such as the left-to-right
 *       mark and the zero-width joiner) deleted, because neither changes the letters a dispatcher
 *       reads;
 *   <li>each run of Unicode whitespace, no-break spaces included, collapsed to one space, and the
 *       ends trimmed.
 * </ol>
 *
 * <p>Only the comparison uses it: the {@link VehicleName} keeps the text as entered. Case still
 * counts, as it does for the name itself.
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

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String SPACE = " ";

    /**
     * The matching form of a name.
     *
     * @param name the name
     * @return its key
     */
    static VehicleNameKey of(VehicleName name) {
        String composed = Normalizer.normalize(name.value(), Normalizer.Form.NFC);
        String visible = INVISIBLE.matcher(composed).replaceAll("");
        return new VehicleNameKey(WHITESPACE.matcher(visible).replaceAll(SPACE).strip());
    }
}
