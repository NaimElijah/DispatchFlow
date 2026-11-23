package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.DispatchPhone;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores the company's {@link DispatchPhone} as its E.164 text, rebuilt through {@code DispatchPhone.ofStored}. */
@Converter(autoApply = true)
public final class DispatchPhoneConverter extends StoredValueConverter<DispatchPhone, String> {

    /** Creates the converter, which Hibernate does once. */
    public DispatchPhoneConverter() {
        super(DispatchPhone.class, dispatchPhone -> dispatchPhone.phone().e164(), DispatchPhone::ofStored);
    }
}
