package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.PersonalData;

/**
 * Stands in for Orders' ContactName: a module value marked as personal data, with no listed name.
 * It prints redacted, so it is the clean control of the {@code toString} rule.
 */
public record ContactName(String value) implements PersonalData {

    @Override
    public String toString() {
        return "ContactName[redacted]";
    }
}
