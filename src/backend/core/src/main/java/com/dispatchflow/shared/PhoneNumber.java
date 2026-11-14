package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.InvalidPhoneNumber;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A phone number in international form, E.164 (FR-377). Entered or imported text is read as an
 * Israeli number unless it starts with a country code, and is stored normalized, so {@code
 * 052-345-6789} and {@code +972523456789} are equal.
 *
 * <p>It is a class rather than a record so there are exactly two ways in: {@link #parse} checks
 * the number against libphonenumber's metadata, and {@link #ofStored} checks only E.164 syntax. A
 * later metadata release that stops accepting a range then refuses new input, but never stops a
 * stored customer from loading.
 *
 * <p>A phone number is personal data: {@link #toString()} hides it, and a refusal names no number,
 * so neither can carry it into a log.
 */
public final class PhoneNumber {

    private static final String ISRAEL = "IL";
    private static final PhoneNumberUtil PHONE_NUMBERS = PhoneNumberUtil.getInstance();
    private static final Pattern E164 = Pattern.compile("\\+[1-9][0-9]{1,14}");

    private final String e164;

    private PhoneNumber(String e164) {
        this.e164 = e164;
    }

    /**
     * Reads a phone number as entered or imported (FR-377).
     *
     * @param text the number, in national or international form
     * @return the number, normalized to E.164
     * @throws RuleViolation with {@link InvalidPhoneNumber} when the text holds a letter or is not
     *     a valid number
     */
    public static PhoneNumber parse(String text) {
        Objects.requireNonNull(text, "text");
        // libphonenumber reads letters as keypad digits, so 052-FLOWERS would become a real number.
        if (text.codePoints().anyMatch(Character::isLetter)) {
            throw new RuleViolation(new InvalidPhoneNumber());
        }
        Phonenumber.PhoneNumber number;
        try {
            number = PHONE_NUMBERS.parse(text, ISRAEL);
        } catch (NumberParseException notANumber) {
            // The cause is dropped on purpose: its message may quote the text, which is personal
            // data.
            throw new RuleViolation(new InvalidPhoneNumber());
        }
        if (!PHONE_NUMBERS.isValidNumber(number)) {
            throw new RuleViolation(new InvalidPhoneNumber());
        }
        return new PhoneNumber(PHONE_NUMBERS.format(number, PhoneNumberFormat.E164));
    }

    /**
     * Rebuilds a stored number, checking only that it is E.164 text.
     *
     * @param e164 the stored number, such as {@code +972523456789}
     * @return the number
     * @throws StoredStateInvalid when the text is not E.164
     */
    public static PhoneNumber ofStored(String e164) {
        Objects.requireNonNull(e164, "e164");
        if (!E164.matcher(e164).matches()) {
            throw new StoredStateInvalid(PhoneNumber.class, "the stored number is not E.164");
        }
        return new PhoneNumber(e164);
    }

    /**
     * The number in E.164 form.
     *
     * @return the number, such as {@code +972523456789}
     */
    public String e164() {
        return e164;
    }

    /**
     * Whether messages may go to this number: only Israeli mobile numbers get them (FR-378).
     *
     * @return {@code true} for an Israeli mobile number; {@code false} for any other, and for a
     *     stored number the current metadata can't classify
     */
    public boolean isIsraeliMobile() {
        Phonenumber.PhoneNumber number;
        try {
            number = PHONE_NUMBERS.parse(e164, ISRAEL);
        } catch (NumberParseException unclassifiable) {
            // A stored number the current metadata can't read gets no messages; it still loads.
            return false;
        }
        return ISRAEL.equals(PHONE_NUMBERS.getRegionCodeForNumber(number))
                && PHONE_NUMBERS.getNumberType(number) == PhoneNumberType.MOBILE;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof PhoneNumber that && e164.equals(that.e164);
    }

    @Override
    public int hashCode() {
        return e164.hashCode();
    }

    /**
     * A form that hides the number, because a phone number is personal data.
     *
     * @return {@code PhoneNumber[redacted]}
     */
    @Override
    public String toString() {
        return "PhoneNumber[redacted]";
    }
}
