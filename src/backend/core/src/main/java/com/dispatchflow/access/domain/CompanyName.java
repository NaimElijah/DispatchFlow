package com.dispatchflow.access.domain;

import com.dispatchflow.access.domain.AccessError.BlankCompanyName;
import com.dispatchflow.access.domain.AccessError.CompanyNameTooLong;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;

/**
 * The company's name, which starts every message to a recipient (FR-125, FR-382): not blank, and at
 * most 19 characters, so that with a dispatch phone of up to 11 every message fits one SMS of 70
 * characters.
 *
 * <p>Its length is counted in UTF-16 units, the units a 70-character SMS is counted in, so a
 * character outside the basic plane counts twice.
 *
 * @param value the name, trimmed
 */
public record CompanyName(String value) {

    /** The most characters a company name may have (FR-382). */
    public static final int MAXIMUM_LENGTH = 19;

    /**
     * Trims the name and checks it.
     *
     * @throws RuleViolation with {@link BlankCompanyName} when nothing is left once trimmed, or
     *     with {@link CompanyNameTooLong} for more than 19 characters
     */
    public CompanyName {
        value = Objects.requireNonNull(value, "value").strip();
        if (value.isEmpty()) {
            throw new RuleViolation(new BlankCompanyName());
        }
        if (value.length() > MAXIMUM_LENGTH) {
            throw new RuleViolation(new CompanyNameTooLong(MAXIMUM_LENGTH));
        }
    }
}
