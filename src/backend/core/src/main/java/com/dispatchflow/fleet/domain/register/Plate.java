package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.fleet.domain.FleetError.InvalidPlate;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A vehicle's licence plate in its normalized form (FR-131, ADR-103): trimmed, without spaces or
 * hyphens, with Latin letters in upper case, and then 1 to 10 ASCII letters or digits. So {@code
 * 12-345-67} and {@code 1234567} are the same plate, which BR-48's uniqueness compares.
 *
 * @param value the normalized plate
 */
public record Plate(String value) {

    private static final Pattern NORMALIZED = Pattern.compile("[A-Z0-9]{1,10}");
    private static final Pattern SEPARATORS = Pattern.compile("[ -]");

    /**
     * Normalizes and checks the plate.
     *
     * @throws RuleViolation with {@link InvalidPlate} when the normalized text is empty, longer
     *     than 10 characters, or holds anything but ASCII letters and digits
     */
    public Plate {
        Objects.requireNonNull(value, "value");
        value = normalized(value);
        if (!NORMALIZED.matcher(value).matches()) {
            throw new RuleViolation(new InvalidPlate());
        }
    }

    /**
     * Reads a plate as typed or imported.
     *
     * @param text the plate, with or without spaces and hyphens, in either case
     * @return the normalized plate
     * @throws RuleViolation with {@link InvalidPlate} when the normalized text is not 1 to 10 ASCII
     *     letters or digits
     */
    public static Plate of(String text) {
        return new Plate(text);
    }

    private static String normalized(String text) {
        String stripped = SEPARATORS.matcher(text.strip()).replaceAll("");
        StringBuilder upper = new StringBuilder(stripped.length());
        // Only a to z are mapped, so a locale's own rules (Turkish dotless i) can't change a plate.
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            upper.append(c >= 'a' && c <= 'z' ? (char) (c - 'a' + 'A') : c);
        }
        return upper.toString();
    }
}
