package com.dispatchflow.messaging.domain;

import com.dispatchflow.messaging.domain.MessagingError.DispatchPhoneNotIsraeli;
import com.dispatchflow.messaging.domain.MessagingError.DispatchPhoneTooLong;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The company's dispatch phone, which the "your delivery changed" and "missed you" messages give
 * (FR-354, FR-355, FR-125). Messages write it in national form, as digits only, such as {@code
 * 0523456789}, so it is an Israeli number, and that form is at most 11 characters (FR-382,
 * ADR-108).
 *
 * <p>It is the company's number, printed in every such message, so it is not marked as personal
 * data; the {@link PhoneNumber} it holds still prints redacted.
 */
public final class DispatchPhone {

    /** The most characters the national form may have (FR-382). */
    public static final int MAXIMUM_LENGTH = 11;

    private static final PhoneNumberUtil PHONE_NUMBERS = PhoneNumberUtil.getInstance();
    private static final String ISRAEL = "IL";
    private static final String NOT_A_DIGIT = "[^0-9]";

    private final PhoneNumber phone;
    private final String national;

    private DispatchPhone(PhoneNumber phone, String national) {
        this.phone = phone;
        this.national = national;
    }

    /**
     * Takes an Admin's dispatch phone.
     *
     * @param phone the number, already parsed by {@link PhoneNumber#parse}
     * @return the dispatch phone
     * @throws RuleViolation with {@link DispatchPhoneNotIsraeli} when it is not an Israeli number, or
     *     {@link DispatchPhoneTooLong} when its national form is longer than 11 characters
     */
    public static DispatchPhone of(PhoneNumber phone) {
        Phonenumber.PhoneNumber number = read(Objects.requireNonNull(phone, "phone"));
        if (!ISRAEL.equals(PHONE_NUMBERS.getRegionCodeForNumber(number))) {
            throw new RuleViolation(new DispatchPhoneNotIsraeli());
        }
        String national =
                PHONE_NUMBERS.format(number, PhoneNumberFormat.NATIONAL).replaceAll(NOT_A_DIGIT, "");
        if (national.length() > MAXIMUM_LENGTH) {
            throw new RuleViolation(new DispatchPhoneTooLong(MAXIMUM_LENGTH));
        }
        return new DispatchPhone(phone, national);
    }

    /**
     * Rebuilds a stored dispatch phone.
     *
     * @param e164 the stored number
     * @return the dispatch phone
     * @throws StoredStateInvalid when the number is not E.164, not Israeli, or its national form is
     *     too long
     */
    public static DispatchPhone ofStored(String e164) {
        PhoneNumber phone = PhoneNumber.ofStored(e164);
        return StoredStateInvalid.whileRestoring(DispatchPhone.class, () -> of(phone));
    }

    /**
     * The number.
     *
     * @return the phone number
     */
    public PhoneNumber phone() {
        return phone;
    }

    /**
     * The number as messages write it.
     *
     * @return the national form, digits only, such as {@code 0523456789}
     */
    public String national() {
        return national;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof DispatchPhone that && phone.equals(that.phone);
    }

    @Override
    public int hashCode() {
        return phone.hashCode();
    }

    @Override
    public String toString() {
        return "DispatchPhone[" + phone + "]";
    }

    private static Phonenumber.PhoneNumber read(PhoneNumber phone) {
        try {
            return PHONE_NUMBERS.parse(phone.e164(), ISRAEL);
        } catch (NumberParseException unreadable) {
            // A number from PhoneNumber is E.164, which libphonenumber always reads; the cause is
            // dropped because its message may quote the number.
            throw new AssertionError("an E.164 number must be readable");
        }
    }
}
