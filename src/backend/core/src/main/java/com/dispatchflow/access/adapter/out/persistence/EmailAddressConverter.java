package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores an {@link EmailAddress} as its canonical text (BR-45), the form the unique email index
 * compares, and rebuilds it through {@link EmailAddress#ofStored}.
 */
@Converter(autoApply = true)
public final class EmailAddressConverter extends StoredValueConverter<EmailAddress, String> {

    /** Creates the converter, which Hibernate does once. */
    public EmailAddressConverter() {
        super(EmailAddress.class, EmailAddress::value, EmailAddress::ofStored);
    }
}
