package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.InvalidPhoneNumber;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber;
import java.util.Objects;

/**
 * A valid phone number in international form, E.164 (FR-377). Text is read as an Israeli number
 * unless it starts with a country code, and is stored normalized, so {@code 054-123-4567} and
 * {@code +972541234567} are equal.
 *
 * <p>A phone number is personal data: {@link #toString()} hides it, and a refusal names no number,
 * so neither can carry it into a log.
 *
 * @param e164 the number in E.164 form, such as {@code +972541234567}
 */
public record PhoneNumber(String e164) {

    private static final String ISRAEL = "IL";
    private static final PhoneNumberUtil PHONE_NUMBERS = PhoneNumberUtil.getInstance();

    /**
     * Checks the number and normalizes it to E.164.
     *
     * @throws RuleViolation with {@link InvalidPhoneNumber} when the text is not a valid number
     */
    public PhoneNumber {
        Objects.requireNonNull(e164, "e164");
        e164 = PHONE_NUMBERS.format(parsed(e164), PhoneNumberFormat.E164);
    }

    /**
     * Reads a phone number as entered or imported (FR-377).
     *
     * @param text the number, in national or international form
     * @return the number
     * @throws RuleViolation with {@link InvalidPhoneNumber} when the text is not a valid number
     */
    public static PhoneNumber parse(String text) {
        return new PhoneNumber(text);
    }

    /**
     * Whether messages may go to this number: only Israeli mobile numbers get them (FR-378).
     *
     * @return {@code true} for an Israeli mobile number
     */
    public boolean isIsraeliMobile() {
        Phonenumber.PhoneNumber number = parsed(e164);
        return ISRAEL.equals(PHONE_NUMBERS.getRegionCodeForNumber(number))
                && PHONE_NUMBERS.getNumberType(number) == PhoneNumberType.MOBILE;
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

    private static Phonenumber.PhoneNumber parsed(String text) {
        Phonenumber.PhoneNumber number;
        try {
            number = PHONE_NUMBERS.parse(text, ISRAEL);
        } catch (NumberParseException notANumber) {
            // The cause is dropped on purpose: its message may quote the text, which is personal data.
            throw new RuleViolation(new InvalidPhoneNumber());
        }
        if (!PHONE_NUMBERS.isValidNumber(number)) {
            throw new RuleViolation(new InvalidPhoneNumber());
        }
        return number;
    }
}
